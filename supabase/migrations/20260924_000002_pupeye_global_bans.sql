-- Puppy Clicker / PupEye global enforcement v1
-- Server-authoritative review/global-ban data. Direct client table access remains denied.

create extension if not exists pgcrypto;

create table public.pupeye_device_reputation (
    id uuid primary key default gen_random_uuid(),
    platform text not null
        check (platform in ('android','windows','macos','linux','web','other')),
    reputation_state text not null default 'clean'
        check (reputation_state in ('clean','review','blocked')),
    created_from_installation_uuid uuid
        references public.pupeye_installations(id) on delete set null,
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
    status text not null default 'active'
        check (status in ('active','expired','revoked')),
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
    ban_uuid uuid not null
        references public.pupeye_global_bans(id) on delete cascade,
    target_type text not null check (
        target_type in ('PLAYER','DISCORD','DEVICE_REPUTATION','INSTALLATION','WEB_INSTALLATION')
    ),
    player_uuid uuid references public.pupeye_players(id) on delete cascade,
    discord_user_id text,
    device_reputation_uuid uuid
        references public.pupeye_device_reputation(id) on delete cascade,
    installation_uuid uuid
        references public.pupeye_installations(id) on delete cascade,
    web_installation_id text,
    attached_at timestamptz not null default now(),
    attached_by text not null,
    source_event_id bigint,
    check (
        (target_type = 'PLAYER'
            and player_uuid is not null
            and num_nonnulls(discord_user_id, device_reputation_uuid, installation_uuid, web_installation_id) = 0)
        or
        (target_type = 'DISCORD'
            and discord_user_id is not null
            and num_nonnulls(player_uuid, device_reputation_uuid, installation_uuid, web_installation_id) = 0)
        or
        (target_type = 'DEVICE_REPUTATION'
            and device_reputation_uuid is not null
            and num_nonnulls(player_uuid, discord_user_id, installation_uuid, web_installation_id) = 0)
        or
        (target_type = 'INSTALLATION'
            and installation_uuid is not null
            and num_nonnulls(player_uuid, discord_user_id, device_reputation_uuid, web_installation_id) = 0)
        or
        (target_type = 'WEB_INSTALLATION'
            and web_installation_id is not null
            and num_nonnulls(player_uuid, discord_user_id, device_reputation_uuid, installation_uuid) = 0)
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

create unique index pupeye_ban_target_web_uq
    on public.pupeye_ban_targets(ban_uuid, web_installation_id)
    where target_type = 'WEB_INSTALLATION';

create index pupeye_ban_targets_player_lookup
    on public.pupeye_ban_targets(player_uuid)
    where player_uuid is not null;
create index pupeye_ban_targets_discord_lookup
    on public.pupeye_ban_targets(discord_user_id)
    where discord_user_id is not null;
create index pupeye_ban_targets_device_lookup
    on public.pupeye_ban_targets(device_reputation_uuid)
    where device_reputation_uuid is not null;
create index pupeye_ban_targets_installation_lookup
    on public.pupeye_ban_targets(installation_uuid)
    where installation_uuid is not null;

create table public.pupeye_ban_events (
    id bigint generated always as identity primary key,
    ban_uuid uuid references public.pupeye_global_bans(id) on delete cascade,
    event_code text not null,
    actor text not null,
    detail jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    discord_delivery_status text not null default 'pending'
        check (discord_delivery_status in ('pending','sent','failed','not_required')),
    discord_attempt_count integer not null default 0
        check (discord_attempt_count >= 0),
    discord_last_attempt_at timestamptz,
    discord_last_error text
);

create unique index pupeye_single_expiry_event_uq
    on public.pupeye_ban_events(ban_uuid, event_code)
    where event_code = 'BAN_EXPIRED';

create index pupeye_ban_events_ban_created_idx
    on public.pupeye_ban_events(ban_uuid, created_at desc);

create table public.pupeye_identity_links (
    id bigint generated always as identity primary key,
    link_type text not null check (
        link_type in ('PLAYER_DISCORD','PLAYER_INSTALLATION','INSTALLATION_DEVICE','PLAYER_WEB_INSTALLATION')
    ),
    player_uuid uuid references public.pupeye_players(id) on delete cascade,
    discord_user_id text,
    installation_uuid uuid references public.pupeye_installations(id) on delete cascade,
    device_reputation_uuid uuid
        references public.pupeye_device_reputation(id) on delete cascade,
    web_installation_id text,
    confidence text not null
        check (confidence in ('authoritative','strong','weak')),
    evidence_code text not null,
    created_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now()
);

create index pupeye_identity_links_player_idx
    on public.pupeye_identity_links(player_uuid, last_seen_at desc);
create index pupeye_identity_links_installation_idx
    on public.pupeye_identity_links(installation_uuid, last_seen_at desc);
create index pupeye_identity_links_device_idx
    on public.pupeye_identity_links(device_reputation_uuid, last_seen_at desc);
create index pupeye_identity_links_discord_idx
    on public.pupeye_identity_links(discord_user_id, last_seen_at desc);

create table public.pupeye_ban_hits (
    id bigint generated always as identity primary key,
    ban_uuid uuid not null
        references public.pupeye_global_bans(id) on delete cascade,
    player_uuid uuid references public.pupeye_players(id) on delete set null,
    installation_uuid uuid references public.pupeye_installations(id) on delete set null,
    device_reputation_uuid uuid
        references public.pupeye_device_reputation(id) on delete set null,
    discord_user_id text,
    client_platform text,
    request_action text not null,
    created_at timestamptz not null default now()
);

create index pupeye_ban_hits_ban_created_idx
    on public.pupeye_ban_hits(ban_uuid, created_at desc);

create table public.pupeye_save_attestations (
    id uuid primary key default gen_random_uuid(),
    save_id uuid not null,
    player_uuid uuid not null
        references public.pupeye_players(id) on delete cascade,
    installation_uuid uuid not null
        references public.pupeye_installations(id) on delete cascade,
    generation bigint not null check (generation >= 1),
    payload_hash_sha256 text not null
        check (payload_hash_sha256 ~ '^[0-9a-f]{64}$'),
    device_key_id text not null,
    observed_at timestamptz not null default now(),
    unique (save_id, installation_uuid, payload_hash_sha256)
);

create index pupeye_save_attestations_save_idx
    on public.pupeye_save_attestations(installation_uuid, save_id, observed_at desc);

alter table public.pupeye_save_heads
    add column last_save_id uuid,
    add column last_payload_hash_sha256 text
        check (
            last_payload_hash_sha256 is null
            or last_payload_hash_sha256 ~ '^[0-9a-f]{64}$'
        );

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

-- Existing installations get their own initial device-reputation node. This does not claim
-- cross-reinstall identity; stronger links may be added only from supported server evidence.
insert into public.pupeye_device_reputation (
    platform,
    created_from_installation_uuid,
    first_seen_at,
    last_seen_at
)
select
    coalesce(nullif(platform, ''), 'other'),
    id,
    registered_at,
    last_seen_at
from public.pupeye_installations
where device_reputation_uuid is null;

update public.pupeye_installations i
set device_reputation_uuid = d.id
from public.pupeye_device_reputation d
where d.created_from_installation_uuid = i.id
  and i.device_reputation_uuid is null;

insert into public.pupeye_identity_links (
    link_type,
    player_uuid,
    installation_uuid,
    confidence,
    evidence_code,
    created_at,
    last_seen_at
)
select
    'PLAYER_INSTALLATION',
    player_uuid,
    id,
    'authoritative',
    'MIGRATION_BACKFILL',
    registered_at,
    last_seen_at
from public.pupeye_installations;

insert into public.pupeye_identity_links (
    link_type,
    installation_uuid,
    device_reputation_uuid,
    confidence,
    evidence_code,
    created_at,
    last_seen_at
)
select
    'INSTALLATION_DEVICE',
    id,
    device_reputation_uuid,
    'authoritative',
    'MIGRATION_BACKFILL',
    registered_at,
    last_seen_at
from public.pupeye_installations
where device_reputation_uuid is not null;

insert into public.pupeye_identity_links (
    link_type,
    player_uuid,
    discord_user_id,
    confidence,
    evidence_code,
    created_at,
    last_seen_at
)
select
    'PLAYER_DISCORD',
    id,
    discord_user_id,
    'authoritative',
    'MIGRATION_BACKFILL',
    created_at,
    updated_at
from public.pupeye_players
where discord_user_id is not null;

create or replace function public.pupeye_generate_public_ban_id()
returns text
language sql
volatile
security invoker
as $$
    select 'PGB-' ||
        upper(substr(encode(gen_random_bytes(4), 'hex'), 1, 4)) ||
        '-' ||
        upper(substr(encode(gen_random_bytes(4), 'hex'), 1, 4));
$$;

create or replace function public.pupeye_attach_ban_target(
    p_ban_uuid uuid,
    p_target_type text,
    p_attached_by text,
    p_player_uuid uuid default null,
    p_discord_user_id text default null,
    p_device_reputation_uuid uuid default null,
    p_installation_uuid uuid default null,
    p_web_installation_id text default null,
    p_source_event_id bigint default null
)
returns uuid
language plpgsql
security invoker
as $$
declare
    v_target_id uuid;
begin
    insert into public.pupeye_ban_targets (
        ban_uuid,
        target_type,
        player_uuid,
        discord_user_id,
        device_reputation_uuid,
        installation_uuid,
        web_installation_id,
        attached_by,
        source_event_id
    ) values (
        p_ban_uuid,
        p_target_type,
        p_player_uuid,
        p_discord_user_id,
        p_device_reputation_uuid,
        p_installation_uuid,
        p_web_installation_id,
        left(coalesce(nullif(trim(p_attached_by), ''), 'system'), 80),
        p_source_event_id
    )
    returning id into v_target_id;

    return v_target_id;
end;
$$;

create or replace function public.pupeye_create_global_ban(
    p_kind text,
    p_reason_code text,
    p_public_reason text,
    p_issued_by text,
    p_expires_at timestamptz default null,
    p_internal_reason text default null,
    p_support_note text default null,
    p_targets jsonb default '[]'::jsonb
)
returns table (
    ban_uuid uuid,
    public_ban_id text,
    event_id bigint
)
language plpgsql
security invoker
as $$
declare
    v_ban public.pupeye_global_bans%rowtype;
    v_event_id bigint;
    v_target jsonb;
    v_public_id text;
begin
    if p_kind not in ('temporary', 'permanent') then
        raise exception using errcode = '22023', message = 'Invalid ban kind';
    end if;
    if p_kind = 'temporary' and (p_expires_at is null or p_expires_at <= now()) then
        raise exception using errcode = '22023', message = 'Temporary ban requires a future expiry';
    end if;
    if p_kind = 'permanent' and p_expires_at is not null then
        raise exception using errcode = '22023', message = 'Permanent ban must not have an expiry';
    end if;
    if jsonb_typeof(p_targets) <> 'array' or jsonb_array_length(p_targets) = 0 then
        raise exception using errcode = '22023', message = 'Global ban requires at least one target';
    end if;

    loop
        v_public_id := public.pupeye_generate_public_ban_id();
        begin
            insert into public.pupeye_global_bans (
                public_ban_id,
                kind,
                status,
                reason_code,
                public_reason,
                internal_reason,
                issued_at,
                expires_at,
                issued_by,
                support_note
            ) values (
                v_public_id,
                p_kind,
                'active',
                left(trim(p_reason_code), 64),
                left(trim(p_public_reason), 500),
                left(p_internal_reason, 2000),
                now(),
                p_expires_at,
                left(trim(p_issued_by), 80),
                left(p_support_note, 1000)
            )
            returning * into v_ban;
            exit;
        exception when unique_violation then
            -- Extremely unlikely random public ID collision; generate a new one.
        end;
    end loop;

    insert into public.pupeye_ban_events (
        ban_uuid,
        event_code,
        actor,
        detail
    ) values (
        v_ban.id,
        'GLOBAL_BAN_CREATED',
        v_ban.issued_by,
        jsonb_build_object(
            'kind', v_ban.kind,
            'reasonCode', v_ban.reason_code,
            'expiresAt', v_ban.expires_at
        )
    )
    returning id into v_event_id;

    for v_target in select value from jsonb_array_elements(p_targets)
    loop
        perform public.pupeye_attach_ban_target(
            v_ban.id,
            v_target->>'type',
            v_ban.issued_by,
            nullif(v_target->>'playerUuid', '')::uuid,
            nullif(v_target->>'discordUserId', ''),
            nullif(v_target->>'deviceReputationUuid', '')::uuid,
            nullif(v_target->>'installationUuid', '')::uuid,
            nullif(v_target->>'webInstallationId', ''),
            v_event_id
        );
    end loop;

    return query select v_ban.id, v_ban.public_ban_id, v_event_id;
end;
$$;

create or replace function public.pupeye_update_global_ban(
    p_ban_uuid uuid,
    p_actor text,
    p_kind text default null,
    p_expires_at timestamptz default null,
    p_public_reason text default null,
    p_internal_reason text default null,
    p_support_note text default null
)
returns table (
    ban_uuid uuid,
    public_ban_id text,
    event_id bigint
)
language plpgsql
security invoker
as $$
declare
    v_current public.pupeye_global_bans%rowtype;
    v_kind text;
    v_expires timestamptz;
    v_event_code text;
    v_event_id bigint;
begin
    select * into v_current
    from public.pupeye_global_bans
    where id = p_ban_uuid
    for update;

    if not found then
        raise exception using errcode = 'P0002', message = 'Global ban not found';
    end if;
    if v_current.status <> 'active' then
        raise exception using errcode = '22023', message = 'Only active bans can be updated';
    end if;

    v_kind := coalesce(p_kind, v_current.kind);
    if v_kind not in ('temporary', 'permanent') then
        raise exception using errcode = '22023', message = 'Invalid ban kind';
    end if;

    if v_kind = 'permanent' then
        v_expires := null;
    else
        v_expires := coalesce(p_expires_at, v_current.expires_at);
        if v_expires is null or v_expires <= now() then
            raise exception using errcode = '22023', message = 'Temporary ban requires a future expiry';
        end if;
    end if;

    v_event_code := case
        when v_current.kind <> v_kind then 'GLOBAL_BAN_ESCALATED'
        when v_kind = 'temporary' and v_expires is distinct from v_current.expires_at
            then 'GLOBAL_BAN_EXTENDED'
        else 'ADMIN_OVERRIDE'
    end;

    update public.pupeye_global_bans
    set kind = v_kind,
        expires_at = v_expires,
        public_reason = coalesce(nullif(trim(p_public_reason), ''), public_reason),
        internal_reason = case
            when p_internal_reason is null then internal_reason
            else left(p_internal_reason, 2000)
        end,
        support_note = case
            when p_support_note is null then support_note
            else left(p_support_note, 1000)
        end,
        updated_at = now()
    where id = p_ban_uuid
    returning * into v_current;

    insert into public.pupeye_ban_events (
        ban_uuid,
        event_code,
        actor,
        detail
    ) values (
        v_current.id,
        v_event_code,
        left(coalesce(nullif(trim(p_actor), ''), 'system'), 80),
        jsonb_build_object(
            'kind', v_current.kind,
            'expiresAt', v_current.expires_at
        )
    )
    returning id into v_event_id;

    return query select v_current.id, v_current.public_ban_id, v_event_id;
end;
$$;

create or replace function public.pupeye_revoke_global_ban(
    p_ban_uuid uuid,
    p_actor text,
    p_note text default null
)
returns table (
    ban_uuid uuid,
    public_ban_id text,
    event_id bigint
)
language plpgsql
security invoker
as $$
declare
    v_ban public.pupeye_global_bans%rowtype;
    v_event_id bigint;
begin
    update public.pupeye_global_bans
    set status = 'revoked',
        revoked_at = now(),
        revoked_by = left(coalesce(nullif(trim(p_actor), ''), 'system'), 80),
        support_note = case
            when p_note is null then support_note
            else left(p_note, 1000)
        end,
        updated_at = now()
    where id = p_ban_uuid
      and status = 'active'
    returning * into v_ban;

    if not found then
        raise exception using errcode = 'P0002', message = 'Active global ban not found';
    end if;

    insert into public.pupeye_ban_events (
        ban_uuid,
        event_code,
        actor,
        detail
    ) values (
        v_ban.id,
        'GLOBAL_BAN_REVOKED',
        v_ban.revoked_by,
        jsonb_build_object('note', left(coalesce(p_note, ''), 500))
    )
    returning id into v_event_id;

    return query select v_ban.id, v_ban.public_ban_id, v_event_id;
end;
$$;

create or replace function public.pupeye_expire_due_bans()
returns table (
    ban_uuid uuid,
    public_ban_id text,
    event_id bigint
)
language plpgsql
security invoker
as $$
begin
    return query
    with expired as (
        update public.pupeye_global_bans
        set status = 'expired',
            updated_at = now()
        where status = 'active'
          and kind = 'temporary'
          and expires_at <= now()
        returning id, public_ban_id, expires_at
    ),
    inserted as (
        insert into public.pupeye_ban_events (
            ban_uuid,
            event_code,
            actor,
            detail
        )
        select
            e.id,
            'BAN_EXPIRED',
            'system',
            jsonb_build_object('expiredAt', e.expires_at)
        from expired e
        on conflict (ban_uuid, event_code)
            where event_code = 'BAN_EXPIRED'
        do nothing
        returning id, ban_uuid
    )
    select e.id, e.public_ban_id, i.id
    from expired e
    join inserted i on i.ban_uuid = e.id;
end;
$$;

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
stable
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
          (p_player_uuid is not null and t.player_uuid = p_player_uuid)
          or (p_discord_user_id is not null and t.discord_user_id = p_discord_user_id)
          or (p_installation_uuid is not null and t.installation_uuid = p_installation_uuid)
          or (
              p_device_reputation_uuid is not null
              and t.device_reputation_uuid = p_device_reputation_uuid
          )
          or (
              p_web_installation_id is not null
              and t.web_installation_id = p_web_installation_id
          )
      )
    group by b.id
    order by (b.kind = 'permanent') desc, b.issued_at desc
    limit 1;
$$;

revoke all on function public.pupeye_generate_public_ban_id() from public, anon, authenticated;
revoke all on function public.pupeye_attach_ban_target(uuid,text,text,uuid,text,uuid,uuid,text,bigint)
    from public, anon, authenticated;
revoke all on function public.pupeye_create_global_ban(text,text,text,text,timestamptz,text,text,jsonb)
    from public, anon, authenticated;
revoke all on function public.pupeye_update_global_ban(uuid,text,text,timestamptz,text,text,text)
    from public, anon, authenticated;
revoke all on function public.pupeye_revoke_global_ban(uuid,text,text)
    from public, anon, authenticated;
revoke all on function public.pupeye_expire_due_bans()
    from public, anon, authenticated;
revoke all on function public.pupeye_resolve_global_ban(uuid,text,uuid,uuid,text)
    from public, anon, authenticated;

grant execute on function public.pupeye_generate_public_ban_id() to service_role;
grant execute on function public.pupeye_attach_ban_target(uuid,text,text,uuid,text,uuid,uuid,text,bigint)
    to service_role;
grant execute on function public.pupeye_create_global_ban(text,text,text,text,timestamptz,text,text,jsonb)
    to service_role;
grant execute on function public.pupeye_update_global_ban(uuid,text,text,timestamptz,text,text,text)
    to service_role;
grant execute on function public.pupeye_revoke_global_ban(uuid,text,text)
    to service_role;
grant execute on function public.pupeye_expire_due_bans()
    to service_role;
grant execute on function public.pupeye_resolve_global_ban(uuid,text,uuid,uuid,text)
    to service_role;
