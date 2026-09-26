import {
  assertEquals,
  assertFalse,
  assertStringIncludes,
} from "jsr:@std/assert@1";
import {
  buildBanEmbed,
  sendBanEventBestEffort,
} from "../_shared/discord-ban-notify.ts";

Deno.test("global ban embed contains operational fields but no sensitive fields", () => {
  const payload = buildBanEmbed({
    eventCode: "GLOBAL_BAN_CREATED",
    banId: "PGB-7F2A-91C4",
    kind: "permanent",
    reasonCode: "BAN_EVASION",
    username: "max_puppy4",
    playerId: "PC-EXAMPLE",
    deviceWide: true,
    discordLinked: true,
    platform: "android",
    createdAt: "2026-09-24T20:52:00Z",
  });

  const text = JSON.stringify(payload);
  assertStringIncludes(text, "PGB-7F2A-91C4");
  assertStringIncludes(text, "BAN_EVASION");
  assertStringIncludes(text, "PupEye");
  assertFalse(text.includes("sessionToken"));
  assertFalse(text.includes("token_hash"));
  assertFalse(text.includes("ipAddress"));
  assertFalse(text.includes("discord_email"));
  assertFalse(text.includes("public_key_b64"));
});

Deno.test("temporary ban embed uses temporary title and expiry field", () => {
  const payload = buildBanEmbed({
    eventCode: "TEMPORARY_GLOBAL_BAN_CREATED",
    banId: "PGB-TEMP-0001",
    kind: "temporary",
    reasonCode: "SAVE_TAMPERING",
    username: "tester",
    playerId: "PC-TEST",
    deviceWide: true,
    discordLinked: false,
    platform: "android",
    createdAt: "2026-09-24T20:52:00Z",
    expiresAt: "2026-09-25T20:52:00Z",
  });
  const text = JSON.stringify(payload);
  assertStringIncludes(text, "Temporary");
  assertStringIncludes(text, "2026-09-25T20:52:00Z");
});

Deno.test("webhook failure is recorded and does not throw", async () => {
  const updates: Array<Record<string, unknown>> = [];
  const admin = {
    from(table: string) {
      if (table === "pupeye_ban_events") {
        return {
          select() {
            return {
              eq() {
                return {
                  async single() {
                    return {
                      data: {
                        id: 7,
                        ban_uuid: null,
                        event_code: "ADMIN_OVERRIDE",
                        actor: "support:test",
                        detail: {},
                        created_at: "2026-09-24T20:52:00Z",
                        discord_attempt_count: 0,
                      },
                      error: null,
                    };
                  },
                };
              },
            };
          },
          update(value: Record<string, unknown>) {
            updates.push(value);
            return {
              eq() {
                return Promise.resolve({ error: null });
              },
            };
          },
        };
      }
      throw new Error("unexpected table " + table);
    },
  };

  await sendBanEventBestEffort(
    admin,
    7,
    async () => new Response("no", { status: 503 }),
    "https://discord.invalid/webhook",
  );

  assertEquals(updates.at(-1)?.discord_delivery_status, "failed");
});
