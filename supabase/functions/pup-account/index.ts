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

// Account creation is never triggered by an unverified nickname, an email
// string, or a second device's attempt to log in. Both a verified Supabase Auth
// principal and a signed, active PupEye session are required to link credentials.
async function linkLocalAccount(req: Request, admin: any, session: any): Promise<any> {
  const authorization = req.headers.get("authorization") ?? "";
  const match = /^Bearer ([^\s]+)$/.exec(authorization);
  if (!match) throw new HttpError(401, "PUP_LOGIN_TOKEN_REQUIRED", "Sign in to your Pup Account first.");

  const { data: identity, error: authError } = await admin.auth.getUser(match[1]);
  const user = identity?.user;
  if (authError || !user) {
    throw new HttpError(401, "PUP_LOGIN_INVALID", "Pup Account password verification failed.");
  }
  if (!user.email || !(user.email_confirmed_at || user.confirmed_at)) {
    throw new HttpError(403, "PUP_EMAIL_CONFIRMATION_REQUIRED", "Verify your email before signing in.");
  }

  const { data: byUser, error: byUserError } = await admin.from("pup_accounts")
    .select("*").eq("auth_user_id", user.id).maybeSingle();
  if (byUserError) throw byUserError;
  if (byUser && byUser.player_uuid !== session.player.id) {
    throw new HttpError(409, "PUP_ACCOUNT_ON_OTHER_DEVICE",
      "This Pup Account is bound to another installation. An approved device transfer is required.");
  }

  const { data: byPlayer, error: playerError } = await admin.from("pup_accounts")
    .select("*").eq("player_uuid", session.player.id).maybeSingle();
  if (playerError) throw playerError;
  // A verified email may already belong to a Discord-only Kotlin player.
  // Email equality is a collision guard, NOT proof of ownership or authority
  // to migrate that player's save. Require approved transfer/Discord linking.
  if (!byPlayer) {
    const escapedEmail = user.email.toLowerCase().replace(/[\\%_]/g, "\\  if (byPlayer?.auth_user_id && byPlayer.auth_user_id !== user.id) {");
    const { data: legacyMatches, error: legacyError } = await admin
      .from("pupeye_players").select("id")
      .ilike("discord_email", escapedEmail)
      .neq("id", session.player.id).limit(1);
    if (legacyError) throw legacyError;
    if (legacyMatches?.length) {
      throw new HttpError(409, "PUP_ACCOUNT_ON_OTHER_DEVICE",
        "An existing Discord-linked Puppy Clicker identity uses this email. Transfer or link the existing account; do not create a duplicate.");
    }
  }
  if (byPlayer?.auth_user_id && byPlayer.auth_user_id !== user.id) {
    throw new HttpError(409, "PUP_ACCOUNT_IDENTITY_CONFLICT",
      "This PupEye player already belongs to another email/password account.");
  }
  if (byPlayer?.discord_user_id && byPlayer.discord_user_id !== session.player.discord_user_id) {
    throw new HttpError(409, "PUP_ACCOUNT_IDENTITY_CONFLICT",
      "The existing Discord account must be verified on this installation before adding a password.");
  }

  if (byPlayer && byPlayer.status !== "active") {
    throw new HttpError(403, "PUP_ACCOUNT_BLOCKED", "This account cannot be linked while restricted.");
  }

  const requested = user.user_metadata?.puppy_username;
  const username = byPlayer?.local_username ??
    (typeof requested === "string" ? requested.trim() : "");
  if (!/^[A-Za-z0-9_.-]{3,20}$/.test(username)) {
    throw new HttpError(400, "PUP_USERNAME_REQUIRED",
      "Pup Account registration requires a 3–20 character username.");
  }
  const values = {
    auth_user_id: user.id,
    local_username: username,
    // Do not replace an established Discord identity, Player ID or cloud save.
    username,
    active_installation_uuid: session.installation.id,
    updated_at: new Date().toISOString(),
  };
  const write = byPlayer
    ? await admin.from("pup_accounts").update(values).eq("id", byPlayer.id).select("*").single()
    : await admin.from("pup_accounts").insert({
      ...values, player_uuid: session.player.id,
      discord_user_id: session.player.discord_user_id ?? null,
      stage: "t0", status: "active",
    }).select("*").single();
  if (write.error) {
    if (write.error.code === "23505") {
      throw new HttpError(409, "PUP_USERNAME_TAKEN",
        "This username is already in use. Choose another username.");
    }
    throw write.error;
  }
  return write.data;
}

