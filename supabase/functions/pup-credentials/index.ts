import { createClient } from "npm:@supabase/supabase-js@2";
import {
  HttpError, assertPublishableRequest, authenticateSession, audit,
  createAdminClient, errorResponse, json, requiredString, verifyEnvelope,
} from "../_shared/pupeye.ts";
import { assertGlobalEnforcementAllowed } from "../_shared/global-enforcement.ts";

const NAME_RE = /^[A-Za-z0-9_-]{3,20}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const TERMS_VERSION = 1;
const PRIVACY_VERSION = 1;
const GRANT_MS = 7 * 24 * 60 * 60 * 1000;


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

// The synthetic Auth email is internal infrastructure only. No mail is sent,
// no user email is ever used as login or password recovery, and service-role
// operations are confined to the signed server-side PupEye endpoint.
function syntheticAuthEmail() {
  return "pup-" + crypto.randomUUID() + "@accounts.puppyclicker.invalid";
}
function supportEmail(raw: unknown) {
  const value = typeof raw === "string" ? raw.trim().toLowerCase() : "";
  if (value && (value.length > 254 || !EMAIL_RE.test(value))) {
    throw new HttpError(400, "PUP_CONTACT_INVALID", "Enter a valid contact email or leave it blank.");
  }
  return value || null;
}
function legalAccepted(account: any): boolean {
  return Number(account.terms_version) >= TERMS_VERSION &&
    Number(account.privacy_version) >= PRIVACY_VERSION &&
    Boolean(account.terms_accepted_at) && Boolean(account.privacy_accepted_at);
}
function requireLegalConsent(payload: any) {
  // Two independent affirmative actions are required. Pre-ticked checkboxes,
  // email contact permission, and telemetry preferences cannot substitute.
  if (payload?.termsAccepted !== true || payload?.privacyAccepted !== true) {
    throw new HttpError(400, "PUP_LEGAL_ACCEPTANCE_REQUIRED",
      "Accept both the Terms of Use and Privacy Policy to continue.");
  }
}
async function verifiedAccount(admin: any, session: any) {
  const { data: companion, error: ce } = await admin.from("pup_beta_account_links")
    .select("*").eq("installation_uuid", session.installation.id).maybeSingle();
  if (ce) throw ce;
  if (companion && companion.discord_user_id &&
      new Date(companion.expires_at).getTime() > Date.now()) {
    const { data: linked, error: le } = await admin.from("pup_accounts")
      .select("*").eq("id", companion.account_uuid)
      .eq("discord_user_id", companion.discord_user_id).maybeSingle();
    if (le) throw le;
    if (!linked) throw new HttpError(409, "PUP_ACCOUNT_RECOVERY_REQUIRED", "Discord link needs review.");
    await ownerAllowed(admin, linked, "native-account-manage");
    return linked;
  }
  const { data: own, error: oe } = await admin.from("pup_accounts")
    .select("*").eq("player_uuid", session.player.id)
    .eq("active_installation_uuid", session.installation.id).maybeSingle();
  if (oe) throw oe;
  if (!own) throw new HttpError(401, "PUP_ACCOUNT_LOGIN_REQUIRED", "Sign in with Discord or Pup Account first.");
  await ownerAllowed(admin, own, "native-account-manage");
  return own;
}
async function setLegalAcceptance(admin: any, account: any) {
  const now = new Date().toISOString();
  const { data, error } = await admin.from("pup_accounts").update({
    terms_version: TERMS_VERSION, privacy_version: PRIVACY_VERSION,
    terms_accepted_at: now, privacy_accepted_at: now,
    updated_at: now,
  }).eq("id", account.id).eq("status", "active").select("*").single();
  if (error) throw error;
  return data;
}
async function finishPasswordSignIn(admin: any, session: any, account: any, authId: string) {
  await ownerAllowed(admin, account, "native-sign-in");
  const companion = account.player_uuid !== session.player.id;
  if (companion) {
    const { data: previous, error: pe } = await admin.from("pup_beta_account_links")
      .select("*").eq("installation_uuid", session.installation.id).maybeSingle();
    if (pe) throw pe;
    if (previous && previous.account_uuid !== account.id) {
      throw new HttpError(409, "PUP_ACCOUNT_SWITCH_BLOCKED", "This installation is linked to a different Pup Account.");
    }
    const { error } = await admin.rpc("puppy_upsert_beta_link", {p_payload: {
      installation_uuid: session.installation.id,
      account_uuid: account.id,
      discord_user_id: previous?.discord_user_id ?? null,
      credential_auth_user_id: authId,
      expires_at: new Date(Date.now() + GRANT_MS).toISOString(),
      verified_at: new Date().toISOString(),
    }});
    if (error) throw error;
  }
  await audit(admin, "PUP_NATIVE_AUTH_SUCCESS", "info",
    { accountId: account.id, companion, method: "USERNAME_PASSWORD" },
    session.player.id, session.installation.id);
  return { status: "VERIFIED", legalAccepted: legalAccepted(account), mode: companion ? "companion" : "own-installation" };
}

