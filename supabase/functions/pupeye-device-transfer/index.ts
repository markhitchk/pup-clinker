import {
  HttpError,
  assertPublishableRequest,
  audit,
  authenticateSession,
  createAdminClient,
  errorResponse,
  issueSession,
  json,
  requiredString,
  verifyEnvelope,
} from "../_shared/pupeye.ts";
import { assertGlobalEnforcementAllowed } from "../_shared/global-enforcement.ts";

/**
 * Pup Account device transfer.
 *
 * Account ownership is proven directly with Discord OAuth. The requesting phone must also
 * present its authenticated PupEye session + signed installation envelope. No game-save file,
 * migration claim, backup password, or client-side Supabase secret participates in this flow.
 */
Deno.serve(async (req: Request) => {
  try {
    assertPublishableRequest(req);
    const envelope = verifyEnvelope(await req.json(), "pup-account-device-transfer");
    const admin = createAdminClient();
    const requesting = await authenticateSession(req, admin, envelope);
    const accessToken = requiredString(
      envelope.payload.discordAccessToken,
      "discordAccessToken",
    );

    const profileResponse = await fetch("https://discord.com/api/v10/users/@me", {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
      },
    });
    if (!profileResponse.ok) {
      throw new HttpError(
        401,
        "DISCORD_TOKEN_INVALID",
        "Discord could not verify this Pup Account authorization.",
      );
    }

    const profile = await profileResponse.json();
    const discordUserId = requiredString(profile.id, "Discord user ID");
    const discordUsername = requiredString(profile.username, "Discord username");

    const { data: targetPlayer, error: targetPlayerError } = await admin
      .from("pupeye_players")
      .select("*")
      .eq("discord_user_id", discordUserId)
      .maybeSingle();
    if (targetPlayerError) throw targetPlayerError;
    if (!targetPlayer) {
      throw new HttpError(
        404,
        "PUP_ACCOUNT_NOT_FOUND",
        "No existing Pup Account is linked to this Discord identity.",
      );
    }

    const { data: targetInstallation, error: targetInstallationError } = await admin
      .from("pupeye_installations")
      .select("*")
      .eq("player_uuid", targetPlayer.id)
      .eq("active", true)
      .is("revoked_at", null)
      .maybeSingle();
    if (targetInstallationError) throw targetInstallationError;

    await assertGlobalEnforcementAllowed(admin, {
      player: targetPlayer,
      installation: targetInstallation,
      discordUserId,
      requestAction: "pup-account-device-transfer",
    });

    let { data: targetAccount, error: accountError } = await admin
      .from("pup_accounts")
      .select("*")
      .eq("discord_user_id", discordUserId)
      .maybeSingle();
    if (accountError) throw accountError;

    if (!targetAccount) {
      const { data, error } = await admin
        .from("pup_accounts")
        .insert({
          player_uuid: targetPlayer.id,
          discord_user_id: discordUserId,
          username: discordUsername,
          display_name: typeof profile.global_name === "string"
            ? profile.global_name
            : null,
          avatar_hash: typeof profile.avatar === "string" ? profile.avatar : null,
          active_installation_uuid: targetInstallation?.id ?? null,
          stage: "t0",
          status: "active",
        })
        .select("*")
        .single();
      if (error) throw error;
      targetAccount = data;
    }

    if (targetAccount.status !== "active") {
      throw new HttpError(
        403,
        "PUP_ACCOUNT_BLOCKED",
        "This Pup Account is not currently allowed to transfer devices.",
      );
    }

    const { data: save, error: saveError } = await admin
      .from("pup_account_saves")
      .select("revision,generation")
      .eq("player_uuid", targetPlayer.id)
      .maybeSingle();
    if (saveError) throw saveError;

    if (targetPlayer.id !== requesting.player.id) {
      const { data: requestingAccount, error: requestingAccountError } = await admin
        .from("pup_accounts")
        .select("id")
        .eq("player_uuid", requesting.player.id)
        .maybeSingle();
      if (requestingAccountError) throw requestingAccountError;
      if (requestingAccount && requestingAccount.id !== targetAccount.id) {
        throw new HttpError(
          409,
          "PUP_ACCOUNT_TRANSFER_CONFLICT",
          "This installation is already attached to a different Pup Account.",
        );
      }

      const { error: transferError } = await admin.rpc(
        "pup_account_transfer_device",
        {
          p_source_player_uuid: requesting.player.id,
          p_source_installation_uuid: requesting.installation.id,
          p_target_player_uuid: targetPlayer.id,
          p_target_account_uuid: targetAccount.id,
          p_cloud_generation: Number(save?.generation ?? 0),
        },
      );
      if (transferError) throw transferError;

      await audit(
        admin,
        "PUP_ACCOUNT_DEVICE_TRANSFER",
        "info",
        {
          discordUserId,
          previousInstallationUuid: targetInstallation?.id ?? null,
          newInstallationUuid: requesting.installation.id,
          cloudRevision: Number(save?.revision ?? 0),
          cloudGeneration: Number(save?.generation ?? 0),
        },
        targetPlayer.id,
        requesting.installation.id,
      );
    }

    const session = await issueSession(
      admin,
      targetPlayer.id,
      requesting.installation.id,
    );

    return json(200, {
      ok: true,
      deviceTransferred: targetPlayer.id !== requesting.player.id,
      sessionToken: session.token,
      expiresAtEpochMs: session.expiresAtEpochMs,
      backendPlayerId: targetPlayer.id,
      backendInstallationId: requesting.installation.id,
      playerId: targetPlayer.player_id,
      friendCode: targetPlayer.friend_code,
      username: targetPlayer.username,
      cloudRevision: Number(save?.revision ?? 0),
      cloudGeneration: Number(save?.generation ?? 0),
      message: "Pup Account device authorization completed.",
    });
  } catch (error) {
    return errorResponse(error);
  }
});
