-- STAGING ONLY. Trusted metrics and role-scoped auto-mode setup.
alter table pup_queue.control add column if not exists high_pressure numeric;
alter table pup_queue.control add column if not exists low_pressure numeric;
alter table pup_queue.control add column if not exists high_samples integer not null default 0;
alter table pup_queue.control add column if not exists low_samples integer not null default 0;
alter table pup_queue.control add column if not exists minimum_hold_seconds integer not null default 120;

create or replace function public.pup_queue_guard(
 p_ticket uuid, p_binding_hash text, p_player uuid, p_installation uuid,
 p_touch boolean default true
) returns boolean language plpgsql security definer
set search_path=pg_catalog as $$
declare c pup_queue.control%rowtype; t pup_queue.tickets%rowtype;
begin
 select * into c from pup_queue.control where id=1;
 if c.mode='disabled' then return true; end if;
 if c.mode='automatic' and c.automatic_active=false and c.health_trustworthy=true
   and c.health_checked_at>clock_timestamp()-interval '90 seconds'
 then return true;end if;
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


create or replace function public.pup_queue_admin_status()
returns jsonb language plpgsql security definer set search_path=pg_catalog as $$
declare c pup_queue.control%rowtype; waiting integer;
begin
 select * into c from pup_queue.control where id=1;
 select count(*)::integer into waiting from pup_queue.tickets where state='WAITING';
 return jsonb_build_object('mode',c.mode,'capacity',c.capacity,
  'slots_used',c.slots_used,'max_admits_per_minute',c.max_admits_per_minute,
  'automatic_active',c.automatic_active,'health_checked_at',c.health_checked_at,
  'health_trustworthy',c.health_trustworthy,'high_pressure',c.high_pressure,
  'low_pressure',c.low_pressure,'waiting',waiting,'revision',c.revision);
end $$;
revoke all on function public.pup_queue_admin_status() from public,anon,authenticated;
grant execute on function public.pup_queue_admin_status() to service_role;

create or replace function public.pup_queue_admin_thresholds(
 p_actor uuid,p_high numeric,p_low numeric,p_hold_seconds integer default 120
) returns jsonb language plpgsql security definer set search_path=pg_catalog as $$
declare c pup_queue.control%rowtype;
begin
 if p_actor is null or p_high is null or p_low is null or p_high<=p_low
  or p_high>1 or p_low<0 or p_hold_seconds not between 30 and 3600
 then raise exception 'QUEUE_THRESHOLDS_INVALID' using errcode='22023';end if;
 select * into c from pup_queue.control where id=1 for update;
 update pup_queue.control set high_pressure=p_high,low_pressure=p_low,
  minimum_hold_seconds=p_hold_seconds,high_samples=0,low_samples=0,
  health_trustworthy=false,revision=revision+1,changed_at=clock_timestamp() where id=1;
 insert into pup_queue.audit(actor_player,action,old_mode,new_mode,old_revision,new_revision)
 values(p_actor,'QUEUE_THRESHOLDS_CHANGED',c.mode,c.mode,c.revision,c.revision+1);
 return jsonb_build_object('ok',true,'revision',c.revision+1);
end $$;
revoke all on function public.pup_queue_admin_thresholds(uuid,numeric,numeric,integer) from public,anon,authenticated;
grant execute on function public.pup_queue_admin_thresholds(uuid,numeric,numeric,integer) to service_role;

create or replace function public.pup_queue_health_report(
 p_score numeric,p_trustworthy boolean
) returns jsonb language plpgsql security definer set search_path=pg_catalog as $$
declare c pup_queue.control%rowtype; at_time timestamptz:=clock_timestamp();
 high_count integer; low_count integer; active boolean;
begin
 if p_score is null or p_score<0 or p_score>1
 then raise exception 'QUEUE_HEALTH_INVALID' using errcode='22023';end if;
 select * into c from pup_queue.control where id=1 for update;
 high_count:=case when p_trustworthy and c.high_pressure is not null and
  p_score>=c.high_pressure then c.high_samples+1 else 0 end;
 low_count:=case when p_trustworthy and c.low_pressure is not null and
  p_score<=c.low_pressure then c.low_samples+1 else 0 end;
 active:=c.automatic_active;
 if p_trustworthy and c.high_pressure is not null and c.low_pressure is not null and
   (c.activated_at is null or at_time>=c.activated_at+make_interval(secs=>c.minimum_hold_seconds))
 then
  if not active and high_count>=3 then active:=true;end if;
  if active and low_count>=3 then active:=false;end if;
 end if;
 update pup_queue.control set health_checked_at=at_time,health_score=p_score,
  health_trustworthy=p_trustworthy,high_samples=high_count,low_samples=low_count,
  automatic_active=active,
  activated_at=case when active is distinct from c.automatic_active then at_time else c.activated_at end
 where id=1;
 return jsonb_build_object('ok',true,'automaticActive',active);
end $$;
revoke all on function public.pup_queue_health_report(numeric,boolean) from public,anon,authenticated;
grant execute on function public.pup_queue_health_report(numeric,boolean) to service_role;
