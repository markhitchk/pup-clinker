-- Puppy Clicker / PupEye global enforcement v1.
-- Additive migration: existing clean players/installations remain active.

create table public.pupeye_device_reputation (
    id uuid primary key default gen_random_uuid(),
    platform text not null check (platform in ('android','windows','macos','linux','web','other')),
    reputation_state text not null default 'clean'
        check (reputation_state in ('clean','review','blocked')),
    created_from_installation_uuid uuid references public.pupeye_installations(id) on delete set null,
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
    status text not null default 'active' check (status in ('active','expired','revoked')),
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
    ban_uuid uuid not null references public.pupeye_global_bans(id) on delete cascade,
    target_type text not null
        check (target_type in ('PLAYER','DISCORD','DEVICE_REPUTATION','INSTALLATION','WEB_INSTALLATION')),
    player_uuid uuid references public.pupeye_players(id) on delete cascade,
    discord_user_id text,
    device_reputation_uuid uuid references public.pupeye_device_reputation(id) on delete cascade,
    installation_uuid uuid references public.pupeye_installations(id) on delete cascade,
    web_installation_id text,
    attached_at timestamptz not null default now(),
    attached_by text not null,
    source_event_id bigint,
    check (
        (target_type = 'PLAYER' and player_uuid is not null
            and num_nonnulls(discord_user_id, device_reputation_uuid, installation_uuid, web_installation_id) = 0)
        or
        (target_type = 'DISCORD' and discord_user_id is not null
            and num_nonnulls(player_uuid, device_reputation_uuid, installation_uuid, web_installation_id) = 0)
        or
        (target_type = 'DEVICE_REPUTATION' and device_reputation_uuid is not null
            and num_nonnulls(player_uuid, discord_user_id, installation_uuid, web_installation_id) = 0)
        or
        (target_type = 'INSTALLATION' and installation_uuid is not null
            and num_nonnulls(player_uuid, discord_user_id, device_reputation_uuid, web_installation_id) = 0)
        or
        (target_type = 'WEB_INSTALLATION' and web_installation_id is not null
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

create index pupeye_ban_targets_player_idx on public.pupeye_ban_targets(player_uuid)
    where player_uuid is not null;
create index pupeye_ban_targets_discord_idx on public.pupeye_ban_targets(discord_user_id)
    where discord_user_id is not null;
create index pupeye_ban_targets_device_idx on public.pupeye_ban_targets(device_reputation_uuid)
    where device_reputation_uuid is not null;
create index pupeye_ban_targets_installation_idx on public.pupeye_ban_targets(installation_uuid)
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
    discord_attempt_count integer not null default 0 check (discord_attempt_count >= 0),
    discord_last_attempt_at timestamptz,
    discord_last_error text
);

create index pupeye_ban_events_ban_idx
    on public.pupeye_ban_events(ban_uuid, created_at desc);

create unique index pupeye_single_expiry_event_uq
    on public.pupeye_ban_events(ban_uuid, event_code)
    where event_code = 'BAN_EXPIRED';

alter table public.pupeye_ban_targets
    add constraint pupeye_ban_targets_source_event_fkey
    foreign key (source_event_id) references public.pupeye_ban_events(id) on delete set null;

create table public.pupeye_identity_links (
    id bigint generated always as identity primary key,
    link_type text not null
        check (link_type in ('PLAYER_DISCORD','PLAYER_INSTALLATION','INSTALLATION_DEVICE','PLAYER_WEB_INSTALLATION')),
    player_uuid uuid references public.pupeye_players(id) on delete cascade,
    discord_user_id text,
    installation_uuid uuid references public.pupeye_installations(id) on delete cascade,
    device_reputation_uuid uuid references public.pupeye_device_reputation(id) on delete cascade,
    web_installation_id text,
    confidence text not null check (confidence in ('authoritative','strong','weak')),
    evidence_code text not null,
    created_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    check (
        (link_type = 'PLAYER_DISCORD'
            and player_uuid is not null and discord_user_id is not null
            and installation_uuid is null and device_reputation_uuid is null and web_installation_id is null)
        or
        (link_type = 'PLAYER_INSTALLATION'
            and player_uuid is not null and installation_uuid is not null
            and discord_user_id is null and device_reputation_uuid is null and web_installation_id is null)
        or
        (link_type = 'INSTALLATION_DEVICE'
            and installation_uuid is not null and device_reputation_uuid is not null
            and player_uuid is null and discord_user_id is null and web_installation_id is null)
        or
        (link_type = 'PLAYER_WEB_INSTALLATION'
            and player_uuid is not null and web_installation_id is not null
            and discord_user_id is null and installation_uuid is null and device_reputation_uuid is null)
    )
);

create unique index pupeye_identity_player_discord_uq
    on public.pupeye_identity_links(player_uuid, discord_user_id)
    where link_type = 'PLAYER_DISCORD';
create unique index pupeye_identity_player_installation_uq
    on public.pupeye_identity_links(player_uuid, installation_uuid)
    where link_type = 'PLAYER_INSTALLATION';
create unique index pupeye_identity_installation_device_uq
    on public.pupeye_identity_links(installation_uuid, device_reputation_uuid)
    where link_type = 'INSTALLATION_DEVICE';

create table public.pupeye_ban_hits (
    id bigint generated always as identity primary key,
    ban_uuid uuid not null references public.pupeye_global_bans(id) on delete cascade,
    player_uuid uuid references public.pupeye_players(id) on delete set null,
    installation_uuid uuid references public.pupeye_installations(id) on delete set null,
    device_reputation_uuid uuid references public.pupeye_device_reputation(id) on delete set null,
    discord_user_id text,
    client_platform text,
    request_action text not null,
    created_at timestamptz not null default now()
);

create index pupeye_ban_hits_ban_idx
    on public.pupeye_ban_hits(ban_uuid, created_at desc);

create table public.pupeye_save_attestations (
    id uuid primary key default gen_random_uuid(),
    save_id uuid not null,
    player_uuid uuid not null references public.pupeye_players(id) on delete cascade,
    installation_uuid uuid not null references public.pupeye_installations(id) on delete cascade,
    generation bigint not null check (generation >= 1),
    payload_hash_sha256 text not null
        check (payload_hash_sha256 ~ '^[0-9a-f]{64}$'),
    device_key_id text not null,
    observed_at timestamptz not null default now(),
    unique (save_id, installation_uuid, payload_hash_sha256)
);

create index pupeye_save_attestations_lookup_idx
    on public.pupeye_save_attestations(installation_uuid, save_id, observed_at desc);

alter table public.pupeye_save_heads
    add column last_save_id uuid,
    add column last_payload_hash_sha256 text
        check (
            last_payload_hash_sha256 is null
            or last_payload_hash_sha256 ~ '^[0-9a-f]{64}$'
        );

-- Backfill a reputation record per current installation. This is deliberately
-- one-to-one: no migration pretends to know two installations are the same hardware.
insert into public.pupeye_device_reputation (
    platform,
    created_from_installation_uuid,
    first_seen_at,
    last_seen_at
)
select
    case
        when lower(coalesce(platform, '')) in ('android','windows','macos','linux','web')
            then lower(platform)
        else 'other'
    end,
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
    link_type, player_uuid, installation_uuid, confidence, evidence_code
)
select
    'PLAYER_INSTALLATION', player_uuid, id, 'authoritative', 'MIGRATION_BACKFILL'
from public.pupeye_installations
on conflict do nothing;

insert into public.pupeye_identity_links (
    link_type, installation_uuid, device_reputation_uuid, confidence, evidence_code
)
select
    'INSTALLATION_DEVICE', id, device_reputation_uuid, 'authoritative', 'MIGRATION_BACKFILL'
from public.pupeye_installations
where device_reputation_uuid is not null
on conflict do nothing;

insert into public.pupeye_identity_links (
    link_type, player_uuid, discord_user_id, confidence, evidence_code
)
select
    'PLAYER_DISCORD', id, discord_user_id, 'authoritative', 'MIGRATION_BACKFILL'
from public.pupeye_players
where discord_user_id is not null
on conflict do nothing;

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

grant select, insert, update, delete on table public.pupeye_global_bans to service_role;
grant select, insert, update, delete on table public.pupeye_ban_targets to service_role;
grant select, insert, update on table public.pupeye_ban_events to service_role;
grant select, insert, update, delete on table public.pupeye_device_reputation to service_role;
grant select, insert, update, delete on table public.pupeye_identity_links to service_role;
grant select, insert on table public.pupeye_ban_hits to service_role;
grant select, insert on table public.pupeye_save_attestations to service_role;
grant usage, select on sequence public.pupeye_ban_events_id_seq to service_role;
grant usage, select on sequence public.pupeye_identity_links_id_seq to service_role;
grant usage, select on sequence public.pupeye_ban_hits_id_seq to service_role;

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
security invoker
set search_path = public
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
          or (p_device_reputation_uuid is not null and t.device_reputation_uuid = p_device_reputation_uuid)
          or (p_web_installation_id is not null and t.web_installation_id = p_web_installation_id)
      )
    group by b.id
    order by (b.kind = 'permanent') desc, b.issued_at desc
    limit 1;
