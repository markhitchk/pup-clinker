-- ADDITIVE STAGING MIGRATION. Do not apply to production before load/security sign-off.
-- All gameplay admission state lives in an unexposed schema. No access for public roles.
create schema if not exists pup_queue;
revoke all on schema pup_queue from public, anon, authenticated;
grant usage on schema pup_queue to service_role;

create table if not exists pup_queue.control (
 id smallint primary key default 1 check (id=1),
 mode text not null default 'disabled' check (mode in ('disabled','manual','automatic','maintenance')),
 capacity integer check (capacity is null or capacity between 1 and 200000),
 slots_used integer not null default 0 check (slots_used>=0),
 max_admits_per_minute integer check (max_admits_per_minute is null or max_admits_per_minute between 1 and 100000),
 admissions_in_minute integer not null default 0,
 minute_started_at timestamptz not null default now(),
 automatic_active boolean not null default false,
 health_checked_at timestamptz,
 health_score numeric,
 health_trustworthy boolean not null default false,
 activated_at timestamptz,
 changed_at timestamptz not null default now(),
 revision bigint not null default 1,
 constraint queue_slots_not_overbooked check (capacity is null or slots_used <= capacity)
);
insert into pup_queue.control(id,mode) values(1,'disabled') on conflict(id) do nothing;

create table if not exists pup_queue.tickets (
 id uuid primary key default gen_random_uuid(),
 sequence_id bigint generated always as identity unique,
 binding_hash text not null check (binding_hash ~ '^[a-f0-9]{64}$'),
 state text not null default 'WAITING'
   check (state in ('WAITING','READY','ADMITTED','EXPIRED','REVOKED')),
 created_at timestamptz not null default now(),
 last_seen_at timestamptz not null default now(),
 expires_at timestamptz not null default (now()+interval '30 minutes'),
 ready_until timestamptz,
 lease_until timestamptz,
 bound_player_uuid uuid references public.pupeye_players(id) on delete set null,
 bound_installation_uuid uuid references public.pupeye_installations(id) on delete set null,
 admitted_at timestamptz,
 released_at timestamptz
);
create unique index if not exists pup_queue_active_binding_idx on pup_queue.tickets(binding_hash)
 where state in ('WAITING','READY','ADMITTED');
create index if not exists pup_queue_fifo_idx on pup_queue.tickets(state,sequence_id)
 where state='WAITING';
create index if not exists pup_queue_lease_expiration_idx on pup_queue.tickets(state,lease_until)
 where state in ('READY','ADMITTED');
create index if not exists pup_queue_ticket_expiration_idx on pup_queue.tickets(expires_at);
create index if not exists pup_queue_installation_idx on pup_queue.tickets(bound_installation_uuid)
 where state='ADMITTED';

create table if not exists pup_queue.audit (
 id bigint generated always as identity primary key,
 at timestamptz not null default now(),
 actor_player uuid,
 action text not null,
 old_mode text,
 new_mode text,
 old_revision bigint,
 new_revision bigint,
 details jsonb not null default '{}'::jsonb
);
create index if not exists pup_queue_audit_created_idx on pup_queue.audit(at desc);

-- No Data API access: privileged Edge Functions use only explicitly service-role RPCs.
revoke all on all tables in schema pup_queue from public,anon,authenticated;
grant select,insert,update,delete on pup_queue.tickets to service_role;
grant select,update on pup_queue.control to service_role;
grant select,insert on pup_queue.audit to service_role;
grant usage,select on all sequences in schema pup_queue to service_role;
alter table pup_queue.control enable row level security;
alter table pup_queue.tickets enable row level security;
alter table pup_queue.audit enable row level security;
create policy queue_control_service_only on pup_queue.control to service_role using(true) with check(true);
create policy queue_tickets_service_only on pup_queue.tickets to service_role using(true) with check(true);
create policy queue_audit_service_only on pup_queue.audit to service_role using(true) with check(true);

