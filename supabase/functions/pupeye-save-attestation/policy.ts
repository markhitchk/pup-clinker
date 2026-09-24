export type SaveAttestationAction =
  | "NEW"
  | "IDEMPOTENT"
  | "REVIEW"
  | "TEMPORARY_GLOBAL_BAN";

export type ValidatedSaveAttestation = {
  saveId: string;
  generation: number;
  payloadHashSha256: string;
};

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SHA256_RE = /^[0-9a-f]{64}$/;

export function validateSaveAttestationRequest(
  input: Record<string, unknown>,
): ValidatedSaveAttestation {
  const saveId = typeof input.saveId === "string" ? input.saveId.trim() : "";
  if (!UUID_RE.test(saveId)) {
    throw new Error("Invalid Puppy Clicker save ID.");
  }

  const generation = input.generation;
  if (
    typeof generation !== "number" ||
    !Number.isSafeInteger(generation) ||
    generation < 1
  ) {
    throw new Error("Invalid Puppy Clicker save generation.");
  }

  const payloadHashSha256 =
    typeof input.payloadHashSha256 === "string"
      ? input.payloadHashSha256.trim().toLowerCase()
      : "";
  if (!SHA256_RE.test(payloadHashSha256)) {
    throw new Error("Invalid Puppy Clicker save hash.");
  }

  return { saveId, generation, payloadHashSha256 };
}

export function classifySaveAttestation(input: {
  incomingHash: string;
  existingHashes: string[];
  distinctSaveConflicts24h: number;
}): SaveAttestationAction {
  const normalizedIncoming = input.incomingHash.toLowerCase();
  const existing = new Set(input.existingHashes.map((value) => value.toLowerCase()));

  if (existing.has(normalizedIncoming)) return "IDEMPOTENT";
  if (existing.size === 0) return "NEW";

  return input.distinctSaveConflicts24h >= 2
    ? "TEMPORARY_GLOBAL_BAN"
    : "REVIEW";
}
