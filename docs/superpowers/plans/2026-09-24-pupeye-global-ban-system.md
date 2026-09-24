# PupEye Global Ban System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a server-authoritative PupEye global enforcement system that supports review, temporary global bans, permanent global bans, device/account/Discord/install targets, save-attestation tamper evidence, Discord webhook embeds, and a full Android lockout UI without deleting player data.

**Architecture:** Supabase remains the source of truth. New ban/audit/device-reputation/save-attestation tables and transactional RPCs sit behind RLS; a shared Edge Function resolver is called by every client-facing PupEye endpoint. Android caches only the last server-authoritative enforcement result in encrypted device storage and routes to a dedicated review/ban screen when required. The repository currently contains no implemented web or desktop client, so this plan defines the shared backend/client contract for those platforms without inventing a framework.

**Tech Stack:** PostgreSQL + RLS + pgTAP, Supabase Edge Functions (Deno/TypeScript), `@supabase/supabase-js@2`, Android Kotlin/Jetpack Compose, Android Keystore-backed `PupEyeAuthority`, JUnit, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-24-pupeye-global-ban-system-design.md`

## Global Constraints

- Supabase is authoritative for global enforcement.
- Temporary and permanent bans both propagate across every strongly linked matching player/account and recognized device target.
- A known active ban must not be cleared by Android local clock changes, offline mode, app preferences, or session-file deletion.
- Bans must never delete saves, puppies, balances, achievements, inventory, or historical moderation records.
- Do not collect IMEI, hardware serial, Android ID, SIM serial, phone number, advertising ID, Wi-Fi MAC, or Bluetooth MAC for banning.
- IP/network metadata is evidence only and never an automatic ban target.
- The Discord webhook credential is server-side only; never commit the raw webhook URL to GitHub or ship it in Android/web/desktop code.
- All new `public` Supabase tables have RLS enabled and direct `anon`/`authenticated` table access revoked.
- Secret/service-role credentials never appear in public clients.
- Ban expiry uses server time.
- `REVIEW_REQUIRED` is a near-full lockout, distinct from a global ban.
- The current `Website/` and `Desktop/` directories contain no app implementation; do not choose a web/desktop framework in this work.
- Invalid unsigned/invalid-signature requests must never be able to frame another installation into an automatic global ban.
- Preserve the existing permanent Android signing identity and never decrease `versionCode`.

## Review Focus

1. **Identity collision / weak evidence:** a username, device model, IP, or other weak signal must not attach an unrelated player to a ban. Task 5 adds an integration test proving weak evidence alone does not propagate enforcement.
2. **Forged claimed identity:** an invalid signature that merely claims a victim installation ID must not auto-ban that victim. Task 2 adds a test that auto-escalation requires verified/authenticated evidence.
3. **Concurrent expiry:** multiple clients checking an expiring temporary ban must produce one `BAN_EXPIRED` transition/event, not duplicate moderation actions or webhook embeds. Tasks 1 and 4 test idempotent expiry.
4. **Save attestation replay:** repeated identical `save_id + hash` submissions must be idempotent, while a genuinely conflicting hash for the same authenticated save ID must create review evidence. Task 6 tests both paths and the two-conflict escalation threshold.
5. **Android cache bypass:** a known active ban must stay locked offline and after a local clock jump, but a clean never-banned player must not be fabricated into a ban just because Supabase is temporarily unavailable. Task 8 tests both states.

---

### Task 1: Add the global-enforcement database schema and transactional RPCs

**Files:**
- Create: `supabase/tests/pupeye_global_bans_test.sql`
- Create: `supabase/migrations/20260924_000002_pupeye_global_bans.sql`

**Interfaces:**
- Consumes: existing `public.pupeye_players`, `public.pupeye_installations`, `public.pupeye_sessions`, `public.pupeye_save_heads`.
- Produces: `pupeye_global_bans`, `pupeye_ban_targets`, `pupeye_ban_events`, `pupeye_device_reputation`, `pupeye_identity_links`, `pupeye_ban_hits`, `pupeye_save_attestations`; RPCs `pupeye_resolve_global_ban`, `pupeye_expire_due_bans`, `pupeye_create_global_ban`, `pupeye_update_global_ban`, `pupeye_revoke_global_ban`, `pupeye_attach_ban_target`.

- [ ] **Step 1: Write the failing pgTAP schema/constraint tests**

Create `supabase/tests/pupeye_global_bans_test.sql` with tests that require the new tables, RLS, temporary/permanent expiry constraints, target-shape constraints, and RPC permissions.

```sql
begin;
create extension if not exists pgtap with schema extensions;

select plan(12);

select has_table('public', 'pupeye_global_bans');
select has_table('public', 'pupeye_ban_targets');
select has_table('public', 'pupeye_ban_events');
select has_table('public', 'pupeye_device_reputation');
select has_table('public', 'pupeye_identity_links');
select has_table('public', 'pupeye_ban_hits');
select has_table('public', 'pupeye_save_attestations');

select ok(
  (select relrowsecurity from pg_class where oid = 'public.pupeye_global_bans'::regclass),
  'global bans has RLS enabled'
);

select throws_ok(
  $$insert into public.pupeye_global_bans
      (public_ban_id, kind, status, reason_code, public_reason, issued_at, issued_by)
    values ('PGB-TEST-TEMP', 'temporary', 'active', 'OTHER', 'test', now(), 'test')$$,
  '23514',
  null,
  'temporary ban without expires_at is rejected'
);

select throws_ok(
  $$insert into public.pupeye_global_bans
      (public_ban_id, kind, status, reason_code, public_reason, issued_at, expires_at, issued_by)
    values ('PGB-TEST-PERM', 'permanent', 'active', 'OTHER', 'test', now(), now() + interval '1 day', 'test')$$,
  '23514',
  null,
  'permanent ban with expires_at is rejected'
);

select has_function('public', 'pupeye_resolve_global_ban');
select has_function('public', 'pupeye_create_global_ban');

select * from finish();
rollback;
```

- [ ] **Step 2: Run the DB test and verify RED**

From the repository root:

```bash
supabase start
supabase test db supabase/tests/pupeye_global_bans_test.sql
```

Expected: FAIL because the global-ban tables/functions do not exist.

- [ ] **Step 3: Create the migration with the exact domain constraints**

Create the migration through the Supabase CLI first, then normalize it to this repository's sequential naming convention:

```bash
supabase migration new pupeye_global_bans
MIGRATION="$(find supabase/migrations -maxdepth 1 -type f -name '*_pupeye_global_bans.sql' | sort | tail -1)"
test -n "$MIGRATION"
if [ "$MIGRATION" != "supabase/migrations/20260924_000002_pupeye_global_bans.sql" ]; then
  mv "$MIGRATION" supabase/migrations/20260924_000002_pupeye_global_bans.sql
fi
```

Define the tables with this shape:

```sql
create table public.pupeye_device_reputation (
  id uuid primary key default gen_random_uuid(),
  platform text not null check (platform in ('android','windows','macos','linux','web','other')),
  reputation_state text not null default 'clean'
    check (reputation_state in ('clean','review','blocked')),
  created_from_installation_uuid uuid references public.pupeye_installations(id) on delete set null,
  metadata jsonb not null default '{}'::jsonb,
  first_seen_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now()
);

alter table public.pupeye_installations
  add column device_reputation_uuid uuid
  references public.pupeye_device_reputation(id) on delete set null;

create table public.pupeye_global_bans (
  id uuid primary key default gen_random_uuid(),
  public_ban_id text not null unique,
  kind text not null check (kind in ('temporary','permanent')),
  status text not null default 'active' check (status in ('active','expired','revoked')),
  reason_code text not null,
  public_reason text not null,
  internal_reason text,
  issued_at timestamptz not null default now(),
  expires_at timestamptz,
  issued_by text not null,
  support_note text,
  revoked_at timestamptz,
  revoked_by text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (
    (kind = 'temporary' and expires_at is not null) or
    (kind = 'permanent' and expires_at is null)
  )
);

