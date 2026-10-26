import { createClient } from "npm:@supabase/supabase-js@2";
import {
  HttpError, assertPublishableRequest, authenticateSession, audit,
  createAdminClient, errorResponse, json, requiredString, verifyEnvelope,
} from "../_shared/pupeye.ts";
import { assertGlobalEnforcementAllowed } from "../_shared/global-enforcement.ts";

const NAME_RE = /^[A-Za-z0-9_-]{3,20}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const GRANT_MS = 7 * 24 * 60 * 60 * 1000;
type LoginUser = { id: string; email?: string; email_confirmed_at?: string | null };

function authClient() {
  const url = Deno.env.get("SUPABASE_URL");
  let key = Deno.env.get("SUPABASE_ANON_KEY");
  if (!key) {
    const keys = JSON.parse(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS") || "{}");
    key = Object.values(keys).find((item) => typeof item === "string") as string | undefined;
  }
  if (!url || !key) throw new Error("Public Supabase Auth configuration is unavailable.");
  // Only a publishable/anon key is used for Auth password operations.
  return createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false } });
}

async function ownerAllowed(admin: any, account: any, action: string) {
  if (account.status !== "active") {
    throw new HttpError(403, "PUP_ACCOUNT_BLOCKED", "The Pup Account needs Support review.");
  }
  const [{ data: player, error: pe }, { data: installation, error: ie }] = await Promise.all([
    admin.from("pupeye_players").select("*").eq("id", account.player_uuid).maybeSingle(),
    admin.from("pupeye_installations").select("*").eq("id", account.active_installation_uuid).maybeSingle(),
  ]);
  if (pe) throw pe;
  if (ie) throw ie;
  if (!player || !installation || !installation.active || installation.revoked_at ||
      installation.player_uuid !== player.id ||
      (account.discord_user_id && player.discord_user_id !== account.discord_user_id)) {
    throw new HttpError(409, "PUP_ACCOUNT_DEVICE_UNAVAILABLE", "Original PupEye account installation is unavailable.");
  }
  await assertGlobalEnforcementAllowed(admin, {
    player, installation, discordUserId: account.discord_user_id ?? player.discord_user_id ?? null,
    requestAction: action,
  });
}

async function finishVerified(admin: any, session: any, user: LoginUser) {
  if (!user.email_confirmed_at) {
    throw new HttpError(403, "PUP_EMAIL_UNVERIFIED", "Verify your email using the confirmation message, then sign in.");
  }
  const { data: link, error: linkError } = await admin.from("pup_credential_links")
    .select("account_uuid").eq("auth_user_id", user.id).maybeSingle();
  if (linkError) throw linkError;
  let account: any = null;
  if (link) {
    const { data, error } = await admin.from("pup_accounts").select("*").eq("id", link.account_uuid).maybeSingle();
    if (error) throw error;
    account = data;
    if (!account) throw new HttpError(409, "PUP_ACCOUNT_RECOVERY_REQUIRED", "Linked Pup Account was not found.");
  } else {
    const { data: pending, error: pendingError } = await admin.from("pup_credential_pending")
      .select("*").eq("auth_user_id", user.id).maybeSingle();
    if (pendingError) throw pendingError;
    if (!pending) throw new HttpError(409, "PUP_ACCOUNT_RECOVERY_REQUIRED", "Pup Account registration needs Support review.");

    const { data: existing, error: existingError } = await admin.from("pup_accounts")
      .select("*").eq("player_uuid", session.player.id).maybeSingle();
    if (existingError) throw existingError;

    // Existing, legitimately verified Discord companion may add a password
    // to that same Pup Account. Never create a second save or reassign player.
    const { data: companion, error: companionError } = await admin.from("pup_beta_account_links")
      .select("*").eq("installation_uuid", session.installation.id).maybeSingle();
    if (companionError) throw companionError;
    if (companion?.discord_user_id) {
      const { data, error } = await admin.from("pup_accounts")
        .select("*").eq("id", companion.account_uuid)
        .eq("discord_user_id", companion.discord_user_id).maybeSingle();
      if (error) throw error;
      if (!data) throw new HttpError(409, "PUP_ACCOUNT_RECOVERY_REQUIRED", "Linked Discord identity has changed.");
      account = data;
    } else if (existing) {
      account = existing;
    } else {
      const { data: collision, error: collisionError } = await admin.from("pup_accounts")
        .select("id").ilike("username", pending.username).maybeSingle();
      if (collisionError) throw collisionError;
      if (collision) throw new HttpError(409, "PUP_USERNAME_TAKEN", "Choose a different Pup username.");
      const { data, error } = await admin.from("pup_accounts").insert({
        player_uuid: session.player.id,
        active_installation_uuid: session.installation.id,
        discord_user_id: null,
        username: pending.username,
        status: "active",
        stage: "t0",
      }).select("*").single();
      if (error) throw error;
      account = data;
      await admin.from("pupeye_players").update({ username: pending.username })
        .eq("id", session.player.id).is("discord_user_id", null);
    }

    if (account.discord_user_id && account.username.toLowerCase() !== pending.username.toLowerCase() &&
        account.player_uuid !== session.player.id) {
      // Adding a native credential to an existing Discord account does not
      // rename its public identity or create another player.
    }
    const { error: connectError } = await admin.from("pup_credential_links").insert({
      auth_user_id: user.id, account_uuid: account.id, email: user.email,
    });
    if (connectError) {
      if (connectError.code === "23505") {
        throw new HttpError(409, "PUP_CREDENTIAL_ALREADY_LINKED", "This account already has Pup credentials.");
      }
      throw connectError;
    }
    await admin.from("pup_credential_pending").delete().eq("auth_user_id", user.id);
  }

  await ownerAllowed(admin, account, "native-sign-in");
  const companion = account.player_uuid !== session.player.id;
  if (companion) {
    const { data: previous, error: previousError } = await admin.from("pup_beta_account_links")
      .select("*").eq("installation_uuid", session.installation.id).maybeSingle();
    if (previousError) throw previousError;
    if (previous && previous.account_uuid !== account.id) {
      throw new HttpError(409, "PUP_ACCOUNT_SWITCH_BLOCKED", "This installation is linked to a different Pup Account.");
    }
    const { error: grantError } = await admin.from("pup_beta_account_links").upsert({
      installation_uuid: session.installation.id,
      account_uuid: account.id,
      discord_user_id: previous?.discord_user_id ?? null,
      credential_auth_user_id: user.id,
      expires_at: new Date(Date.now() + GRANT_MS).toISOString(),
      verified_at: new Date().toISOString(),
    }, { onConflict: "installation_uuid" });
    if (grantError) throw grantError;
  }
  await audit(admin, "PUP_NATIVE_AUTH_SUCCESS", "info", {
    accountId: account.id, companion, method: "SUPABASE_AUTH_PASSWORD",
  }, session.player.id, session.installation.id);
  return { ok: true, mode: companion ? "read-only-companion" : "own-installation" };
}

