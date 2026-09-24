import type {
  AdminClient,
  SessionContext,
  VerifiedEnvelope,
} from "../_shared/pupeye.ts";
import { audit } from "../_shared/pupeye.ts";
import { sendBanEventBestEffort } from "../_shared/discord-ban-notify.ts";
import {
  classifySaveAttestation,
  type ValidatedSaveAttestation,
} from "./policy.ts";

export type AttestationResponse = {
  status: number;
  body: Record<string, unknown>;
};

export type CreatedTemporaryBan = {
  id: string;
  issuedAt: string;
  expiresAt: string;
  deviceWide: boolean;
};

export type AttestationStore = {
  existingHashes(saveId: string): Promise<string[]>;
  recordAttestation(input: ValidatedSaveAttestation): Promise<void>;
  updateSaveHead(input: ValidatedSaveAttestation): Promise<void>;
  recordConflict(
    input: ValidatedSaveAttestation,
    existingHashes: string[],
  ): Promise<void>;
  distinctConflictCount24h(): Promise<number>;
  createTemporaryGlobalBan(
    input: ValidatedSaveAttestation,
  ): Promise<CreatedTemporaryBan>;
};

export async function processSaveAttestation(
  store: AttestationStore,
  input: ValidatedSaveAttestation,
): Promise<AttestationResponse> {
  const existingHashes = await store.existingHashes(input.saveId);
  const initial = classifySaveAttestation({
    incomingHash: input.payloadHashSha256,
    existingHashes,
    distinctSaveConflicts24h: 0,
  });

  if (initial === "IDEMPOTENT") {
    await store.updateSaveHead(input);
    return {
      status: 200,
      body: {
        ok: true,
        state: "ATTESTED",
        idempotent: true,
      },
    };
  }

  if (initial === "NEW") {
    await store.recordAttestation(input);
    await store.updateSaveHead(input);
    return {
      status: 200,
      body: {
        ok: true,
        state: "ATTESTED",
        idempotent: false,
      },
    };
  }

  await store.recordAttestation(input);
  await store.recordConflict(input, existingHashes);
  const distinctSaveConflicts24h = await store.distinctConflictCount24h();
  const action = classifySaveAttestation({
    incomingHash: input.payloadHashSha256,
    existingHashes,
    distinctSaveConflicts24h,
  });

  if (action === "TEMPORARY_GLOBAL_BAN") {
    const ban = await store.createTemporaryGlobalBan(input);
    return {
      status: 403,
      body: {
        code: "GLOBAL_BANNED",
        message: "PupEye detected repeated authenticated save-content conflicts.",
        ban: {
          id: ban.id,
          kind: "temporary",
          reasonCode: "SAVE_TAMPERING",
          publicReason:
            "PupEye detected repeated authenticated save-content conflicts.",
          issuedAt: ban.issuedAt,
          expiresAt: ban.expiresAt,
          scope: "GLOBAL",
          deviceWide: ban.deviceWide,
        },
      },
    };
  }

  return {
    status: 409,
    body: {
      code: "REVIEW_REQUIRED",
      message:
        "PupEye detected conflicting authenticated save content. Support review is required.",
      review: { scope: "GLOBAL" },
    },
  };
}