create table public.pupeye_ban_targets (
  id uuid primary key default gen_random_uuid(),
  ban_uuid uuid not null references public.pupeye_global_bans(id) on delete cascade,
  target_type text not null check (
    target_type in ('PLAYER','DISCORD','DEVICE_REPUTATION','INSTALLATION','WEB_INSTALLATION')
  ),
  player_uuid uuid references public.pupeye_players(id) on delete cascade,
  discord_user_id text,
  device_reputation_uuid uuid references public.pupeye_device_reputation(id) on delete cascade,
  installation_uuid uuid references public.pupeye_installations(id) on delete cascade,
  web_installation_id text,
  attached_at timestamptz not null default now(),
  attached_by text not null,
  source_event_id bigint,
  check (
    (target_type = 'PLAYER' and player_uuid is not null and
      num_nonnulls(discord_user_id, device_reputation_uuid, installation_uuid, web_installation_id) = 0) or
    (target_type = 'DISCORD' and discord_user_id is not null and
      num_nonnulls(player_uuid, device_reputation_uuid, installation_uuid, web_installation_id) = 0) or
    (target_type = 'DEVICE_REPUTATION' and device_reputation_uuid is not null and
      num_nonnulls(player_uuid, discord_user_id, installation_uuid, web_installation_id) = 0) or
    (target_type = 'INSTALLATION' and installation_uuid is not null and
      num_nonnulls(player_uuid, discord_user_id, device_reputation_uuid, web_installation_id) = 0) or
    (target_type = 'WEB_INSTALLATION' and web_installation_id is not null and
      num_nonnulls(player_uuid, discord_user_id, device_reputation_uuid, installation_uuid) = 0)
  )
);

create unique index pupeye_ban_target_player_uq
  on public.pupeye_ban_targets(ban_uuid, player_uuid)
  where target_type = 'PLAYER';
create unique index pupeye_ban_target_discord_uq
  on public.pupeye_ban_targets(ban_uuid, discord_user_id)
  where target_type = 'DISCORD';
create unique index pupeye_ban_target_device_uq
  on public.pupeye_ban_targets(ban_uuid, device_reputation_uuid)
  where target_type = 'DEVICE_REPUTATION';
create unique index pupeye_ban_target_installation_uq
  on public.pupeye_ban_targets(ban_uuid, installation_uuid)
  where target_type = 'INSTALLATION';

create table public.pupeye_ban_events (
  id bigint generated always as identity primary key,
  ban_uuid uuid references public.pupeye_global_bans(id) on delete cascade,
  event_code text not null,
  actor text not null,
  detail jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  discord_delivery_status text not null default 'pending'
    check (discord_delivery_status in ('pending','sent','failed','not_required')),
  discord_attempt_count integer not null default 0 check (discord_attempt_count >= 0),
  discord_last_attempt_at timestamptz,
  discord_last_error text
);

create unique index pupeye_single_expiry_event_uq
  on public.pupeye_ban_events(ban_uuid, event_code)
  where event_code = 'BAN_EXPIRED';

create table public.pupeye_identity_links (
  id bigint generated always as identity primary key,
  link_type text not null check (
    link_type in ('PLAYER_DISCORD','PLAYER_INSTALLATION','INSTALLATION_DEVICE','PLAYER_WEB_INSTALLATION')
  ),
  player_uuid uuid references public.pupeye_players(id) on delete cascade,
  discord_user_id text,
  installation_uuid uuid references public.pupeye_installations(id) on delete cascade,
  device_reputation_uuid uuid references public.pupeye_device_reputation(id) on delete cascade,
  web_installation_id text,
  confidence text not null check (confidence in ('authoritative','strong','weak')),
  evidence_code text not null,
  created_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now()
);

create table public.pupeye_ban_hits (
  id bigint generated always as identity primary key,
  ban_uuid uuid not null references public.pupeye_global_bans(id) on delete cascade,
  player_uuid uuid references public.pupeye_players(id) on delete set null,
  installation_uuid uuid references public.pupeye_installations(id) on delete set null,
  device_reputation_uuid uuid references public.pupeye_device_reputation(id) on delete set null,
  discord_user_id text,
  client_platform text,
  request_action text not null,
  created_at timestamptz not null default now()
);

create table public.pupeye_save_attestations (
  id uuid primary key default gen_random_uuid(),
  save_id uuid not null,
  player_uuid uuid not null references public.pupeye_players(id) on delete cascade,
  installation_uuid uuid not null references public.pupeye_installations(id) on delete cascade,
  generation bigint not null check (generation >= 1),
  payload_hash_sha256 text not null check (payload_hash_sha256 ~ '^[0-9a-f]{64}$'),
  device_key_id text not null,
  observed_at timestamptz not null default now(),
  unique (save_id, installation_uuid, payload_hash_sha256)
);

alter table public.pupeye_save_heads
  add column last_save_id uuid,
  add column last_payload_hash_sha256 text
    check (last_payload_hash_sha256 is null or last_payload_hash_sha256 ~ '^[0-9a-f]{64}$');
```

Enable RLS and revoke direct client access on every new table:

```sql
alter table public.pupeye_global_bans enable row level security;
alter table public.pupeye_ban_targets enable row level security;
alter table public.pupeye_ban_events enable row level security;
alter table public.pupeye_device_reputation enable row level security;
alter table public.pupeye_identity_links enable row level security;
alter table public.pupeye_ban_hits enable row level security;
alter table public.pupeye_save_attestations enable row level security;

revoke all on table public.pupeye_global_bans from anon, authenticated;
revoke all on table public.pupeye_ban_targets from anon, authenticated;
revoke all on table public.pupeye_ban_events from anon, authenticated;
revoke all on table public.pupeye_device_reputation from anon, authenticated;
revoke all on table public.pupeye_identity_links from anon, authenticated;
revoke all on table public.pupeye_ban_hits from anon, authenticated;
revoke all on table public.pupeye_save_attestations from anon, authenticated;
```

Backfill one device-reputation row per existing installation without claiming cross-reinstall identity:

```sql
insert into public.pupeye_device_reputation (
  platform, created_from_installation_uuid, first_seen_at, last_seen_at
)
select platform, id, registered_at, last_seen_at
from public.pupeye_installations
where device_reputation_uuid is null;

update public.pupeye_installations i
set device_reputation_uuid = d.id
from public.pupeye_device_reputation d
where d.created_from_installation_uuid = i.id
  and i.device_reputation_uuid is null;
```

Add the service-role-only RPCs with `security invoker`, not `security definer`. The critical resolver signature is:

```sql
create or replace function public.pupeye_resolve_global_ban(
  p_player_uuid uuid,
  p_discord_user_id text,
  p_installation_uuid uuid,
  p_device_reputation_uuid uuid,
  p_web_installation_id text
)
returns table (
  ban_uuid uuid,
  public_ban_id text,
  kind text,
  reason_code text,
  public_reason text,
  issued_at timestamptz,
  expires_at timestamptz,
  device_wide boolean
)
language sql
security invoker
as $$
  select
    b.id,
    b.public_ban_id,
    b.kind,
    b.reason_code,
    b.public_reason,
    b.issued_at,
    b.expires_at,
    bool_or(t.target_type = 'DEVICE_REPUTATION') as device_wide
  from public.pupeye_global_bans b
  join public.pupeye_ban_targets t on t.ban_uuid = b.id
  where b.status = 'active'
    and (b.kind = 'permanent' or b.expires_at > now())
    and (
      (p_player_uuid is not null and t.player_uuid = p_player_uuid) or
      (p_discord_user_id is not null and t.discord_user_id = p_discord_user_id) or
      (p_installation_uuid is not null and t.installation_uuid = p_installation_uuid) or
      (p_device_reputation_uuid is not null and t.device_reputation_uuid = p_device_reputation_uuid) or
      (p_web_installation_id is not null and t.web_installation_id = p_web_installation_id)
    )
  group by b.id
  order by (b.kind = 'permanent') desc, b.issued_at desc
  limit 1;
$$;
```

Restrict all moderation RPC execution:

```sql
revoke execute on function public.pupeye_resolve_global_ban(uuid,text,uuid,uuid,text)
  from public, anon, authenticated;
grant execute on function public.pupeye_resolve_global_ban(uuid,text,uuid,uuid,text)
  to service_role;
