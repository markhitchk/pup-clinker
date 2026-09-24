import { withSupabase } from "npm:@supabase/server";
import { sendBanEventBestEffort } from "../_shared/discord-ban-notify.ts";
import {
  HttpError,
  errorResponse,
  json,
  requiredString,
} from "../_shared/pupeye.ts";
import {
  optionalText,
  requiredText,
  requiredUuid,
  validateIssueBanRequest,
  validateSupportAction,
  validateTargets,
} from "./policy.ts";

type AdminClient = any;

async function revokeSessionsForTargets(admin: AdminClient, targets: Array<Record<string, unknown>>) {
  const playerIds = new Set<string>();
  const installationIds = new Set<string>();

  for (const target of targets) {
    const type = String(target.type ?? "");
    if (type === "PLAYER" && target.playerUuid) playerIds.add(String(target.playerUuid));
    if (type === "INSTALLATION" && target.installationUuid) installationIds.add(String(target.installationUuid));

    if (type === "DISCORD" && target.discordUserId) {
      const { data, error } = await admin.from("pupeye_players")
        .select("id")
        .eq("discord_user_id", String(target.discordUserId));
      if (error) throw error;
      for (const row of data ?? []) playerIds.add(row.id);
    }

    if (type === "DEVICE_REPUTATION" && target.deviceReputationUuid) {
      const { data, error } = await admin.from("pupeye_installations")
        .select("id,player_uuid")
        .eq("device_reputation_uuid", String(target.deviceReputationUuid));
      if (error) throw error;
      for (const row of data ?? []) {
        installationIds.add(row.id);
        playerIds.add(row.player_uuid);
      }
    }
  }

  const now = new Date().toISOString();
  if (playerIds.size > 0) {
    const ids = [...playerIds];
    const { error } = await admin.from("pupeye_sessions")
      .update({ revoked_at: now })
      .in("player_uuid", ids)
      .is("revoked_at", null);
    if (error) throw error;
  }
  if (installationIds.size > 0) {
    const ids = [...installationIds];
    const { error } = await admin.from("pupeye_sessions")
      .update({ revoked_at: now })
      .in("installation_uuid", ids)
      .is("revoked_at", null);
    if (error) throw error;
  }
}

async function issueBan(admin: AdminClient, body: Record<string, unknown>) {
  const input = validateIssueBanRequest(body);
  const { data, error } = await admin.rpc("pupeye_create_global_ban", {
    p_kind: input.kind,
    p_reason_code: input.reasonCode,
    p_public_reason: input.publicReason,
    p_internal_reason: input.internalReason,
    p_expires_at: input.expiresAt,
    p_issued_by: input.actor,
    p_support_note: input.supportNote,
    p_targets: input.targets,
  });
  if (error) throw error;

  await revokeSessionsForTargets(admin, input.targets as Array<Record<string, unknown>>);
  const row = Array.isArray(data) ? data[0] : data;
  if (typeof row?.event_id === "number") {
    await sendBanEventBestEffort(admin, row.event_id);
  }
  return json(200, {
    ok: true,
    banId: row?.public_ban_id ?? null,
    banUuid: row?.ban_uuid ?? null,
    eventId: row?.event_id ?? null,
    kind: input.kind,
    expiresAt: input.expiresAt,
  });
}

async function updateBan(admin: AdminClient, body: Record<string, unknown>, action: string) {
  const banUuid = requiredUuid(body.banUuid, "banUuid");
  const actor = requiredText(body.actor, "actor", 80);
  const supportNote = optionalText(body.supportNote, 1000);
  let kind: "temporary" | "permanent";
  let expiresAt: string | null;

  if (action === "convert_to_permanent") {
    kind = "permanent";
    expiresAt = null;
  } else {
    kind = "temporary";
    const raw = requiredText(body.expiresAt, "expiresAt", 64);
    const epoch = Date.parse(raw);
    if (!Number.isFinite(epoch) || epoch <= Date.now()) {
      throw new HttpError(400, "BAN_EXPIRY_INVALID", "Temporary ban requires a future expiry.");
    }
    expiresAt = new Date(epoch).toISOString();
  }

  const { data, error } = await admin.rpc("pupeye_update_global_ban", {
    p_ban_uuid: banUuid,
    p_kind: kind,
    p_expires_at: expiresAt,
    p_actor: actor,
    p_support_note: supportNote,
  });
  if (error) throw error;
  const row = Array.isArray(data) ? data[0] : data;
  if (typeof row?.event_id === "number") {
    await sendBanEventBestEffort(admin, row.event_id);
  }
  return json(200, {
    ok: true,
    banId: row?.public_ban_id ?? null,
    eventId: row?.event_id ?? null,
    kind,
    expiresAt,
  });
}

