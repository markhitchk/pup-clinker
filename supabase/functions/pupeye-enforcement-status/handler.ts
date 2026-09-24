import type { AdminClient, VerifiedEnvelope } from "../_shared/pupeye.ts";
import { HttpError } from "../_shared/http.ts";
import { assertGlobalEnforcementAllowed } from "../_shared/global-enforcement.ts";

export type EnforcementStatusResult = {
  status: number;
  body: Record<string, unknown>;
};

export async function resolveVerifiedEnforcementStatus(
  admin: AdminClient,
  envelope: VerifiedEnvelope,
): Promise<EnforcementStatusResult> {
  const { data: installation, error: installationError } = await admin
    .from("pupeye_installations")
    .select("*")
    .eq("installation_id", envelope.installationId)
    .maybeSingle();
  if (installationError) throw installationError;

  if (!installation) {
    return {
      status: 200,
      body: {
        ok: true,
        state: "ALLOWED",
        registered: false,
        serverTimeEpochMs: Date.now(),
      },
    };
  }

  if (
    installation.device_key_id !== envelope.deviceKeyId ||
    (installation.public_key_b64 && installation.public_key_b64 !== envelope.publicKey)
  ) {
    return {
      status: 403,
      body: {
        code: "INSTALLATION_MISMATCH",
        message: "PupEye installation identity does not match this device key.",
      },
    };
  }

  const { data: player, error: playerError } = await admin
    .from("pupeye_players")
    .select("*")
    .eq("id", installation.player_uuid)
    .maybeSingle();
  if (playerError) throw playerError;
  if (!player) {
    return {
      status: 401,
      body: {
        code: "PLAYER_NOT_FOUND",
        message: "Pupeye player record was not found.",
      },
    };
  }

  try {
    await assertGlobalEnforcementAllowed(admin, {
      player,
      installation,
      discordUserId: player.discord_user_id ?? null,
      requestAction: envelope.action,
    });
  } catch (error) {
    if (error instanceof HttpError) {
      return {
        status: error.status,
        body: {
          code: error.code,
          message: error.message,
          ...error.details,
          serverTimeEpochMs: Date.now(),
        },
      };
    }
    throw error;
  }

  if (!installation.active || installation.revoked_at) {
    return {
      status: 403,
      body: {
        code: "INSTALLATION_REVOKED",
        message: "This Puppy Clicker installation is no longer active.",
        serverTimeEpochMs: Date.now(),
      },
    };
  }

  return {
    status: 200,
    body: {
      ok: true,
      state: "ALLOWED",
      registered: true,
      serverTimeEpochMs: Date.now(),
    },
  };
}
