import {
  assertEquals,
  assertRejects,
  assertThrows,
} from "jsr:@std/assert@1";
import {
  createHash,
  createSign,
  generateKeyPairSync,
} from "node:crypto";
import {
  canonicalJson,
  verifyEnvelope,
} from "../_shared/pupeye.ts";
import {
  resolveVerifiedEnforcementStatus,
} from "../pupeye-enforcement-status/handler.ts";

function signingIdentity() {
  const { publicKey, privateKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const publicDer = publicKey.export({ type: "spki", format: "der" });
  const publicKeyB64 = publicDer.toString("base64");
  const deviceKeyId = createHash("sha256").update(publicDer).digest("hex");
  return {
    installationId: "11111111-1111-4111-8111-111111111111",
    deviceKeyId,
    publicKeyB64,
    privateKey,
  };
}

function signedEnvelope(
  identity: ReturnType<typeof signingIdentity>,
  action: string,
  payload: Record<string, unknown> = {},
) {
  const generation = 7;
  const timestampEpochMs = Date.now();
  const nonce = "22222222-2222-4222-8222-222222222222";
  const material = [
    "PuppyClicker/PupEye/request/v1",
    action,
    identity.installationId,
    identity.deviceKeyId,
    String(generation),
    String(timestampEpochMs),
    nonce,
    canonicalJson(payload),
  ].join("\n");
  const signature = createSign("SHA256")
    .update(material)
    .end()
    .sign(identity.privateKey)
    .toString("base64");

  return {
    schema: 1,
    action,
    installationId: identity.installationId,
    deviceKeyId: identity.deviceKeyId,
    publicKey: identity.publicKeyB64,
    generation,
    timestampEpochMs,
    nonce,
    payload,
    signature,
  };
}

function fakeAdmin(options: {
  playerStatus?: string;
  integrityState?: string;
  ban?: Record<string, unknown> | null;
  deviceReputationUuid?: string | null;
}) {
  const calls: Array<{ name: string; args: Record<string, unknown> }> = [];
  const installation = {
    id: "33333333-3333-4333-8333-333333333333",
    player_uuid: "44444444-4444-4444-8444-444444444444",
    installation_id: "11111111-1111-4111-8111-111111111111",
    device_key_id: "device-key-placeholder",
    device_reputation_uuid: options.deviceReputationUuid ?? null,
    integrity_state: options.integrityState ?? "clean",
    platform: "android",
    active: true,
    revoked_at: null,
    device_model: "same-model-is-not-identity",
  };
  const player = {
    id: installation.player_uuid,
    status: options.playerStatus ?? "active",
    discord_user_id: null,
  };

  const admin = {
    calls,
    from(table: string) {
      if (table === "pupeye_installations") {
        return selectSingleChain(installation);
      }
      if (table === "pupeye_players") {
        return selectSingleChain(player);
      }
      if (table === "pupeye_ban_hits") {
        return {
          async insert() {
            return { error: null };
          },
        };
      }
      throw new Error("unexpected table " + table);
    },
    async rpc(name: string, args: Record<string, unknown> = {}) {
      calls.push({ name, args });
      if (name === "pupeye_expire_due_bans") return { data: [], error: null };
      if (name === "pupeye_resolve_global_ban") {
        return { data: options.ban ? [options.ban] : [], error: null };
      }
      throw new Error("unexpected rpc " + name);
    },
  };
  return admin;
}

function selectSingleChain(row: Record<string, unknown>) {
  const chain: Record<string, any> = {};
  chain.select = () => chain;
  chain.eq = () => chain;
  chain.maybeSingle = async () => ({ data: row, error: null });
  chain.single = async () => ({ data: row, error: null });
  return chain;
}

Deno.test("invalid signed envelope is rejected before enforcement lookup", () => {
  const identity = signingIdentity();
  const envelope = signedEnvelope(identity, "enforcement-status");
  envelope.signature = envelope.signature.slice(0, -4) + "AAAA";
  assertThrows(
    () => verifyEnvelope(envelope, "enforcement-status"),
    Error,
    "signature",
  );
});

Deno.test("signed status returns global ban for a recognized device reputation", async () => {
  const identity = signingIdentity();
  const verified = verifyEnvelope(signedEnvelope(identity, "enforcement-status"), "enforcement-status");
  const deviceReputationUuid = "55555555-5555-4555-8555-555555555555";
  const admin = fakeAdmin({
    deviceReputationUuid,
    ban: {
      ban_uuid: "66666666-6666-4666-8666-666666666666",
      public_ban_id: "PGB-DEVICE-0001",
      kind: "temporary",
      status: "active",
      reason_code: "DEVICE_BAN_EVASION",
      public_reason: "This device is temporarily banned from Puppy Clicker.",
      issued_at: "2026-09-24T20:00:00Z",
      expires_at: "2026-09-25T20:00:00Z",
      device_wide: true,
    },
  });
  // Match the generated key to the fake installation after signature verification.
  const result = await resolveVerifiedEnforcementStatus(admin, verified, {
    installationDeviceKeyOverrideForTest: identity.deviceKeyId,
  });
  assertEquals(result.status, 403);
  assertEquals(result.body.code, "GLOBAL_BANNED");
  assertEquals((result.body.ban as Record<string, unknown>).id, "PGB-DEVICE-0001");
  const resolveCall = admin.calls.find((call) => call.name === "pupeye_resolve_global_ban");
  assertEquals(resolveCall?.args.p_device_reputation_uuid, deviceReputationUuid);
});

Deno.test("weak device metadata without a reputation target does not propagate a ban", async () => {
  const identity = signingIdentity();
  const verified = verifyEnvelope(signedEnvelope(identity, "enforcement-status"), "enforcement-status");
  const admin = fakeAdmin({ ban: null, deviceReputationUuid: null });
  const result = await resolveVerifiedEnforcementStatus(admin, verified, {
    installationDeviceKeyOverrideForTest: identity.deviceKeyId,
  });
  assertEquals(result.status, 200);
  assertEquals(result.body.state, "ALLOWED");
});

Deno.test("review state returns REVIEW_REQUIRED", async () => {
  const identity = signingIdentity();
  const verified = verifyEnvelope(signedEnvelope(identity, "enforcement-status"), "enforcement-status");
  const admin = fakeAdmin({ playerStatus: "review", ban: null });
  const result = await resolveVerifiedEnforcementStatus(admin, verified, {
    installationDeviceKeyOverrideForTest: identity.deviceKeyId,
  });
  assertEquals(result.status, 409);
  assertEquals(result.body.code, "REVIEW_REQUIRED");
});
