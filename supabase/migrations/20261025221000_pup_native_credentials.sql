-- Native Pup Account username/email/password, independent from optional Discord.
-- Passwords and password hashes remain solely in Supabase Auth (auth.users).
alter table public.pup_accounts alter column discord_user_id drop not null;
create unique index if not exists pup_accounts_username_ci_unique
  on public.pup_accounts (lower(username));

create table if not exists public.pup_credential_pending (
  auth_user_id uuid primary key references auth.users(id) on delete cascade,
  email text not null,
  username text not null check(username ~ '^[A-Za-z0-9_-]{3,20}$'),
  created_at timestamptz not null default now()
);
create unique index if not exists pup_credential_pending_username_ci_unique
  on public.pup_credential_pending(lower(username));

create table if not exists public.pup_credential_links (
  auth_user_id uuid primary key references auth.users(id) on delete cascade,
  account_uuid uuid not null unique references public.pup_accounts(id) on delete cascade,
  email text not null,
  linked_at timestamptz not null default now()
);
create unique index if not exists pup_credential_links_email_ci_unique
  on public.pup_credential_links(lower(email));

-- A scoped beta link can be authorized by verified native credentials OR Discord.
alter table public.pup_beta_account_links alter column discord_user_id drop not null;
alter table public.pup_beta_account_links
  add column if not exists credential_auth_user_id uuid references auth.users(id) on delete cascade;
alter table public.pup_beta_account_links
  add constraint pup_beta_authorized_identity_required
  check (discord_user_id is not null or credential_auth_user_id is not null);

alter table public.pup_credential_pending enable row level security;
alter table public.pup_credential_links enable row level security;
revoke all on public.pup_credential_pending from anon, authenticated;
revoke all on public.pup_credential_links from anon, authenticated;

comment on table public.pup_credential_pending is 'Private, unconfirmed username reservations tied to Supabase Auth; never contains passwords.';
comment on table public.pup_credential_links is 'Verified Supabase Auth user-to-Pup Account identity link, accessible only through authenticated Edge Functions.';