```

Use the same permission pattern for create/update/revoke/attach/expiry RPCs.

`pupeye_expire_due_bans()` must transition only `status='active'` temporary rows whose expiry is due, use `update ... returning`, append exactly one `BAN_EXPIRED` event for each transitioned ban, and return those event IDs. This is what makes concurrent expiry checks idempotent.

- [ ] **Step 4: Run DB tests and verify GREEN**

```bash
supabase db reset
supabase test db supabase/tests/pupeye_global_bans_test.sql
```

Expected: PASS.

- [ ] **Step 5: Commit the schema task**

```bash
git add supabase/migrations/20260924_000002_pupeye_global_bans.sql supabase/tests/pupeye_global_bans_test.sql
git commit -m "feat: add PupEye global enforcement schema"
```

---

### Task 2: Add the shared enforcement domain and resolver

**Files:**
- Create: `supabase/functions/_shared/global-enforcement.ts`
- Create: `supabase/functions/tests/global-enforcement-test.ts`
- Modify: `supabase/functions/_shared/pupeye.ts`

**Interfaces:**
- Consumes: `AdminClient`, existing player/installation/session rows, Task 1 RPCs.
- Produces:
  - `type PublicGlobalBan`
  - `type GlobalEnforcementResult = { state: "ALLOWED" } | { state: "REVIEW_REQUIRED"; message: string } | { state: "GLOBAL_BANNED"; ban: PublicGlobalBan }`
  - `resolveGlobalEnforcement(admin, context): Promise<GlobalEnforcementResult>`
  - `assertGlobalEnforcementAllowed(admin, context): Promise<void>`
  - `recordGlobalBanHit(admin, context, ban): Promise<void>`
  - enhanced `HttpError` details payload support.

- [ ] **Step 1: Write failing pure-domain tests**

```ts
import { assertEquals } from "jsr:@std/assert@1";
import { selectEffectiveBan } from "../_shared/global-enforcement.ts";

Deno.test("permanent ban wins over temporary ban", () => {
  const now = Date.parse("2026-09-24T21:00:00Z");
  const result = selectEffectiveBan([
    {
      public_ban_id: "PGB-TEMP",
      kind: "temporary",
      status: "active",
      expires_at: "2026-09-25T21:00:00Z",
      issued_at: "2026-09-24T20:00:00Z",
    },
    {
      public_ban_id: "PGB-PERM",
      kind: "permanent",
      status: "active",
      expires_at: null,
      issued_at: "2026-09-23T20:00:00Z",
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
    expires_at: "2026-09-24T20:59:59Z",
    issued_at: "2026-09-24T20:00:00Z",
  }], now);

  assertEquals(result, null);
});

Deno.test("unverified claimed identity is never auto-escalation evidence", () => {
  // The policy helper must require verifiedIdentity=true before returning an automatic action.
  const action = automaticEnforcementAction({
    evidenceCode: "SIGNATURE_INVALID",
    verifiedIdentity: false,
    distinctSaveConflicts24h: 0,
  });
  assertEquals(action, "AUDIT_ONLY");
});
```

- [ ] **Step 2: Run the test and verify RED**

```bash
deno test --allow-env supabase/functions/tests/global-enforcement-test.ts
```

Expected: FAIL because the shared module does not exist.

- [ ] **Step 3: Implement the shared types and resolver**

Use a public-safe ban shape only:

```ts
type GlobalBanRow = {
  public_ban_id: string;
  kind: "temporary" | "permanent";
  status: "active" | "expired" | "revoked";
  reason_code?: string;
  public_reason?: string;
  issued_at: string;
  expires_at: string | null;
  device_wide?: boolean;
};

export type PublicGlobalBan = {
  id: string;
  kind: "temporary" | "permanent";
  reasonCode: string;
  publicReason: string;
  issuedAt: string;
  expiresAt: string | null;
  scope: "GLOBAL";
  deviceWide: boolean;
};

export type GlobalEnforcementResult =
  | { state: "ALLOWED" }
  | { state: "REVIEW_REQUIRED"; message: string }
  | { state: "GLOBAL_BANNED"; ban: PublicGlobalBan };

export type EnforcementIdentityContext = {
  player?: any | null;
  installation?: any | null;
  discordUserId?: string | null;
  webInstallationId?: string | null;
  requestAction: string;
};
```

Resolver order is **global ban first, then review**:

```ts
export async function resolveGlobalEnforcement(
  admin: AdminClient,
  context: EnforcementIdentityContext,
): Promise<GlobalEnforcementResult> {
  const expiredEvents = await expireDueBans(admin);
  await notifyExpiredEventsBestEffort(admin, expiredEvents);

  const { data, error } = await admin.rpc("pupeye_resolve_global_ban", {
    p_player_uuid: context.player?.id ?? null,
    p_discord_user_id: context.discordUserId ?? context.player?.discord_user_id ?? null,
    p_installation_uuid: context.installation?.id ?? null,
    p_device_reputation_uuid: context.installation?.device_reputation_uuid ?? null,
    p_web_installation_id: context.webInstallationId ?? null,
  });
  if (error) throw error;

  const row = Array.isArray(data) ? data[0] : data;
  if (row) {
    return {
      state: "GLOBAL_BANNED",
      ban: {
        id: row.public_ban_id,
        kind: row.kind,
        reasonCode: row.reason_code,
        publicReason: row.public_reason,
        issuedAt: row.issued_at,
        expiresAt: row.expires_at,
        scope: "GLOBAL",
        deviceWide: Boolean(row.device_wide),
      },
    };
  }

  if (context.player?.status === "review" || context.installation?.integrity_state === "review") {
    return {
      state: "REVIEW_REQUIRED",
      message: "PupEye requires Support review before Puppy Clicker can continue.",
    };
  }

  return { state: "ALLOWED" };
}
```

`assertGlobalEnforcementAllowed` must record a ban hit and return an `HttpError` containing structured details:

```ts
if (result.state === "GLOBAL_BANNED") {
  await recordGlobalBanHit(admin, context, result.ban);
  throw new HttpError(
    403,
    "GLOBAL_BANNED",
    result.ban.publicReason,
    { ban: result.ban },
  );
}
if (result.state === "REVIEW_REQUIRED") {
  throw new HttpError(
    409,
    "REVIEW_REQUIRED",
    result.message,
    { review: { scope: "GLOBAL" } },
  );
}
```

Modify `HttpError` in `_shared/pupeye.ts`:

```ts
export class HttpError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public details: Record<string, unknown> = {},
  ) {
    super(message);
  }
}

export function errorResponse(error: unknown): Response {
  if (error instanceof HttpError) {
    console.error("[PupEye]", error.code, error.message);
    return json(error.status, {
      code: error.code,
      message: error.message,
      ...error.details,
    });
  }
  const message = error instanceof Error ? error.message : "Unexpected Pupeye backend error";
  console.error("[PupEye] PUPEYE_REQUEST_INVALID", message);
  return json(400, { code: "PUPEYE_REQUEST_INVALID", message });
}
```

Automatic policy is intentionally conservative:

```ts
export function automaticEnforcementAction(input: {
  evidenceCode: string;
  verifiedIdentity: boolean;
  distinctSaveConflicts24h: number;
}): "AUDIT_ONLY" | "REVIEW" | "TEMPORARY_GLOBAL_BAN" {
  if (!input.verifiedIdentity) return "AUDIT_ONLY";
  if (input.evidenceCode === "SAVE_HASH_CONFLICT") {
    return input.distinctSaveConflicts24h >= 2 ? "TEMPORARY_GLOBAL_BAN" : "REVIEW";
  }
  return "REVIEW";
}
```

No automatic path in this task creates a permanent ban.

- [ ] **Step 4: Run the shared-domain test and verify GREEN**

```bash
deno test --allow-env supabase/functions/tests/global-enforcement-test.ts
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add supabase/functions/_shared/global-enforcement.ts supabase/functions/_shared/pupeye.ts supabase/functions/tests/global-enforcement-test.ts
git commit -m "feat: add shared PupEye enforcement resolver"
```

---

### Task 3: Add trusted Support/admin ban actions

**Files:**
- Create: `supabase/functions/pupeye-support-ban/index.ts`
- Create: `supabase/functions/tests/pupeye-support-ban-test.ts`
- Modify: `supabase/config.toml`

**Interfaces:**
- Consumes: Task 1 moderation RPCs and Task 2 public ban shape.
- Produces server-secret-only actions:
  - `issue_temporary`
  - `issue_permanent`
  - `extend_temporary`
  - `convert_to_temporary`
  - `convert_to_permanent`
  - `attach_target`
  - `revoke_ban`
  - `clear_review`
  - `record_appeal`
  - `lookup`

- [ ] **Step 1: Write failing action validation tests**

The tests must prove temporary bans require a future expiry, permanent bans reject expiry, a ban requires at least one target, and public callers cannot use the function.

```ts
Deno.test("temporary issue requires future expiry", async () => {
  const response = await invokeSupportBan({
    action: "issue_temporary",
    reasonCode: "SUPPORT_ACTION",
    publicReason: "Temporary enforcement.",
    expiresAt: "2026-09-24T20:00:00Z",
    target: { type: "PLAYER", playerUuid: FIXTURE_PLAYER_UUID },
  });
  assertEquals(response.status, 400);
});

