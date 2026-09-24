begin;
create extension if not exists pgtap with schema extensions;

select plan(18);

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
select has_function('public', 'pupeye_update_global_ban');
select has_function('public', 'pupeye_revoke_global_ban');
select has_function('public', 'pupeye_attach_ban_target');
select has_function('public', 'pupeye_expire_due_bans');

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