async function attachTarget(admin: AdminClient, body: Record<string, unknown>) {
  const banUuid = requiredUuid(body.banUuid, "banUuid");
  const actor = requiredText(body.actor, "actor", 80);
  const [target] = validateTargets([body.target]);
  const { data, error } = await admin.rpc("pupeye_attach_ban_target", {
    p_ban_uuid: banUuid,
    p_target_type: target.type,
    p_player_uuid: target.playerUuid ?? null,
    p_discord_user_id: target.discordUserId ?? null,
    p_device_reputation_uuid: target.deviceReputationUuid ?? null,
    p_installation_uuid: target.installationUuid ?? null,
    p_web_installation_id: target.webInstallationId ?? null,
    p_actor: actor,
    p_source_event_id: null,
  });
  if (error) throw error;
  await revokeSessionsForTargets(admin, [target as Record<string, unknown>]);
  const row = Array.isArray(data) ? data[0] : data;
  if (typeof row?.event_id === "number") {
    await sendBanEventBestEffort(admin, row.event_id);
  }
  return json(200, { ok: true, targetId: row?.target_id ?? null, eventId: row?.event_id ?? null });
}

async function revokeBan(admin: AdminClient, body: Record<string, unknown>) {
  const banUuid = requiredUuid(body.banUuid, "banUuid");
  const actor = requiredText(body.actor, "actor", 80);
  const note = optionalText(body.note, 1000);
  const { data, error } = await admin.rpc("pupeye_revoke_global_ban", {
    p_ban_uuid: banUuid,
    p_actor: actor,
    p_note: note,
  });
  if (error) throw error;
  const row = Array.isArray(data) ? data[0] : data;
  if (typeof row?.event_id === "number") {
    await sendBanEventBestEffort(admin, row.event_id);
  }
  return json(200, { ok: true, banId: row?.public_ban_id ?? null, eventId: row?.event_id ?? null });
}

async function clearReview(admin: AdminClient, body: Record<string, unknown>) {
  const supportCode = requiredString(body.supportCode, "supportCode");
  const actor = requiredText(body.actor, "actor", 80);
  const { data: installation, error } = await admin.from("pupeye_installations")
    .select("*")
    .eq("support_code", supportCode)
    .maybeSingle();
  if (error) throw error;
  if (!installation) {
    throw new HttpError(404, "INSTALLATION_NOT_FOUND", "Support Installation Code was not found.");
  }

  const { error: installError } = await admin.from("pupeye_installations")
    .update({ integrity_state: "clean" })
    .eq("id", installation.id);
  if (installError) throw installError;

  const { error: playerError } = await admin.from("pupeye_players")
    .update({ status: "active", updated_at: new Date().toISOString() })
    .eq("id", installation.player_uuid)
    .eq("status", "review");
  if (playerError) throw playerError;

  const { error: auditError } = await admin.from("pupeye_events").insert({
    player_uuid: installation.player_uuid,
    installation_uuid: installation.id,
    event_code: "SUPPORT_REVIEW_CLEARED",
    severity: "info",
    detail: { supportCode, actor },
  });
  if (auditError) throw auditError;
  return json(200, { ok: true, supportCode });
}