Deno.test("permanent issue rejects expiresAt", async () => {
  const response = await invokeSupportBan({
    action: "issue_permanent",
    reasonCode: "SUPPORT_ACTION",
    publicReason: "Permanent enforcement.",
    expiresAt: "2027-01-01T00:00:00Z",
    target: { type: "PLAYER", playerUuid: FIXTURE_PLAYER_UUID },
  });
  assertEquals(response.status, 400);
});
```

- [ ] **Step 2: Run and verify RED**

```bash
deno test --allow-all supabase/functions/tests/pupeye-support-ban-test.ts
```

Expected: FAIL because the function does not exist.

- [ ] **Step 3: Implement the trusted handler**

Use `@supabase/server` secret authentication, matching the existing support-only function pattern:

```ts
import { withSupabase } from "npm:@supabase/server";
import { HttpError, errorResponse, json, requiredString } from "../_shared/pupeye.ts";

export default {
  fetch: withSupabase({ auth: "secret" }, async (req, ctx) => {
    try {
      const body = await req.json();
      const action = requiredString(body.action, "action");
      const admin = ctx.supabaseAdmin;

      switch (action) {
        case "issue_temporary":
          return await issueBan(admin, body, "temporary");
        case "issue_permanent":
          return await issueBan(admin, body, "permanent");
        case "revoke_ban":
          return await revokeBan(admin, body);
        case "attach_target":
          return await attachTarget(admin, body);
        case "clear_review":
          return await clearReview(admin, body);
        case "record_appeal":
          return await recordAppeal(admin, body);
        case "lookup":
          return await lookupBan(admin, body);
        default:
          throw new HttpError(400, "BAN_ACTION_INVALID", "Unsupported PupEye ban action.");
      }
    } catch (error) {
      return errorResponse(error);
    }
  }),
};
```

The implementation must call the transactional RPCs rather than reproducing SQL mutation logic in TypeScript. After a ban becomes active, revoke matching existing sessions best-effort; correctness still comes from the resolver on every subsequent request.

Use actor input only from the trusted Support caller and bound it to 80 characters. Never accept a client-provided `issued_by` from public app requests.

- [ ] **Step 4: Add Supabase function config**

```toml
[functions.pupeye-support-ban]
verify_jwt = false
```

The function remains secret-authenticated inside the handler.

- [ ] **Step 5: Run tests and verify GREEN**

```bash
deno test --allow-all supabase/functions/tests/pupeye-support-ban-test.ts
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add supabase/functions/pupeye-support-ban supabase/functions/tests/pupeye-support-ban-test.ts supabase/config.toml
git commit -m "feat: add PupEye support ban actions"
```

---

### Task 4: Add Discord webhook embed notifications

**Files:**
- Create: `supabase/functions/_shared/discord-ban-notify.ts`
- Create: `supabase/functions/tests/discord-ban-notify-test.ts`
- Modify: `supabase/functions/pupeye-support-ban/index.ts`
- Modify: `supabase/functions/_shared/global-enforcement.ts`

**Interfaces:**
- Consumes: `PUPPY_GLOBAL_BAN_DISCORD_WEBHOOK` from `Deno.env`, `pupeye_ban_events`.
- Produces:
  - `buildBanEmbed(event): DiscordWebhookPayload`
  - `sendBanEventBestEffort(admin, eventId): Promise<void>`
  - embed delivery status updates.

- [ ] **Step 1: Write failing embed-format and redaction tests**

```ts
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
  assertFalse(text.includes("sessionToken"));
  assertFalse(text.includes("token_hash"));
  assertFalse(text.includes("ipAddress"));
  assertFalse(text.includes("discord_email"));
  assertFalse(text.includes("public_key_b64"));
});
```

- [ ] **Step 2: Run and verify RED**

```bash
deno test --allow-env supabase/functions/tests/discord-ban-notify-test.ts
```

Expected: FAIL because the helper does not exist.

- [ ] **Step 3: Implement rich embed mapping**

Map event type to a title/description and Discord embed color integer. Do not include the raw webhook URL anywhere in code.

```ts
const EVENT_TITLES: Record<string, string> = {
  GLOBAL_BAN_CREATED: "🔴 PupEye Global Ban",
  TEMPORARY_GLOBAL_BAN_CREATED: "🟠 PupEye Temporary Global Ban",
  REVIEW_REQUIRED: "🟡 PupEye Review Required",
  BAN_EVASION_DETECTED: "🚫 PupEye Ban Evasion Detected",
  GLOBAL_BAN_EXTENDED: "🔄 PupEye Ban Extended",
  GLOBAL_BAN_REVOKED: "✅ PupEye Ban Revoked",
  BAN_EXPIRED: "⌛ PupEye Temporary Ban Expired",
  NEW_ACCOUNT_BLOCKED_ON_BANNED_DEVICE: "⚠️ New Account Blocked on Banned Device",
  APPEAL_SUBMITTED: "📨 PupEye Appeal Submitted",
  APPEAL_APPROVED: "✅ PupEye Appeal Approved",
  APPEAL_DENIED: "❌ PupEye Appeal Denied",
  ADMIN_OVERRIDE: "🛡️ PupEye Admin Override",
};
```

Webhook send semantics:

```ts
export async function sendBanEventBestEffort(
  admin: AdminClient,
  eventId: number,
  fetcher: typeof fetch = fetch,
): Promise<void> {
  const url = Deno.env.get("PUPPY_GLOBAL_BAN_DISCORD_WEBHOOK")?.trim();
  if (!url) {
    await markDelivery(admin, eventId, "not_required", null);
    return;
  }

  try {
    const response = await fetcher(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(await payloadForEvent(admin, eventId)),
      signal: AbortSignal.timeout(3_000),
    });
    if (!response.ok) throw new Error(`Discord webhook returned ${response.status}`);
    await markDelivery(admin, eventId, "sent", null);
  } catch (error) {
    await markDelivery(
      admin,
      eventId,
      "failed",
      error instanceof Error ? error.message.slice(0, 300) : "Discord delivery failed",
    );
  }
}
```

A failed webhook must never throw back into the already-committed moderation action.

- [ ] **Step 4: Call the sender after committed moderation/expiry events**

`pupeye-support-ban` sends the event after the RPC returns. `expireDueBans` sends only the event IDs returned by the atomic expiry RPC, which prevents duplicate expiry embeds under concurrent checks.

- [ ] **Step 5: Run tests and verify GREEN**

```bash
deno test --allow-env supabase/functions/tests/discord-ban-notify-test.ts
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add supabase/functions/_shared/discord-ban-notify.ts supabase/functions/tests/discord-ban-notify-test.ts supabase/functions/pupeye-support-ban/index.ts supabase/functions/_shared/global-enforcement.ts
git commit -m "feat: add PupEye Discord moderation embeds"
```

---

### Task 5: Add enforcement-status and enforce bans in every existing client flow

**Files:**
- Create: `supabase/functions/pupeye-enforcement-status/index.ts`
- Create: `supabase/functions/tests/pupeye-enforcement-integration-test.ts`
- Modify: `supabase/functions/_shared/pupeye.ts`
- Modify: `supabase/functions/pupeye-register/index.ts`
- Modify: `supabase/functions/pupeye-auth-discord/index.ts`
- Modify: `supabase/functions/pupeye-sync/index.ts`
- Modify: `supabase/functions/pupeye-transaction/index.ts`
- Modify: `supabase/config.toml`

**Interfaces:**
- Consumes: Task 2 resolver.
- Produces public signed action `enforcement-status`; all existing session-authenticated flows call global enforcement through `authenticateSession`.

- [ ] **Step 1: Write failing signed-request integration tests**

Create a Deno test helper that generates a real P-256 key and the same canonical request signature as Android.

```ts
async function signedEnvelope(
  action: string,
  payload: Record<string, unknown>,
  identity: TestIdentity,
): Promise<Record<string, unknown>> {
  const timestampEpochMs = Date.now();
  const nonce = crypto.randomUUID();
  const material = [
    "PuppyClicker/PupEye/request/v1",
    action,
    identity.installationId,
    identity.deviceKeyId,
    String(identity.generation),
    String(timestampEpochMs),
    nonce,
    canonicalJson(payload),
  ].join("\n");

  const signature = await crypto.subtle.sign(
    { name: "ECDSA", hash: "SHA-256" },
    identity.privateKey,
    new TextEncoder().encode(material),
  );

  return {
    schema: 1,
    action,
    installationId: identity.installationId,
    deviceKeyId: identity.deviceKeyId,
    publicKey: identity.publicKeyB64,
    generation: identity.generation,
    timestampEpochMs,
    nonce,
    payload,
    signature: bytesToBase64(new Uint8Array(signature)),
  };
}
```

Required integration cases:

```ts
Deno.test("active player ban blocks enforcement-status", async () => { /* expect 403 GLOBAL_BANNED */ });
Deno.test("device ban blocks a second player strongly linked to the same device reputation", async () => { /* expect same Ban ID */ });
Deno.test("weak metadata match alone does not propagate a ban", async () => { /* same model/IP-like fixture, expect allowed */ });
Deno.test("review returns REVIEW_REQUIRED instead of GLOBAL_BANNED", async () => { /* expect 409 */ });
Deno.test("concurrent expiry checks transition one BAN_EXPIRED event", async () => { /* Promise.all checks, count event = 1 */ });
Deno.test("invalid signature claiming a banned or clean victim installation does not create a new ban", async () => { /* count unchanged */ });
```

- [ ] **Step 2: Run and verify RED**

```bash
deno test --allow-all supabase/functions/tests/pupeye-enforcement-integration-test.ts
```

Expected: FAIL because the status function and resolver integration are incomplete.

- [ ] **Step 3: Enforce globally inside `authenticateSession`**

After player + installation are loaded and identity matches, but before last-seen timestamps are updated:

```ts
await assertGlobalEnforcementAllowed(admin, {
  player,
  installation,
  discordUserId: player.discord_user_id,
  requestAction: envelope.action,
});
```

This automatically covers `pupeye-sync`, `pupeye-transaction`, and the pre-Discord portion of `pupeye-auth-discord`.

- [ ] **Step 4: Add the public signed status endpoint**

```ts
Deno.serve(async (req: Request) => {
  try {
    assertPublishableRequest(req);
    const envelope = verifyEnvelope(await req.json(), "enforcement-status");
    const admin = createAdminClient();

    const { data: installation, error } = await admin
      .from("pupeye_installations")
      .select("*")
      .eq("installation_id", envelope.installationId)
      .eq("device_key_id", envelope.deviceKeyId)
      .maybeSingle();
    if (error) throw error;

    if (!installation) {
      return json(200, { ok: true, state: "ALLOWED", registered: false });
    }

    const { data: player, error: playerError } = await admin
      .from("pupeye_players")
      .select("*")
      .eq("id", installation.player_uuid)
      .single();
    if (playerError) throw playerError;

    await assertGlobalEnforcementAllowed(admin, {
      player,
      installation,
      discordUserId: player.discord_user_id,
      requestAction: envelope.action,
    });

    return json(200, {
      ok: true,
      state: "ALLOWED",
      serverTimeEpochMs: Date.now(),
    });
  } catch (error) {
    return errorResponse(error);
  }
});
```

- [ ] **Step 5: Make registration check device/install bans before identity mutation**

Before creating or updating a player, query existing installation identity by `installation_id` and `device_key_id`. If either maps to a known device reputation, resolve enforcement using that installation/device first.

This ensures a banned device cannot evade the ban by presenting a new Player ID on an already recognized installation.

For a truly new device key with no strong server link, create a new device reputation. Do **not** infer sameness from model string, username, or IP.

On successful new installation:

```ts
const { data: reputation, error: reputationError } = await admin
  .from("pupeye_device_reputation")
  .insert({
    platform,
    reputation_state: "clean",
    metadata: { appVersion },
  })
  .select("*")
  .single();

