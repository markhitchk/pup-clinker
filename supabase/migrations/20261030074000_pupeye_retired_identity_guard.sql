-- Puppy Clicker / PupEye: durable retirement of deleted Player IDs and Friend Codes.
-- Physical table is puppy_pupeye.pupeye_players; public.pupeye_players is an updatable view.
-- Historical identity values are seeded privately in the live project and intentionally
-- omitted from this public source-controlled migration.
create table if not exists puppy_pupeye.retired_player_identifiers (
  identifier_type text not null check (identifier_type in ('player_id', 'friend_code')),
  identifier text not null check (identifier = upper(btrim(identifier)) and identifier <> ''),
  deleted_player_uuid uuid,
  retired_at timestamptz not null default now(),
  source text not null default 'pupeye_players_delete',
  primary key (identifier_type, identifier)
);
revoke all on puppy_pupeye.retired_player_identifiers from public, anon, authenticated, service_role;
alter table puppy_pupeye.retired_player_identifiers enable row level security;
comment on table puppy_pupeye.retired_player_identifiers is
  'Private Puppy Clicker/PupEye-only identity tombstones. Never auto-clear or reuse retired identifiers.';

create or replace function puppy_pupeye.capture_deleted_pupeye_identity()
returns trigger language plpgsql security definer
set search_path = pg_catalog, puppy_pupeye as $fn$
begin
  insert into puppy_pupeye.retired_player_identifiers
    (identifier_type, identifier, deleted_player_uuid, source)
  select v.identifier_type, upper(btrim(v.identifier)), old.id, 'pupeye_players_delete'
  from (values ('player_id', old.player_id), ('friend_code', old.friend_code))
       as v(identifier_type, identifier)
  where v.identifier is not null and btrim(v.identifier) <> ''
  on conflict (identifier_type, identifier) do nothing;
  return old;
end;
$fn$;

create or replace function puppy_pupeye.reject_retired_pupeye_identity()
returns trigger language plpgsql security definer
set search_path = pg_catalog, puppy_pupeye as $fn$
begin
  if tg_op = 'INSERT'
      or (tg_op = 'UPDATE' and new.player_id is distinct from old.player_id) then
    if exists (select 1 from puppy_pupeye.retired_player_identifiers
               where identifier_type = 'player_id'
                 and identifier = upper(btrim(new.player_id))) then
      raise exception using errcode = '23514', message = 'PUPEYE_PLAYER_ID_RETIRED';
    end if;
  end if;
  if tg_op = 'INSERT'
      or (tg_op = 'UPDATE' and new.friend_code is distinct from old.friend_code) then
    if exists (select 1 from puppy_pupeye.retired_player_identifiers
               where identifier_type = 'friend_code'
                 and identifier = upper(btrim(new.friend_code))) then
      raise exception using errcode = '23514', message = 'PUPEYE_FRIEND_CODE_RETIRED';
    end if;
  end if;
  return new;
end;
$fn$;

drop trigger if exists pupeye_capture_deleted_identity on puppy_pupeye.pupeye_players;
create trigger pupeye_capture_deleted_identity before delete
on puppy_pupeye.pupeye_players for each row
execute function puppy_pupeye.capture_deleted_pupeye_identity();

drop trigger if exists pupeye_reject_retired_identity on puppy_pupeye.pupeye_players;
create trigger pupeye_reject_retired_identity before insert or update of player_id, friend_code
on puppy_pupeye.pupeye_players for each row
execute function puppy_pupeye.reject_retired_pupeye_identity();

-- TRUNCATE bypasses DELETE triggers and RLS; only trusted DB administration may run it.
revoke truncate on table puppy_pupeye.pupeye_players from anon, authenticated, service_role;