Deno.serve(async (req: Request) => {
  try {
    assertPublishableRequest(req);
    const envelope = verifyEnvelope(await req.json());
    if (!["native-sign-up", "native-sign-in"].includes(envelope.action)) {
      throw new HttpError(400, "PUP_CREDENTIAL_ACTION_INVALID", "Unsupported Pup Account operation.");
    }
    const admin = createAdminClient();
    const session = await authenticateSession(req, admin, envelope);
    if (session.installation.platform !== "android") {
      throw new HttpError(403, "PUP_ANDROID_REQUIRED", "Native Pup Account sign-in requires an Android installation.");
    }
    const auth = authClient();

    if (envelope.action === "native-sign-up") {
      const username = requiredString(envelope.payload.username, "username");
      const email = requiredString(envelope.payload.email, "email").toLowerCase();
      const password = requiredString(envelope.payload.password, "password");
      if (!NAME_RE.test(username)) {
        throw new HttpError(400, "PUP_USERNAME_INVALID", "Username must be 3–20 letters, numbers, underscores or hyphens.");
      }
      if (!EMAIL_RE.test(email) || email.length > 254 || password.length < 8 || password.length > 128) {
        throw new HttpError(400, "PUP_CREDENTIAL_INVALID", "Enter a valid email and password of 8–128 characters.");
      }
      const [{ data: taken, error: takenError }, { data: reserved, error: reserveError }] = await Promise.all([
        admin.from("pup_accounts").select("id").ilike("username", username).maybeSingle(),
        admin.from("pup_credential_pending").select("auth_user_id").ilike("username", username).maybeSingle(),
      ]);
      if (takenError) throw takenError;
      if (reserveError) throw reserveError;
      if (taken || reserved) throw new HttpError(409, "PUP_USERNAME_TAKEN", "This Pup username is unavailable.");

      const { data, error } = await auth.auth.signUp({ email, password });
      if (error || !data.user || (data.user.identities && data.user.identities.length === 0)) {
        throw new HttpError(400, "PUP_SIGNUP_UNAVAILABLE", "Unable to create this Pup Account. Try signing in or use a different email.");
      }
      const { error: pendingError } = await admin.from("pup_credential_pending").insert({
        auth_user_id: data.user.id, username, email,
      });
      if (pendingError) {
        if (pendingError.code === "23505") throw new HttpError(409, "PUP_USERNAME_TAKEN", "This Pup username is unavailable.");
        throw pendingError;
      }
      if (!data.session) {
        return json(200, { status: "VERIFY_EMAIL", message: "Check your email to verify your Pup Account, then sign in." });
      }
      const result = await finishVerified(admin, session, data.user as LoginUser);
      return json(200, { status: "VERIFIED", ...result });
    }

    const login = requiredString(envelope.payload.login, "login").trim();
    const password = requiredString(envelope.payload.password, "password");
    if (login.length > 254 || password.length === 0 || password.length > 128) {
      throw new HttpError(400, "PUP_SIGNIN_INVALID", "Incorrect username/email or password.");
    }
    let email = login.toLowerCase();
    if (!login.includes("@")) {
      if (!NAME_RE.test(login)) throw new HttpError(400, "PUP_SIGNIN_INVALID", "Incorrect username/email or password.");
      const [{ data: pending, error: pe }, { data: registered, error: re }] = await Promise.all([
        admin.from("pup_credential_pending").select("email").ilike("username", login).maybeSingle(),
        admin.from("pup_accounts").select("id").ilike("username", login).maybeSingle(),
      ]);
      if (pe) throw pe;
      if (re) throw re;
      let stored = null;
      if (registered) {
        const { data, error } = await admin.from("pup_credential_links")
          .select("email").eq("account_uuid", registered.id).maybeSingle();
        if (error) throw error;
        stored = data;
      }
      email = stored?.email ?? pending?.email ?? "";
      if (!email) throw new HttpError(400, "PUP_SIGNIN_INVALID", "Incorrect username/email or password.");
    }
    const { data, error } = await auth.auth.signInWithPassword({ email, password });
    if (error || !data.user || !data.session) {
      throw new HttpError(401, "PUP_SIGNIN_INVALID", "Incorrect username/email or password, or email verification is pending.");
    }
    const result = await finishVerified(admin, session, data.user as LoginUser);
    return json(200, { status: "VERIFIED", ...result });
  } catch (error) {
    return errorResponse(error);
  }
});