if (reputationError) throw reputationError;
```

Insert the installation with `device_reputation_uuid: reputation.id` and append `PLAYER_INSTALLATION` + `INSTALLATION_DEVICE` strong/authoritative identity-link rows.

- [ ] **Step 6: Re-check Discord-target bans before linking OAuth identity**

After `/users/@me` returns a verified Discord user ID but before writing it to `pupeye_players`:

```ts
await assertGlobalEnforcementAllowed(admin, {
  player: session.player,
  installation: session.installation,
  discordUserId: discord.id,
  requestAction: "auth-discord",
});
```

On successful link, upsert a `PLAYER_DISCORD` authoritative identity link.

- [ ] **Step 7: Add function config and run integration tests**

```toml
[functions.pupeye-enforcement-status]
verify_jwt = false
```

Run:

```bash
deno test --allow-all supabase/functions/tests/pupeye-enforcement-integration-test.ts
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add supabase/functions/_shared/pupeye.ts supabase/functions/pupeye-register supabase/functions/pupeye-auth-discord supabase/functions/pupeye-sync supabase/functions/pupeye-transaction supabase/functions/pupeye-enforcement-status supabase/functions/tests/pupeye-enforcement-integration-test.ts supabase/config.toml
git commit -m "feat: enforce PupEye global bans across backend"
```

---

### Task 6: Add authenticated save-attestation conflict detection and conservative automatic escalation

**Files:**
- Create: `supabase/functions/pupeye-save-attestation/index.ts`
- Create: `supabase/functions/tests/pupeye-save-attestation-test.ts`
- Modify: `supabase/config.toml`
- Modify: `supabase/functions/_shared/global-enforcement.ts`

**Interfaces:**
- Consumes: signed action `save-attestation`, active PupEye session, Task 1 attestation table.
- Produces idempotent save attestation recording; `SAVE_HASH_CONFLICT` evidence; first distinct conflict => review, second distinct save-ID conflict in 24h => 24-hour temporary global ban.

- [ ] **Step 1: Write failing attestation tests**

```ts
Deno.test("same save id and same hash is idempotent", async () => {
  const first = await attest(SAVE_ID_A, HASH_A);
  const second = await attest(SAVE_ID_A, HASH_A);
  assertEquals(first.status, 200);
  assertEquals(second.status, 200);
  assertEquals(await attestationCount(SAVE_ID_A), 1);
});

Deno.test("same save id with a different signed hash creates review evidence", async () => {
  await attest(SAVE_ID_A, HASH_A);
  const conflict = await attest(SAVE_ID_A, HASH_B);
  assertEquals(conflict.status, 409);
  assertEquals(await playerStatus(), "review");
});

Deno.test("two distinct save-id conflicts in 24h create a 24-hour temporary global ban", async () => {
  await createConflict(SAVE_ID_A, HASH_A, HASH_B);
  await clearReviewFixtureOnly();
  await createConflict(SAVE_ID_B, HASH_C, HASH_D);

  const ban = await activeBan();
  assertEquals(ban.kind, "temporary");
  assertEquals(ban.reason_code, "SAVE_TAMPERING");
});
```

- [ ] **Step 2: Run and verify RED**

```bash
deno test --allow-all supabase/functions/tests/pupeye-save-attestation-test.ts
```

Expected: FAIL because the function does not exist.

- [ ] **Step 3: Implement strict input validation**

```ts
const saveId = requiredString(envelope.payload.saveId, "saveId");
const payloadHash = requiredString(
  envelope.payload.payloadHashSha256,
  "payloadHashSha256",
).toLowerCase();
const generation = requiredNumber(envelope.payload.generation, "generation");

if (!/^[0-9a-f]{64}$/.test(payloadHash)) {
  throw new HttpError(400, "SAVE_HASH_INVALID", "Invalid Puppy Clicker save hash.");
}
if (!Number.isSafeInteger(generation) || generation < 1) {
  throw new HttpError(400, "SAVE_GENERATION_INVALID", "Invalid Puppy Clicker save generation.");
}
```

Authenticate the session first; the signed envelope is the evidence source.

For an existing row with the exact same `save_id + installation_uuid + hash`, return 200 without creating another event.

If another hash already exists for that `save_id + installation_uuid`:

1. insert the new attestation once;
2. append one `SAVE_HASH_CONFLICT` hard/review event containing only save ID/generation/hash prefixes;
3. set player status and installation integrity to `review`;
4. count **distinct save IDs** with a conflict event for this installation in the last 24 hours;
5. if count >= 2, create a 24-hour temporary global ban targeted to PLAYER + INSTALLATION + DEVICE_REPUTATION;
6. emit Discord moderation embed after the ban transaction commits.

Do not auto-permanently ban.

- [ ] **Step 4: Add config and verify GREEN**

```toml
[functions.pupeye-save-attestation]
verify_jwt = false
```

Run:

```bash
deno test --allow-all supabase/functions/tests/pupeye-save-attestation-test.ts
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add supabase/functions/pupeye-save-attestation supabase/functions/tests/pupeye-save-attestation-test.ts supabase/functions/_shared/global-enforcement.ts supabase/config.toml
git commit -m "feat: add PupEye save attestation enforcement"
```

---

### Task 7: Make Android export/import publish the authenticated .pupsave payload hash

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PupEyeAuthority.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/GameSaveTransfer.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/SupabasePupEyeClient.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PupEyeSaveAttestationTest.kt`

