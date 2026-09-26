import { assertEquals } from "jsr:@std/assert@1";
import {
  automaticEnforcementAction,
  selectEffectiveBan,
} from "../_shared/global-enforcement.ts";

Deno.test("permanent ban wins over temporary ban", () => {
  const now = Date.parse("2026-09-24T21:00:00Z");
  const result = selectEffectiveBan([
    {
      public_ban_id: "PGB-TEMP",
      kind: "temporary",
      status: "active",
      reason_code: "OTHER",
      public_reason: "Temporary.",
      issued_at: "2026-09-24T20:00:00Z",
      expires_at: "2026-09-25T21:00:00Z",
      device_wide: false,
    },
    {
      public_ban_id: "PGB-PERM",
      kind: "permanent",
      status: "active",
      reason_code: "BAN_EVASION",
      public_reason: "Permanent.",
      issued_at: "2026-09-23T20:00:00Z",
      expires_at: null,
      device_wide: true,
    },
  ], now);

  assertEquals(result?.public_ban_id, "PGB-PERM");
});

Deno.test("expired temporary ban is ignored", () => {
  const now = Date.parse("2026-09-24T21:00:00Z");
  const result = selectEffectiveBan([{
    public_ban_id: "PGB-OLD",
    kind: "temporary",
    status: "active",
    reason_code: "OTHER",
    public_reason: "Old.",
    issued_at: "2026-09-24T20:00:00Z",
    expires_at: "2026-09-24T20:59:59Z",
    device_wide: false,
  }], now);

  assertEquals(result, null);
});

Deno.test("revoked ban is ignored", () => {
  const result = selectEffectiveBan([{
    public_ban_id: "PGB-REVOKED",
    kind: "permanent",
    status: "revoked",
    reason_code: "OTHER",
    public_reason: "Revoked.",
    issued_at: "2026-09-24T20:00:00Z",
    expires_at: null,
    device_wide: true,
  }], Date.parse("2026-09-24T21:00:00Z"));

  assertEquals(result, null);
});

Deno.test("unverified claimed identity is never auto-escalation evidence", () => {
  assertEquals(
    automaticEnforcementAction({
      evidenceCode: "SIGNATURE_INVALID",
      verifiedIdentity: false,
      distinctSaveConflicts24h: 99,
    }),
    "AUDIT_ONLY",
  );
});

Deno.test("first verified save hash conflict requires review", () => {
  assertEquals(
    automaticEnforcementAction({
      evidenceCode: "SAVE_HASH_CONFLICT",
      verifiedIdentity: true,
      distinctSaveConflicts24h: 1,
    }),
    "REVIEW",
  );
});

Deno.test("second distinct verified save conflict can create temporary ban", () => {
  assertEquals(
    automaticEnforcementAction({
      evidenceCode: "SAVE_HASH_CONFLICT",
      verifiedIdentity: true,
      distinctSaveConflicts24h: 2,
    }),
    "TEMPORARY_GLOBAL_BAN",
  );
});
