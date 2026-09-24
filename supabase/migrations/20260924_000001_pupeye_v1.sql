-- Puppy Clicker / PupEye backend v1
-- Direct table access is intentionally denied by RLS. Android talks to Edge Functions only.

create extension if not exists pgcrypto;

create table if not exists public.pupeye_players (
    id uuid primary key default gen_random_uuid(),
    player_id text not null unique,
    friend_code text not null unique,
    username text not null,
    discord_user_id text unique,
    discord_username text,
    discord_global_name text,
    discord_avatar_hash text,
    discord_email text,
    guild_role text check (guild_role is null or guild_role in ('DEVELOPER','ADMIN','PUP_MEMBER','GUEST')),
    guild_verified_at timestamptz,
    status text not null default 'active' check (status in ('active','review','blocked')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.pupeye_installations (
    id uuid primary key default gen_random_uuid(),
    player_uuid uuid not null references public.pupeye_players(id) on delete cascade,
    installation_id uuid not null unique,
    device_key_id text not null unique,
    public_key_b64 text not null,
    support_code text not null unique,
    device_model text,
    platform text not null default 'android',
    app_version text,
    active boolean not null default true,
    integrity_state text not null default 'clean'
        check (integrity_state in ('clean','review','blocked')),
    max_save_generation bigint not null default 0 check (max_save_generation >= 0),
    registered_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    revoked_at timestamptz
);

create unique index if not exists pupeye_one_active_installation_per_player
    on public.pupeye_installations(player_uuid)
    where active = true and revoked_at is null;

create table if not exists public.pupeye_sessions (
    id uuid primary key default gen_random_uuid(),
    player_uuid uuid not null references public.pupeye_players(id) on delete cascade,
    installation_uuid uuid not null references public.pupeye_installations(id) on delete cascade,
    token_hash text not null unique,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    last_seen_at timestamptz not null default now(),
    revoked_at timestamptz
);

create index if not exists pupeye_sessions_installation_idx
    on public.pupeye_sessions(installation_uuid, expires_at);

create table if not exists public.pupeye_save_heads (
    installation_uuid uuid primary key references public.pupeye_installations(id) on delete cascade,
    generation bigint not null check (generation >= 0),
    support_code text not null,
    last_reason text,
    hard_flags text[] not null default '{}',
    updated_at timestamptz not null default now()
);

create table if not exists public.pupeye_transactions (
    transaction_id text primary key,
    player_uuid uuid not null references public.pupeye_players(id) on delete cascade,
    installation_uuid uuid not null references public.pupeye_installations(id) on delete cascade,
    source text not null,
    details text not null,
    generation bigint not null check (generation >= 1),
    request_nonce uuid not null unique,
    created_at timestamptz not null default now()
);

create index if not exists pupeye_transactions_player_idx
    on public.pupeye_transactions(player_uuid, created_at desc);

create table if not exists public.pupeye_events (
    id bigint generated always as identity primary key,
    player_uuid uuid references public.pupeye_players(id) on delete cascade,
    installation_uuid uuid references public.pupeye_installations(id) on delete cascade,
    event_code text not null,
    severity text not null default 'info' check (severity in ('info','warning','review','hard')),
    detail jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists pupeye_events_installation_idx
    on public.pupeye_events(installation_uuid, created_at desc);

create table if not exists public.pupeye_device_migrations (
    id uuid primary key default gen_random_uuid(),
    player_uuid uuid not null references public.pupeye_players(id) on delete cascade,
    from_installation_uuid uuid references public.pupeye_installations(id) on delete set null,
    requested_installation_id uuid not null,
    requested_device_key_id text not null,
    requested_public_key_b64 text not null,
    requested_support_code text not null,
    requested_device_model text,
    requested_app_version text,
    status text not null default 'pending'
        check (status in ('pending','approved','rejected','consumed','expired')),
    requested_at timestamptz not null default now(),
    reviewed_at timestamptz,
    consumed_at timestamptz,
    support_note text,
    unique (player_uuid, requested_installation_id, requested_device_key_id)
);

alter table public.pupeye_players enable row level security;
alter table public.pupeye_installations enable row level security;
alter table public.pupeye_sessions enable row level security;
alter table public.pupeye_save_heads enable row level security;
alter table public.pupeye_transactions enable row level security;
alter table public.pupeye_events enable row level security;
alter table public.pupeye_device_migrations enable row level security;

-- No client policies are created. Edge Functions use the server-side secret client.
revoke all on table public.pupeye_players from anon, authenticated;
revoke all on table public.pupeye_installations from anon, authenticated;
revoke all on table public.pupeye_sessions from anon, authenticated;
revoke all on table public.pupeye_save_heads from anon, authenticated;
revoke all on table public.pupeye_transactions from anon, authenticated;
revoke all on table public.pupeye_events from anon, authenticated;
revoke all on table public.pupeye_device_migrations from anon, authenticated;
