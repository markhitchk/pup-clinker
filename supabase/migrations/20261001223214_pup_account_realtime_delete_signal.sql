-- Preserve the opaque Pup Account realtime topic while deleting the account row.
-- This lets connected clients receive the final account invalidation without exposing row data.

create or replace function public.pup_account_emit_realtime()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public, realtime
as $$
declare
  v_player_uuid uuid;
  v_topic uuid;
  v_kind text;
  v_revision bigint;
  v_generation bigint;
  v_changed_at timestamptz;
begin
  if tg_table_name = 'pup_account_saves' then
    v_player_uuid := case when tg_op = 'DELETE' then old.player_uuid else new.player_uuid end;
    v_kind := 'save';
    v_revision := case when tg_op = 'DELETE' then old.revision else new.revision end;
    v_generation := case when tg_op = 'DELETE' then old.generation else new.generation end;
    v_changed_at := case when tg_op = 'DELETE' then old.updated_at else new.updated_at end;
  elsif tg_table_name = 'pup_accounts' then
    v_player_uuid := case when tg_op = 'DELETE' then old.player_uuid else new.player_uuid end;
    v_topic := case when tg_op = 'DELETE' then old.realtime_topic else new.realtime_topic end;
    v_kind := 'account';
    v_changed_at := case when tg_op = 'DELETE' then old.updated_at else new.updated_at end;
  elsif tg_table_name = 'pupeye_players' then
    v_player_uuid := case when tg_op = 'DELETE' then old.id else new.id end;
    v_kind := 'identity';
    v_changed_at := case when tg_op = 'DELETE' then old.updated_at else new.updated_at end;
  elsif tg_table_name = 'pup_account_devices' then
    v_player_uuid := case when tg_op = 'DELETE' then old.player_uuid else new.player_uuid end;
    v_kind := 'device';
    v_changed_at := case when tg_op = 'DELETE' then old.last_seen_at else new.last_seen_at end;
  else
    if tg_op = 'DELETE' then return old; else return new; end if;
  end if;

  if v_topic is null then
    select account.realtime_topic
      into v_topic
    from public.pup_accounts as account
    where account.player_uuid = v_player_uuid;
  end if;

  if v_topic is not null then
    perform realtime.send(
      jsonb_strip_nulls(
        jsonb_build_object(
          'kind', v_kind,
          'operation', tg_op,
          'revision', v_revision,
          'generation', v_generation,
          'updatedAt', coalesce(v_changed_at, clock_timestamp())
        )
      ),
      'changed',
      'pup-account:' || v_topic::text,
      false
    );
  end if;

  if tg_op = 'DELETE' then return old; else return new; end if;
end;
$$;

revoke all on function public.pup_account_emit_realtime() from public;
revoke all on function public.pup_account_emit_realtime() from anon;
revoke all on function public.pup_account_emit_realtime() from authenticated;
