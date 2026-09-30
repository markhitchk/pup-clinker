-- T0 passwordless Pup Accounts.
-- Discord OAuth is the account identity; PupEye remains the one-device authority.
-- Direct client access is intentionally disabled. Edge Functions use the service role
-- after validating the signed PupEye installation envelope + session.

create table if not exists public.pup_accounts (
  id uuid primary key default gen_random_uuid(),
  player_uuid uuid not null unique references public.pupeye_players(id) on delete cascade,
  discord_user_id text not null unique,
  username text not null check (char_length(username) between 1 and 32),
  display_name text,
  avatar_hash text,
  active_installation_uuid uuid not null references public.pupeye_installations(id),
  stage text not null default 't0' check (stage in ('t0','t1','production')),
  status text not null default 'active' check (status in ('active','review','blocked')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.pup_account_saves (
  player_uuid uuid primary key references public.pupeye_players(id) on delete cascade,
  account_uuid uuid not null unique references public.pup_accounts(id) on delete cascade,
  installation_uuid uuid not null references public.pupeye_installations(id),
  revision bigint not null default 0 check (revision >= 0),
  generation bigint not null default 1 check (generation >= 1),
  save_schema integer not null default 1 check (save_schema >= 1),
  save_data jsonb not null default '{}'::jsonb,
  payload_hash_sha256 text,
  updated_at timestamptz not null default now(),
  constraint pup_account_saves_hash_format
    check (payload_hash_sha256 is null or payload_hash_sha256 ~ '^[0-9a-f]{64}$')
);

create index if not exists pup_accounts_active_installation_idx
  on public.pup_accounts(active_installation_uuid);

create index if not exists pup_account_saves_installation_idx
  on public.pup_account_saves(installation_uuid);

alter table public.pup_accounts enable row level security;
alter table public.pup_account_saves enable row level security;

revoke all on table public.pup_accounts from anon, authenticated;
revoke all on table public.pup_account_saves from anon, authenticated;

comment on table public.pup_accounts is
  'T0 Puppy Clicker passwordless accounts. Discord is the external identity; PupEye binds the active device.';
comment on table public.pup_account_saves is
  'Authoritative T0 cloud save. Android SharedPreferences is a local cache only.';
