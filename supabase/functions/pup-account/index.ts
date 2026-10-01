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

const MAX_SAVE_BYTES = 1024 * 1024;

function objectValue(value: unknown, field: string): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new HttpError(400, "PUP_ACCOUNT_PAYLOAD_INVALID", `${field} must be an object.`);
  }
  return value as Record<string, unknown>;
}

function integerValue(value: unknown, field: string, minimum = 0): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < minimum) {
    throw new HttpError(400, "PUP_ACCOUNT_PAYLOAD_INVALID", `${field} must be an integer >= ${minimum}.`);
  }
  return value;
}

function cloudSaveValue(payload: Record<string, unknown>): Record<string, unknown> {
  const serialized = payload.saveDataJson;
  if (typeof serialized === "string") {
    const encoded = new TextEncoder().encode(serialized);
    if (encoded.byteLength > MAX_SAVE_BYTES) {
      throw new HttpError(
        413,
        "PUP_ACCOUNT_SAVE_TOO_LARGE",
        "Pup Account cloud save exceeds the T0 1 MiB limit.",
      );
    }

    let parsed: unknown;
    try {
      parsed = JSON.parse(serialized);
    } catch {
      throw new HttpError(
        400,
        "PUP_ACCOUNT_PAYLOAD_INVALID",
        "saveDataJson must contain valid JSON.",
      );
    }
    return objectValue(parsed, "saveDataJson");
  }

  // Backward compatibility for older Puppy Clicker builds.
  const legacy = objectValue(payload.saveData, "saveData");
  const encoded = new TextEncoder().encode(JSON.stringify(legacy));
  if (encoded.byteLength > MAX_SAVE_BYTES) {
    throw new HttpError(
      413,
      "PUP_ACCOUNT_SAVE_TOO_LARGE",
      "Pup Account cloud save exceeds the T0 1 MiB limit.",
    );
  }
  return legacy;
}

async function ensureAccount(admin: any, session: any): Promise<any> {
  const discordUserId = session.player.discord_user_id;
  const discordUsername = session.player.discord_username;
  if (!discordUserId || !discordUsername) {
    throw new HttpError(
      409,
      "PUP_ACCOUNT_DISCORD_REQUIRED",
      "Connect Discord to create or sign in to a Pup Account.",
    );
  }

  const { data: existing, error: existingError } = await admin
    .from("pup_accounts")
    .select("*")
    .eq("player_uuid", session.player.id)
    .maybeSingle();
  if (existingError) throw existingError;

  if (existing) {
    if (existing.discord_user_id !== discordUserId) {
      throw new HttpError(
        409,
        "PUP_ACCOUNT_IDENTITY_CONFLICT",
        "This Pup Account is linked to a different Discord identity.",
      );
    }

    const { data: updated, error: updateError } = await admin
      .from("pup_accounts")
      .update({
        username: discordUsername,
        display_name: session.player.discord_global_name ?? null,
        avatar_hash: session.player.discord_avatar_hash ?? null,
        active_installation_uuid: session.installation.id,
        stage: "t0",
        updated_at: new Date().toISOString(),
      })
      .eq("id", existing.id)
      .select("*")
      .single();
    if (updateError) throw updateError;
    return updated;
  }

  const { data: discordOwner, error: ownerError } = await admin
    .from("pup_accounts")
    .select("id,player_uuid")
    .eq("discord_user_id", discordUserId)
    .maybeSingle();
  if (ownerError) throw ownerError;
  if (discordOwner && discordOwner.player_uuid !== session.player.id) {
    throw new HttpError(
      409,
      "PUP_ACCOUNT_ON_OTHER_DEVICE",
      "This Discord account already owns a Pup Account on another Puppy Clicker player/device. Device transfer is required.",
    );
  }

  const { data: account, error: createError } = await admin
    .from("pup_accounts")
    .insert({
      player_uuid: session.player.id,
      discord_user_id: discordUserId,
      username: discordUsername,
      display_name: session.player.discord_global_name ?? null,
      avatar_hash: session.player.discord_avatar_hash ?? null,
      active_installation_uuid: session.installation.id,
      stage: "t0",
      status: "active",
    })
    .select("*")
    .single();
  if (createError) throw createError;
  return account;
}

