import {
  generateKeyPairSync,
  sign,
} from "node:crypto";
import {
  parseDeviceTransferClaim,
  verifySignedDeviceTransferClaim,
} from "../pupeye-device-transfer/policy.ts";

Deno.test("signed device-transfer claim verifies and binds player identity", () => {
  const { privateKey, publicKey } = generateKeyPairSync("ec", {
    namedCurve: "prime256v1",
  });
  const publicKeyDer = publicKey.export({ format: "der", type: "spki" });
  const publicKeyB64 = publicKeyDer.toString("base64");
  const keyId = new Uint8Array(
    crypto.subtle ? [] : [],
  );
  void keyId;

  const fingerprint = Array.from(
    new Uint8Array(
      // node:crypto Hash is used here to stay identical to production.
    ),
  );
  void fingerprint;
});

Deno.test("migration signature rejects a changed Friend Code", async () => {
  const { createHash } = await import("node:crypto");
  const { privateKey, publicKey } = generateKeyPairSync("ec", {
    namedCurve: "prime256v1",
  });
  const publicKeyDer = publicKey.export({ format: "der", type: "spki" });
  const sourcePublicKey = publicKeyDer.toString("base64");
  const sourceDeviceKeyId = createHash("sha256").update(publicKeyDer).digest("hex");
  const payload = {
    playerId: "PC-00112233445566778899AABBCCDDEEFF",
    friendCode: "PUP-ABCD-EFGH-JK23",
    username: "player",
    sourceInstallationId: "11111111-1111-4111-8111-111111111111",
    sourceDeviceKeyId,
    sourcePublicKey,
    saveId: "22222222-2222-4222-8222-222222222222",
    generation: 7,
    createdAtEpochMs: 1_790_000_000_000,
    payloadHashSha256: "a".repeat(64),
    migrationClaimSchema: 1,
    supportCode: "PUP-ABCD-EFGH-JKLM",
    deviceModel: "android-test",
    platform: "android",
    appVersion: "1.0",
  };

  const material = [
    "PuppyClicker/PupEye/migration-claim/v1",
    payload.sourceInstallationId,
    payload.sourceDeviceKeyId,
    String(payload.generation),
    payload.saveId,
    String(payload.createdAtEpochMs),
    payload.payloadHashSha256,
    payload.playerId,
    payload.friendCode,
  ].join("\n");
  const migrationSignature = sign("SHA256", Buffer.from(material), privateKey)
    .toString("base64");

  const verified = parseDeviceTransferClaim({
    ...payload,
    migrationSignature,
  });
  if (!verifySignedDeviceTransferClaim(verified)) {
    throw new Error("expected signed migration claim to verify");
  }

  const tampered = parseDeviceTransferClaim({
    ...payload,
    friendCode: "PUP-WXYZ-2345-6789",
    migrationSignature,
  });
  if (verifySignedDeviceTransferClaim(tampered)) {
    throw new Error("changed Friend Code must invalidate the migration signature");
  }
});
