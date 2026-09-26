-- Align production and fresh-environment PupEye ban ID generation.
-- PostgreSQL 17 provides gen_random_uuid() without requiring pgcrypto's gen_random_bytes().

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
