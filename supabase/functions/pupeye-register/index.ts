import {
  HttpError,
  assertPublishableRequest,
  createAdminClient,
  audit,
  errorResponse,
  issueSession,
  json,
  optionalString,
  requiredString,
  verifyEnvelope,
} from "../_shared/pupeye.ts";
import { assertGlobalEnforcementAllowed } from "../_shared/global-enforcement.ts";
import { sendBanEventBestEffort } from "../_shared/discord-ban-notify.ts";

Deno.serve(async (req: Request) => {
  try {
    assertPublishableRequest(req);
    const envelope = verifyEnvelope(await req.json(), "register");
    const payload = envelope.payload;
    const playerId = requiredString(payload.playerId, "playerId");
    const friendCode = requiredString(payload.friendCode, "friendCode");
    const username = requiredString(payload.username, "username").slice(0, 32);
    const supportCode = requiredString(payload.supportCode, "supportCode").slice(0, 32);
    const deviceModel = optionalString(payload.deviceModel)?.slice(0, 64);
    const requestedPlatform = optionalString(payload.platform)?.slice(0, 24)?.toLowerCase() ?? "android";
    const platform = ["android", "windows", "macos", "linux", "web"].includes(requestedPlatform)
      ? requestedPlatform
      : "other";
    const appVersion = optionalString(payload.appVersion)?.slice(0, 32);
    const admin = createAdminClient();

    // Resolve a known installation before touching the supplied Player ID. This is the
    // device-ban evasion gate: a recognized banned installation cannot mint a fresh player.
    const { data: exactInstallation, error: exactError } = await admin
      .from("pupeye_installations")
      .select("*")
      .eq("installation_id", envelope.installationId)
      .maybeSingle();
    if (exactError) throw exactError;

    let exactInstallationPlayer: any = null;
    if (exactInstallation) {
      if (
        exactInstallation.device_key_id !== envelope.deviceKeyId ||
        exactInstallation.public_key_b64 !== envelope.publicKey
      ) {
        throw new HttpError(
          409,
          "INSTALLATION_IDENTITY_CONFLICT",
          "Registered installation identity does not match this device key.",
        );
      }
      const { data, error } = await admin.from("pupeye_players")
        .select("*")
        .eq("id", exactInstallation.player_uuid)
        .maybeSingle();
      if (error) throw error;
      exactInstallationPlayer = data;
      if (!exactInstallationPlayer) {
        throw new HttpError(401, "PLAYER_NOT_FOUND", "Pupeye player record was not found.");
      }

      try {
        await assertGlobalEnforcementAllowed(admin, {
          player: exactInstallationPlayer,
          installation: exactInstallation,
          discordUserId: exactInstallationPlayer.discord_user_id ?? null,
          requestAction: "register",
        });
      } catch (error) {
        if (
          error instanceof HttpError &&
          error.code === "GLOBAL_BANNED" &&
          (
            exactInstallationPlayer.player_id !== playerId ||
            exactInstallationPlayer.friend_code !== friendCode
          )
        ) {
          const ban = error.details.ban as Record<string, unknown> | undefined;
          const publicBanId = typeof ban?.id === "string" ? ban.id : null;
          if (publicBanId) {
            const { data: banRow } = await admin.from("pupeye_global_bans")
              .select("id")
              .eq("public_ban_id", publicBanId)
              .maybeSingle();
            if (banRow?.id) {
              const { data: event } = await admin.from("pupeye_ban_events")
                .insert({
                  ban_uuid: banRow.id,
                  event_code: "NEW_ACCOUNT_BLOCKED_ON_BANNED_DEVICE",
                  actor: "system",
                  detail: {
                    attemptedPlayerId: playerId.slice(0, 80),
                    attemptedFriendCode: friendCode.slice(0, 40),
                    installationId: envelope.installationId,
                    platform,
                  },
                  discord_delivery_status: "pending",
                })
                .select("id")
                .single();
              if (typeof event?.id === "number") {
                await sendBanEventBestEffort(admin, event.id);
              }
            }
          }
        }
        throw error;
      }

      if (
        exactInstallationPlayer.player_id !== playerId ||
        exactInstallationPlayer.friend_code !== friendCode
      ) {
        throw new HttpError(
          409,
          "INSTALLATION_IDENTITY_CONFLICT",
          "This installation is already registered to a different Puppy Clicker player.",
        );
      }
    }

    const { data: byPlayerId, error: playerIdError } = await admin
      .from("pupeye_players").select("*").eq("player_id", playerId).maybeSingle();
    if (playerIdError) throw playerIdError;

    const { data: byFriendCode, error: friendCodeError } = await admin
      .from("pupeye_players").select("*").eq("friend_code", friendCode).maybeSingle();
    if (friendCodeError) throw friendCodeError;

    if (byPlayerId && byFriendCode && byPlayerId.id !== byFriendCode.id) {
      throw new HttpError(
        409,
        "PLAYER_IDENTITY_CONFLICT",
        "Player ID and Friend Code belong to different registered players.",
      );
    }

    let player = byPlayerId ?? byFriendCode;
    if (player && (player.player_id !== playerId || player.friend_code !== friendCode)) {
      throw new HttpError(
        409,
        "PLAYER_IDENTITY_CONFLICT",
        "This player identity conflicts with an existing Puppy Clicker account.",
      );
    }

    if (!player) {
      const { data, error } = await admin.from("pupeye_players").insert({
        player_id: playerId,
        friend_code: friendCode,
        username,
      }).select("*").single();
      if (error) throw error;
      player = data;
    } else {
      await assertGlobalEnforcementAllowed(admin, {
        player,
        installation: exactInstallation?.player_uuid === player.id ? exactInstallation : null,
        discordUserId: player.discord_user_id ?? null,
        requestAction: "register",
      });
      const { error } = await admin.from("pupeye_players")
        .update({ username, updated_at: new Date().toISOString() })
        .eq("id", player.id);
      if (error) throw error;
    }

    if (exactInstallation) {
      if (exactInstallation.player_uuid !== player.id) {
        throw new HttpError(
          409,
          "INSTALLATION_IDENTITY_CONFLICT",
          "Registered installation identity does not match this player.",
        );
      }
      if (!exactInstallation.active || exactInstallation.revoked_at) {
        throw new HttpError(
          403,
          "INSTALLATION_REVOKED",
          "This Puppy Clicker installation was revoked.",
        );
      }

      const { error } = await admin.from("pupeye_installations").update({
        support_code: supportCode,
        device_model: deviceModel,
        platform,
        app_version: appVersion,
        last_seen_at: new Date().toISOString(),
      }).eq("id", exactInstallation.id);
      if (error) throw error;

      const session = await issueSession(admin, player.id, exactInstallation.id);
      return json(200, {
        sessionToken: session.token,
        expiresAtEpochMs: session.expiresAtEpochMs,
        backendPlayerId: player.id,
        backendInstallationId: exactInstallation.id,
      });
    }

    const { data: activeInstallation, error: activeError } = await admin
      .from("pupeye_installations")
      .select("*")
      .eq("player_uuid", player.id)
      .eq("active", true)
      .is("revoked_at", null)
      .maybeSingle();
    if (activeError) throw activeError;

    if (activeInstallation) {
      const { data: approved, error: approvedError } = await admin
        .from("pupeye_device_migrations")
        .select("*")
        .eq("player_uuid", player.id)
        .eq("requested_installation_id", envelope.installationId)
        .eq("requested_device_key_id", envelope.deviceKeyId)
        .eq("status", "approved")
        .maybeSingle();
      if (approvedError) throw approvedError;

      if (!approved) {
        const { data: migration, error: migrationError } = await admin
          .from("pupeye_device_migrations")
          .upsert({
            player_uuid: player.id,
            from_installation_uuid: activeInstallation.id,
            requested_installation_id: envelope.installationId,
            requested_device_key_id: envelope.deviceKeyId,
            requested_public_key_b64: envelope.publicKey,
            requested_support_code: supportCode,
            requested_device_model: deviceModel,
            requested_app_version: appVersion,
            status: "pending",
            requested_at: new Date().toISOString(),
          }, {
            onConflict: "player_uuid,requested_installation_id,requested_device_key_id",
          })
          .select("*")
          .single();
        if (migrationError) throw migrationError;

        await audit(
          admin,
          "DEVICE_MIGRATION_REQUIRED",
          "review",
          { supportCode, requestedInstallationId: envelope.installationId },
          player.id,
          activeInstallation.id,
        );
        return json(409, {
          code: "DEVICE_MIGRATION_REQUIRED",
          message: "This player already has an active Puppy Clicker installation. Contact Support and provide the new Support Installation Code.",
          migrationId: migration.id,
          supportCode,
        });
      }

      await admin.from("pupeye_sessions")
        .update({ revoked_at: new Date().toISOString() })
        .eq("installation_uuid", activeInstallation.id)
        .is("revoked_at", null);
      await admin.from("pupeye_installations")
        .update({ active: false, revoked_at: new Date().toISOString() })
        .eq("id", activeInstallation.id);

      const reputation = await createDeviceReputation(admin, platform, appVersion);
      const { data: migratedInstallation, error: migratedError } = await admin
        .from("pupeye_installations")
        .insert({
          player_uuid: player.id,
          installation_id: envelope.installationId,
          device_key_id: envelope.deviceKeyId,
          public_key_b64: envelope.publicKey,
          support_code: supportCode,
          device_model: deviceModel,
          platform,
          app_version: appVersion,
          active: true,
          device_reputation_uuid: reputation.id,
        })
        .select("*")
        .single();
      if (migratedError) throw migratedError;

      await finishDeviceIdentity(admin, player.id, migratedInstallation.id, reputation.id);

      await admin.from("pupeye_device_migrations").update({
        status: "consumed",
        consumed_at: new Date().toISOString(),
      }).eq("id", approved.id);

      await audit(
        admin,
        "DEVICE_MIGRATION_CONSUMED",
        "info",
        { fromInstallationId: activeInstallation.installation_id, supportCode },
        player.id,
        migratedInstallation.id,
      );

      const session = await issueSession(admin, player.id, migratedInstallation.id);
      return json(200, {
        sessionToken: session.token,
        expiresAtEpochMs: session.expiresAtEpochMs,
        backendPlayerId: player.id,
        backendInstallationId: migratedInstallation.id,
        migrated: true,
      });
    }

    const reputation = await createDeviceReputation(admin, platform, appVersion);
    const { data: installation, error: installationError } = await admin
      .from("pupeye_installations")
      .insert({
        player_uuid: player.id,
        installation_id: envelope.installationId,
        device_key_id: envelope.deviceKeyId,
        public_key_b64: envelope.publicKey,
        support_code: supportCode,
        device_model: deviceModel,
        platform,
        app_version: appVersion,
        active: true,
        device_reputation_uuid: reputation.id,
      })
      .select("*")
      .single();
    if (installationError) throw installationError;

    await finishDeviceIdentity(admin, player.id, installation.id, reputation.id);

    await audit(
      admin,
      "INSTALLATION_REGISTERED",
      "info",
      { supportCode, platform, appVersion },
      player.id,
      installation.id,
    );

    const session = await issueSession(admin, player.id, installation.id);
    return json(200, {
      sessionToken: session.token,
      expiresAtEpochMs: session.expiresAtEpochMs,
      backendPlayerId: player.id,
      backendInstallationId: installation.id,
    });
  } catch (error) {
    return errorResponse(error);
  }
});

