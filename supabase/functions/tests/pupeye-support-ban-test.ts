import { assertEquals, assertThrows } from "jsr:@std/assert@1";
import {
  validateIssueBanRequest,
  validateSupportAction,
} from "../pupeye-support-ban/policy.ts";

const NOW = Date.parse("2026-09-24T22:40:00Z");

Deno.test("temporary ban requires a future expiry", () => {
  assertThrows(
    () => validateIssueBanRequest({
      action: "issue_temporary",
      reasonCode: "SUPPORT_ACTION",
      publicReason: "Temporary enforcement.",
      expiresAt: "2026-09-24T22:39:59Z",
      actor: "support:test",
      targets: [{ type: "PLAYER", playerUuid: "11111111-1111-4111-8111-111111111111" }],
    }, NOW),
    Error,
    "future expiry",
  );
});

Deno.test("permanent ban rejects expiry", () => {
  assertThrows(
    () => validateIssueBanRequest({
      action: "issue_permanent",
      reasonCode: "SUPPORT_ACTION",
      publicReason: "Permanent enforcement.",
      expiresAt: "2027-01-01T00:00:00Z",
      actor: "support:test",
      targets: [{ type: "PLAYER", playerUuid: "11111111-1111-4111-8111-111111111111" }],
    }, NOW),
    Error,
    "cannot include expiresAt",
  );
});

Deno.test("ban requires at least one target", () => {
  assertThrows(
    () => validateIssueBanRequest({
      action: "issue_permanent",
      reasonCode: "SUPPORT_ACTION",
      publicReason: "Permanent enforcement.",
      actor: "support:test",
      targets: [],
    }, NOW),
    Error,
    "at least one target",
  );
});

Deno.test("valid temporary request returns normalized rpc payload", () => {
  const result = validateIssueBanRequest({
    action: "issue_temporary",
    reasonCode: " save_tampering ",
    publicReason: "  Save integrity violation.  ",
    internalReason: "conflicting authenticated hashes",
    expiresAt: "2026-09-25T22:40:00Z",
    actor: " support:test ",
    supportNote: " fixture ",
    targets: [{
      type: "PLAYER",
      playerUuid: "11111111-1111-4111-8111-111111111111",
    }],
  }, NOW);

  assertEquals(result.kind, "temporary");
  assertEquals(result.reasonCode, "SAVE_TAMPERING");
  assertEquals(result.publicReason, "Save integrity violation.");
  assertEquals(result.actor, "support:test");
  assertEquals(result.targets.length, 1);
});

Deno.test("unsupported moderation action is rejected", () => {
  assertThrows(() => validateSupportAction("delete_everything"), Error, "Unsupported");
});