**Interfaces:**
- Produces:
  - `PupEyeAuthority.transferPayloadHash(payloadWithoutProof: JSONObject): String`
  - `SupabasePupEyeClient.queueSaveAttestation(context, saveId, generation, payloadHashSha256)`.

- [ ] **Step 1: Write the failing canonical-hash test**

```kotlin
@Test
fun canonicalTransferHashIgnoresObjectInsertionOrder() {
    val a = JSONObject()
        .put("format", "puppy-clicker-transfer-payload")
        .put("version", 3)
        .put("stores", JSONObject().put("b", 2).put("a", 1))

    val b = JSONObject()
        .put("stores", JSONObject().put("a", 1).put("b", 2))
        .put("version", 3)
        .put("format", "puppy-clicker-transfer-payload")

    assertEquals(
        PupEyeAuthority.transferPayloadHash(a),
        PupEyeAuthority.transferPayloadHash(b)
    )
}
```

- [ ] **Step 2: Run and verify RED**

From `App/`:

```bash
./gradlew --no-daemon :app:testDebugUnitTest   --tests com.harleytg.puppyclicker.PupEyeSaveAttestationTest   --stacktrace
```

Expected: compilation failure because `transferPayloadHash` does not exist.

- [ ] **Step 3: Implement the hash over the canonical unsigned payload**

```kotlin
internal fun transferPayloadHash(payloadWithoutProof: JSONObject): String {
    require(!payloadWithoutProof.has("pupeye")) {
        "Pupeye transfer payload hash must exclude the proof wrapper"
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(canonicalJson(payloadWithoutProof).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
```

- [ ] **Step 4: Queue attestation after a successful export write**

Build the unsigned payload, calculate the hash, create the proof, write the encrypted file, and only after the write succeeds enqueue the server attestation:

```kotlin
val unsignedPayload = JSONObject().apply {
    put("format", PAYLOAD_FORMAT)
    put("version", PAYLOAD_VERSION)
    put("package", context.packageName)
    put("exportedAtEpochMs", System.currentTimeMillis())
    put("identity", PuppyPlayerIdentity.metadata(context))
    put("stores", stores)
}
val payloadHash = PupEyeAuthority.transferPayloadHash(unsignedPayload)
val proof = PupEyeAuthority.createTransferProof(context, unsignedPayload)
val payload = JSONObject(unsignedPayload.toString()).apply {
    put("pupeye", proof)
}

// write encrypted bytes first...

SupabasePupEyeClient.queueSaveAttestation(
    context = context,
    saveId = proof.getString("saveId"),
    generation = proof.getLong("generation"),
    payloadHashSha256 = payloadHash
)
```

On successful import, recompute the unsigned payload hash after signature verification and queue the same attestation so an offline export can eventually be recorded.

- [ ] **Step 5: Add Android transport**

```kotlin
fun queueSaveAttestation(
    context: Context,
    saveId: String,
    generation: Long,
    payloadHashSha256: String
) {
    if (!isConfigured()) return
    require(payloadHashSha256.matches(Regex("[0-9a-f]{64}")))

    val app = context.applicationContext
    scope.launch {
        runCatching {
            val session = ensureRegistered(app) ?: return@runCatching
            val payload = JSONObject().apply {
                put("saveId", saveId)
                put("generation", generation)
                put("payloadHashSha256", payloadHashSha256)
            }
            val response = invoke(
                functionName = "pupeye-save-attestation",
                envelope = PupEyeAuthority.signedEnvelope(app, "save-attestation", payload),
                sessionToken = session.token
            )
            if (response.status !in 200..299) {
                handleAuthoritativeFailure(app, response)
            }
        }
    }
}
```

- [ ] **Step 6: Run test and verify GREEN**

```bash
./gradlew --no-daemon :app:testDebugUnitTest   --tests com.harleytg.puppyclicker.PupEyeSaveAttestationTest   --stacktrace
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/harleytg/puppyclicker/PupEyeAuthority.kt   app/src/main/java/com/harleytg/puppyclicker/GameSaveTransfer.kt   app/src/main/java/com/harleytg/puppyclicker/SupabasePupEyeClient.kt   app/src/test/java/com/harleytg/puppyclicker/PupEyeSaveAttestationTest.kt
git commit -m "feat: attest portable save hashes with PupEye"
```

---

### Task 8: Add the Android encrypted global-enforcement cache and client state model

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PupEyeEnforcement.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/SupabasePupEyeClient.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PupEyeEnforcementTest.kt`

**Interfaces:**
- Produces:
  - `enum class PupEyeEnforcementMode { ALLOWED, REVIEW_REQUIRED, GLOBAL_BANNED }`
  - `data class PupEyeGlobalBanInfo(...)`
  - `data class PupEyeEnforcementSnapshot(...)`
  - encrypted cache file `pupeye/enforcement_v1.pup`
  - `SupabasePupEyeClient.enforcementState(context): StateFlow<PupEyeEnforcementSnapshot>`
  - `refreshEnforcementAsync(context)`.

- [ ] **Step 1: Write failing state/clock/offline tests**

```kotlin
@Test
fun knownTemporaryBanRemainsLockedAfterLocalClockPassesExpiry() {
    val cached = PupEyeEnforcementSnapshot(
        mode = PupEyeEnforcementMode.GLOBAL_BANNED,
        ban = PupEyeGlobalBanInfo(
            id = "PGB-TEST",
            kind = "temporary",
            reasonCode = "SAVE_TAMPERING",
            publicReason = "Temporary enforcement.",
            issuedAtEpochMs = 1_000L,
            expiresAtEpochMs = 2_000L,
            deviceWide = true
        ),
        lastServerVerifiedAtMs = 1_500L
    )

    assertTrue(cached.blocksApp(nowMs = 9_999_999L))
}

@Test
fun cleanOfflineStateDoesNotInventABan() {
    val cached = PupEyeEnforcementSnapshot.allowed(lastServerVerifiedAtMs = 1_500L)
    assertFalse(cached.blocksApp(nowMs = 9_999_999L))
}
```

Also test JSON parsing of `GLOBAL_BANNED`, `REVIEW_REQUIRED`, and `ALLOWED` responses.

- [ ] **Step 2: Run and verify RED**

```bash
./gradlew --no-daemon :app:testDebugUnitTest   --tests com.harleytg.puppyclicker.PupEyeEnforcementTest   --stacktrace
```

Expected: compilation failure because enforcement models do not exist.

- [ ] **Step 3: Implement pure enforcement state rules**

```kotlin
internal enum class PupEyeEnforcementMode {
    ALLOWED,
    REVIEW_REQUIRED,
    GLOBAL_BANNED
}

internal data class PupEyeGlobalBanInfo(
    val id: String,
    val kind: String,
    val reasonCode: String,
    val publicReason: String,
    val issuedAtEpochMs: Long,
    val expiresAtEpochMs: Long?,
    val deviceWide: Boolean
)

