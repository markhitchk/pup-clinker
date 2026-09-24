import { createHash, createPublicKey, createVerify, randomBytes } from "node:crypto";

export type AdminClient = any;

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

export function json(status: number, body: Record<string, unknown>): Response {
  return Response.json(body, {
    status,
    headers: { "Cache-Control": "no-store" },
  });
}

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

export function randomSessionToken(): string {
  return randomBytes(32).toString("base64url");
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

  const publicKeyBytes = Buffer.from(publicKey, "base64");
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
    key: publicKeyBytes,
    format: "der",
    type: "spki",
  });
  if (!verifier.verify(key, Buffer.from(signature, "base64"))) {
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
  if (player.status === "blocked") {
    throw new HttpError(403, "PLAYER_BLOCKED", "This player requires Support review.");
  }

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

export class HttpError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message);
  }
}

export function errorResponse(error: unknown): Response {
  if (error instanceof HttpError) {
    return json(error.status, { code: error.code, message: error.message });
  }
  const message = error instanceof Error ? error.message : "Unexpected Pupeye backend error";
  return json(400, { code: "PUPEYE_REQUEST_INVALID", message });
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
