import {
  assertEquals,
  assertThrows,
} from "jsr:@std/assert@1";
import {
  classifySaveAttestation,
  validateSaveAttestationRequest,
} from "../pupeye-save-attestation/policy.ts";

const HASH_A = "a".repeat(64);
const HASH_B = "b".repeat(64);

Deno.test("same save id and same hash is idempotent", () => {
  assertEquals(
    classifySaveAttestation({
      incomingHash: HASH_A,
      existingHashes: [HASH_A],
      distinctSaveConflicts24h: 0,
    }),
    "IDEMPOTENT",
  );
});

Deno.test("same save id with a different signed hash requires review", () => {
  assertEquals(
    classifySaveAttestation({
      incomingHash: HASH_B,
      existingHashes: [HASH_A],
      distinctSaveConflicts24h: 1,
    }),
    "REVIEW",
  );
});

Deno.test("second distinct save-id conflict in 24h creates temporary ban", () => {
  assertEquals(
    classifySaveAttestation({
      incomingHash: HASH_B,
      existingHashes: [HASH_A],
      distinctSaveConflicts24h: 2,
    }),
    "TEMPORARY_GLOBAL_BAN",
  );
});

Deno.test("attestation validation rejects malformed hashes", () => {
  assertThrows(
    () => validateSaveAttestationRequest({
      saveId: "11111111-1111-4111-8111-111111111111",
      generation: 7,
      payloadHashSha256: "not-a-sha256",
    }),
    Error,
    "hash",
  );
});

Deno.test("attestation validation normalizes a valid signed payload", () => {
  assertEquals(
    validateSaveAttestationRequest({
      saveId: "11111111-1111-4111-8111-111111111111",
      generation: 7,
      payloadHashSha256: HASH_A.toUpperCase(),
    }),
    {
      saveId: "11111111-1111-4111-8111-111111111111",
      generation: 7,
      payloadHashSha256: HASH_A,
    },
  );
});
