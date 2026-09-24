import {
  HttpError,
  assertPublishableRequest,
  createAdminClient,
  authenticateSession,
  errorResponse,
  json,
  requiredString,
  verifyEnvelope,
} from "../_shared/pupeye.ts";
import { assertGlobalEnforcementAllowed } from "../_shared/global-enforcement.ts";

const GUILD_ID = Deno.env.get("PUPPY_DISCORD_GUILD_ID") ?? "1547285473397837985";
const ROLE_DEVELOPER_ID = Deno.env.get("PUPPY_DISCORD_ROLE_DEVELOPER_ID") ?? "1547298673006747678";
const ROLE_ADMIN_ID = Deno.env.get("PUPPY_DISCORD_ROLE_ADMIN_ID") ?? "1547298831216017509";
const ROLE_PUP_MEMBERS_ID = Deno.env.get("PUPPY_DISCORD_ROLE_PUP_MEMBERS_ID") ?? "1547299180484104283";
const ROLE_GUEST_ID = Deno.env.get("PUPPY_DISCORD_ROLE_GUEST_ID") ?? "1547299276353441812";

Deno.serve(async (req: Request) => {
  try {
    assertPublishableRequest(req);
      const envelope = verifyEnvelope(await req.json());
      if (!["auth-discord", "unlink-discord"].includes(envelope.action)) {
        throw new HttpError(400, "PUPEYE_ACTION_INVALID", "Unsupported Discord auth action.");
      }
      const admin = createAdminClient();
      const session = await authenticateSession(req, admin, envelope);

      if (envelope.action === "unlink-discord") {
        const { error } = await admin.from("pupeye_players").update({
          discord_user_id: null,
          discord_username: null,
          discord_global_name: null,
          discord_avatar_hash: null,
          discord_email: null,
          guild_role: null,
          guild_verified_at: null,
          updated_at: new Date().toISOString(),
        }).eq("id", session.player.id);
        if (error) throw error;
        return json(200, { ok: true, message: "Discord unlinked from Pupeye." });
      }

      const accessToken = requiredString(envelope.payload.discordAccessToken, "discordAccessToken");
      const profileResponse = await fetch("https://discord.com/api/v10/users/@me", {
        headers: { Authorization: `Bearer ${accessToken}`, Accept: "application/json" },
      });
      if (!profileResponse.ok) {
        throw new HttpError(401, "DISCORD_TOKEN_INVALID", "Discord could not verify this authorization.");
      }
      const profile = await profileResponse.json();
      const discordId = requiredString(profile.id, "Discord user ID");
      const username = requiredString(profile.username, "Discord username");

      // Discord identity becomes authoritative only after Discord /users/@me succeeds.
      // Check any global Discord target before persisting the link or granting a role.
      await assertGlobalEnforcementAllowed(admin, {
        session: session.session,
        player: session.player,
        installation: session.installation,
        discordUserId: discordId,
        requestAction: "auth-discord",
      });

      const { data: alreadyLinked, error: linkedError } = await admin
        .from("pupeye_players")
        .select("id")
        .eq("discord_user_id", discordId)
        .maybeSingle();
      if (linkedError) throw linkedError;
      if (alreadyLinked && alreadyLinked.id !== session.player.id) {
        throw new HttpError(
          409,
          "DISCORD_ALREADY_LINKED",
          "This Discord account is already linked to another Puppy Clicker player. Contact Support for account recovery or device migration.",
        );
      }

      let guildRole: string | null = null;
      const memberResponse = await fetch(
        `https://discord.com/api/v10/users/@me/guilds/${GUILD_ID}/member`,
        { headers: { Authorization: `Bearer ${accessToken}`, Accept: "application/json" } },
      );
      if (memberResponse.ok) {
        const member = await memberResponse.json();
        const roles = new Set<string>(Array.isArray(member.roles) ? member.roles : []);
        guildRole =
          roles.has(ROLE_DEVELOPER_ID) ? "DEVELOPER" :
          roles.has(ROLE_ADMIN_ID) ? "ADMIN" :
          roles.has(ROLE_PUP_MEMBERS_ID) ? "PUP_MEMBER" :
          roles.has(ROLE_GUEST_ID) ? "GUEST" :
          null;
      } else if (memberResponse.status !== 404) {
        throw new HttpError(502, "DISCORD_GUILD_VERIFY_FAILED", "Discord server-role verification failed.");
      }

      const verifiedAt = guildRole ? new Date().toISOString() : null;
      const { error: updateError } = await admin.from("pupeye_players").update({
        discord_user_id: discordId,
        discord_username: username,
        discord_global_name: typeof profile.global_name === "string" ? profile.global_name : null,
        discord_avatar_hash: typeof profile.avatar === "string" ? profile.avatar : null,
        discord_email: typeof profile.email === "string" ? profile.email : null,
        guild_role: guildRole,
        guild_verified_at: verifiedAt,
        updated_at: new Date().toISOString(),
      }).eq("id", session.player.id);
      if (updateError) throw updateError;

      const { error: linkError } = await admin.from("pupeye_identity_links").insert({
        link_type: "PLAYER_DISCORD",
        player_uuid: session.player.id,
        discord_user_id: discordId,
        confidence: "authoritative",
        evidence_code: "DISCORD_OAUTH_USERS_ME",
      });
      if (linkError && linkError.code !== "23505") throw linkError;

      return json(200, {
        discord: {
          id: discordId,
          username,
          globalName: profile.global_name ?? null,
          avatarHash: profile.avatar ?? null,
          email: profile.email ?? null,
        },
        guildRole,
      });
  } catch (error) {
    return errorResponse(error);
  }
});
