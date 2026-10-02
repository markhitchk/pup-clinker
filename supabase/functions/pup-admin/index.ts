import {
  HttpError,
  assertPublishableRequest,
  authenticateSession,
  canonicalJson,
  createAdminClient,
  errorResponse,
  json,
  sha256Hex,
  verifyEnvelope,
} from "../_shared/pupeye.ts";

const MAIN_STORE = "puppy_clicker_save";
const CORE_PUPPIES = new Set(["classic", "golden", "poodle", "spotty"]);
const TICKET_IDS = new Set(["common", "uncommon", "rare", "epic", "legendary"]);
const MAX_TICKETS = 9999;
const MAX_BALANCE = 9_000_000_000_000_000;

function requiredString(value: unknown, field: string, max = 160): string {
  if (typeof value !== "string") {
    throw new HttpError(400, "ADMIN_PAYLOAD_INVALID", field + " must be a string.");
  }
  const clean = value.trim();
  if (!clean || clean.length > max) {
    throw new HttpError(400, "ADMIN_PAYLOAD_INVALID", field + " is invalid.");
  }
  return clean;
}

function optionalString(value: unknown, max = 160): string | null {
  if (value == null) return null;
  if (typeof value !== "string") {
    throw new HttpError(400, "ADMIN_PAYLOAD_INVALID", "Expected a string value.");
  }
  const clean = value.trim();
  if (!clean) return null;
  if (clean.length > max) {
    throw new HttpError(400, "ADMIN_PAYLOAD_INVALID", "String value is too long.");
  }
  return clean;
}

function safeInteger(value: unknown, field: string, minimum: number, maximum: number): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < minimum || value > maximum) {
    throw new HttpError(
      400,
      "ADMIN_PAYLOAD_INVALID",
      field + " must be an integer from " + minimum + " to " + maximum + ".",
    );
  }
  return value;
}

function requireDeveloper(session: any): void {
  if (session.player.guild_role !== "DEVELOPER") {
    throw new HttpError(
      403,
      "DEVELOPER_ROLE_REQUIRED",
      "A verified Puppy Clicker Developer role is required.",
    );
  }
}

function objectValue(value: unknown, field: string): Record<string, any> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new HttpError(400, "ADMIN_PAYLOAD_INVALID", field + " must be an object.");
  }
  return value as Record<string, any>;
}

function encodedMain(saveData: any): { values: Record<string, any>; types: Record<string, string> } {
  const store = saveData?.stores?.[MAIN_STORE];
  if (!store || typeof store !== "object" || Array.isArray(store)) {
    throw new HttpError(409, "ADMIN_SAVE_UNAVAILABLE", "The target cloud save has no Puppy Clicker main store.");
  }
  if (!store.values || typeof store.values !== "object" || Array.isArray(store.values)) {
    throw new HttpError(409, "ADMIN_SAVE_UNAVAILABLE", "The target main store is invalid.");
  }
  if (!store.types || typeof store.types !== "object" || Array.isArray(store.types)) {
    throw new HttpError(409, "ADMIN_SAVE_UNAVAILABLE", "The target main store type map is invalid.");
  }
  return { values: store.values as Record<string, any>, types: store.types as Record<string, string> };
}

function stringSet(main: { values: Record<string, any>; types: Record<string, string> }, key: string): Set<string> {
  const raw = main.values[key];
  const values = Array.isArray(raw) ? raw.filter((v) => typeof v === "string") : [];
  return new Set(values);
}

function writeStringSet(
  main: { values: Record<string, any>; types: Record<string, string> },
  key: string,
  values: Iterable<string>,
): void {
  main.values[key] = [...new Set(values)].sort();
  main.types[key] = "string_set";
}

function writeLong(
  main: { values: Record<string, any>; types: Record<string, string> },
  key: string,
  value: number,
): void {
  main.values[key] = value;
  main.types[key] = "long";
}

function writeInt(
  main: { values: Record<string, any>; types: Record<string, string> },
  key: string,
  value: number,
): void {
  main.values[key] = value;
  main.types[key] = "int";
}

async function targetPlayer(admin: any, playerUuid: string): Promise<any> {
  const { data, error } = await admin
    .from("pupeye_players")
    .select("id,player_id,friend_code,username,discord_user_id,discord_username,discord_global_name,guild_role,status,created_at,updated_at")
    .eq("id", playerUuid)
    .maybeSingle();
  if (error) throw error;
  if (!data) throw new HttpError(404, "ADMIN_TARGET_NOT_FOUND", "Player was not found.");
  return data;
}

async function targetSave(admin: any, playerUuid: string): Promise<any> {
  const { data, error } = await admin
    .from("pup_account_saves")
    .select("*")
    .eq("player_uuid", playerUuid)
    .maybeSingle();
  if (error) throw error;
  if (!data) throw new HttpError(409, "ADMIN_SAVE_UNAVAILABLE", "The target player has no Pup Account cloud save yet.");
  return data;
}

