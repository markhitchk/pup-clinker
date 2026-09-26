import { createHash, createPublicKey, createVerify } from "node:crypto";
import type { Buffer as NodeBuffer } from "node:buffer";
import { createClient } from "npm:@supabase/supabase-js@2";
import { HttpError } from "./http.ts";
import { assertGlobalEnforcementAllowed } from "./global-enforcement.ts";

export { HttpError, errorResponse, json } from "./http.ts";

export type AdminClient = any;

export function createAdminClient(): AdminClient {
  const url = Deno.env.get("SUPABASE_URL");
  if (!url) throw new Error("Supabase function environment is missing SUPABASE_URL");

  let key: string | undefined;
  const modern = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (modern) {
    try {
      const parsed = JSON.parse(modern) as Record<string, string>;
      key = parsed.default ?? Object.values(parsed).find((value) => value?.startsWith("sb_secret_"));
    } catch {
      // Fall through to the legacy service-role key while projects transition key models.
    }
  }
  key = key ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? undefined;
  if (!key) throw new Error("Supabase function environment has no server-side secret key");

  return createClient(url, key, {
    auth: {
      persistSession: false,
      autoRefreshToken: false,
      detectSessionInUrl: false,
    },
  });
}

export function assertPublishableRequest(req: Request): void {
  const supplied = req.headers.get("apikey")?.trim();
  if (!supplied) {
    throw new HttpError(401, "PUBLISHABLE_KEY_REQUIRED", "Missing Puppy Clicker project key.");
  }

  const accepted = new Set<string>();
  const modern = Deno.env.get("SUPABASE_PUBLISHABLE_KEYS");
  if (modern) {
    try {
      const parsed = JSON.parse(modern) as Record<string, string>;
      Object.values(parsed).forEach((value) => {
        if (value) accepted.add(value);
      });
    } catch {
      // The legacy anon key remains available during the 2026 key transition.
    }
  }
  const legacy = Deno.env.get("SUPABASE_ANON_KEY");
  if (legacy) accepted.add(legacy);

  if (!accepted.has(supplied)) {
    throw new HttpError(401, "PUBLISHABLE_KEY_INVALID", "Invalid Puppy Clicker project key.");
  }
}

export type VerifiedEnvelope = {
  action: string;
  installationId: string;
  deviceKeyId: string;
  publicKey: string;
  generation: number;
  timestampEpochMs: number;
  nonce: string;
  payload: Record<string, unknown>;
};

export type SessionContext = {
  session: any;
  installation: any;
  player: any;
};

const MAX_CLOCK_SKEW_MS = 5 * 60 * 1000;

export function canonicalJson(value: unknown): string {
  if (value === null || value === undefined) return "null";
  if (Array.isArray(value)) return "[" + value.map(canonicalJson).join(",") + "]";
  if (typeof value === "object") {
    const object = value as Record<string, unknown>;
    return "{" + Object.keys(object).sort().map((key) =>
      JSON.stringify(key) + ":" + canonicalJson(object[key])
    ).join(",") + "}";
  }
  return JSON.stringify(value);
}

export function sha256Hex(value: string | Uint8Array): string {
  return createHash("sha256").update(value).digest("hex");
}

function decodeBase64(value: string): Uint8Array {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

function encodeBase64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary)
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replaceAll("=", "");
}

export function randomSessionToken(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return encodeBase64Url(bytes);
}

export function verifyEnvelope(
  body: Record<string, unknown>,
  expectedAction?: string,
): VerifiedEnvelope {
  if (body.schema !== 1) throw new Error("Unsupported Pupeye request schema");
  const action = requiredString(body.action, "action");
  if (expectedAction && action !== expectedAction) throw new Error("Unexpected Pupeye action");

  const installationId = requiredString(body.installationId, "installationId");
  const deviceKeyId = requiredString(body.deviceKeyId, "deviceKeyId").toLowerCase();
  const publicKey = requiredString(body.publicKey, "publicKey");
  const nonce = requiredString(body.nonce, "nonce");
  const signature = requiredString(body.signature, "signature");
  const generation = requiredNumber(body.generation, "generation");
  const timestampEpochMs = requiredNumber(body.timestampEpochMs, "timestampEpochMs");
  const payload = body.payload;
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    throw new Error("Missing Pupeye payload");
  }

  if (generation < 1 || !Number.isSafeInteger(generation)) throw new Error("Invalid save generation");
  if (Math.abs(Date.now() - timestampEpochMs) > MAX_CLOCK_SKEW_MS) {
    throw new Error("Pupeye request timestamp is outside the allowed window");
  }

  const publicKeyBytes = decodeBase64(publicKey);
  if (sha256Hex(publicKeyBytes) !== deviceKeyId) {
    throw new Error("Pupeye device key fingerprint mismatch");
  }

  const material = [
    "PuppyClicker/PupEye/request/v1",
    action,
    installationId,
    deviceKeyId,
    String(generation),
    String(timestampEpochMs),
    nonce,
    canonicalJson(payload),
  ].join("\n");

  const verifier = createVerify("SHA256");
  verifier.update(material);
  verifier.end();
  const key = createPublicKey({
    // Deno's Node compatibility runtime accepts Uint8Array here, while its
    // Node type declarations currently narrow this field to Buffer. Keep the
    // runtime value as Uint8Array so we never depend on the Node Buffer global.
    key: publicKeyBytes as unknown as NodeBuffer,
    format: "der",
    type: "spki",
  });
  if (!verifier.verify(key, decodeBase64(signature))) {
    throw new Error("Pupeye request signature is invalid");
  }

  return {
    action,
    installationId,
    deviceKeyId,
    publicKey,
    generation,
    timestampEpochMs,
    nonce,
    payload: payload as Record<string, unknown>,
  };
}

