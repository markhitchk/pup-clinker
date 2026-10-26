import {
  HttpError,
  assertPublishableRequest,
  authenticateSession,
  canonicalJson,
  sha256Hex,
  audit,
  createAdminClient,
  errorResponse,
  json,
  requiredString,
  verifyEnvelope,
} from "../_shared/pupeye.ts";
import { assertGlobalEnforcementAllowed } from "../_shared/global-enforcement.ts";
import { applyBetaLiveGameplay } from "../_shared/beta-live-gameplay.ts";

// A beta app is a separate Android installation. This grants a deliberately
// read-only, expiring companion link after Discord proves account ownership.
// The primary PupEye installation, player and cloud-write authority never move.
const MAX_BETA_GRANT_MS = 7 * 24 * 60 * 60 * 1000;
const BETA_ACTIONS = new Set(["beta-link-discord", "beta-account-status", "beta-cloud-read", "beta-account-update", "beta-bootstrap", "beta-gameplay"]);

Deno.serve(async (req: Request) => {
  try {
    assertPublishableRequest(req);
    const envelope = verifyEnvelope(await req.json());
    if (!BETA_ACTIONS.has(envelope.action)) {
      throw new HttpError(400, "BETA_ACTION_INVALID", "Unsupported beta account action.");
    }
    const admin = createAdminClient();
    const session = await authenticateSession(req, admin, envelope);

    // This endpoint never authorizes gameplay or transfer. Reject other clients
    // so the production app keeps using its existing account/auth endpoints.
    if (session.installation.platform !== "android" ||
        !/^-?\d+(?:\.\d+)*(?:-[\w.]+)?-beta$/i.test(
          session.installation.app_version ?? ""
        )) {
      throw new HttpError(403, "BETA_INSTALLATION_REQUIRED", "Only registered Android beta installations can use this endpoint.");
    }

    let account: any;
    if (envelope.action === "beta-link-discord") {
      const accessToken = requiredString(envelope.payload.discordAccessToken, "discordAccessToken");
      const response = await fetch("https://discord.com/api/v10/users/@me", {
        headers: { Authorization: `Bearer ${accessToken}`, Accept: "application/json" },
      });
      if (!response.ok) {
        throw new HttpError(401, "DISCORD_TOKEN_INVALID", "Discord authorization could not be verified.");
      }
      const profile = await response.json();
      const discordId = requiredString(profile.id, "Discord user ID");
      // A provisional account must never be switched away from an existing
      // authenticated Discord identity through the companion path.
      if (session.player.discord_user_id &&
          session.player.discord_user_id !== discordId) {
        throw new HttpError(409, "BETA_IDENTITY_CONFLICT", "This beta already belongs to a different Discord identity.");
      }

      const { data: owner, error: ownerError } = await admin
        .from("pup_accounts").select("*").eq("discord_user_id", discordId).maybeSingle();
      if (ownerError) throw ownerError;
      if (!owner || owner.player_uuid === session.player.id) {
        // A genuinely new player still goes through the ordinary PupEye
        // Discord registration; it is not a borrowed or synthetic account.
        throw new HttpError(404, "BETA_ACCOUNT_NOT_FOUND", "No existing Pup Account link; use normal PupEye registration.");
      }
      if (owner.status !== "active") {
        throw new HttpError(403, "PUP_ACCOUNT_BLOCKED", "This Pup Account cannot be linked for beta access.");
      }

      const { data: prior, error: priorError } = await admin
        .from("pup_beta_account_links")
        .select("account_uuid")
        .eq("installation_uuid", session.installation.id).maybeSingle();
      if (priorError) throw priorError;
      if (prior && prior.account_uuid !== owner.id) {
        throw new HttpError(409, "BETA_IDENTITY_CONFLICT", "This beta installation is linked to another account. Contact Support.");
      }

      await verifyOwner(admin, owner, "beta-link-discord");
      const { error: linkError } = await admin.from("pup_beta_account_links").upsert({
        installation_uuid: session.installation.id,
        account_uuid: owner.id,
        discord_user_id: discordId,
        expires_at: new Date(Date.now() + MAX_BETA_GRANT_MS).toISOString(),
        verified_at: new Date().toISOString(),
      }, { onConflict: "installation_uuid" });
      if (linkError) throw linkError;
      await audit(admin, "BETA_COMPANION_LINKED", "info", {
        accountId: owner.id, mode: "read_only", source: "DISCORD_OAUTH_USERS_ME",
      }, session.player.id, session.installation.id);
      account = owner;
    } else {
      const { data: grant, error: grantError } = await admin
        .from("pup_beta_account_links")
        .select("account_uuid,discord_user_id,credential_auth_user_id")
        .eq("installation_uuid", session.installation.id)
        .gt("expires_at", new Date().toISOString())
        .maybeSingle();
      if (grantError) throw grantError;
      if (!grant) {
        // A brand-new Pup Account created on this beta does not have a
        // companion grant: it already owns its signed PupEye installation.
        if (envelope.action !== "beta-gameplay") {
          throw new HttpError(401, "BETA_LINK_REQUIRED", "Reconnect your Pup Account.");
        }
        const { data: own, error: ownError } = await admin.from("pup_accounts")
          .select("*").eq("player_uuid", session.player.id)
          .eq("active_installation_uuid", session.installation.id).maybeSingle();
        if (ownError) throw ownError;
        if (!own || own.status !== "active") {
          throw new HttpError(401, "BETA_LINK_REQUIRED", "A verified Pup Account is required.");
        }
        account = own;
      } else {
        let query = admin.from("pup_accounts").select("*").eq("id", grant.account_uuid);
        if (grant.discord_user_id) query = query.eq("discord_user_id", grant.discord_user_id);
        const { data: linked, error: ownerError } = await query.maybeSingle();
        if (!grant.discord_user_id && !grant.credential_auth_user_id) {
          throw new HttpError(401, "BETA_LINK_REQUIRED", "Reconnect your Pup Account.");
        }
        if (ownerError) throw ownerError;
        if (!linked || linked.status !== "active") {
          throw new HttpError(403, "BETA_LINK_REVOKED", "Your existing Pup Account is unavailable; sign in again.");
        }
        account = linked;
      }
    }

    const owner = await verifyOwner(admin, account, envelope.action);
    if (envelope.action === "beta-account-update") {
      // Signed beta account profile edits only: no cloud-save or economy writes.
      const displayName = requiredString(envelope.payload.displayName, "displayName").trim();
      if (displayName.length < 2 || displayName.length > 24 ||
          !/^[\p{L}\p{N} ._'’-]+$/u.test(displayName)) {
        throw new HttpError(400, "PUP_DISPLAY_NAME_INVALID", "Display name must be 2–24 characters.");
      }
      const { data: changed, error: updateError } = await admin.from("pup_accounts")
        .update({ display_name: displayName, updated_at: new Date().toISOString() })
        .eq("id", account.id).eq("status", "active")
        .select("id,display_name").single();
      if (updateError) throw updateError;
      await audit(admin, "BETA_ACCOUNT_DISPLAY_NAME_UPDATED", "info",
        { accountId: account.id }, session.player.id, session.installation.id);
      return json(200, { updated: true, displayName: changed.display_name, gameplayReadOnly: true });
    }
    if (envelope.action === "beta-gameplay") {
      // Only server-computed Play and Care actions are enabled. Never accept
      // arbitrary client balances, XP, shop inventory or Kotlin save blobs.
      const intent = envelope.payload;
      if (intent.type !== "tap" && intent.type !== "care") {
        throw new HttpError(400, "BETA_GAMEPLAY_NOT_PORTED", "This gameplay action is not yet live.");
      }
      const { data: current, error: currentError } = await admin
        .from("pup_account_saves").select("*")
        .eq("player_uuid", account.player_uuid).maybeSingle();
      if (currentError) throw currentError;
      if (!current || current.account_uuid !== account.id) {
        throw new HttpError(409, "CLOUD_SAVE_REQUIRED", "Restore the existing Kotlin cloud save first.");
      }
      let next: Record<string, unknown>;
      try {
        next = applyBetaLiveGameplay(current.save_data, {
          type: intent.type, kind: intent.kind,
        });
      } catch (error) {
        const code = error instanceof Error ? error.message : "BETA_GAMEPLAY_REJECTED";
        throw new HttpError(409, "BETA_GAMEPLAY_REJECTED", code);
      }
      const revision = Number(current.revision) + 1;
      const { data: written, error: writeError } = await admin
        .from("pup_account_saves")
        .update({
          revision,
          save_data: next,
          payload_hash_sha256: sha256Hex(canonicalJson(next)),
          updated_at: new Date().toISOString(),
        })
        .eq("player_uuid", account.player_uuid)
        .eq("account_uuid", account.id)
        .eq("revision", current.revision)
        .select("revision,save_data").maybeSingle();
      if (writeError) throw writeError;
      if (!written) {
        throw new HttpError(409, "CLOUD_SAVE_CONFLICT", "Progress changed on Play or beta; refresh and retry.");
      }
      // A failed audit must never turn a committed save into an error response:
      // retrying after a false-negative would double-apply the same action.
      // Sample taps to keep the audit ledger manageable on busy installations.
      if (intent.type !== "tap" || revision % 100 === 0) {
        try {
          await audit(admin, "BETA_LIVE_GAMEPLAY", "info",
            { action: intent.type, kind: intent.type === "care" ? intent.kind : null, revision },
            session.player.id, session.installation.id);
        } catch (auditError) {
          console.error("Beta save committed; follow-up audit unavailable", auditError);
        }
      }
      return json(200, { revision, saveData: written.save_data });
    }
    if (envelope.action === "beta-bootstrap") {
      // Single signed startup response: account identity and snapshot share the
      // same ownership/enforcement check and one network round trip.
      const { data: save, error: saveError } = await admin.from("pup_account_saves")
        .select("revision,generation,save_schema,save_data,updated_at")
        .eq("player_uuid", account.player_uuid).maybeSingle();
      if (saveError) throw saveError;
      return json(200, {
        readOnly: !save,
        gameplayWritable: Boolean(save),
        accountWritable: true,
        linkedFromPlay: true,
        playerId: owner.player_id,
        friendCode: owner.friend_code,
        guildRole: owner.guild_role ?? null,
        account: {
          id: account.id, stage: account.stage, status: account.status,
          username: account.username, displayName: account.display_name ?? null,
          avatarHash: account.avatar_hash ?? null,
          discordUserId: account.discord_user_id,
        },
        cloudSave: save ? {
          exists: true, revision: Number(save.revision), generation: Number(save.generation),
          updatedAt: save.updated_at,
        } : { exists: false, revision: 0, generation: 0, updatedAt: null },
        save: save ? {
          revision: Number(save.revision), generation: Number(save.generation),
          saveSchema: Number(save.save_schema), saveData: save.save_data,
          updatedAt: save.updated_at,
        } : null,
      });
    }
    if (envelope.action === "beta-cloud-read") {
      const { data: save, error: saveError } = await admin
        .from("pup_account_saves").select("*")
        .eq("player_uuid", account.player_uuid).maybeSingle();
      if (saveError) throw saveError;
      return json(200, {
        readOnly: true,
        linkedFromPlay: true,
        save: save ? {
          revision: Number(save.revision),
          generation: Number(save.generation),
          saveSchema: Number(save.save_schema),
          saveData: save.save_data,
          payloadHashSha256: save.payload_hash_sha256,
          updatedAt: save.updated_at,
        } : null,
      });
    }

    const { data: save, error: saveError } = await admin.from("pup_account_saves")
      .select("revision,generation,updated_at")
      .eq("player_uuid", account.player_uuid).maybeSingle();
    if (saveError) throw saveError;
    return json(200, {
      readOnly: true,
      linkedFromPlay: true,
      accountWritable: true,
      playerId: owner.player_id,
      friendCode: owner.friend_code,
      guildRole: owner.guild_role ?? null,
      account: {
        id: account.id,
        stage: account.stage,
        status: account.status,
        username: account.username,
        displayName: account.display_name ?? null,
        avatarHash: account.avatar_hash ?? null,
        discordUserId: account.discord_user_id,
        allowMultipleDevices: false,
      },
      cloudSave: save ? {
        exists: true,
        revision: Number(save.revision),
        generation: Number(save.generation),
        updatedAt: save.updated_at,
      } : { exists: false, revision: 0, generation: 0, updatedAt: null },
    });
  } catch (error) {
    return errorResponse(error);
  }
});

async function verifyOwner(admin: any, account: any, action: string): Promise<any> {
  if (account.status !== "active") {
    throw new HttpError(403, "PUP_ACCOUNT_BLOCKED", "Pup Account is not active.");
  }
  const [{ data: player, error: playerError }, { data: installation, error: installError }] =
    await Promise.all([
      admin.from("pupeye_players").select("*").eq("id", account.player_uuid).maybeSingle(),
      admin.from("pupeye_installations").select("*")
        .eq("id", account.active_installation_uuid).maybeSingle(),
    ]);
  if (playerError) throw playerError;
  if (installError) throw installError;
  if (!player || !installation || installation.player_uuid !== player.id ||
      !installation.active || installation.revoked_at ||
      (account.discord_user_id && player.discord_user_id !== account.discord_user_id)) {
    throw new HttpError(409, "BETA_OWNER_DEVICE_UNAVAILABLE", "Original PupEye installation must be active and verified.");
  }
  await assertGlobalEnforcementAllowed(admin, {
    player, installation,
    discordUserId: account.discord_user_id,
    requestAction: action,
  });
  return player;
}