async function recordAppeal(admin: AdminClient, body: Record<string, unknown>) {
  const banUuid = requiredUuid(body.banUuid, "banUuid");
  const actor = requiredText(body.actor, "actor", 80);
  const state = requiredText(body.appealState, "appealState", 20).toLowerCase();
  const eventCode = {
    submitted: "APPEAL_SUBMITTED",
    approved: "APPEAL_APPROVED",
    denied: "APPEAL_DENIED",
  }[state];
  if (!eventCode) {
    throw new HttpError(400, "APPEAL_STATE_INVALID", "Appeal state must be submitted, approved, or denied.");
  }
  const note = optionalText(body.note, 1000);
  const { data, error } = await admin.from("pupeye_ban_events")
    .insert({
      ban_uuid: banUuid,
      event_code: eventCode,
      actor,
      detail: note ? { note } : {},
      discord_delivery_status: "pending",
    })
    .select("id")
    .single();
  if (error) throw error;
  await sendBanEventBestEffort(admin, data.id);
  return json(200, { ok: true, eventId: data.id, appealState: state });
}

async function lookup(admin: AdminClient, body: Record<string, unknown>) {
  const publicBanId = typeof body.banId === "string" ? body.banId.trim() : "";
  const playerId = typeof body.playerId === "string" ? body.playerId.trim() : "";
  const friendCode = typeof body.friendCode === "string" ? body.friendCode.trim() : "";
  const supportCode = typeof body.supportCode === "string" ? body.supportCode.trim() : "";
  const discordUserId = typeof body.discordUserId === "string" ? body.discordUserId.trim() : "";

  if (publicBanId) {
    const { data, error } = await admin.from("pupeye_global_bans")
      .select("*,pupeye_ban_targets(*)")
      .eq("public_ban_id", publicBanId)
      .maybeSingle();
    if (error) throw error;
    return json(200, { ok: true, ban: data ?? null });
  }

  let playerUuid: string | null = null;
  if (playerId || friendCode || discordUserId) {
    let query = admin.from("pupeye_players").select("*");
    if (playerId) query = query.eq("player_id", playerId);
    else if (friendCode) query = query.eq("friend_code", friendCode);
    else query = query.eq("discord_user_id", discordUserId);
    const { data, error } = await query.maybeSingle();
    if (error) throw error;
    playerUuid = data?.id ?? null;
  } else if (supportCode) {
    const { data, error } = await admin.from("pupeye_installations")
      .select("player_uuid")
      .eq("support_code", supportCode)
      .maybeSingle();
    if (error) throw error;
    playerUuid = data?.player_uuid ?? null;
  } else {
    throw new HttpError(400, "BAN_LOOKUP_INVALID", "Provide Ban ID, Player ID, Friend Code, Discord ID, or Support Code.");
  }

  if (!playerUuid) return json(200, { ok: true, player: null, bans: [] });
  const { data: player, error: playerError } = await admin.from("pupeye_players")
    .select("*")
    .eq("id", playerUuid)
    .single();
  if (playerError) throw playerError;
  const { data: targets, error: targetError } = await admin.from("pupeye_ban_targets")
    .select("ban_uuid")
    .eq("player_uuid", playerUuid);
  if (targetError) throw targetError;
  const banIds = [...new Set((targets ?? []).map((row: any) => row.ban_uuid))];
  let bans: any[] = [];
  if (banIds.length > 0) {
    const { data, error } = await admin.from("pupeye_global_bans")
      .select("*")
      .in("id", banIds)
      .order("issued_at", { ascending: false });
    if (error) throw error;
    bans = data ?? [];
  }
  return json(200, { ok: true, player, bans });
}

export default {
  fetch: withSupabase({ auth: "secret" }, async (req, ctx) => {
    try {
      const body = await req.json() as Record<string, unknown>;
      const action = validateSupportAction(body.action);
      const admin = ctx.supabaseAdmin;

      if (action === "issue_temporary" || action === "issue_permanent") {
        return await issueBan(admin, body);
      }
      if (action === "extend_temporary" || action === "convert_to_temporary" || action === "convert_to_permanent") {
        return await updateBan(admin, body, action);
      }
      if (action === "attach_target") return await attachTarget(admin, body);
      if (action === "revoke_ban") return await revokeBan(admin, body);
      if (action === "clear_review") return await clearReview(admin, body);
      if (action === "record_appeal") return await recordAppeal(admin, body);
      if (action === "lookup") return await lookup(admin, body);

      throw new HttpError(400, "BAN_ACTION_INVALID", "Unsupported PupEye ban action.");
    } catch (error) {
      return errorResponse(error);
    }
  }),
};
