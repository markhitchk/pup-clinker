import { withSupabase } from "npm:@supabase/server";
import {
  HttpError,
  canonicalJson,
  errorResponse,
  json,
  sha256Hex,
} from "../_shared/pupeye.ts";

type AdminClient = any;

const MAIN_STORE = "puppy_clicker_save";
const MAX_LONG = Number.MAX_SAFE_INTEGER;
const MAX_INT = 2_000_000_000;
const TICKET_IDS = new Set(["common", "uncommon", "rare", "epic", "legendary"]);
const MAX_TICKETS = 9_999;

function requiredString(value: unknown, field: string, max = 160): string {
  if (typeof value !== "string") {
    throw new HttpError(400, "GIFT_PAYLOAD_INVALID", field + " must be a string.");
  }
  const clean = value.trim();
  if (!clean || clean.length > max) {
    throw new HttpError(400, "GIFT_PAYLOAD_INVALID", field + " is invalid.");
  }
  return clean;
}

function safeInteger(value: unknown, field: string, minimum: number, maximum: number): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < minimum || value > maximum) {
    throw new HttpError(
      400,
      "GIFT_PAYLOAD_INVALID",
      field + " must be an integer from " + minimum + " to " + maximum + ".",
    );
  }
  return value;
}

function safeAdd(current: unknown, amount: number, field: string, maximum = MAX_LONG): number {
  const value = Number(current ?? 0);
  if (!Number.isSafeInteger(value) || value < 0) {
    throw new HttpError(409, "GIFT_SAVE_INVALID", field + " is not a valid stored integer.");
  }
  if (value > maximum - amount) {
    throw new HttpError(409, "GIFT_BALANCE_LIMIT", field + " cannot safely accept that gift amount.");
  }
  return value + amount;
}

function encodedMain(saveData: any): { values: Record<string, any>; types: Record<string, string> } {
  const store = saveData?.stores?.[MAIN_STORE];
  if (!store || typeof store !== "object" || Array.isArray(store)) {
    throw new HttpError(409, "GIFT_SAVE_UNAVAILABLE", "The target cloud save has no Puppy Clicker main store.");
  }
  if (!store.values || typeof store.values !== "object" || Array.isArray(store.values)) {
    throw new HttpError(409, "GIFT_SAVE_UNAVAILABLE", "The target main store is invalid.");
  }
  if (!store.types || typeof store.types !== "object" || Array.isArray(store.types)) {
    throw new HttpError(409, "GIFT_SAVE_UNAVAILABLE", "The target main store type map is invalid.");
  }
  return { values: store.values as Record<string, any>, types: store.types as Record<string, string> };
}

function writeLong(main: { values: Record<string, any>; types: Record<string, string> }, key: string, value: number) {
  main.values[key] = value;
  main.types[key] = "long";
}

function writeInt(main: { values: Record<string, any>; types: Record<string, string> }, key: string, value: number) {
  main.values[key] = value;
  main.types[key] = "int";
}

function stringSet(main: { values: Record<string, any>; types: Record<string, string> }, key: string): Set<string> {
  const raw = main.values[key];
  return new Set(Array.isArray(raw) ? raw.filter((value) => typeof value === "string") : []);
}

function writeStringSet(
  main: { values: Record<string, any>; types: Record<string, string> },
  key: string,
  values: Iterable<string>,
) {
  main.values[key] = [...new Set(values)].sort();
  main.types[key] = "string_set";
}

async function developerActor(admin: AdminClient, discordUserId: string) {
  const { data, error } = await admin
    .from("pupeye_players")
    .select("id,player_id,username,discord_user_id,discord_username,guild_role,status")
    .eq("discord_user_id", discordUserId)
    .maybeSingle();
  if (error) throw error;
  if (!data || data.guild_role !== "DEVELOPER") {
    throw new HttpError(
      403,
      "DEVELOPER_ROLE_REQUIRED",
      "The Discord command actor must have a verified Puppy Clicker Developer role.",
    );
  }
  if (data.status !== "active") {
    throw new HttpError(403, "DEVELOPER_ACCOUNT_RESTRICTED", "The developer Pup Account is not active.");
  }
  return data;
}

async function targetPlayer(admin: AdminClient, playerUuid: string) {
  const { data, error } = await admin
    .from("pupeye_players")
    .select("id,player_id,friend_code,username,discord_user_id,discord_username,status")
    .eq("id", playerUuid)
    .maybeSingle();
  if (error) throw error;
  if (!data) throw new HttpError(404, "GIFT_TARGET_NOT_FOUND", "Player was not found.");
  return data;
}