internal data class PupEyeEnforcementSnapshot(
    val mode: PupEyeEnforcementMode,
    val ban: PupEyeGlobalBanInfo? = null,
    val reviewMessage: String? = null,
    val lastServerVerifiedAtMs: Long = 0L
) {
    fun blocksApp(nowMs: Long): Boolean =
        mode == PupEyeEnforcementMode.GLOBAL_BANNED ||
            mode == PupEyeEnforcementMode.REVIEW_REQUIRED

    companion object {
        fun allowed(lastServerVerifiedAtMs: Long = 0L) =
            PupEyeEnforcementSnapshot(
                mode = PupEyeEnforcementMode.ALLOWED,
                lastServerVerifiedAtMs = lastServerVerifiedAtMs
            )
    }
}
```

Do not use `expiresAtEpochMs` to locally unlock.

- [ ] **Step 4: Persist the snapshot in encrypted device storage**

Use `PuppySaveCrypto.encryptDevice/decryptDevice` and `context.noBackupFilesDir`, matching the backend-session storage pattern.

The cache may be cleared only by a successful server-authoritative `ALLOWED` response or explicit app erase-all-data flow; a local clock comparison must never clear it.

- [ ] **Step 5: Parse authoritative failures into the enforcement cache**

Extend `handleAuthoritativeFailure`:

```kotlin
when (code) {
    "GLOBAL_BANNED" -> {
        val banJson = response.body.getJSONObject("ban")
        saveEnforcement(
            context,
            PupEyeEnforcementSnapshot(
                mode = PupEyeEnforcementMode.GLOBAL_BANNED,
                ban = PupEyeGlobalBanInfo.fromJson(banJson),
                lastServerVerifiedAtMs = System.currentTimeMillis()
            )
        )
        clearSessionFileOnly(context)
    }
    "REVIEW_REQUIRED",
    "SAVE_ROLLBACK",
    "DUPLICATE_TRANSACTION",
    "TRANSACTION_CONFLICT" -> {
        saveEnforcement(
            context,
            PupEyeEnforcementSnapshot(
                mode = PupEyeEnforcementMode.REVIEW_REQUIRED,
                reviewMessage = response.message("PupEye requires Support review."),
                lastServerVerifiedAtMs = System.currentTimeMillis()
            )
        )
    }
}
```

Keep `MIGRATION_REQUIRED` separate from global review/ban cache because device migration is a different user flow.

- [ ] **Step 6: Add signed enforcement refresh**

```kotlin
fun refreshEnforcementAsync(context: Context) {
    if (!isConfigured()) return
    val app = context.applicationContext
    scope.launch {
        val response = runCatching {
            invoke(
                functionName = "pupeye-enforcement-status",
                envelope = PupEyeAuthority.signedEnvelope(
                    app,
                    "enforcement-status",
                    JSONObject()
                ),
                sessionToken = null
            )
        }.getOrElse { return@launch }

        if (response.status in 200..299) {
            saveEnforcement(
                app,
                PupEyeEnforcementSnapshot.allowed(System.currentTimeMillis())
            )
        } else {
            handleAuthoritativeFailure(app, response)
        }
    }
}
```

`serverAllowsProtectedGameplay` must return false for review/global-ban snapshots in addition to existing migration state.

- [ ] **Step 7: Run tests and verify GREEN**

```bash
./gradlew --no-daemon :app:testDebugUnitTest   --tests com.harleytg.puppyclicker.PupEyeEnforcementTest   --stacktrace
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/harleytg/puppyclicker/PupEyeEnforcement.kt   app/src/main/java/com/harleytg/puppyclicker/SupabasePupEyeClient.kt   app/src/test/java/com/harleytg/puppyclicker/PupEyeEnforcementTest.kt
git commit -m "feat: cache PupEye global enforcement on Android"
```

---

### Task 9: Add the app-level review/global-ban UI gate

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PupEyeEnforcementUi.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerApplication.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PupEyeEnforcementUiContractTest.kt`

**Interfaces:**
- Consumes: Task 8 `StateFlow<PupEyeEnforcementSnapshot>`, `PuppyLinks.DISCORD_INVITE`, `PuppyLegalLinks`, `PupEyeAuthority.supportInstallationCode(context)`.
- Produces: `PupEyeEnforcementGate(snapshot)`.

- [ ] **Step 1: Write failing UI contract tests**

Use the repository's existing source-contract test style to pin the safety-critical route.

```kotlin
@Test
fun rootRoutesEnforcementBeforeOnboardingOrGameplay() {
    val source = source("src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt")
    val gate = source.indexOf("PupEyeEnforcementGate")
    val onboarding = source.indexOf("PuppyOnboardingFlow")
    assertTrue(gate >= 0)
    assertTrue(gate < onboarding)
}

@Test
fun banUiContainsOnlySupportAndLegalRecoveryActions() {
    val source = source("src/main/java/com/harleytg/puppyclicker/PupEyeEnforcementUi.kt")
    assertTrue(source.contains("Copy Ban ID"))
    assertTrue(source.contains("Contact Support"))
    assertTrue(source.contains("PuppyLegalLinks"))
    assertFalse(source.contains("V6Play("))
    assertFalse(source.contains("SaveTransferSettings("))
}
```

- [ ] **Step 2: Run and verify RED**

```bash
./gradlew --no-daemon :app:testDebugUnitTest   --tests com.harleytg.puppyclicker.PupEyeEnforcementUiContractTest   --stacktrace
```

Expected: FAIL because the gate/UI file does not exist.

- [ ] **Step 3: Route the root before onboarding/gameplay**

At the top of `PuppyClickerV6App`:

```kotlin
val context = LocalContext.current
val enforcement by SupabasePupEyeClient
    .enforcementState(context)
    .collectAsStateWithLifecycle()

if (enforcement.blocksApp(System.currentTimeMillis())) {
    PupEyeEnforcementGate(enforcement)
    return
}

if (!uiPreferences.setupComplete) {
    PuppyOnboardingFlow(vm)
    return
}
```

This ensures a known active ban/review cannot reach gameplay, Settings mutations, save transfer, or account-switching UI.

- [ ] **Step 4: Implement the restricted screen**

The screen must show:

```kotlin
Text(
    if (snapshot.mode == PupEyeEnforcementMode.GLOBAL_BANNED) {
        "PupEye Global Ban"
    } else {
        "PupEye Review Required"
    },
    style = MaterialTheme.typography.headlineMedium,
    fontWeight = FontWeight.Black
)
```

For a ban, display Ban ID, temporary/permanent kind, safe public reason, issued time, expiry or `Never`, `Puppy Clicker Global`, and Support Installation Code.

Actions:

```kotlin
Button(onClick = { copyBanId(context, ban.id) }) { Text("Copy Ban ID") }
OutlinedButton(onClick = { openUri(context, PuppyLinks.DISCORD_INVITE) }) {
    Text("Contact Support")
}
PuppyLegalLinks(
    modifier = Modifier.fillMaxWidth(),
    acknowledgementText = "Review Puppy Clicker's Terms and Privacy information."
)
```

Do not expose internal evidence or add account switching.

- [ ] **Step 5: Refresh enforcement on startup and foreground return**

`PuppyClickerApplication.onCreate()` already calls `SupabasePupEyeClient.initialize(this)`; make initialization load the encrypted cache before network work and then call `refreshEnforcementAsync`.

In `onActivityStarted`, when `returningFromBackground`:

```kotlin
if (returningFromBackground) {
    startupSafely("foreground PupEye enforcement refresh") {
        SupabasePupEyeClient.refreshEnforcementAsync(this)
    }
    startupSafely("foreground AFK reward preparation") {
        prepareAfkReward(System.currentTimeMillis())
    }
}
```

A known cached ban remains locked if this refresh fails.

- [ ] **Step 6: Run tests and verify GREEN**

```bash
./gradlew --no-daemon :app:testDebugUnitTest   --tests com.harleytg.puppyclicker.PupEyeEnforcementUiContractTest   --stacktrace
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/harleytg/puppyclicker/PupEyeEnforcementUi.kt   app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt   app/src/main/java/com/harleytg/puppyclicker/PuppyClickerApplication.kt   app/src/test/java/com/harleytg/puppyclicker/PupEyeEnforcementUiContractTest.kt
git commit -m "feat: add PupEye global ban lockout UI"
```

---

### Task 10: Update legal disclosure and define the future web/desktop enforcement contract

**Files:**
- Modify: `assets/legal/privacy-policy.md`
- Modify: `assets/legal/terms-of-use.md`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyPrivacyDataUi.kt`
- Modify: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyPrivacyDataContractTest.kt`
- Modify: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyOnboardingPreferencesContractTest.kt`
- Create: `docs/pupeye/global-enforcement-client-contract.md`
- Modify: `Website/README.md`
- Modify: `Desktop/README.md`

**Interfaces:**
- Produces privacy consent version 5 and the implementation contract future web/desktop clients must consume.

- [ ] **Step 1: Write failing legal/consent contract assertions**

```kotlin
@Test
fun privacyDisclosesGlobalEnforcementAndDeviceReputation() {
    val privacy = repoFile("../assets/legal/privacy-policy.md")
    assertTrue(privacy.contains("global enforcement"))
    assertTrue(privacy.contains("device reputation"))
    assertTrue(privacy.contains("save-content hash"))
    assertTrue(privacy.contains("Discord moderation notification"))
}

@Test
fun privacyConsentVersionIsFive() {
    val source = source("src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt")
    assertTrue(source.contains("CURRENT_PRIVACY_CONSENT_VERSION = 5"))
}
```

- [ ] **Step 2: Run and verify RED**

```bash
./gradlew --no-daemon :app:testDebugUnitTest   --tests com.harleytg.puppyclicker.PuppyPrivacyDataContractTest   --tests com.harleytg.puppyclicker.PuppyOnboardingPreferencesContractTest   --stacktrace
```

Expected: FAIL because the new disclosure/version is absent.

- [ ] **Step 3: Update Privacy Policy and Terms**

Privacy must explicitly disclose:

```markdown
PupEye may maintain server-side global-enforcement records, including a public Ban ID,
Player/Discord/installation links, privacy-compliant device reputation records, enforcement
audit events, and authenticated save-content SHA-256 attestations. These records are used
to detect replay/tampering, apply Support review, and enforce temporary or permanent bans.