async function ensureAccount(admin: any, session: any): Promise<any> {
  const discordUserId = session.player.discord_user_id;
  const discordUsername = session.player.discord_username;
  const { data: existing, error: existingError } = await admin
    .from("pup_accounts").select("*").eq("player_uuid", session.player.id).maybeSingle();
  if (existingError) throw existingError;

  if (existing) {
    if (existing.discord_user_id && discordUserId &&
        existing.discord_user_id !== discordUserId) {
      throw new HttpError(409, "PUP_ACCOUNT_IDENTITY_CONFLICT",
        "This Pup Account is linked to a different Discord identity.");
    }
    if (!existing.auth_user_id && !existing.discord_user_id && !discordUserId) {
      throw new HttpError(401, "PUP_ACCOUNT_LOGIN_REQUIRED", "Sign in to your Pup Account.");
    }
    // First-party local username survives connecting or disconnecting Discord.
    const changes = {
      username: existing.local_username ?? (discordUsername || existing.username),
      discord_user_id: discordUserId ?? existing.discord_user_id,
      display_name: discordUserId ? session.player.discord_global_name ?? null : existing.display_name,
      avatar_hash: discordUserId ? session.player.discord_avatar_hash ?? null : existing.avatar_hash,
      active_installation_uuid: session.installation.id,
      updated_at: new Date().toISOString(),
    };
    if (existing.username === changes.username &&
        existing.discord_user_id === changes.discord_user_id &&
        existing.display_name === changes.display_name &&
        existing.avatar_hash === changes.avatar_hash &&
        existing.active_installation_uuid === changes.active_installation_uuid) return existing;
    const { data: updated, error } = await admin.from("pup_accounts")
      .update(changes).eq("id", existing.id).select("*").single();
    if (error) throw error;
    return updated;
  }

  if (!discordUserId || !discordUsername) {
    throw new HttpError(401, "PUP_ACCOUNT_LOGIN_REQUIRED",
      "Create or sign in to a Pup Account. Discord is optional.");
  }
  const { data: owner, error: ownerError } = await admin.from("pup_accounts")
    .select("id,player_uuid").eq("discord_user_id", discordUserId).maybeSingle();
  if (ownerError) throw ownerError;
  if (owner && owner.player_uuid !== session.player.id) {
    throw new HttpError(409, "PUP_ACCOUNT_ON_OTHER_DEVICE",
      "This Discord account is linked to another installation. Approved device transfer is required.");
  }
  const { data: created, error: createError } = await admin.from("pup_accounts").insert({
    player_uuid: session.player.id, discord_user_id: discordUserId,
    username: discordUsername, display_name: session.player.discord_global_name ?? null,
    avatar_hash: session.player.discord_avatar_hash ?? null,
    active_installation_uuid: session.installation.id, stage: "t0", status: "active",
  }).select("*").single();
  if (createError) throw createError;
  return created;
}

Deno.serve(async (req: Request) => {
  try {
    assertPublishableRequest(req);
    const envelope = verifyEnvelope(await req.json());
    if (!["auth-local", "account-status", "cloud-save-read", "cloud-save-write"].includes(envelope.action)) {
      throw new HttpError(400, "PUP_ACCOUNT_ACTION_INVALID", "Unsupported Pup Account action.");
    }

    const admin = createAdminClient();
    const session = await authenticateSession(req, admin, envelope);
    const account = envelope.action === "auth-local"
      ? await linkLocalAccount(req, admin, session)
      : await ensureAccount(admin, session);

    if (account.status !== "active") {
      throw new HttpError(
        403,
        "PUP_ACCOUNT_BLOCKED",
        "This Pup Account is not currently allowed to access cloud progression.",
      );
    }

    if (envelope.action === "auth-local") {
      return json(200, { ok: true, accountId: account.id });
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
          loginMethods: { password: Boolean(account.auth_user_id), discord: Boolean(account.discord_user_id) },
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
