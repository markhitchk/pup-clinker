-- Realtime invalidation for Pup Account state.
-- Supabase remains authoritative. Clients receive only opaque change signals and
-- re-read protected account/save data through authenticated Edge Functions.

alter table public.pup_accounts
  add column if not exists realtime_topic uuid default gen_random_uuid();

update public.pup_accounts
set realtime_topic = gen_random_uuid()
where realtime_topic is null;

alter table public.pup_accounts
  alter column realtime_topic set default gen_random_uuid(),
  alter column realtime_topic set not null;

create unique index if not exists pup_accounts_realtime_topic_uidx
  on public.pup_accounts(realtime_topic);

comment on column public.pup_accounts.realtime_topic is
  'Opaque capability-style topic used only for non-sensitive Supabase Realtime invalidation events.';

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

  select account.realtime_topic
    into v_topic
  from public.pup_accounts as account
  where account.player_uuid = v_player_uuid;

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

drop trigger if exists pup_account_saves_realtime on public.pup_account_saves;
create trigger pup_account_saves_realtime
after insert or update or delete on public.pup_account_saves
for each row execute function public.pup_account_emit_realtime();

drop trigger if exists pup_accounts_realtime on public.pup_accounts;
create trigger pup_accounts_realtime
after insert or update or delete on public.pup_accounts
for each row execute function public.pup_account_emit_realtime();

drop trigger if exists pupeye_players_account_realtime on public.pupeye_players;
create trigger pupeye_players_account_realtime
after insert or update or delete on public.pupeye_players
for each row execute function public.pup_account_emit_realtime();

drop trigger if exists pup_account_devices_realtime on public.pup_account_devices;
create trigger pup_account_devices_realtime
after insert or update or delete on public.pup_account_devices
for each row execute function public.pup_account_emit_realtime();