async function createDeviceReputation(admin: any, platform: string, appVersion: string | null) {
  const { data, error } = await admin.from("pupeye_device_reputation")
    .insert({
      platform,
      reputation_state: "clean",
      metadata: appVersion ? { appVersion } : {},
    })
    .select("*")
    .single();
  if (error) throw error;
  return data;
}

async function finishDeviceIdentity(
  admin: any,
  playerUuid: string,
  installationUuid: string,
  deviceReputationUuid: string,
): Promise<void> {
  const { error: reputationError } = await admin.from("pupeye_device_reputation")
    .update({
      created_from_installation_uuid: installationUuid,
      last_seen_at: new Date().toISOString(),
    })
    .eq("id", deviceReputationUuid);
  if (reputationError) throw reputationError;

  for (const row of [
    {
      link_type: "PLAYER_INSTALLATION",
      player_uuid: playerUuid,
      installation_uuid: installationUuid,
      confidence: "authoritative",
      evidence_code: "SIGNED_REGISTRATION",
    },
    {
      link_type: "INSTALLATION_DEVICE",
      installation_uuid: installationUuid,
      device_reputation_uuid: deviceReputationUuid,
      confidence: "authoritative",
      evidence_code: "SIGNED_REGISTRATION",
    },
  ]) {
    const { error } = await admin.from("pupeye_identity_links").insert(row);
    if (error && error.code !== "23505") throw error;
  }
}
