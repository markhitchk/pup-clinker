import type { AdminClient } from "./pupeye.ts";

export type BanEmbedSummary = {
  eventCode: string;
  banId?: string | null;
  kind?: string | null;
  reasonCode?: string | null;
  username?: string | null;
  playerId?: string | null;
  deviceWide?: boolean;
  discordLinked?: boolean;
  platform?: string | null;
  createdAt: string;
  expiresAt?: string | null;
};

export type DiscordWebhookPayload = {
  allowed_mentions: { parse: string[] };
  embeds: Array<{
    title: string;
    description: string;
    color: number;
    fields: Array<{ name: string; value: string; inline: boolean }>;
    footer: { text: string };
    timestamp: string;
  }>;
};

const PRESENTATION: Record<string, { title: string; description: string; color: number }> = {
  GLOBAL_BAN_CREATED: {
    title: "🔴 PupEye Global Ban",
    description: "A permanent Puppy Clicker global enforcement action was issued.",
    color: 0xD32F2F,
  },
  TEMPORARY_GLOBAL_BAN_CREATED: {
    title: "🟠 PupEye Temporary Global Ban",
    description: "A temporary Puppy Clicker global enforcement action was issued.",
    color: 0xF57C00,
  },
  REVIEW_REQUIRED: {
    title: "🟡 PupEye Review Required",
    description: "PupEye restricted an identity pending Support review.",
    color: 0xFBC02D,
  },
  BAN_EVASION_DETECTED: {
    title: "🚫 PupEye Ban Evasion Detected",
    description: "PupEye detected a new identity linked to an active enforcement action.",
    color: 0xB71C1C,
  },
  GLOBAL_BAN_EXTENDED: {
    title: "🔄 PupEye Ban Extended",
    description: "An active temporary global ban was extended.",
    color: 0xEF6C00,
  },
  GLOBAL_BAN_ESCALATED: {
    title: "🔴 PupEye Ban Escalated",
    description: "An existing PupEye enforcement action was escalated.",
    color: 0xC62828,
  },
  GLOBAL_BAN_REVOKED: {
    title: "✅ PupEye Ban Revoked",
    description: "An authorized Support action revoked a global ban.",
    color: 0x2E7D32,
  },
  BAN_EXPIRED: {
    title: "⌛ PupEye Temporary Ban Expired",
    description: "A temporary global ban expired by server time.",
    color: 0x388E3C,
  },
  NEW_ACCOUNT_BLOCKED_ON_BANNED_DEVICE: {
    title: "⚠️ New Account Blocked on Banned Device",
    description: "A new Puppy Clicker identity attempted to register on a recognized banned device.",
    color: 0xC62828,
  },
  APPEAL_SUBMITTED: {
    title: "📨 PupEye Appeal Submitted",
    description: "A PupEye enforcement appeal was recorded.",
    color: 0x1976D2,
  },
  APPEAL_APPROVED: {
    title: "✅ PupEye Appeal Approved",
    description: "A PupEye enforcement appeal was approved.",
    color: 0x2E7D32,
  },
  APPEAL_DENIED: {
    title: "❌ PupEye Appeal Denied",
    description: "A PupEye enforcement appeal was denied.",
    color: 0xC62828,
  },
  ADMIN_OVERRIDE: {
    title: "🛡️ PupEye Admin Override",
    description: "An authorized moderation override was recorded.",
    color: 0x455A64,
  },
  BAN_TARGET_ATTACHED: {
    title: "🔗 PupEye Ban Target Attached",
    description: "An additional identity was attached to an existing global ban.",
    color: 0x6A1B9A,
  },
};

export function buildBanEmbed(summary: BanEmbedSummary): DiscordWebhookPayload {
  const presentation = PRESENTATION[summary.eventCode] ?? {
    title: "🛡️ PupEye Global Enforcement",
    description: "A Puppy Clicker enforcement event was recorded.",
    color: 0x455A64,
  };
  const fields: Array<{ name: string; value: string; inline: boolean }> = [];

  pushField(fields, "Ban ID", summary.banId, true);
  pushField(fields, "Status", friendlyKind(summary.kind), true);
  pushField(fields, "Reason", summary.reasonCode, true);
  pushField(fields, "Player", summary.username, true);
  pushField(fields, "Player ID", summary.playerId, false);
  pushField(fields, "Scope", summary.banId ? "Global — Android / Desktop / Web" : "PupEye moderation", false);

  if (summary.deviceWide !== undefined) {
    fields.push({ name: "Device-wide", value: summary.deviceWide ? "Yes" : "No", inline: true });
  }
  if (summary.discordLinked !== undefined) {
    fields.push({ name: "Discord-linked", value: summary.discordLinked ? "Yes" : "No", inline: true });
  }
  pushField(fields, "Source platform", summary.platform, true);
  pushField(fields, "Expires", summary.expiresAt ?? (summary.kind === "permanent" ? "Never" : null), false);

  return {
    allowed_mentions: { parse: [] },
    embeds: [{
      title: presentation.title,
      description: presentation.description,
      color: presentation.color,
      fields,
      footer: { text: "PupEye Global Enforcement" },
      timestamp: normalizeTimestamp(summary.createdAt),
    }],
  };
}