Deno.serve(async (req: Request) => {
  try {
    assertPublishableRequest(req);
    const envelope = verifyEnvelope(await req.json());
    if (!["account-status", "cloud-save-read", "cloud-save-write"].includes(envelope.action)) {
      throw new HttpError(400, "PUP_ACCOUNT_ACTION_INVALID", "Unsupported Pup Account action.");
    }

    const admin = createAdminClient();
    const session = await authenticateSession(req, admin, envelope);
    const account = await ensureAccount(admin, session);

    if (account.status !== "active") {
      throw new HttpError(
        403,
        "PUP_ACCOUNT_BLOCKED",
        "This Pup Account is not currently allowed to access cloud progression.",
      );
    }

    if (envelope.action === "account-status") {
      const { data: save, error: saveError } = await admin
        .from("pup_account_saves")
        .select("revision,generation,updated_at")
        .eq("player_uuid", session.player.id)
        .maybeSingle();
      if (saveError) throw saveError;

      return json(200, {
        realtimeTopic: `pup-account:${account.realtime_topic}`,
        guildRole: session.player.guild_role ?? null,
        guildVerifiedAt: session.player.guild_verified_at ?? null,
        account: {
          id: account.id,
          stage: account.stage,
          status: account.status,
          username: account.username,
          displayName: account.display_name ?? null,
          avatarHash: account.avatar_hash ?? null,
          discordUserId: account.discord_user_id,
          allowMultipleDevices: Boolean(account.allow_multiple_devices),
        },
        cloudSave: save
          ? {
              exists: true,
              revision: Number(save.revision),
              generation: Number(save.generation),
              updatedAt: save.updated_at,
            }
          : { exists: false, revision: 0, generation: 0, updatedAt: null },
      });
    }

    if (envelope.action === "cloud-save-read") {
      const { data: save, error: saveError } = await admin
        .from("pup_account_saves")
        .select("*")
        .eq("player_uuid", session.player.id)
        .maybeSingle();
      if (saveError) throw saveError;

      return json(200, {
        realtimeTopic: `pup-account:${account.realtime_topic}`,
        save: save
          ? {
              revision: Number(save.revision),
              generation: Number(save.generation),
              saveSchema: Number(save.save_schema),
              saveData: save.save_data,
              payloadHashSha256: save.payload_hash_sha256,
              updatedAt: save.updated_at,
            }
          : null,
      });
    }

    const payload = envelope.payload;
    const expectedRevision = integerValue(payload.expectedRevision, "expectedRevision", 0);
    const saveSchema = integerValue(payload.saveSchema ?? 1, "saveSchema", 1);
    const saveData = cloudSaveValue(payload);

    const { data: current, error: currentError } = await admin
      .from("pup_account_saves")
      .select("*")
      .eq("player_uuid", session.player.id)
      .maybeSingle();
    if (currentError) throw currentError;

    const currentRevision = current ? Number(current.revision) : 0;
    if (expectedRevision !== currentRevision) {
      throw new HttpError(
        409,
        "CLOUD_SAVE_CONFLICT",
        `Cloud save changed from revision ${expectedRevision} to ${currentRevision}. Reload before saving.`,
        { currentRevision },
      );
    }

    if (current && Number(current.generation) > envelope.generation) {
      throw new HttpError(
        409,
        "SAVE_ROLLBACK",
        "This device attempted to replace a newer authenticated cloud save.",
        {
          currentGeneration: Number(current.generation),
          attemptedGeneration: envelope.generation,
        },
      );
    }

    const nextRevision = currentRevision + 1;
    const payloadHashSha256 = sha256Hex(canonicalJson(saveData));
    const row = {
      player_uuid: session.player.id,
      account_uuid: account.id,
      installation_uuid: session.installation.id,
      revision: nextRevision,
      generation: envelope.generation,
      save_schema: saveSchema,
      save_data: saveData,
      payload_hash_sha256: payloadHashSha256,
      updated_at: new Date().toISOString(),
    };

    const { error: writeError } = await admin
      .from("pup_account_saves")
      .upsert(row, { onConflict: "player_uuid" });
    if (writeError) throw writeError;

    return json(200, {
      realtimeTopic: `pup-account:${account.realtime_topic}`,
      revision: nextRevision,
      generation: envelope.generation,
      payloadHashSha256,
    });
  } catch (error) {
    return errorResponse(error);
  }
});
