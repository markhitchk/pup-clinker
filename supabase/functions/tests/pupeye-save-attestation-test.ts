import {
  assertEquals,
  assertThrows,
} from "jsr:@std/assert@1";
import {
  classifySaveAttestation,
  validateSaveAttestationRequest,
} from "../pupeye-save-attestation/policy.ts";
import {
  processSaveAttestation,
  type AttestationStore,
} from "../pupeye-save-attestation/handler.ts";

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


function memoryStore(options: {
  existingHashes?: string[];
  conflicts24h?: number;
}) {
  const calls: string[] = [];
  const store: AttestationStore = {
    async existingHashes() {
      calls.push("existingHashes");
      return options.existingHashes ?? [];
    },
    async recordAttestation() {
      calls.push("recordAttestation");
    },
    async updateSaveHead() {
      calls.push("updateSaveHead");
    },
    async recordConflict() {
      calls.push("recordConflict");
    },
    async distinctConflictCount24h() {
      calls.push("distinctConflictCount24h");
      return options.conflicts24h ?? 0;
    },
    async createTemporaryGlobalBan() {
      calls.push("createTemporaryGlobalBan");
      return {
        id: "PGB-TEST-0001",
        issuedAt: "2026-09-24T23:00:00Z",
        expiresAt: "2026-09-25T23:00:00Z",
        deviceWide: true,
      };
    },
  };
  return { store, calls };
}

Deno.test("handler does not duplicate an identical attestation", async () => {
  const { store, calls } = memoryStore({ existingHashes: [HASH_A] });
  const result = await processSaveAttestation(store, {
    saveId: "11111111-1111-4111-8111-111111111111",
    generation: 7,
    payloadHashSha256: HASH_A,
  });

  assertEquals(result.status, 200);
  assertEquals(result.body.idempotent, true);
  assertEquals(calls, ["existingHashes", "updateSaveHead"]);
});

Deno.test("handler records first conflicting hash as review", async () => {
  const { store, calls } = memoryStore({
    existingHashes: [HASH_A],
    conflicts24h: 1,
  });
  const result = await processSaveAttestation(store, {
    saveId: "11111111-1111-4111-8111-111111111111",
    generation: 8,
    payloadHashSha256: HASH_B,
  });

  assertEquals(result.status, 409);
  assertEquals(result.body.code, "REVIEW_REQUIRED");
  assertEquals(calls, [
    "existingHashes",
    "recordAttestation",
    "recordConflict",
    "distinctConflictCount24h",
  ]);
});

Deno.test("handler creates temporary global ban after second distinct conflict", async () => {
  const { store, calls } = memoryStore({
    existingHashes: [HASH_A],
    conflicts24h: 2,
  });
  const result = await processSaveAttestation(store, {
    saveId: "22222222-2222-4222-8222-222222222222",
    generation: 9,
    payloadHashSha256: HASH_B,
  });

  assertEquals(result.status, 403);
  assertEquals(result.body.code, "GLOBAL_BANNED");
  assertEquals(
    (result.body.ban as Record<string, unknown>).id,
    "PGB-TEST-0001",
  );
  assertEquals(calls, [
    "existingHashes",
    "recordAttestation",
    "recordConflict",
    "distinctConflictCount24h",
    "createTemporaryGlobalBan",
  ]);
});