Puppy Clicker does not use IMEI, hardware serial number, Android ID, phone number,
advertising ID, SIM serial, or MAC address as global-ban identifiers.

Operational moderation events may be sent to Harley's Studios through a Discord webhook
embed. These notifications do not include session tokens, IP addresses, email addresses,
save contents, or raw cryptographic key material.
```

Terms must explain review/temporary/permanent global bans, cross-platform scope, device-wide scope for strongly linked recognized devices, Support/appeal availability, and the fact that enforcement does not delete player data.

- [ ] **Step 4: Bump privacy consent version and copy**

```kotlin
const val CURRENT_PRIVACY_CONSENT_VERSION = 5
```

Update the Privacy & Data status text from onboarding v4 to current privacy version 5 language.

- [ ] **Step 5: Write the cross-platform contract without inventing clients**

`docs/pupeye/global-enforcement-client-contract.md` must define:

```markdown
# PupEye Global Enforcement Client Contract

All Puppy Clicker clients MUST:
1. generate/hold a client installation signing key appropriate to the platform;
2. call the shared PupEye registration/enforcement APIs;
3. honor GLOBAL_BANNED and REVIEW_REQUIRED before protected operations;
4. keep a known active ban locked when offline;
5. require server confirmation before clearing a temporary ban;
6. never expose server/admin secrets.

Android uses Android Keystore.
Desktop must use the strongest available OS-secure key storage once a desktop framework is selected.
Web uses account/Discord + browser installation identity and MUST NOT claim hardware-grade identity.
```

`Website/README.md` and `Desktop/README.md` link to this contract and continue to state that no client implementation/framework exists yet.

- [ ] **Step 6: Run tests and verify GREEN**

```bash
./gradlew --no-daemon :app:testDebugUnitTest   --tests com.harleytg.puppyclicker.PuppyPrivacyDataContractTest   --tests com.harleytg.puppyclicker.PuppyOnboardingPreferencesContractTest   --stacktrace
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add ../assets/legal/privacy-policy.md ../assets/legal/terms-of-use.md   app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt   app/src/main/java/com/harleytg/puppyclicker/PuppyPrivacyDataUi.kt   app/src/test/java/com/harleytg/puppyclicker/PuppyPrivacyDataContractTest.kt   app/src/test/java/com/harleytg/puppyclicker/PuppyOnboardingPreferencesContractTest.kt   ../docs/pupeye/global-enforcement-client-contract.md ../Website/README.md ../Desktop/README.md
git commit -m "docs: disclose PupEye global enforcement"
```

---

### Task 11: Add Supabase tests to CI and run the full Android regression suite

**Files:**
- Create: `.github/workflows/supabase-pupeye-tests.yml`
- Modify: `.github/workflows/android.yml` only if the new Android test classes are not already covered by `:app:testDebugUnitTest`.

**Interfaces:**
- Produces CI gates for pgTAP + Deno Edge Function tests in addition to the existing Android build/signing checks.

- [ ] **Step 1: Add a failing CI contract check locally**

Before creating the workflow:

```bash
test -f .github/workflows/supabase-pupeye-tests.yml
```

Expected: non-zero because the workflow does not exist.

- [ ] **Step 2: Create the Supabase CI workflow**

Use the current Supabase CLI setup action/version supported by the project environment, then:

```yaml
name: PupEye Supabase Tests

on:
  push:
    branches: [main]
    paths:
      - 'supabase/**'
      - '.github/workflows/supabase-pupeye-tests.yml'
  pull_request:
    branches: [main]
    paths:
      - 'supabase/**'
      - '.github/workflows/supabase-pupeye-tests.yml'

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: supabase/setup-cli@v1
        with:
          version: 2.117.0

      - uses: denoland/setup-deno@v2
        with:
          deno-version: v2.x

      - name: Start local Supabase
        run: supabase start

      - name: Run database tests
        run: supabase test db supabase/tests/pupeye_global_bans_test.sql

      - name: Run Edge Function tests
        run: deno test --allow-all supabase/functions/tests/
```

- [ ] **Step 3: Run all local Supabase tests**

```bash
supabase db reset
supabase test db supabase/tests/pupeye_global_bans_test.sql
deno test --allow-all supabase/functions/tests/
```

Expected: all PASS.

- [ ] **Step 4: Run the full Android suite**

From `App/`:

```bash
./gradlew --no-daemon   :app:testDebugUnitTest   :app:assembleDebugAndroidTest   :app:assembleRelease   --stacktrace
```

Expected: PASS. Report every failing existing test by name rather than hiding unrelated failures.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/supabase-pupeye-tests.yml .github/workflows/android.yml
git commit -m "ci: test PupEye global enforcement"
```

---

### Task 12: Deploy safely, configure the rotated webhook secret, and verify production behavior

**Files:**
- No new source file required unless deployment reveals a verified defect.
- Verify committed migration/function/config files from Tasks 1–11.

**Interfaces:**
- Consumes: Supabase project `damcmyqusdxucwozkoeh`.
- Produces live schema/functions and production verification evidence.

- [ ] **Step 1: Rotate the Discord webhook token before production configuration**

Do not copy the previously shared webhook credential into source control.

Set only the rotated URL as the Supabase secret:

```bash
supabase secrets set PUPPY_GLOBAL_BAN_DISCORD_WEBHOOK='<rotated webhook URL>'
```

The URL must not appear in commit diffs or Android resources.

- [ ] **Step 2: Apply the migration and run Supabase advisors**

Apply the committed global-ban migration through the approved Supabase deployment path, then run:

```text
Security advisor: no new RLS/public-access findings from global-ban tables.
Performance advisor: review any missing-index findings on ban-target lookup paths.
```

Fix any new advisor issue attributable to this migration before continuing.

- [ ] **Step 3: Deploy Edge Functions in backend-first order**

Deploy:

```text
1. pupeye-support-ban
2. pupeye-enforcement-status
3. pupeye-save-attestation
4. pupeye-register
5. pupeye-auth-discord
6. pupeye-sync
7. pupeye-transaction
```

Include the updated shared modules with each deployment.

Do not deploy the Android client before the backend understands `GLOBAL_BANNED` and `REVIEW_REQUIRED`.

- [ ] **Step 4: Verify the existing clean production player is unaffected**

Read production rows and verify:

```text
pupeye_players.status = active
pupeye_installations.integrity_state = clean
no active pupeye_global_bans target the existing player/install/device
pupeye-enforcement-status returns ALLOWED
existing session/registration can still sync
```

This is the explicit migration safety gate.

- [ ] **Step 5: Create and revoke a temporary test ban through the Support endpoint**

Using a non-production fixture identity or a controlled test identity, verify:

```text
issue_temporary -> GLOBAL_BANNED
Discord embed -> sent
status endpoint -> same public Ban ID
revoke_ban -> ALLOWED after refresh
Discord revoke embed -> sent
ban/audit history remains in database
save/inventory data unchanged
```

Never test a permanent ban against an uncontrolled real user.

- [ ] **Step 6: Verify device-wide propagation on a controlled fixture**

Attach a test ban to `DEVICE_REPUTATION`, then attempt registration with a second test Player ID using the same recognized test installation/device reputation.

Expected:

```text
HTTP 403
code = GLOBAL_BANNED
same public Ban ID
NEW_ACCOUNT_BLOCKED_ON_BANNED_DEVICE / BAN_EVASION_DETECTED audit
no usable session issued
```

- [ ] **Step 7: Verify save-attestation conflict escalation on fixtures**

Expected sequence:

```text
same save/hash repeated -> 200 idempotent
first distinct save hash conflict -> REVIEW_REQUIRED
second distinct conflicted save ID within 24h -> temporary global ban
temporary ban expires/revokes only by server state
```

- [ ] **Step 8: Build the exact final Android head**

After all tests/deploy verification:

```bash
cd App
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease --stacktrace
```

The repository's existing signing workflow must still produce the update-compatible APK and non-empty v4 `.idsig` when signing secrets are configured.

- [ ] **Step 9: Final production audit**

Confirm:

```text
- no raw Discord webhook URL in Git history/change set
- no service-role/secret key in APK/source
- no IMEI/serial/MAC/Android-ID collection
- all new public tables have RLS
- anon/authenticated direct access remains revoked
- active bans block before protected mutations
- webhook failure does not undo enforcement
- existing clean player remains active
- no user data is deleted by ban/revoke operations
```

- [ ] **Step 10: Commit any deployment-only documentation changes, otherwise do not create an empty commit**

If verification required no source changes, record the deployment run IDs externally and leave Git unchanged.
