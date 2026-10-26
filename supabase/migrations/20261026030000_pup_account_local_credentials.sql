-- Add first-party email/password login without replacing existing Discord identities.
-- Supabase Auth stores/verifies passwords; PupEye remains the device authority.
alter table public.pup_accounts
  alter column discord_user_id drop not null;
alter table public.pup_accounts
  add column if not exists auth_user_id uuid references auth.users(id) on delete set null;
alter table public.pup_accounts
  add column if not exists local_username text;
alter table public.pup_accounts
  add constraint pup_accounts_signin_method_required
  check (auth_user_id is not null or discord_user_id is not null);
alter table public.pup_accounts
  add constraint pup_accounts_local_username_valid
  check (local_username is null or local_username ~ '^[A-Za-z0-9_.-]{3,20}$');
create unique index if not exists pup_accounts_auth_user_unique
  on public.pup_accounts(auth_user_id) where auth_user_id is not null;
create unique index if not exists pup_accounts_local_username_unique
  on public.pup_accounts(lower(local_username)) where local_username is not null;
comment on column public.pup_accounts.auth_user_id is
  'Optional Supabase Auth email/password identity, associated only after password authentication and a signed PupEye device session.';
comment on column public.pup_accounts.local_username is
  'Unique, user-selected Pup Account handle. Discord is optional for these accounts.';


-- Username reservations exist as soon as Supabase Auth creates an email
-- identity, *before* email verification or first PupEye login. This means a
-- confirmed first-time user may sign in by username without signing in by
-- email once. Never store email addresses or password hashes in this index.
create table if not exists public.pup_account_login_handles (
  auth_user_id uuid primary key references auth.users(id) on delete cascade,
  username text not null check (username ~ '^[A-Za-z0-9_.-]{3,20}$'),
  created_at timestamptz not null default now()
);
create unique index if not exists pup_login_handles_casefold_unique
  on public.pup_account_login_handles (lower(username));
alter table public.pup_account_login_handles enable row level security;
revoke all on table public.pup_account_login_handles from anon, authenticated;
-- No client SELECT policies: only the server-authenticated Edge Functions
-- and the narrowly scoped Auth INSERT trigger may access handle mappings.

create schema if not exists puppy_auth_private;
revoke all on schema puppy_auth_private from public, anon, authenticated;
create or replace function puppy_auth_private.reserve_pup_username()
returns trigger
language plpgsql
security definer
set search_path = ''
as $reserve$
declare
  v_username text;
begin
  v_username := btrim(new.raw_user_meta_data ->> 'puppy_username');
  if v_username is null then
    return new;  -- Discord/social/existing Supabase users without Pup credentials
  end if;
  if v_username !~ '^[A-Za-z0-9_.-]{3,20}$' then
    raise exception 'Invalid Pup Account username' using errcode = '22023';
  end if;
  insert into public.pup_account_login_handles (auth_user_id, username)
  values (new.id, v_username);
  return new;
end;
$reserve$;
revoke all on function puppy_auth_private.reserve_pup_username() from public, anon, authenticated;

drop trigger if exists reserve_pup_username_on_signup on auth.users;
create trigger reserve_pup_username_on_signup
after insert on auth.users
for each row execute function puppy_auth_private.reserve_pup_username();

comment on table public.pup_account_login_handles is
  'Private, case-insensitive login lookup. No email/password in index; protected by RLS and denied client grants.';