export function createSupabaseAttestationStore(
  admin: AdminClient,
  session: SessionContext,
  envelope: VerifiedEnvelope,
): AttestationStore {
  const playerUuid = String(session.player.id);
  const installationUuid = String(session.installation.id);
  const deviceReputationUuid =
    typeof session.installation.device_reputation_uuid === "string"
      ? session.installation.device_reputation_uuid
      : null;

  return {
    async existingHashes(saveId) {
      const { data, error } = await admin
        .from("pupeye_save_attestations")
        .select("payload_hash_sha256")
        .eq("installation_uuid", installationUuid)
        .eq("save_id", saveId);
      if (error) throw error;
      return (data ?? [])
        .map((row: Record<string, unknown>) => row.payload_hash_sha256)
        .filter((value: unknown): value is string => typeof value === "string");
    },

    async recordAttestation(input) {
      const { error } = await admin.from("pupeye_save_attestations").insert({
        save_id: input.saveId,
        player_uuid: playerUuid,
        installation_uuid: installationUuid,
        generation: input.generation,
        payload_hash_sha256: input.payloadHashSha256,
        device_key_id: envelope.deviceKeyId,
      });
      if (error && error.code !== "23505") throw error;
    },

    async updateSaveHead(input) {
      const { error } = await admin
        .from("pupeye_save_heads")
        .update({
          last_save_id: input.saveId,
          last_payload_hash_sha256: input.payloadHashSha256,
          updated_at: new Date().toISOString(),
        })
        .eq("installation_uuid", installationUuid);
      if (error) throw error;
    },

    async recordConflict(input, existingHashes) {
      await audit(
        admin,
        "SAVE_HASH_CONFLICT",
        "hard",
        {
          saveId: input.saveId,
          generation: input.generation,
          incomingHashPrefix: input.payloadHashSha256.slice(0, 12),
          existingHashPrefixes: existingHashes
            .map((value) => value.slice(0, 12))
            .slice(0, 4),
        },
        playerUuid,
        installationUuid,
      );

      const now = new Date().toISOString();
      const { error: installationError } = await admin
        .from("pupeye_installations")
        .update({ integrity_state: "review", last_seen_at: now })
        .eq("id", installationUuid);
      if (installationError) throw installationError;

      const { error: playerError } = await admin
        .from("pupeye_players")
        .update({ status: "review", updated_at: now })
        .eq("id", playerUuid)
        .neq("status", "blocked");
      if (playerError) throw playerError;
    },

    async distinctConflictCount24h() {
      const since = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
      const { data, error } = await admin
        .from("pupeye_save_attestations")
        .select("save_id,payload_hash_sha256,observed_at")
        .eq("installation_uuid", installationUuid)
        .gte("observed_at", since);
      if (error) throw error;

      const bySave = new Map<string, Set<string>>();
      for (const row of data ?? []) {
        if (
          typeof row.save_id !== "string" ||
          typeof row.payload_hash_sha256 !== "string"
        ) continue;
        const hashes = bySave.get(row.save_id) ?? new Set<string>();
        hashes.add(row.payload_hash_sha256);
        bySave.set(row.save_id, hashes);
      }
      return [...bySave.values()].filter((hashes) => hashes.size > 1).length;
    },

    async createTemporaryGlobalBan(input) {
      const issuedAt = new Date();
      const expiresAt = new Date(
        issuedAt.getTime() + 24 * 60 * 60 * 1000,
      );
      const targets: Array<Record<string, unknown>> = [
        { type: "PLAYER", playerUuid },
        { type: "INSTALLATION", installationUuid },
      ];
      if (deviceReputationUuid) {
        targets.push({
          type: "DEVICE_REPUTATION",
          deviceReputationUuid,
        });
      }

      const { data, error } = await admin.rpc("pupeye_create_global_ban", {
        p_kind: "temporary",
        p_reason_code: "SAVE_TAMPERING",
        p_public_reason:
          "PupEye detected repeated authenticated save-content conflicts.",
        p_internal_reason:
          "Two or more distinct authenticated save IDs had conflicting canonical payload hashes within 24 hours.",
        p_expires_at: expiresAt.toISOString(),
        p_issued_by: "system:save-attestation",
        p_support_note: null,
        p_targets: targets,
      });
      if (error) throw error;
      const row = Array.isArray(data) ? data[0] : data;
      if (!row?.public_ban_id) {
        throw new Error("PupEye global-ban RPC returned no public Ban ID");
      }

      const now = new Date().toISOString();
      const { error: playerError } = await admin
        .from("pupeye_players")
        .update({ status: "blocked", updated_at: now })
        .eq("id", playerUuid);
      if (playerError) throw playerError;

      const { error: installationError } = await admin
        .from("pupeye_installations")
        .update({ integrity_state: "blocked" })
        .eq("id", installationUuid);
      if (installationError) throw installationError;

      if (deviceReputationUuid) {
        const { error: deviceError } = await admin
          .from("pupeye_device_reputation")
          .update({ reputation_state: "blocked", last_seen_at: now })
          .eq("id", deviceReputationUuid);
        if (deviceError) throw deviceError;
      }

      const { error: sessionError } = await admin
        .from("pupeye_sessions")
        .update({ revoked_at: now })
        .eq("player_uuid", playerUuid)
        .is("revoked_at", null);
      if (sessionError) throw sessionError;

      if (typeof row.event_id === "number") {
        await sendBanEventBestEffort(admin, row.event_id);
      }

      return {
        id: String(row.public_ban_id),
        issuedAt: issuedAt.toISOString(),
        expiresAt: expiresAt.toISOString(),
        deviceWide: Boolean(deviceReputationUuid),
      };
    },
  };
}
