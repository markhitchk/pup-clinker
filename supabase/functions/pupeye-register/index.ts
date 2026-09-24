import { withSupabase } from "npm:@supabase/server";
import {
  HttpError,
  audit,
  errorResponse,
  issueSession,
  json,
  optionalString,
  requiredString,
  verifyEnvelope,
} from "../_shared/pupeye.ts";

export default {
  fetch: withSupabase({ auth: "publishable" }, async (req, ctx) => {
    try {
      const envelope = verifyEnvelope(await req.json(), "register");
      const payload = envelope.payload;
      const playerId = requiredString(payload.playerId, "playerId");
      const friendCode = requiredString(payload.friendCode, "friendCode");
      const username = requiredString(payload.username, "username").slice(0, 32);
      const supportCode = requiredString(payload.supportCode, "supportCode").slice(0, 32);
      const deviceModel = optionalString(payload.deviceModel)?.slice(0, 64);
      const platform = optionalString(payload.platform)?.slice(0, 24) ?? "android";
      const appVersion = optionalString(payload.appVersion)?.slice(0, 32);
      const admin = ctx.supabaseAdmin;

      const { data: byPlayerId, error: playerIdError } = await admin
        .from("pupeye_players").select("*").eq("player_id", playerId).maybeSingle();
      if (playerIdError) throw playerIdError;

      const { data: byFriendCode, error: friendCodeError } = await admin
        .from("pupeye_players").select("*").eq("friend_code", friendCode).maybeSingle();
      if (friendCodeError) throw friendCodeError;

      if (byPlayerId && byFriendCode && byPlayerId.id !== byFriendCode.id) {
        throw new HttpError(409, "PLAYER_IDENTITY_CONFLICT", "Player ID and Friend Code belong to different registered players.");
      }

      let player = byPlayerId ?? byFriendCode;
      if (player && (player.player_id !== playerId || player.friend_code !== friendCode)) {
        throw new HttpError(409, "PLAYER_IDENTITY_CONFLICT", "This player identity conflicts with an existing Puppy Clicker account.");
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
        if (player.status === "blocked") {
          throw new HttpError(403, "PLAYER_BLOCKED", "This Puppy Clicker player requires Support review.");
        }
        await admin.from("pupeye_players")
          .update({ username, updated_at: new Date().toISOString() })
          .eq("id", player.id);
      }

      const { data: exactInstallation, error: exactError } = await admin
        .from("pupeye_installations")
        .select("*")
        .eq("installation_id", envelope.installationId)
        .maybeSingle();
      if (exactError) throw exactError;

      if (exactInstallation) {
        if (
          exactInstallation.player_uuid !== player.id ||
          exactInstallation.device_key_id !== envelope.deviceKeyId ||
          exactInstallation.public_key_b64 !== envelope.publicKey
        ) {
          throw new HttpError(409, "INSTALLATION_IDENTITY_CONFLICT", "Registered installation identity does not match this device key.");
        }
        if (!exactInstallation.active || exactInstallation.revoked_at) {
          throw new HttpError(403, "INSTALLATION_REVOKED", "This Puppy Clicker installation was revoked.");
        }

        await admin.from("pupeye_installations").update({
          support_code: supportCode,
          device_model: deviceModel,
          platform,
          app_version: appVersion,
          last_seen_at: new Date().toISOString(),
        }).eq("id", exactInstallation.id);

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
          })
          .select("*")
          .single();
        if (migratedError) throw migratedError;

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
        })
        .select("*")
        .single();
      if (installationError) throw installationError;

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
  }),
};