-- A single control-row lock serializes all capacity reservations/claims.
-- Expiry can leave capacity underused but NEVER permits overbooking.
create or replace function public.pup_queue_dispatch(
 p_action text, p_binding_hash text, p_ticket uuid default null
) returns jsonb language plpgsql security definer
set search_path=pg_catalog as $$
declare
 c pup_queue.control%rowtype;
 t pup_queue.tickets%rowtype;
 t2 pup_queue.tickets%rowtype;
 v_now timestamptz:=clock_timestamp();
 n integer;
 promoted integer:=0;
 ahead integer;
 active_mode boolean;
 begin
 if p_action not in ('bootstrap','heartbeat','admit','release') or
    p_binding_hash is null or p_binding_hash !~ '^[a-f0-9]{64}$'
 then raise exception 'QUEUE_REQUEST_INVALID' using errcode='22023'; end if;
 select * into c from pup_queue.control where id=1 for update;
 if not found then raise exception 'QUEUE_CONTROL_UNAVAILABLE'; end if;
 if c.mode='disabled' then
  return jsonb_build_object('state','ALLOW','retryAfterMs',10000,'protocol',1);
 end if;
 if c.mode='maintenance' then
  return jsonb_build_object('state','MAINTENANCE','code','PC-Q007','retryAfterMs',30000);
 end if;
 if c.capacity is null or c.max_admits_per_minute is null then
  return jsonb_build_object('state','UNAVAILABLE','code','PC-Q005','retryAfterMs',30000);
 end if;
 -- Automatic admission is intentionally fail-closed without trusted FRESH
 -- operational health. No client-supplied load/capacity numbers are accepted.
 if c.mode='automatic' and (c.health_trustworthy is not true or
    c.health_checked_at is null or c.health_checked_at < v_now-interval '90 seconds')
 then return jsonb_build_object('state','UNAVAILABLE','code','PC-Q005','retryAfterMs',30000);
 end if;
 if c.mode='automatic' and c.automatic_active=false then
   return jsonb_build_object('state','ALLOW','retryAfterMs',10000,'protocol',1);
 end if;
 -- Retire a bounded number of expired reservations per call. The slot counter is
 -- decreased exactly once inside this serialized transaction.
 for t2 in select * from pup_queue.tickets
  where (state='READY' and ready_until < v_now)
     or (state='ADMITTED' and lease_until < v_now)
  order by coalesce(lease_until,ready_until),sequence_id limit 64 for update skip locked
 loop
  update pup_queue.tickets set state='EXPIRED',released_at=v_now where id=t2.id;
  update pup_queue.control set slots_used=greatest(0,slots_used-1) where id=1;
  c.slots_used:=greatest(0,c.slots_used-1);
 end loop;
 -- Expire abandoned waiting entries without spending a transaction per heartbeat.
 update pup_queue.tickets set state='EXPIRED',released_at=v_now
  where state='WAITING' and (expires_at<v_now or last_seen_at<v_now-interval '8 minutes')
   and sequence_id in (select sequence_id from pup_queue.tickets
      where state='WAITING' and (expires_at<v_now or last_seen_at<v_now-interval '8 minutes')
      order by sequence_id limit 64);
 if p_ticket is not null then
  select * into t from pup_queue.tickets where id=p_ticket for update;
  if not found or t.binding_hash<>p_binding_hash then
   return jsonb_build_object('state','REVOKED','code','PC-Q008','retryAfterMs',15000);
  end if;
 else
  select * into t from pup_queue.tickets
   where binding_hash=p_binding_hash and state in ('WAITING','READY','ADMITTED')
   for update;
 end if;
 if p_action='release' then
  if found and t.state in ('READY','ADMITTED') then
   update pup_queue.tickets set state='EXPIRED',released_at=v_now where id=t.id;
   update pup_queue.control set slots_used=slots_used-1 where id=1;
  elsif found and t.state='WAITING' then
   update pup_queue.tickets set state='EXPIRED',released_at=v_now where id=t.id;
  end if;
  return jsonb_build_object('state','RELEASED','retryAfterMs',10000);
 end if;
 if not found and p_action='bootstrap' then
  insert into pup_queue.tickets(binding_hash) values(p_binding_hash) returning * into t;
 elsif not found then
  return jsonb_build_object('state','EXPIRED','code','PC-Q003','retryAfterMs',10000);
 end if;
 if t.state='REVOKED' then
  return jsonb_build_object('state','REVOKED','code','PC-Q008','retryAfterMs',30000);
 end if;
 if t.state='EXPIRED' then
  return jsonb_build_object('state','EXPIRED','code','PC-Q003','retryAfterMs',15000);
 end if;
 if t.state='WAITING' and p_action<>'admit' and t.last_seen_at < v_now-interval '45 seconds' then
  update pup_queue.tickets set last_seen_at=v_now,expires_at=v_now+interval '30 minutes'
   where id=t.id;
 end if;
 if p_action='admit' and t.state='READY' and t.ready_until>=v_now then
  update pup_queue.tickets set state='ADMITTED',
   admitted_at=coalesce(admitted_at,v_now),lease_until=v_now+interval '30 minutes',
   last_seen_at=v_now where id=t.id;
  t.state:='ADMITTED';
  t.lease_until:=v_now+interval '30 minutes';
 end if;
 if c.minute_started_at<v_now-interval '1 minute' then
  update pup_queue.control set minute_started_at=v_now,admissions_in_minute=0 where id=1;
  c.admissions_in_minute:=0;
 end if;
 -- Oldest WAITING first, bounded promotions and serialized slot accounting.
 n:=least(greatest(c.capacity-c.slots_used,0),
       greatest(c.max_admits_per_minute-c.admissions_in_minute,0),32);
 if n>0 then
  for t2 in select * from pup_queue.tickets where state='WAITING'
   and expires_at>=v_now and last_seen_at>v_now-interval '8 minutes'
   order by sequence_id limit n for update skip locked
  loop
   update pup_queue.tickets set state='READY',ready_until=v_now+interval '2 minutes',
     lease_until=v_now+interval '2 minutes' where id=t2.id;
   promoted:=promoted+1;
  end loop;
  if promoted>0 then
   update pup_queue.control
    set slots_used=slots_used+promoted,
        admissions_in_minute=admissions_in_minute+promoted where id=1;
  end if;
 end if;
 select * into t from pup_queue.tickets where id=t.id;
 if t.state='WAITING' then
  select count(*)::integer into ahead from pup_queue.tickets
   where state='WAITING' and sequence_id<t.sequence_id;
 end if;
 return jsonb_build_object('state',t.state,'ticketId',t.id,
  'playersAhead',case when t.state='WAITING' then ahead else 0 end,
  'retryAfterMs',case when t.state='WAITING' then 12000 else 10000 end,
  'positionReliable',true,
  'leaseExpiresAt',case when t.state='ADMITTED' then extract(epoch from t.lease_until)*1000 else null end,
  'protocol',1);
