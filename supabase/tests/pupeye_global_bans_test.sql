begin;
create extension if not exists pgtap with schema extensions;

select plan(18);

select ok(to_regclass('public.pupeye_global_bans') is not null, 'pupeye_global_bans exists');
select ok(to_regclass('public.pupeye_ban_targets') is not null, 'pupeye_ban_targets exists');
select ok(to_regclass('public.pupeye_ban_events') is not null, 'pupeye_ban_events exists');
select ok(to_regclass('public.pupeye_device_reputation') is not null, 'pupeye_device_reputation exists');
select ok(to_regclass('public.pupeye_identity_links') is not null, 'pupeye_identity_links exists');
select ok(to_regclass('public.pupeye_ban_hits') is not null, 'pupeye_ban_hits exists');
select ok(to_regclass('public.pupeye_save_attestations') is not null, 'pupeye_save_attestations exists');

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

select ok(exists (
  select 1
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'pupeye_resolve_global_ban'
), 'pupeye_resolve_global_ban exists');
select ok(exists (
  select 1
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'pupeye_create_global_ban'
), 'pupeye_create_global_ban exists');
select ok(exists (
  select 1
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'pupeye_update_global_ban'
), 'pupeye_update_global_ban exists');
select ok(exists (
  select 1
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'pupeye_revoke_global_ban'
), 'pupeye_revoke_global_ban exists');
select ok(exists (
  select 1
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'pupeye_attach_ban_target'
), 'pupeye_attach_ban_target exists');
select ok(exists (
  select 1
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'pupeye_expire_due_bans'
), 'pupeye_expire_due_bans exists');

select ok(
  not has_table_privilege('anon', 'public.pupeye_global_bans', 'SELECT'),
  'anon has no direct global-ban table access'
);
select ok(
  not has_table_privilege('authenticated', 'public.pupeye_global_bans', 'SELECT'),
  'authenticated has no direct global-ban table access'
);

select * from finish();
rollback;
