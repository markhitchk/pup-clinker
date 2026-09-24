import type { AdminClient } from "./pupeye.ts";
import { HttpError } from "./pupeye.ts";
import { sendBanEventBestEffort } from "./discord-ban-notify.ts";

export type GlobalBanRow = {
  ban_uuid?: string;
  public_ban_id: string;
  kind: "temporary" | "permanent";
  status?: "active" | "expired" | "revoked";
  reason_code: string;
  public_reason: string;
  issued_at: string;
  expires_at: string | null;
  device_wide: boolean;
};

export type PublicGlobalBan = {
  id: string;
  kind: "temporary" | "permanent";
  reasonCode: string;
  publicReason: string;
  issuedAt: string;
  expiresAt: string | null;
  scope: "GLOBAL";
  deviceWide: boolean;
};

export type GlobalEnforcementResult =
  | { state: "ALLOWED" }
  | { state: "REVIEW_REQUIRED"; message: string }
  | { state: "GLOBAL_BANNED"; ban: PublicGlobalBan; banUuid: string | null };

export type EnforcementIdentityContext = {
  player?: Record<string, unknown> | null;
  installation?: Record<string, unknown> | null;
  session?: Record<string, unknown> | null;
  discordUserId?: string | null;
  webInstallationId?: string | null;
  requestAction: string;
};

export type AutomaticEnforcementAction =
  | "AUDIT_ONLY"
  | "REVIEW"
  | "TEMPORARY_GLOBAL_BAN";

export function selectEffectiveBan(
  rows: GlobalBanRow[],
  nowEpochMs = Date.now(),
): GlobalBanRow | null {
  return rows
    .filter((row) => {
      if ((row.status ?? "active") !== "active") return false;
      if (row.kind === "permanent") return true;
      if (!row.expires_at) return false;
      const expires = Date.parse(row.expires_at);
      return Number.isFinite(expires) && expires > nowEpochMs;
    })
    .sort((a, b) => {
      if (a.kind !== b.kind) return a.kind === "permanent" ? -1 : 1;
      return Date.parse(b.issued_at) - Date.parse(a.issued_at);
    })[0] ?? null;
}

export function automaticEnforcementAction(input: {
  evidenceCode: string;
  verifiedIdentity: boolean;
  distinctSaveConflicts24h: number;
}): AutomaticEnforcementAction {
  if (!input.verifiedIdentity) return "AUDIT_ONLY";
  if (input.evidenceCode === "SAVE_HASH_CONFLICT") {
    return input.distinctSaveConflicts24h >= 2
      ? "TEMPORARY_GLOBAL_BAN"
      : "REVIEW";
  }
  return "REVIEW";
}

export async function expireDueBans(admin: AdminClient): Promise<Array<{
  event_id: number;
  ban_uuid: string;
  public_ban_id: string;
}>> {
  const { data, error } = await admin.rpc("pupeye_expire_due_bans");
  if (error) throw error;
  return Array.isArray(data) ? data : [];
}

export async function resolveGlobalEnforcement(
  admin: AdminClient,
  context: EnforcementIdentityContext,
): Promise<GlobalEnforcementResult> {
  const expiredEvents = await expireDueBans(admin);
  for (const event of expiredEvents) {
    await sendBanEventBestEffort(admin, event.event_id);
  }

  const player = context.player ?? null;
  const installation = context.installation ?? null;
  const playerId = stringValue(player, "id");
  const playerDiscord = stringValue(player, "discord_user_id");
  const installationId = stringValue(installation, "id");
  const deviceReputationId = stringValue(installation, "device_reputation_uuid");

  const { data, error } = await admin.rpc("pupeye_resolve_global_ban", {
    p_player_uuid: playerId,
    p_discord_user_id: context.discordUserId ?? playerDiscord,
    p_installation_uuid: installationId,
    p_device_reputation_uuid: deviceReputationId,
    p_web_installation_id: context.webInstallationId ?? null,
  });
  if (error) throw error;

  const rawRows = Array.isArray(data) ? data : data ? [data] : [];
  const row = selectEffectiveBan(rawRows as GlobalBanRow[]);
  if (row) {
    return {
      state: "GLOBAL_BANNED",
      banUuid: row.ban_uuid ?? null,
      ban: {
        id: row.public_ban_id,
        kind: row.kind,
        reasonCode: row.reason_code,
        publicReason: row.public_reason,
        issuedAt: row.issued_at,
        expiresAt: row.expires_at,
        scope: "GLOBAL",
        deviceWide: Boolean(row.device_wide),
      },
    };
  }

  const playerStatus = stringValue(player, "status");
  const integrityState = stringValue(installation, "integrity_state");
  if (
    playerStatus === "review" ||
    playerStatus === "blocked" ||
    integrityState === "review" ||
    integrityState === "blocked"
  ) {
    return {
      state: "REVIEW_REQUIRED",
      message: "PupEye requires Support review before Puppy Clicker can continue.",
    };
  }

  return { state: "ALLOWED" };
}

export async function recordGlobalBanHit(
  admin: AdminClient,
  context: EnforcementIdentityContext,
  banUuid: string | null,
): Promise<void> {
  if (!banUuid) return;
  const player = context.player ?? null;
  const installation = context.installation ?? null;
  const { error } = await admin.from("pupeye_ban_hits").insert({
    ban_uuid: banUuid,
    player_uuid: stringValue(player, "id"),
    installation_uuid: stringValue(installation, "id"),
    device_reputation_uuid: stringValue(installation, "device_reputation_uuid"),
    discord_user_id: context.discordUserId ?? stringValue(player, "discord_user_id"),
    client_platform: stringValue(installation, "platform"),
    request_action: context.requestAction.slice(0, 80),
  });
  if (error) throw error;
}

export async function assertGlobalEnforcementAllowed(
  admin: AdminClient,
  context: EnforcementIdentityContext,
): Promise<void> {
  const result = await resolveGlobalEnforcement(admin, context);
  if (result.state === "GLOBAL_BANNED") {
    await recordGlobalBanHit(admin, context, result.banUuid);

    const sessionId = stringValue(context.session ?? null, "id");
    if (sessionId) {
      await admin.from("pupeye_sessions")
        .update({ revoked_at: new Date().toISOString() })
        .eq("id", sessionId)
        .is("revoked_at", null);
    }

    throw new HttpError(
      403,
      "GLOBAL_BANNED",
      result.ban.publicReason,
      { ban: result.ban },
    );
  }
  if (result.state === "REVIEW_REQUIRED") {
    throw new HttpError(
      409,
      "REVIEW_REQUIRED",
      result.message,
      { review: { scope: "GLOBAL" } },
    );
  }
}

function stringValue(
  object: Record<string, unknown> | null,
  key: string,
): string | null {
  const value = object?.[key];
  return typeof value === "string" && value.length > 0 ? value : null;
}
