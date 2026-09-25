import {
  assertEquals,
  assertThrows,
} from "jsr:@std/assert@1";
import {
  classifySaveAttestation,
  validateSaveAttestationRequest,
  assertAttestationGeneration,
} from "../pupeye-save-attestation/policy.ts";
import {
  createSupabaseAttestationStore,
  processSaveAttestation,
  type AttestationStore,
} from "../pupeye-save-attestation/handler.ts";

const HASH_A = "a".repeat(64);
const HASH_B = "b".repeat(64);

Deno.test("automatic temporary ban does not leave legacy blocked flags after expiry", async () => {
  const writes: Array<{ table: string; values: Record<string, unknown> }> = [];
  const admin = {
    async rpc() {
      return { data: { public_ban_id: "PGB-AUTO-1", event_id: null }, error: null };
    },
    from(table: string) {
      return {
        update(values: Record<string, unknown>) {
          writes.push({ table, values });
          const chain = {
            eq() { return chain; },
            is() { return Promise.resolve({ error: null }); },
            then(resolve: (value: { error: null }) => unknown) {
              return Promise.resolve({ error: null }).then(resolve);
            },
          };
          return chain;
        },
      };
    },
  };
  const session = {
    player: { id: "11111111-1111-4111-8111-111111111111" },
    installation: { id: "22222222-2222-4222-8222-222222222222", device_reputation_uuid: null },
  };
  const store = createSupabaseAttestationStore(
    admin,
    session as never,
    { deviceKeyId: "key" } as never,
  );
  await store.createTemporaryGlobalBan({
    saveId: "33333333-3333-4333-8333-333333333333",
    generation: 7,
    payloadHashSha256: HASH_B,
  });

  assertEquals(
    writes.filter(({ values }) =>
      values.status === "blocked" || values.integrity_state === "blocked" ||
      values.reputation_state === "blocked"
    ),
    [],
  );
});

Deno.test("historical replay cannot move latest save head backwards", async () => {
  const predicates: Array<[string, number]> = [];
  const admin = {
    from(table: string) {
      assertEquals(table, "pupeye_save_heads");
      return {
        update(values: Record<string, unknown>) {
          assertEquals(values.generation, 7);
          const chain = {
            eq() { return chain; },
            lte(column: string, value: number) {
              predicates.push([column, value]);
              return Promise.resolve({ error: null });
            },
          };
          return chain;
        },
      };
    },
  };
  const store = createSupabaseAttestationStore(
    admin,
    { player: { id: "player" }, installation: { id: "installation" } } as never,
    { deviceKeyId: "key" } as never,
  );
  await store.updateSaveHead({
    saveId: "33333333-3333-4333-8333-333333333333",
    generation: 7,
    payloadHashSha256: HASH_A,
  });
  assertEquals(predicates, [["generation", 7]]);
});

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


Deno.test("historical attestation generation may trail current signed request generation", () => {
  assertAttestationGeneration(7, 9);
});

Deno.test("attestation generation cannot exceed current signed request generation", () => {
  assertThrows(
    () => assertAttestationGeneration(10, 9),
    Error,
    "cannot exceed",
  );
});
