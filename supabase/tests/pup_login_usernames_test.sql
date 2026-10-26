begin;
create extension if not exists pgtap with schema extensions;
select plan(9);

select ok(to_regclass('public.pup_account_login_handles') is not null,
  'username reservations exist');
select ok((select relrowsecurity from pg_class where oid = 'public.pup_account_login_handles'::regclass),
  'username reservations have RLS');
select ok(not has_table_privilege('anon', 'public.pup_account_login_handles', 'SELECT'),
  'anonymous clients cannot enumerate usernames');
select ok(not has_table_privilege('authenticated', 'public.pup_account_login_handles', 'SELECT'),
  'authenticated clients cannot enumerate usernames or account bindings');
select ok(exists(select 1 from pg_indexes
  where schemaname='public' and tablename='pup_account_login_handles'
    and indexname='pup_login_handles_casefold_unique' and indexdef ilike '%unique%lower(username)%'),
  'username uniqueness is case insensitive');
select ok(exists(select 1 from pg_trigger
  where tgname='reserve_pup_username_on_signup'
    and tgrelid='auth.users'::regclass and not tgisinternal),
  'registration reserves a username as part of Auth sign-up');
select ok(to_regprocedure('puppy_auth_private.reserve_pup_username()') is not null,
  'username registration trigger exists in a non-public schema');
select ok(not has_schema_privilege('anon', 'puppy_auth_private', 'USAGE'),
  'private username trigger schema is not exposed');
select ok(not has_schema_privilege('authenticated', 'puppy_auth_private', 'USAGE'),
  'private username trigger is not exposed to authenticated users');

select * from finish();
rollback;