$$;

create or replace function public.pupeye_expire_due_bans()
returns table (
    event_id bigint,
    ban_uuid uuid,
    public_ban_id text
)
language sql
security invoker
set search_path = public
as $$
    with expired as (
        update public.pupeye_global_bans b
        set status = 'expired',
            updated_at = now()
        where b.status = 'active'
          and b.kind = 'temporary'
          and b.expires_at <= now()
        returning b.id, b.public_ban_id
    ),
    inserted as (
        insert into public.pupeye_ban_events (
            ban_uuid, event_code, actor, detail, discord_delivery_status
        )
        select
            e.id,
            'BAN_EXPIRED',
            'system',
            jsonb_build_object('publicBanId', e.public_ban_id),
            'pending'
        from expired e
        on conflict (ban_uuid, event_code)
            where event_code = 'BAN_EXPIRED'
            do nothing
        returning id, ban_uuid
    )
    select i.id, i.ban_uuid, e.public_ban_id
    from inserted i
    join expired e on e.id = i.ban_uuid;
$$;

create or replace function public.pupeye_create_global_ban(
    p_kind text,
    p_reason_code text,
    p_public_reason text,
    p_internal_reason text,
    p_expires_at timestamptz,
    p_issued_by text,
    p_support_note text,
    p_targets jsonb
)
returns table (
    ban_uuid uuid,
    public_ban_id text,
    event_id bigint
)
language plpgsql
security invoker
set search_path = public
as $$
declare
    v_ban public.pupeye_global_bans%rowtype;
    v_event_id bigint;
    v_target jsonb;
    v_random text;