async function targetSave(admin: AdminClient, playerUuid: string) {
  const { data, error } = await admin
    .from("pup_account_saves")
    .select("*")
    .eq("player_uuid", playerUuid)
    .maybeSingle();
  if (error) throw error;
  if (!data) {
    throw new HttpError(409, "GIFT_SAVE_UNAVAILABLE", "The target player has no Pup Account cloud save yet.");
  }
  return data;
}

async function commitSave(admin: AdminClient, current: any, saveData: any): Promise<number> {
  const nextRevision = Number(current.revision) + 1;
  saveData.savedAtEpochMs = Date.now();
  const payloadHashSha256 = sha256Hex(canonicalJson(saveData));
  const { data, error } = await admin
    .from("pup_account_saves")
    .update({
      save_data: saveData,
      payload_hash_sha256: payloadHashSha256,
      revision: nextRevision,
      updated_at: new Date().toISOString(),
    })
    .eq("player_uuid", current.player_uuid)
    .eq("revision", current.revision)
    .select("revision")
    .maybeSingle();
  if (error) throw error;
  if (!data) {
    throw new HttpError(
      409,
      "GIFT_SAVE_CONFLICT",
      "The player's save changed while the Discord gift was being applied. Retry the command.",
    );
  }
  return Number(data.revision);
}

async function sendNotification(
  admin: AdminClient,
  actorPlayerUuid: string,
  targetPlayerUuid: string,
  title: string,
  body: string,
  detail: Record<string, unknown>,
) {
  const { error } = await admin.from("pup_system_notifications").insert({
    player_uuid: targetPlayerUuid,
    created_by_player_uuid: actorPlayerUuid,
    kind: "developer",
    title: title.slice(0, 100),
    body: body.slice(0, 1500),
    detail: { source: "discord_bot", ...detail },
  });
  if (error) throw error;
}

async function recordAction(
  admin: AdminClient,
  actorPlayerUuid: string,
  targetPlayerUuid: string,
  actionCode: string,
  detail: Record<string, unknown>,
) {
  const { error } = await admin.from("pup_admin_actions").insert({
    actor_player_uuid: actorPlayerUuid,
    target_player_uuid: targetPlayerUuid,
    action_code: actionCode,
    detail: { source: "discord_bot", ...detail },
  });
  if (error) throw error;
}

async function bestEffortPostCommit(
  admin: AdminClient,
  actorPlayerUuid: string,
  targetPlayerUuid: string,
  actionCode: string,
  title: string,
  body: string,
  detail: Record<string, unknown>,
): Promise<string[]> {
  const warnings: string[] = [];
  try {
    await sendNotification(admin, actorPlayerUuid, targetPlayerUuid, title, body, detail);
  } catch (error) {
    warnings.push("notification:" + (error instanceof Error ? error.message : String(error)));
  }
  try {
    await recordAction(admin, actorPlayerUuid, targetPlayerUuid, actionCode, detail);
  } catch (error) {
    warnings.push("audit:" + (error instanceof Error ? error.message : String(error)));
  }
  return warnings;
}