end $$;
revoke all on function public.pup_queue_dispatch(text,text,uuid) from public,anon,authenticated;
grant execute on function public.pup_queue_dispatch(text,text,uuid) to service_role;

-- Low-cost polling never locks the global capacity-control row and never
-- writes to Postgres. Only every fourth/fifth poll needs a heartbeat.
create or replace function public.pup_queue_status(
 p_binding_hash text,p_ticket uuid
) returns jsonb language plpgsql security definer
set search_path=pg_catalog as $$
declare c pup_queue.control%rowtype; t pup_queue.tickets%rowtype; ahead integer;
begin
 if p_binding_hash is null or length(p_binding_hash)<>64 or p_binding_hash !~ '^[a-f0-9]{64}' then
  raise exception 'QUEUE_PROOF_INVALID' using errcode='22023';end if;
 select * into c from pup_queue.control where id=1;
 if c.mode='disabled' or (c.mode='automatic' and c.automatic_active=false
    and c.health_trustworthy=true and c.health_checked_at>clock_timestamp()-interval '90 seconds')
 then return jsonb_build_object('state','ALLOW','retryAfterMs',10000);end if;
 if c.mode='maintenance' then
  return jsonb_build_object('state','MAINTENANCE','code','PC-Q007','retryAfterMs',30000);end if;
 if c.mode='automatic' and (c.health_trustworthy=false or c.health_checked_at is null or
    c.health_checked_at<clock_timestamp()-interval '90 seconds')
 then return jsonb_build_object('state','UNAVAILABLE','code','PC-Q005','retryAfterMs',30000);end if;
 if p_ticket is null then
  return jsonb_build_object('state','EXPIRED','code','PC-Q003','retryAfterMs',15000);end if;
 select * into t from pup_queue.tickets where id=p_ticket;
 if not found or t.binding_hash<>p_binding_hash then
  return jsonb_build_object('state','REVOKED','code','PC-Q008','retryAfterMs',15000);end if;
 if t.state='WAITING' then
  select count(*)::integer into ahead from pup_queue.tickets
   where state='WAITING' and sequence_id<t.sequence_id;
 end if;
 return jsonb_build_object('state',t.state,'ticketId',t.id,
   'playersAhead',case when t.state='WAITING' then ahead else 0 end,
   'retryAfterMs',case when t.state='WAITING' then 12000 else 10000 end,
   'protocol',1);