begin
    if p_kind not in ('temporary','permanent') then
        raise exception 'invalid ban kind' using errcode = '22023';
    end if;
    if p_kind = 'temporary' and (p_expires_at is null or p_expires_at <= now()) then
        raise exception 'temporary ban requires a future expiry' using errcode = '22023';
    end if;
    if p_kind = 'permanent' and p_expires_at is not null then
        raise exception 'permanent ban cannot have an expiry' using errcode = '22023';
    end if;
    if coalesce(jsonb_typeof(p_targets), '') <> 'array' or jsonb_array_length(p_targets) < 1 then
        raise exception 'ban requires at least one target' using errcode = '22023';
    end if;

    v_random := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 12));

    insert into public.pupeye_global_bans (
        public_ban_id, kind, status, reason_code, public_reason,
        internal_reason, expires_at, issued_by, support_note
    ) values (
        'PGB-' || substr(v_random, 1, 4) || '-' || substr(v_random, 5, 4) || '-' || substr(v_random, 9, 4),
        p_kind,
        'active',
        left(trim(p_reason_code), 64),
        left(trim(p_public_reason), 500),
        nullif(left(coalesce(p_internal_reason, ''), 2000), ''),
        p_expires_at,
        left(trim(p_issued_by), 80),
        nullif(left(coalesce(p_support_note, ''), 1000), '')
    )
    returning * into v_ban;

    insert into public.pupeye_ban_events (
        ban_uuid, event_code, actor, detail, discord_delivery_status
    ) values (
        v_ban.id,
        case when p_kind = 'temporary'
            then 'TEMPORARY_GLOBAL_BAN_CREATED'
            else 'GLOBAL_BAN_CREATED'
        end,
        v_ban.issued_by,
        jsonb_build_object(
            'publicBanId', v_ban.public_ban_id,
            'kind', v_ban.kind,
            'reasonCode', v_ban.reason_code,
            'expiresAt', v_ban.expires_at
        ),
        'pending'
    )
    returning id into v_event_id;

    for v_target in select value from jsonb_array_elements(p_targets)
    loop
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
            v_ban.id,
            upper(v_target->>'type'),
            nullif(v_target->>'playerUuid', '')::uuid,
            nullif(v_target->>'discordUserId', ''),
            nullif(v_target->>'deviceReputationUuid', '')::uuid,
            nullif(v_target->>'installationUuid', '')::uuid,
            nullif(v_target->>'webInstallationId', ''),
            v_ban.issued_by,
            v_event_id
        );
    end loop;

    return query select v_ban.id, v_ban.public_ban_id, v_event_id;