export async function sendBanEventBestEffort(
  admin: AdminClient,
  eventId: number,
  fetcher: typeof fetch = fetch,
  webhookOverride?: string,
): Promise<void> {
  try {
    const webhookUrl = webhookOverride ??
      Deno.env.get("PUPPY_GLOBAL_BAN_DISCORD_WEBHOOK")?.trim() ??
      "";

    const { data: event, error: eventError } = await admin
      .from("pupeye_ban_events")
      .select("*")
      .eq("id", eventId)
      .single();
    if (eventError || !event) return;

    if (!webhookUrl) {
      await markDelivery(admin, event, "not_required", null);
      return;
    }

    const summary = await loadSummary(admin, event);
    const response = await fetcher(webhookUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(buildBanEmbed(summary)),
      signal: AbortSignal.timeout(3_000),
    });
    if (!response.ok) {
      throw new Error(`Discord webhook returned HTTP ${response.status}`);
    }
    await markDelivery(admin, event, "sent", null);
  } catch (error) {
    try {
      const { data: event } = await admin
        .from("pupeye_ban_events")
        .select("*")
        .eq("id", eventId)
        .single();
      if (event) {
        await markDelivery(
          admin,
          event,
          "failed",
          error instanceof Error ? error.message.slice(0, 300) : "Discord delivery failed",
        );
      }
    } catch {
      // Discord delivery is observability only. Never fail a committed moderation action.
    }
  }
}

async function loadSummary(admin: AdminClient, event: Record<string, any>): Promise<BanEmbedSummary> {
  let ban: Record<string, any> | null = null;
  let targets: Array<Record<string, any>> = [];
  if (event.ban_uuid) {
    const banResult = await admin.from("pupeye_global_bans")
      .select("public_ban_id,kind,reason_code,expires_at")
      .eq("id", event.ban_uuid)
      .maybeSingle();
    if (!banResult.error) ban = banResult.data;

    const targetResult = await admin.from("pupeye_ban_targets")
      .select("target_type,player_uuid,installation_uuid,device_reputation_uuid,discord_user_id")
      .eq("ban_uuid", event.ban_uuid);
    if (!targetResult.error) targets = targetResult.data ?? [];
  }

  const detail = isRecord(event.detail) ? event.detail : {};
  let player: Record<string, any> | null = null;
  const playerUuid = targets.find((target) => target.player_uuid)?.player_uuid ?? null;
  if (playerUuid) {
    const result = await admin.from("pupeye_players")
      .select("username,player_id,discord_user_id")
      .eq("id", playerUuid)
      .maybeSingle();
    if (!result.error) player = result.data;
  }

  let platform: string | null = null;
  const installationUuid = targets.find((target) => target.installation_uuid)?.installation_uuid ?? null;
  if (installationUuid) {
    const result = await admin.from("pupeye_installations")
      .select("platform")
      .eq("id", installationUuid)
      .maybeSingle();
    if (!result.error) platform = result.data?.platform ?? null;
  }

  const deviceWide = targets.some((target) => target.target_type === "DEVICE_REPUTATION");
  const discordLinked =
    targets.some((target) => target.target_type === "DISCORD") ||
    Boolean(player?.discord_user_id);

  return {
    eventCode: String(event.event_code ?? "GLOBAL_ENFORCEMENT"),
    banId: ban?.public_ban_id ?? stringOrNull(detail.publicBanId),
    kind: ban?.kind ?? stringOrNull(detail.kind),
    reasonCode: ban?.reason_code ?? stringOrNull(detail.reasonCode),
    username: player?.username ?? stringOrNull(detail.username),
    playerId: player?.player_id ?? stringOrNull(detail.playerId),
    deviceWide,
    discordLinked,
    platform: platform ?? stringOrNull(detail.platform),
    createdAt: String(event.created_at ?? new Date().toISOString()),
    expiresAt: ban?.expires_at ?? stringOrNull(detail.expiresAt),
  };
}

async function markDelivery(
  admin: AdminClient,
  event: Record<string, any>,
  status: "sent" | "failed" | "not_required",
  errorMessage: string | null,
): Promise<void> {
  const { error } = await admin.from("pupeye_ban_events")
    .update({
      discord_delivery_status: status,
      discord_attempt_count: Number(event.discord_attempt_count ?? 0) + 1,
      discord_last_attempt_at: new Date().toISOString(),
      discord_last_error: errorMessage,
    })
    .eq("id", event.id);
  if (error) {
    console.error("[PupEye] DISCORD_DELIVERY_AUDIT_FAILED", error.message ?? error);
  }
}

function pushField(
  fields: Array<{ name: string; value: string; inline: boolean }>,
  name: string,
  value: unknown,
  inline: boolean,
): void {
  if (typeof value !== "string" || !value.trim()) return;
  fields.push({ name, value: sanitize(value, 1000), inline });
}

function sanitize(value: string, max: number): string {
  return value
    .replaceAll("@everyone", "@ everyone")
    .replaceAll("@here", "@ here")
    .trim()
    .slice(0, max);
}

function friendlyKind(value: string | null | undefined): string | null {
  if (value === "temporary") return "Temporary Global Ban";
  if (value === "permanent") return "Permanent Global Ban";
  return value ?? null;
}

function normalizeTimestamp(value: string): string {
  const epoch = Date.parse(value);
  return Number.isFinite(epoch) ? new Date(epoch).toISOString() : new Date().toISOString();
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function stringOrNull(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}