end $$;
revoke all on function public.pup_queue_status(text,uuid) from public,anon,authenticated;
grant execute on function public.pup_queue_status(text,uuid) to service_role;
-- Binding is established only AFTER PupEye authenticates the same installation
-- that owns the signing key. Anonymous tickets convey zero player permissions.
create or replace function public.pup_queue_guard(
 p_ticket uuid, p_binding_hash text, p_player uuid, p_installation uuid,
 p_touch boolean default true
) returns boolean language plpgsql security definer
set search_path=pg_catalog as $$
declare c pup_queue.control%rowtype; t pup_queue.tickets%rowtype;
begin
 select * into c from pup_queue.control where id=1;
 if c.mode='disabled' then return true; end if;
 if c.mode='maintenance' then raise exception 'QUEUE_MAINTENANCE' using errcode='P0001';end if;
 if p_ticket is null or p_binding_hash is null or p_player is null or p_installation is null
 then raise exception 'QUEUE_ADMISSION_REQUIRED' using errcode='P0001'; end if;
 select * into t from pup_queue.tickets where id=p_ticket for update;
 if not found or t.binding_hash<>p_binding_hash or t.state<>'ADMITTED' or
    t.lease_until<=clock_timestamp()
 then raise exception 'QUEUE_ADMISSION_INVALID' using errcode='P0001'; end if;
 if t.bound_player_uuid is not null and t.bound_player_uuid<>p_player then
  raise exception 'QUEUE_ACCOUNT_MISMATCH' using errcode='P0001';end if;
 if t.bound_installation_uuid is not null and t.bound_installation_uuid<>p_installation then
  raise exception 'QUEUE_INSTALLATION_MISMATCH' using errcode='P0001';end if;
 if p_touch and (t.last_seen_at<clock_timestamp()-interval '45 seconds' or
   t.bound_player_uuid is null or t.bound_installation_uuid is null) then
  update pup_queue.tickets set last_seen_at=clock_timestamp(),
   lease_until=clock_timestamp()+interval '30 minutes',
   bound_player_uuid=p_player,bound_installation_uuid=p_installation
   where id=t.id;
 end if;
 return true;
end $$;
revoke all on function public.pup_queue_guard(uuid,text,uuid,uuid,boolean) from public,anon,authenticated;
grant execute on function public.pup_queue_guard(uuid,text,uuid,uuid,boolean) to service_role;

-- Only the role-restricted admin Edge Function may invoke this privileged RPC.
create or replace function public.pup_queue_admin_update(
 p_actor uuid,p_mode text,p_capacity integer,p_rate integer
) returns jsonb language plpgsql security definer set search_path=pg_catalog as $$
declare c pup_queue.control%rowtype; revised bigint;
begin
 if p_actor is null or p_mode not in ('disabled','manual','automatic','maintenance')
 then raise exception 'QUEUE_ADMIN_INVALID' using errcode='22023';end if;
 select * into c from pup_queue.control where id=1 for update;
 if p_mode in ('manual','automatic') and
  (p_capacity is null or p_capacity<1 or p_rate is null or p_rate<1)
 then raise exception 'QUEUE_CAPACITY_UNMEASURED' using errcode='22023';end if;
 if p_capacity is not null and (p_capacity>200000 or p_capacity<c.slots_used)
 then raise exception 'QUEUE_CAPACITY_BELOW_ACTIVE_LEASES' using errcode='22023';end if;
 if p_rate is not null and p_rate>100000 then raise exception 'QUEUE_RATE_INVALID' using errcode='22023';end if;
 revised:=c.revision+1;
 update pup_queue.control set mode=p_mode,capacity=p_capacity,
   max_admits_per_minute=p_rate,revision=revised,changed_at=clock_timestamp()
   where id=1;
 insert into pup_queue.audit(actor_player,action,old_mode,new_mode,
  old_revision,new_revision,details) values
  (p_actor,'QUEUE_CONFIG_CHANGE',c.mode,p_mode,c.revision,revised,
   jsonb_build_object('capacity_changed',c.capacity is distinct from p_capacity,
                     'rate_changed',c.max_admits_per_minute is distinct from p_rate));
 return jsonb_build_object('ok',true,'revision',revised,'mode',p_mode);
end $$;
revoke all on function public.pup_queue_admin_update(uuid,text,integer,integer) from public,anon,authenticated;
grant execute on function public.pup_queue_admin_update(uuid,text,integer,integer) to service_role;

comment on schema pup_queue is 'PRIVATE stage-only admission control. Never expose via Data API.';
