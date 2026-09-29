import { createHash, createPublicKey, createVerify } from "node:crypto";
import type { Buffer as NodeBuffer } from "node:buffer";
import { optionalString, requiredNumber, requiredString } from "../_shared/pupeye.ts";

export type DeviceTransferClaim = {
  playerId: string;
  friendCode: string;
  username: string;
  sourceInstallationId: string;
  sourceDeviceKeyId: string;
  sourcePublicKey: string;
  saveId: string;
  generation: number;
  createdAtEpochMs: number;
  payloadHashSha256: string;
  migrationClaimSchema: number;
  migrationSignature: string | null;
  supportCode: string;
  deviceModel: string | null;
  platform: string;
  appVersion: string | null;
};

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const PLAYER_ID_RE = /^PC-[0-9A-F]{32}$/;
const FRIEND_CODE_RE = /^PUP-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$/;
const HASH_RE = /^[0-9a-f]{64}$/;
const KEY_ID_RE = /^[0-9a-f]{64}$/;

function decodeBase64(value: string): Uint8Array {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

export function parseDeviceTransferClaim(
  payload: Record<string, unknown>,
): DeviceTransferClaim {
  const playerId = requiredString(payload.playerId, "playerId").toUpperCase();
  const friendCode = requiredString(payload.friendCode, "friendCode").toUpperCase();
  const sourceInstallationId = requiredString(
    payload.sourceInstallationId,
    "sourceInstallationId",
  );
  const sourceDeviceKeyId = requiredString(
    payload.sourceDeviceKeyId,
    "sourceDeviceKeyId",
  ).toLowerCase();
  const sourcePublicKey = requiredString(payload.sourcePublicKey, "sourcePublicKey");
  const saveId = requiredString(payload.saveId, "saveId");
  const generation = requiredNumber(payload.generation, "generation");
  const createdAtEpochMs = requiredNumber(
    payload.createdAtEpochMs,
    "createdAtEpochMs",
  );
  const payloadHashSha256 = requiredString(
    payload.payloadHashSha256,
    "payloadHashSha256",
  ).toLowerCase();
  const supportCode = requiredString(payload.supportCode, "supportCode").slice(0, 32);
  const username = requiredString(payload.username, "username").slice(0, 32);
  const requestedPlatform =
    optionalString(payload.platform)?.toLowerCase() ?? "android";
  const platform = ["android", "windows", "macos", "linux", "web"].includes(
      requestedPlatform,
    )
    ? requestedPlatform
    : "other";
  const migrationClaimSchema =
    typeof payload.migrationClaimSchema === "number"
      ? payload.migrationClaimSchema
      : 0;
  const migrationSignature = optionalString(payload.migrationSignature);

  if (!PLAYER_ID_RE.test(playerId)) throw new Error("Invalid migration Player ID");
  if (!FRIEND_CODE_RE.test(friendCode)) throw new Error("Invalid migration Friend Code");
  if (!UUID_RE.test(sourceInstallationId)) {
    throw new Error("Invalid source installation ID");
  }
  if (!UUID_RE.test(saveId)) throw new Error("Invalid save ID");
  if (!KEY_ID_RE.test(sourceDeviceKeyId)) {
    throw new Error("Invalid source device key ID");
  }
  if (!HASH_RE.test(payloadHashSha256)) {
    throw new Error("Invalid migration payload hash");
  }
  if (!Number.isSafeInteger(generation) || generation < 1) {
    throw new Error("Invalid migration generation");
  }
  if (!Number.isSafeInteger(createdAtEpochMs) || createdAtEpochMs <= 0) {
    throw new Error("Invalid migration creation time");
  }

  return {
    playerId,
    friendCode,
    username,
    sourceInstallationId,
    sourceDeviceKeyId,
    sourcePublicKey,
    saveId,
    generation,
    createdAtEpochMs,
    payloadHashSha256,
    migrationClaimSchema,
    migrationSignature,
    supportCode,
    deviceModel: optionalString(payload.deviceModel)?.slice(0, 64) ?? null,
    platform,
    appVersion: optionalString(payload.appVersion)?.slice(0, 32) ?? null,
  };
}

export function sourcePublicKeyMatchesFingerprint(
  claim: DeviceTransferClaim,
): boolean {
  const publicKeyBytes = decodeBase64(claim.sourcePublicKey);
  return createHash("sha256").update(publicKeyBytes).digest("hex") ===
    claim.sourceDeviceKeyId;
}

export function verifySignedDeviceTransferClaim(
  claim: DeviceTransferClaim,
): boolean {
  if (claim.migrationClaimSchema !== 1 || !claim.migrationSignature) return false;
  if (!sourcePublicKeyMatchesFingerprint(claim)) return false;

  const material = [
    "PuppyClicker/PupEye/migration-claim/v1",
    claim.sourceInstallationId,
    claim.sourceDeviceKeyId,
    String(claim.generation),
    claim.saveId,
    String(claim.createdAtEpochMs),
    claim.payloadHashSha256,
    claim.playerId,
    claim.friendCode,
  ].join("\n");

  const publicKeyBytes = decodeBase64(claim.sourcePublicKey);
  const verifier = createVerify("SHA256");
  verifier.update(material);
  verifier.end();
  const key = createPublicKey({
    key: publicKeyBytes as unknown as NodeBuffer,
    format: "der",
    type: "spki",
  });
  return verifier.verify(key, decodeBase64(claim.migrationSignature));
}
