begin;
create extension if not exists pgtap with schema extensions;

select plan(12);

select ok(to_regclass('public.pup_accounts') is not null, 'pup_accounts exists');
select ok(to_regclass('public.pup_account_saves') is not null, 'pup_account_saves exists');

select ok(
  (select relrowsecurity from pg_class where oid = 'public.pup_accounts'::regclass),
  'pup_accounts has RLS enabled'
);
select ok(
  (select relrowsecurity from pg_class where oid = 'public.pup_account_saves'::regclass),
  'pup_account_saves has RLS enabled'
);

select ok(
  not has_table_privilege('anon', 'public.pup_accounts', 'SELECT'),
  'anon cannot directly read Pup Accounts'
);
select ok(
  not has_table_privilege('authenticated', 'public.pup_accounts', 'SELECT'),
  'authenticated cannot directly read Pup Accounts'
);
select ok(
  not has_table_privilege('anon', 'public.pup_account_saves', 'SELECT'),
  'anon cannot directly read cloud saves'
);
select ok(
  not has_table_privilege('authenticated', 'public.pup_account_saves', 'SELECT'),
  'authenticated cannot directly read cloud saves'
);

select ok(exists (
  select 1
  from pg_constraint
  where conrelid = 'public.pup_accounts'::regclass
    and contype = 'u'
    and pg_get_constraintdef(oid) like '%(discord_user_id)%'
), 'one Pup Account per Discord identity is enforced');

select ok(exists (
  select 1
  from pg_constraint
  where conrelid = 'public.pup_accounts'::regclass
    and contype = 'u'
    and pg_get_constraintdef(oid) like '%(player_uuid)%'
), 'one Pup Account per PupEye player is enforced');

select ok(exists (
  select 1
  from pg_indexes
  where schemaname = 'public'
    and tablename = 'pup_account_saves'
    and indexname = 'pup_account_saves_installation_idx'
), 'cloud saves are indexed by installation');

select throws_ok(
  $$insert into public.pup_account_saves
      (player_uuid, account_uuid, installation_uuid, revision, generation, save_schema, save_data)
    values (
      '11111111-1111-4111-8111-111111111111',
      '22222222-2222-4222-8222-222222222222',
      '33333333-3333-4333-8333-333333333333',
      -1, 1, 1, '{}'::jsonb
    )$$,
  '23514',
  null,
  'cloud save rejects negative revisions'
);

select * from finish();
rollback;