export default {
  fetch: withSupabase({ auth: "secret" }, async (req, ctx) => {
    try {
      const body = await req.json() as Record<string, unknown>;
      const action = requiredString(body.action, "action", 40);
      const actorDiscordUserId = requiredString(body.actorDiscordUserId, "actorDiscordUserId", 32);
      const actor = await developerActor(ctx.supabaseAdmin, actorDiscordUserId);
      const actorLabel = typeof body.actorLabel === "string" ? body.actorLabel.trim().slice(0, 120) : actor.discord_username;

      const targetPlayerUuid = requiredString(body.targetPlayerUuid, "targetPlayerUuid", 80);
      const target = await targetPlayer(ctx.supabaseAdmin, targetPlayerUuid);

      if (action === "gift-message") {
        const title = requiredString(body.title, "title", 100);
        const message = requiredString(body.message, "message", 1500);
        const detail = { actorDiscordUserId, actorLabel, requestId: body.requestId ?? null };
        const warnings = await bestEffortPostCommit(
          ctx.supabaseAdmin,
          actor.id,
          targetPlayerUuid,
          "BOT_MESSAGE_SENT",
          title,
          message,
          detail,
        );
        return json(200, { ok: true, target, warnings });
      }

      const save = await targetSave(ctx.supabaseAdmin, targetPlayerUuid);
      const saveData = structuredClone(save.save_data);
      const main = encodedMain(saveData);

      let title = "Developer sent you a gift 🎁";
      let message = "A developer sent a gift to your Pup Account.";
      let actionCode = "BOT_GIFT";
      const detail: Record<string, unknown> = {
        actorDiscordUserId,
        actorLabel,
        requestId: body.requestId ?? null,
      };

      if (action === "gift-currency") {
        const currency = requiredString(body.currency, "currency", 40);
        const amount = safeInteger(body.amount, "amount", 1, MAX_LONG);
        const longMap: Record<string, { key: string; label: string }> = {
          treats: { key: "treats", label: "Treats" },
          bones: { key: "bones_v7", label: "Bones" },
          pupCoins: { key: "pup_coins_v7", label: "Pup Coins" },
          casinoChips: { key: "casino_chips_v7", label: "Casino Chips" },
          playerXp: { key: "player_xp_v1", label: "XP" },
          totalPrestigePointsEarned: { key: "prestige_total_points_v6", label: "Prestige Points" },
        };
        if (currency === "skillPoints") {
          const next = safeAdd(main.values.prestige_skill_points_v6, amount, "Skill Points", MAX_INT);
          writeInt(main, "prestige_skill_points_v6", next);
          detail.currency = currency;
          detail.amount = amount;
          detail.newValue = next;
          message = "Developer gave you " + amount + " Skill Points.";
        } else {
          const config = longMap[currency];
          if (!config) throw new HttpError(400, "GIFT_CURRENCY_INVALID", "Unsupported gift currency.");
          const next = safeAdd(main.values[config.key], amount, config.label);
          writeLong(main, config.key, next);
          if (currency === "treats") {
            const nextLifetime = safeAdd(main.values.lifetime_treats, amount, "Lifetime Treats");
            writeLong(main, "lifetime_treats", nextLifetime);
            detail.newLifetimeTreats = nextLifetime;
          }
          detail.currency = currency;
          detail.amount = amount;
          detail.newValue = next;
          message = "Developer gave you " + amount + " " + config.label + ".";
        }
        actionCode = "BOT_GIFT_CURRENCY";
      } else if (action === "gift-item") {
        const itemType = requiredString(body.itemType, "itemType", 32).toLowerCase();
        const itemId = requiredString(body.itemId, "itemId", 120);
        detail.itemType = itemType;
        detail.itemId = itemId;

        if (itemType === "puppy") {
          const items = stringSet(main, "unlocked_puppies");
          items.add(itemId);
          writeStringSet(main, "unlocked_puppies", items);
          title = "Developer unlocked a puppy for you 🐾";
          message = itemId + " was added to your Puppy Clicker collection.";
          actionCode = "BOT_GIFT_PUPPY";
        } else if (itemType === "accessory") {
          const items = stringSet(main, "owned_accessories_v7");
          items.add("None");
          items.add(itemId);
          writeStringSet(main, "owned_accessories_v7", items);
          title = "Developer unlocked an accessory for you ✨";
          message = itemId + " was added to your Puppy Clicker accessories.";
          actionCode = "BOT_GIFT_ACCESSORY";
        } else if (itemType === "ticket") {
          const rarity = itemId.toLowerCase();
          if (!TICKET_IDS.has(rarity)) {
            throw new HttpError(400, "GIFT_TICKET_INVALID", "Unknown Upgrade Ticket rarity.");
          }
          const amount = safeInteger(body.amount, "amount", 1, MAX_TICKETS);
          const key = "upgrade_ticket_" + rarity;
          const next = safeAdd(main.values[key], amount, rarity + " tickets", MAX_TICKETS);
          writeInt(main, key, next);
          detail.amount = amount;
          detail.newValue = next;
          title = "Developer sent Upgrade Tickets 🎟️";
          message = "You received " + amount + " " + rarity + " Upgrade Ticket" + (amount === 1 ? "" : "s") + ".";
          actionCode = "BOT_GIFT_TICKET";
        } else {
          throw new HttpError(400, "GIFT_ITEM_INVALID", "Gift item type must be puppy, accessory, or ticket.");
        }
      } else {
        throw new HttpError(400, "GIFT_ACTION_INVALID", "Unsupported Discord gift action.");
      }

      const revision = await commitSave(ctx.supabaseAdmin, save, saveData);
      detail.revision = revision;
      const warnings = await bestEffortPostCommit(
        ctx.supabaseAdmin,
        actor.id,
        targetPlayerUuid,
        actionCode,
        title,
        message,
        detail,
      );

      return json(200, {
        ok: true,
        target,
        revision,
        gift: detail,
        warnings,
      });
    } catch (error) {
      return errorResponse(error);
    }
  }),
};