end;
$$;

create or replace function public.pupeye_attach_ban_target(
    p_ban_uuid uuid,
    p_target_type text,
    p_player_uuid uuid,
    p_discord_user_id text,
    p_device_reputation_uuid uuid,
    p_installation_uuid uuid,
    p_web_installation_id text,
    p_actor text,
    p_source_event_id bigint
)
returns table (
    target_id uuid,
    event_id bigint
)
language plpgsql
security invoker
set search_path = public
as $$
declare
    v_target_id uuid;
    v_event_id bigint;
begin
    insert into public.pupeye_ban_targets (
        ban_uuid, target_type, player_uuid, discord_user_id,
        device_reputation_uuid, installation_uuid, web_installation_id,
        attached_by, source_event_id
    ) values (
        p_ban_uuid, upper(p_target_type), p_player_uuid, nullif(p_discord_user_id, ''),
        p_device_reputation_uuid, p_installation_uuid, nullif(p_web_installation_id, ''),
        left(trim(p_actor), 80), p_source_event_id
    )
    returning id into v_target_id;

    insert into public.pupeye_ban_events (
        ban_uuid, event_code, actor, detail, discord_delivery_status
    ) values (
        p_ban_uuid,
        'BAN_TARGET_ATTACHED',
        left(trim(p_actor), 80),
        jsonb_strip_nulls(jsonb_build_object(
            'targetType', upper(p_target_type),
            'playerUuid', p_player_uuid,
            'discordUserId', nullif(p_discord_user_id, ''),
            'deviceReputationUuid', p_device_reputation_uuid,
            'installationUuid', p_installation_uuid,
            'webInstallationId', nullif(p_web_installation_id, '')
        )),
        'pending'
    )
    returning id into v_event_id;

    return query select v_target_id, v_event_id;
end;
$$;

create or replace function public.pupeye_update_global_ban(
    p_ban_uuid uuid,
    p_kind text,
    p_expires_at timestamptz,
    p_actor text,
    p_support_note text
)
returns table (
    ban_uuid uuid,
    public_ban_id text,
    event_id bigint
)
language plpgsql
security invoker
set search_path = public
as $$
declare
    v_before public.pupeye_global_bans%rowtype;
    v_after public.pupeye_global_bans%rowtype;
    v_event_code text;
    v_event_id bigint;
