import { assertPublishableRequest, createAdminClient, HttpError, json } from "../_shared/pupeye.ts";

// Resolve a private username to an email *only on the server*, then let
// GoTrue verify the password and email-confirmation state. Never return email,
// refresh tokens, or account enumeration details to the Android client.
const failure = () => new HttpError(401, "PUP_LOGIN_INVALID", "Incorrect username or password.");

Deno.serve(async (req: Request) => {
  try {
    if (req.method !== "POST") throw new HttpError(405, "METHOD_NOT_ALLOWED", "POST required.");
    assertPublishableRequest(req);
    if (Number(req.headers.get("content-length") || "0") > 4096) {
      throw new HttpError(413, "PUP_LOGIN_REQUEST_TOO_LARGE", "Request too large.");
    }
    const body = await req.json();
    const username = typeof body?.username === "string" ? body.username.trim() : "";
    const password = typeof body?.password === "string" ? body.password : "";
    if (!/^[A-Za-z0-9_.-]{3,20}$/.test(username) ||
        password.length < 8 || password.length > 128) {
      throw failure();
    }

    const admin = createAdminClient();
    const { data: matches, error: lookupError } = await admin
      .from("pup_account_login_handles")
      .select("auth_user_id")
      .ilike("username", username.replace(/[\\%_]/g, "\\$&"))
      .limit(2);
    if (lookupError) throw lookupError;
    if (matches?.length !== 1) throw failure();

    const { data: identity, error: userError } = await admin.auth.admin
      .getUserById(matches[0].auth_user_id);
    if (userError || !identity?.user?.email) throw failure();

    const base = Deno.env.get("SUPABASE_URL");
    if (!base) throw new HttpError(503, "PUP_LOGIN_UNAVAILABLE", "Account provider unavailable.");
    const authResponse = await fetch(base + "/auth/v1/token?grant_type=password", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        apikey: req.headers.get("apikey") || "",
      },
      body: JSON.stringify({ email: identity.user.email, password }),
    });
    if (!authResponse.ok) throw failure();
    const result = await authResponse.json();
    if (typeof result?.access_token !== "string" || !result.access_token) throw failure();

    // The native module passes this short-lived JWT to pup-account/auth-local
    // alongside its separate signed PupEye session. Javascript never sees it.
    return json(200, { accessToken: result.access_token });
  } catch (error) {
    // No username, email, password, token, or Auth provider error ever returned.
    if (error instanceof HttpError && error.code === "PUP_LOGIN_INVALID") {
      return json(401, { code: error.code, message: error.message });
    }
    console.error("[Pup Account username login]", error instanceof HttpError ? error.code : "UNAVAILABLE");
    return json(503, { code: "PUP_LOGIN_UNAVAILABLE", message: "Pup Account login is temporarily unavailable." });
  }
});
