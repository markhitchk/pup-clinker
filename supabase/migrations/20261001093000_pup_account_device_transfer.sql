-- Account-native device transfer for Pup Account.
-- Discord OAuth proves account ownership; PupEye signed sessions prove the requesting installation.
-- No save file, portable backup, or client-held server secret participates in this transfer.

create or replace function public.pup_account_transfer_device(
    p_source_player_uuid uuid,
    p_source_installation_uuid uuid,
    p_target_player_uuid uuid,
    p_target_account_uuid uuid,
    p_cloud_generation bigint
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_now timestamptz := now();
    v_source_player uuid;
    v_target_account_player uuid;
    v_target_active_installation uuid;
    v_current_max_generation bigint;
begin
    if p_source_player_uuid = p_target_player_uuid then
        raise exception 'source and target player are already the same';
    end if;

    select player_uuid, max_save_generation
      into v_source_player, v_current_max_generation
      from public.pupeye_installations
     where id = p_source_installation_uuid
       and active = true
       and revoked_at is null
     for update;

    if v_source_player is null or v_source_player <> p_source_player_uuid then
        raise exception 'requesting installation is not active for the source player';
    end if;

    select player_uuid
      into v_target_account_player
      from public.pup_accounts
     where id = p_target_account_uuid
       and status = 'active'
     for update;

    if v_target_account_player is null or v_target_account_player <> p_target_player_uuid then
        raise exception 'target Pup Account does not match the target player';
    end if;

    if exists (
        select 1 from public.pup_accounts
         where player_uuid = p_source_player_uuid
           and id <> p_target_account_uuid
    ) then
        raise exception 'requesting player already owns a different Pup Account';
    end if;

    select id
      into v_target_active_installation
      from public.pupeye_installations
     where player_uuid = p_target_player_uuid
       and active = true
       and revoked_at is null
       and id <> p_source_installation_uuid
     for update;

    if v_target_active_installation is not null then
        update public.pupeye_sessions
           set revoked_at = v_now
         where installation_uuid = v_target_active_installation
           and revoked_at is null;

        update public.pupeye_installations
           set active = false,
               revoked_at = v_now,
               last_seen_at = v_now
         where id = v_target_active_installation;
    end if;

    update public.pupeye_sessions
       set revoked_at = v_now
     where installation_uuid = p_source_installation_uuid
       and revoked_at is null;

    delete from public.pupeye_identity_links
     where link_type = 'PLAYER_INSTALLATION'
       and installation_uuid = p_source_installation_uuid;

    update public.pupeye_installations
       set player_uuid = p_target_player_uuid,
           active = true,
           revoked_at = null,
           max_save_generation = greatest(
               coalesce(v_current_max_generation, 0),
               greatest(coalesce(p_cloud_generation, 0), 0)
           ),
           last_seen_at = v_now
     where id = p_source_installation_uuid;

    insert into public.pupeye_identity_links (
        link_type,
        player_uuid,
        installation_uuid,
        confidence,
        evidence_code
    )
    values (
        'PLAYER_INSTALLATION',
        p_target_player_uuid,
        p_source_installation_uuid,
        'authoritative',
        'PUP_ACCOUNT_DISCORD_TRANSFER'
    )
    on conflict do nothing;

    update public.pup_accounts
       set active_installation_uuid = p_source_installation_uuid,
           updated_at = v_now
     where id = p_target_account_uuid;

    -- Keep the cloud payload/revision untouched; only move the authorized installation pointer.
    update public.pup_account_saves
       set installation_uuid = p_source_installation_uuid
     where account_uuid = p_target_account_uuid
       and player_uuid = p_target_player_uuid;

    insert into public.pupeye_save_heads (
        installation_uuid,
        generation,
        support_code,
        last_reason,
        hard_flags,
        updated_at
    )
    select
        p_source_installation_uuid,
        greatest(coalesce(p_cloud_generation, 0), 0),
        i.support_code,
        'pup-account-device-transfer',
        '{}',
        v_now
      from public.pupeye_installations i
     where i.id = p_source_installation_uuid
    on conflict (installation_uuid) do update
       set generation = greatest(
               public.pupeye_save_heads.generation,
               excluded.generation
           ),
           support_code = excluded.support_code,
           last_reason = excluded.last_reason,
           updated_at = excluded.updated_at;
end;
$$;

revoke all on function public.pup_account_transfer_device(uuid, uuid, uuid, uuid, bigint)
    from public, anon, authenticated;
grant execute on function public.pup_account_transfer_device(uuid, uuid, uuid, uuid, bigint)
    to service_role;