begin
    select * into v_before
    from public.pupeye_global_bans
    where id = p_ban_uuid
    for update;

    if not found then
        raise exception 'ban not found' using errcode = 'P0002';
    end if;
    if v_before.status <> 'active' then
        raise exception 'only active bans can be modified' using errcode = '22023';
    end if;
    if p_kind not in ('temporary','permanent') then
        raise exception 'invalid ban kind' using errcode = '22023';
    end if;
    if p_kind = 'temporary' and (p_expires_at is null or p_expires_at <= now()) then
        raise exception 'temporary ban requires a future expiry' using errcode = '22023';
    end if;
    if p_kind = 'permanent' and p_expires_at is not null then
        raise exception 'permanent ban cannot have an expiry' using errcode = '22023';
    end if;

    update public.pupeye_global_bans
    set kind = p_kind,
        expires_at = p_expires_at,
        support_note = coalesce(nullif(left(coalesce(p_support_note, ''), 1000), ''), support_note),
        updated_at = now()
    where id = p_ban_uuid
    returning * into v_after;

    v_event_code := case
        when v_before.kind = 'temporary' and v_after.kind = 'permanent'
            then 'GLOBAL_BAN_ESCALATED'
        when v_before.kind <> v_after.kind
            then 'ADMIN_OVERRIDE'
        else 'GLOBAL_BAN_EXTENDED'
    end;

    insert into public.pupeye_ban_events (
        ban_uuid, event_code, actor, detail, discord_delivery_status
    ) values (
        v_after.id,
        v_event_code,
        left(trim(p_actor), 80),
        jsonb_build_object(
            'previousKind', v_before.kind,
            'kind', v_after.kind,
            'previousExpiresAt', v_before.expires_at,
            'expiresAt', v_after.expires_at
        ),
        'pending'
    )
    returning id into v_event_id;

    return query select v_after.id, v_after.public_ban_id, v_event_id;
end;
$$;

create or replace function public.pupeye_revoke_global_ban(
    p_ban_uuid uuid,
    p_actor text,
    p_note text
)
returns table (
    ban_uuid uuid,
    public_ban_id text,
    event_id bigint
)
language plpgsql
security invoker
set search_path = public
as $$
declare
    v_ban public.pupeye_global_bans%rowtype;
    v_event_id bigint;
begin
    update public.pupeye_global_bans
    set status = 'revoked',
        revoked_at = now(),
        revoked_by = left(trim(p_actor), 80),
        support_note = coalesce(nullif(left(coalesce(p_note, ''), 1000), ''), support_note),
        updated_at = now()
    where id = p_ban_uuid
      and status = 'active'
    returning * into v_ban;

    if not found then
        raise exception 'active ban not found' using errcode = 'P0002';
    end if;

    insert into public.pupeye_ban_events (
        ban_uuid, event_code, actor, detail, discord_delivery_status
    ) values (
        v_ban.id,
        'GLOBAL_BAN_REVOKED',
        left(trim(p_actor), 80),
        jsonb_build_object('publicBanId', v_ban.public_ban_id),
        'pending'
    )
    returning id into v_event_id;

    return query select v_ban.id, v_ban.public_ban_id, v_event_id;
end;
$$;

revoke execute on function public.pupeye_resolve_global_ban(uuid,text,uuid,uuid,text)
    from public, anon, authenticated;
revoke execute on function public.pupeye_expire_due_bans()
    from public, anon, authenticated;
revoke execute on function public.pupeye_create_global_ban(text,text,text,text,timestamptz,text,text,jsonb)
    from public, anon, authenticated;
revoke execute on function public.pupeye_attach_ban_target(uuid,text,uuid,text,uuid,uuid,text,text,bigint)
    from public, anon, authenticated;
revoke execute on function public.pupeye_update_global_ban(uuid,text,timestamptz,text,text)
    from public, anon, authenticated;
revoke execute on function public.pupeye_revoke_global_ban(uuid,text,text)
    from public, anon, authenticated;

grant execute on function public.pupeye_resolve_global_ban(uuid,text,uuid,uuid,text)
    to service_role;
grant execute on function public.pupeye_expire_due_bans()
    to service_role;
grant execute on function public.pupeye_create_global_ban(text,text,text,text,timestamptz,text,text,jsonb)
    to service_role;
grant execute on function public.pupeye_attach_ban_target(uuid,text,uuid,text,uuid,uuid,text,text,bigint)
    to service_role;
grant execute on function public.pupeye_update_global_ban(uuid,text,timestamptz,text,text)
    to service_role;
grant execute on function public.pupeye_revoke_global_ban(uuid,text,text)
    to service_role;