Deno.serve(async (req: Request) => {
  try {
    assertPublishableRequest(req);
    const envelope = verifyEnvelope(await req.json());
    if (!["native-sign-up", "native-sign-in", "native-accept-legal", "native-contact-update"].includes(envelope.action)) {
      throw new HttpError(400, "PUP_CREDENTIAL_ACTION_INVALID", "Unsupported Pup Account operation.");
    }
    const admin = createAdminClient();
    const session = await authenticateSession(req, admin, envelope);
    if (session.installation.platform !== "android") {
      throw new HttpError(403, "PUP_ANDROID_REQUIRED", "Pup Account credentials require a verified Android installation.");
    }

    if (envelope.action === "native-accept-legal") {
      requireLegalConsent(envelope.payload);
      const account = await verifiedAccount(admin, session);
      const updated = await setLegalAcceptance(admin, account);
      await audit(admin, "PUP_LEGAL_ACCEPTED", "info",
        { accountId: account.id, termsVersion: TERMS_VERSION, privacyVersion: PRIVACY_VERSION },
        session.player.id, session.installation.id);
      return json(200, { status: "ACCEPTED", legalAccepted: legalAccepted(updated) });
    }

    if (envelope.action === "native-contact-update") {
      const account = await verifiedAccount(admin, session);
      const email = supportEmail(envelope.payload.contactEmail);
      const { error } = await admin.from("pup_accounts")
        .update({ contact_email: email, updated_at: new Date().toISOString() })
        .eq("id", account.id).eq("status", "active");
      if (error) throw error;
      return json(200, { status: "UPDATED", contactEmail: email });
    }

    if (envelope.action === "native-sign-up") {
      const username = requiredString(envelope.payload.username, "username").trim();
      const password = requiredString(envelope.payload.password, "password");
      const contactEmail = supportEmail(envelope.payload.contactEmail ?? envelope.payload.email);
      requireLegalConsent(envelope.payload);
      if (!NAME_RE.test(username) || password.length < 8 || password.length > 128) {
        throw new HttpError(400, "PUP_CREDENTIAL_INVALID", "Enter a 3–20 character username and an 8–128 character password.");
      }
      const [{ data: taken, error: te }, { data: own, error: oe },
        { data: grant, error: ge }] = await Promise.all([
          admin.from("pup_accounts").select("*").ilike("username", username).maybeSingle(),
          admin.from("pup_accounts").select("*").eq("player_uuid", session.player.id).maybeSingle(),
          admin.from("pup_beta_account_links").select("*")
            .eq("installation_uuid", session.installation.id).maybeSingle(),
        ]);
      if (te) throw te;
      if (oe) throw oe;
      if (ge) throw ge;
      let linked: any = null;
      if (grant?.discord_user_id && new Date(grant.expires_at).getTime() > Date.now()) {
        const { data, error } = await admin.from("pup_accounts").select("*")
          .eq("id", grant.account_uuid).eq("discord_user_id", grant.discord_user_id).maybeSingle();
        if (error) throw error;
        if (!data) throw new HttpError(409, "PUP_ACCOUNT_RECOVERY_REQUIRED", "Discord link needs review.");
        linked = data;
      } else if (own) {
        linked = own;
      }
      if (taken && taken.id !== linked?.id) {
        throw new HttpError(409, "PUP_USERNAME_TAKEN", "That Pup username is already taken.");
      }
      if (linked && linked.username.toLowerCase() !== username.toLowerCase()) {
        throw new HttpError(409, "PUP_USERNAME_MISMATCH", "Use your existing Pup username to add a password.");
      }
      if (linked) {
        const { data: previous, error } = await admin.from("pup_credential_links")
          .select("auth_user_id").eq("account_uuid", linked.id).maybeSingle();
        if (error) throw error;
        if (previous) throw new HttpError(409, "PUP_CREDENTIAL_ALREADY_LINKED", "This Pup Account already has a password.");
        await ownerAllowed(admin, linked, "native-sign-up");
      }

      // Bypass public email signup/SMTP entirely: Auth stores passwords,
      // with a non-routable identifier controlled by the server.
      const internalEmail = syntheticAuthEmail();
      const { data: authUser, error: createError } = await admin.auth.admin.createUser({
        email: internalEmail, email_confirm: true, password,
      });
      if (createError || !authUser.user) {
        throw new HttpError(503, "PUP_PASSWORD_SETUP_UNAVAILABLE", "Password registration is temporarily unavailable. Discord sign-in still works.");
      }
      const authId = authUser.user.id;
      let account = linked;
      let createdAccount = false;
      try {
        if (!account) {
          const { data, error } = await admin.from("pup_accounts").insert({
            player_uuid: session.player.id,
            active_installation_uuid: session.installation.id,
            discord_user_id: null,
            username, status: "active", stage: "t0",
          }).select("*").single();
          if (error) throw error;
          account = data;
          createdAccount = true;
        }
        const { error: linkError } = await admin.from("pup_credential_links").insert({
          auth_user_id: authId, account_uuid: account.id, email: internalEmail,
        });
        if (linkError) throw linkError;
      } catch (error) {
        if (createdAccount && account?.id) {
          await admin.from("pup_accounts").delete().eq("id", account.id)
            .eq("player_uuid", session.player.id);
        }
        await admin.auth.admin.deleteUser(authId);
        throw error;
      }
      const { data: accepted, error: ae } = await admin.from("pup_accounts").update({
        contact_email: contactEmail,
        terms_version: TERMS_VERSION, privacy_version: PRIVACY_VERSION,
        terms_accepted_at: new Date().toISOString(),
        privacy_accepted_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      }).eq("id", account.id).select("*").single();
      if (ae) throw ae;
      if (createdAccount) {
        await admin.from("pupeye_players").update({ username })
          .eq("id", session.player.id).is("discord_user_id", null);
      }
      const outcome = await finishPasswordSignIn(admin, session, accepted, authId);
      return json(200, outcome);
    }

    const username = requiredString(envelope.payload.login, "login").trim();
    const password = requiredString(envelope.payload.password, "password");
    if (!NAME_RE.test(username) || password.length < 1 || password.length > 128) {
      throw new HttpError(401, "PUP_SIGNIN_INVALID", "Incorrect username or password.");
    }
    const { data: account, error: ae } = await admin.from("pup_accounts")
      .select("*").ilike("username", username).maybeSingle();
    if (ae) throw ae;
    if (!account) throw new HttpError(401, "PUP_SIGNIN_INVALID", "Incorrect username or password.");
    const { data: link, error: le } = await admin.from("pup_credential_links")
      .select("auth_user_id,email").eq("account_uuid", account.id).maybeSingle();
    if (le) throw le;
    if (!link) throw new HttpError(401, "PUP_SIGNIN_INVALID", "Incorrect username or password.");
    const auth = authClient();
    const { data: signed, error: signError } = await auth.auth.signInWithPassword({
      email: link.email, password,
    });
    if (signError?.code === "email_provider_disabled") {
      throw new HttpError(503, "PUP_PASSWORD_LOGIN_DISABLED",
        "Password sign-in is not enabled in Supabase Auth. Email messages or SMTP are not required.");
    }
    if (signError || !signed.user || !signed.session || signed.user.id !== link.auth_user_id) {
      throw new HttpError(401, "PUP_SIGNIN_INVALID", "Incorrect username or password.");
    }
    const outcome = await finishPasswordSignIn(admin, session, account, signed.user.id);
    return json(200, outcome);
  } catch (error) {
    return errorResponse(error);
  }
});
