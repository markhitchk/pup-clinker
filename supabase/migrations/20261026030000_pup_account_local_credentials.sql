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