async function targetAccount(admin: any, playerUuid: string): Promise<any | null> {
  const { data, error } = await admin
    .from("pup_accounts")
    .select("id,player_uuid,username,display_name,discord_user_id,status,stage,allow_multiple_devices,created_at,updated_at")
    .eq("player_uuid", playerUuid)
    .maybeSingle();
  if (error) throw error;
  return data ?? null;
}

async function commitSave(admin: any, current: any, saveData: any): Promise<number> {
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
      "ADMIN_SAVE_CONFLICT",
      "The player's save changed while the developer edit was being applied. Refresh and retry.",
    );
  }
  return Number(data.revision);
}

async function recordAction(
  admin: any,
  actorPlayerUuid: string,
  targetPlayerUuid: string | null,
  actionCode: string,
  detail: Record<string, unknown>,
): Promise<void> {
  const { error } = await admin.from("pup_admin_actions").insert({
    actor_player_uuid: actorPlayerUuid,
    target_player_uuid: targetPlayerUuid,
    action_code: actionCode,
    detail,
  });
  if (error) throw error;
}

async function sendNotification(
  admin: any,
  actorPlayerUuid: string,
  targetPlayerUuid: string,
  kind: string,
  title: string,
  body: string,
  detail: Record<string, unknown> = {},
): Promise<void> {
  const { error } = await admin.from("pup_system_notifications").insert({
    player_uuid: targetPlayerUuid,
    created_by_player_uuid: actorPlayerUuid,
    kind,
    title: title.slice(0, 100),
    body: body.slice(0, 1500),
    detail,
  });
  if (error) throw error;
}

function gameSummary(save: any): Record<string, unknown> | null {
  if (!save?.save_data) return null;
  try {
    const main = encodedMain(save.save_data);
    const values = main.values;
    const tickets: Record<string, number> = {};
    for (const rarity of TICKET_IDS) {
      tickets[rarity] = Number(values["upgrade_ticket_" + rarity] ?? 0);
    }
    const puppyStyle = typeof values.puppy_style === "string" ? values.puppy_style : "classic";
    const carePrefix = "care_profile_v1_" + puppyStyle + "_";
    const care = {
      happiness: Number(values[carePrefix + "happiness"] ?? values.happiness ?? 100),
      fullness: Number(values[carePrefix + "fullness"] ?? values.fullness ?? 100),
      energy: Number(values[carePrefix + "energy"] ?? values.energy ?? 100),
      cleanliness: Number(values[carePrefix + "cleanliness"] ?? values.cleanliness_v5 ?? 100),
      bond: Number(values[carePrefix + "bond"] ?? values.bond_v5 ?? 10),
    };
    return {
      activePuppy: puppyStyle,
      treats: Number(values.treats ?? 0),
      lifetimeTreats: Number(values.lifetime_treats ?? 0),
      playerXp: Number(values.player_xp_v1 ?? values.lifetime_treats ?? 0),
      bones: Number(values.bones_v7 ?? 0),
      pupCoins: Number(values.pup_coins_v7 ?? 0),
      casinoChips: Number(values.casino_chips_v7 ?? 0),
      totalTaps: Number(values.total_taps ?? 0),
      careActions: Number(values.care_actions_v5 ?? 0),
      totalShopPurchases: Number(values.total_shop_purchases_v5 ?? 0),
      totalTicketsFound: Number(values.total_tickets_found ?? 0),
      dailyStreak: Number(values.daily_streak_v5 ?? 0),
      prestigeCount: Number(values.prestige_count_v6 ?? 0),
      skillPoints: Number(values.prestige_skill_points_v6 ?? 0),
      totalPrestigePointsEarned: Number(values.prestige_total_points_v6 ?? 0),
      unlockedPuppies: [...stringSet(main, "unlocked_puppies")].sort(),
      ownedAccessories: [...stringSet(main, "owned_accessories_v7")].sort(),
      tickets,
      care,
    };
  } catch {
    return null;
  }
}