export async function authenticateSession(
  req: Request,
  admin: AdminClient,
  envelope: VerifiedEnvelope,
): Promise<SessionContext> {
  const token = req.headers.get("X-Pupeye-Session")?.trim();
  if (!token) throw new HttpError(401, "PUPEYE_SESSION_REQUIRED", "Pupeye session is required.");

  const tokenHash = sha256Hex(token);
  const now = new Date().toISOString();
  const { data: session, error: sessionError } = await admin
    .from("pupeye_sessions")
    .select("*")
    .eq("token_hash", tokenHash)
    .is("revoked_at", null)
    .gt("expires_at", now)
    .maybeSingle();

  if (sessionError) throw sessionError;
  if (!session) throw new HttpError(401, "PUPEYE_SESSION_INVALID", "Pupeye session is invalid or expired.");

  const { data: installation, error: installationError } = await admin
    .from("pupeye_installations")
    .select("*")
    .eq("id", session.installation_uuid)
    .maybeSingle();
  if (installationError) throw installationError;
  if (!installation || !installation.active || installation.revoked_at) {
    throw new HttpError(403, "INSTALLATION_REVOKED", "This Puppy Clicker installation is no longer active.");
  }

  if (
    installation.installation_id !== envelope.installationId ||
    installation.device_key_id !== envelope.deviceKeyId
  ) {
    throw new HttpError(403, "INSTALLATION_MISMATCH", "Pupeye installation identity does not match the session.");
  }

  const { data: player, error: playerError } = await admin
    .from("pupeye_players")
    .select("*")
    .eq("id", session.player_uuid)
    .maybeSingle();
  if (playerError) throw playerError;
  if (!player) throw new HttpError(401, "PLAYER_NOT_FOUND", "Pupeye player record was not found.");

  await assertGlobalEnforcementAllowed(admin, {
    session,
    installation,
    player,
    discordUserId: player.discord_user_id ?? null,
    requestAction: envelope.action,
  });

  await admin.from("pupeye_sessions")
    .update({ last_seen_at: now })
    .eq("id", session.id);
  await admin.from("pupeye_installations")
    .update({ last_seen_at: now })
    .eq("id", installation.id);

  return { session, installation, player };
}

export async function issueSession(
  admin: AdminClient,
  playerUuid: string,
  installationUuid: string,
): Promise<{ token: string; expiresAtEpochMs: number }> {
  const token = randomSessionToken();
  const tokenHash = sha256Hex(token);
  const expiresAtEpochMs = Date.now() + 30 * 24 * 60 * 60 * 1000;
  const { error } = await admin.from("pupeye_sessions").insert({
    player_uuid: playerUuid,
    installation_uuid: installationUuid,
    token_hash: tokenHash,
    expires_at: new Date(expiresAtEpochMs).toISOString(),
  });
  if (error) throw error;
  return { token, expiresAtEpochMs };
}

export async function audit(
  admin: AdminClient,
  eventCode: string,
  severity: "info" | "warning" | "review" | "hard",
  detail: Record<string, unknown>,
  playerUuid?: string | null,
  installationUuid?: string | null,
): Promise<void> {
  await admin.from("pupeye_events").insert({
    player_uuid: playerUuid ?? null,
    installation_uuid: installationUuid ?? null,
    event_code: eventCode,
    severity,
    detail,
  });
}

export function requiredString(value: unknown, field: string): string {
  if (typeof value !== "string" || !value.trim()) throw new Error(`Missing ${field}`);
  return value.trim();
}

export function optionalString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

export function requiredNumber(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isFinite(value)) throw new Error(`Missing ${field}`);
  return value;
}