Deno.serve(async (req: Request) => {
  try {
    assertPublishableRequest(req);
    const envelope = verifyEnvelope(await req.json());
    const allowed = new Set([
      "admin-users-list",
      "admin-user-detail",
      "admin-game-update",
      "admin-item-set",
      "admin-message-send",
    ]);
    if (!allowed.has(envelope.action)) {
      throw new HttpError(400, "PUP_ADMIN_ACTION_INVALID", "Unsupported developer action.");
    }

    const admin = createAdminClient();
    const session = await authenticateSession(req, admin, envelope);
    requireDeveloper(session);
    const payload = envelope.payload;

    if (envelope.action === "admin-users-list") {
      const query = optionalString(payload.query, 80)?.toLowerCase() ?? "";
      const { data: players, error: playersError } = await admin
        .from("pupeye_players")
        .select("id,player_id,friend_code,username,discord_user_id,discord_username,discord_global_name,guild_role,status,created_at,updated_at")
        .order("updated_at", { ascending: false })
        .limit(200);
      if (playersError) throw playersError;

      const filtered = (players ?? []).filter((player: any) => {
        if (!query) return true;
        return [
          player.player_id,
          player.friend_code,
          player.username,
          player.discord_user_id,
          player.discord_username,
          player.discord_global_name,
        ].some((value) => typeof value === "string" && value.toLowerCase().includes(query));
      }).slice(0, 100);

      const ids = filtered.map((player: any) => player.id);
      let accounts: any[] = [];
      let saves: any[] = [];
      if (ids.length > 0) {
        const accountResult = await admin
          .from("pup_accounts")
          .select("player_uuid,id,status,stage,allow_multiple_devices,updated_at")
          .in("player_uuid", ids);
        if (accountResult.error) throw accountResult.error;
        accounts = accountResult.data ?? [];

        const saveResult = await admin
          .from("pup_account_saves")
          .select("player_uuid,revision,generation,updated_at")
          .in("player_uuid", ids);
        if (saveResult.error) throw saveResult.error;
        saves = saveResult.data ?? [];
      }
      const accountByPlayer = new Map(accounts.map((row: any) => [row.player_uuid, row]));
      const saveByPlayer = new Map(saves.map((row: any) => [row.player_uuid, row]));

      return json(200, {
        users: filtered.map((player: any) => ({
          ...player,
          account: accountByPlayer.get(player.id) ?? null,
          cloudSave: saveByPlayer.get(player.id) ?? null,
        })),
      });
    }

    const targetPlayerUuid = requiredString(payload.targetPlayerUuid, "targetPlayerUuid", 80);
    const player = await targetPlayer(admin, targetPlayerUuid);

    if (envelope.action === "admin-user-detail") {
      const account = await targetAccount(admin, targetPlayerUuid);
      let save: any = null;
      try {
        save = await targetSave(admin, targetPlayerUuid);
      } catch (error) {
        if (!(error instanceof HttpError) || error.status !== 409) throw error;
      }
      return json(200, {
        player,
        account,
        cloudSave: save
          ? {
              revision: Number(save.revision),
              generation: Number(save.generation),
              updatedAt: save.updated_at,
              game: gameSummary(save),
            }
          : null,
      });
    }

    if (envelope.action === "admin-message-send") {
      const title = requiredString(payload.title, "title", 100);
      const body = requiredString(payload.body, "body", 1500);
      await sendNotification(
        admin,
        session.player.id,
        targetPlayerUuid,
        "developer",
        title,
        body,
        { source: "developer_console" },
      );
      await recordAction(admin, session.player.id, targetPlayerUuid, "MESSAGE_SENT", { title });
      return json(200, { success: true });
    }

    const save = await targetSave(admin, targetPlayerUuid);
    const saveData = structuredClone(save.save_data);
    const main = encodedMain(saveData);

    if (envelope.action === "admin-game-update") {
      const fields = objectValue(payload.fields, "fields");
      const applied: Record<string, number> = {};
      const longFieldMap: Record<string, string> = {
        treats: "treats",
        lifetimeTreats: "lifetime_treats",
        playerXp: "player_xp_v1",
        bones: "bones_v7",
        pupCoins: "pup_coins_v7",
        casinoChips: "casino_chips_v7",
        totalTaps: "total_taps",
        careActions: "care_actions_v5",
        totalShopPurchases: "total_shop_purchases_v5",
        totalTicketsFound: "total_tickets_found",
        totalPrestigePointsEarned: "prestige_total_points_v6",
      };
      for (const [field, key] of Object.entries(longFieldMap)) {
        if (fields[field] !== undefined) {
          const amount = safeInteger(fields[field], field, 0, MAX_BALANCE);
          writeLong(main, key, amount);
          applied[field] = amount;
        }
      }

      const intFields: Record<string, { key: string; max: number }> = {
        dailyStreak: { key: "daily_streak_v5", max: 365 },
        prestigeCount: { key: "prestige_count_v6", max: 2_000_000_000 },
        skillPoints: { key: "prestige_skill_points_v6", max: 2_000_000_000 },
      };
      for (const [field, config] of Object.entries(intFields)) {
        if (fields[field] !== undefined) {
          const amount = safeInteger(fields[field], field, 0, config.max);
          writeInt(main, config.key, amount);
          applied[field] = amount;
        }
      }

      if (applied.treats !== undefined && fields.lifetimeTreats === undefined) {
        const currentLifetime = Number(main.values.lifetime_treats ?? 0);
        if (currentLifetime < applied.treats) {
          writeLong(main, "lifetime_treats", applied.treats);
          applied.lifetimeTreats = applied.treats;
        }
      }

      const careFields = ["happiness", "fullness", "energy", "cleanliness", "bond"];
      const style = typeof main.values.puppy_style === "string" ? main.values.puppy_style : "classic";
      let touchedCare = false;
      for (const field of careFields) {
        if (fields[field] === undefined) continue;
        const value = safeInteger(fields[field], field, 0, 100);
        const legacyKey = field === "cleanliness" ? "cleanliness_v5" : field === "bond" ? "bond_v5" : field;
        writeInt(main, legacyKey, value);
        writeInt(main, "care_profile_v1_" + style + "_" + field, value);
        applied[field] = value;
        touchedCare = true;
      }
      if (touchedCare) {
        writeLong(main, "care_profile_v1_" + style + "_updated_at", Date.now());
      }
      if (Object.keys(applied).length === 0) {
        throw new HttpError(400, "ADMIN_PAYLOAD_INVALID", "No supported game-data fields were supplied.");
      }

      const revision = await commitSave(admin, save, saveData);
      await sendNotification(
        admin,
        session.player.id,
        targetPlayerUuid,
        "account_update",
        "Developer updated your Pup Account",
        "Your Puppy Clicker account data was updated by a developer and synced from Pup Account.",
        { fields: Object.keys(applied), revision },
      );
      await recordAction(admin, session.player.id, targetPlayerUuid, "GAME_DATA_UPDATED", {
        fields: applied,
        revision,
      });
      return json(200, { success: true, revision, game: gameSummary({ save_data: saveData }) });
    }

    const itemType = requiredString(payload.itemType, "itemType", 32).toLowerCase();
    const itemId = requiredString(payload.itemId, "itemId", 120);
    let title = "";
    let body = "";
    let kind = "item_grant";
    const detail: Record<string, unknown> = { itemType, itemId };

    if (itemType === "puppy") {
      const present = payload.present === true;
      if (!present && CORE_PUPPIES.has(itemId)) {
        throw new HttpError(400, "ADMIN_CORE_ITEM", "Core starter puppies cannot be removed.");
      }
      const items = stringSet(main, "unlocked_puppies");
      if (present) items.add(itemId); else items.delete(itemId);
      CORE_PUPPIES.forEach((id) => items.add(id));
      writeStringSet(main, "unlocked_puppies", items);
      kind = present ? "item_grant" : "item_remove";
      title = present ? "Developer unlocked a puppy for you 🐾" : "Developer removed a puppy";
      body = present
        ? itemId + " was added to your Puppy Clicker collection."
        : itemId + " was removed from your Puppy Clicker collection.";
      detail.present = present;
    } else if (itemType === "accessory") {
      const present = payload.present === true;
      if (!present && itemId === "None") {
        throw new HttpError(400, "ADMIN_CORE_ITEM", "The default None accessory cannot be removed.");
      }
      const items = stringSet(main, "owned_accessories_v7");
      items.add("None");
      if (present) items.add(itemId); else items.delete(itemId);
      writeStringSet(main, "owned_accessories_v7", items);
      if (!present && main.values.accessory === itemId) {
        main.values.accessory = "None";
        main.types.accessory = "string";
      }
      kind = present ? "item_grant" : "item_remove";
      title = present ? "Developer unlocked an accessory for you ✨" : "Developer removed an accessory";
      body = present
        ? itemId + " was added to your Puppy Clicker accessories."
        : itemId + " was removed from your Puppy Clicker accessories.";
      detail.present = present;
    } else if (itemType === "ticket") {
      const rarity = itemId.toLowerCase();
      if (!TICKET_IDS.has(rarity)) {
        throw new HttpError(400, "ADMIN_PAYLOAD_INVALID", "Unknown Upgrade Ticket rarity.");
      }
      const count = safeInteger(payload.count, "count", 0, MAX_TICKETS);
      writeInt(main, "upgrade_ticket_" + rarity, count);
      kind = "account_update";
      title = "Developer updated your Upgrade Tickets 🎟️";
      body = rarity.toUpperCase() + " Upgrade Tickets are now set to " + count + ".";
      detail.count = count;
    } else {
      throw new HttpError(400, "ADMIN_PAYLOAD_INVALID", "itemType must be puppy, accessory, or ticket.");
    }

    const revision = await commitSave(admin, save, saveData);
    detail.revision = revision;
    await sendNotification(
      admin,
      session.player.id,
      targetPlayerUuid,
      kind,
      title,
      body,
      detail,
    );
    await recordAction(admin, session.player.id, targetPlayerUuid, "ITEM_UPDATED", detail);
    return json(200, { success: true, revision, game: gameSummary({ save_data: saveData }) });
  } catch (error) {
    return errorResponse(error);
  }
});
