-- ADDITIVE STAGING FIX: preserve authenticated sessions established before queue activation.
-- This does not admit new logins, does not bypass existing PupEye bans, and expires
-- naturally with the original session token. Not approved for production rollout.
--
-- Replace the five-argument guard instead of leaving an unreviewed public
-- security-definer overload available to service-role callers.
drop function if exists public.pup_queue_guard(uuid,text,uuid,uuid,boolean);

create function public.pup_queue_guard(
 p_ticket uuid, p_binding_hash text, p_player uuid, p_installation uuid,
 p_touch boolean, p_session uuid
) returns boolean language plpgsql security definer
set search_path=pg_catalog as $$
declare
 c pup_queue.control%rowtype;
 t pup_queue.tickets%rowtype;
 v_now timestamptz:=clock_timestamp();
 v_incumbent boolean:=false;
begin
 select * into c from pup_queue.control where id=1;
 if not found then raise exception 'QUEUE_CONTROL_UNAVAILABLE' using errcode='P0001';end if;
 if c.mode='disabled' then return true;end if;
 if c.mode='maintenance' then raise exception 'QUEUE_MAINTENANCE' using errcode='P0001';end if;
 if c.mode='automatic' and c.automatic_active=false and
    c.health_trustworthy is true and c.health_checked_at>v_now-interval '90 seconds'
 then return true;end if;

 -- The Edge Function has already verified the signed PupEye envelope, token,
 -- account, active installation and ban checks. We still verify the actual
 -- session record and matching ownership in the DB; never trust client times.
 -- Only tokens issued BEFORE this activation may complete their cloud writes.
 -- The exception expires with the existing session and cannot be renewed
 -- by obtaining a new token while admission is active.
 if p_session is not null and c.activated_at is not null then
  select exists(
   select 1 from public.pupeye_sessions s
   where s.id=p_session and s.player_uuid=p_player and
    s.installation_uuid=p_installation and s.created_at<=c.activated_at
    and s.expires_at>v_now and s.revoked_at is null
  ) into v_incumbent;
  if v_incumbent then return true;end if;
 end if;

 if p_ticket is null or p_binding_hash is null or p_player is null or p_installation is null
 then raise exception 'QUEUE_ADMISSION_REQUIRED' using errcode='P0001';end if;
 select * into t from pup_queue.tickets where id=p_ticket for update;
 if not found or t.binding_hash<>p_binding_hash or t.state<>'ADMITTED' or
    t.lease_until<=v_now
 then raise exception 'QUEUE_ADMISSION_INVALID' using errcode='P0001';end if;
 if t.bound_player_uuid is not null and t.bound_player_uuid<>p_player
 then raise exception 'QUEUE_ACCOUNT_MISMATCH' using errcode='P0001';end if;
 if t.bound_installation_uuid is not null and t.bound_installation_uuid<>p_installation
 then raise exception 'QUEUE_INSTALLATION_MISMATCH' using errcode='P0001';end if;
 if p_touch and (t.last_seen_at<v_now-interval '45 seconds' or
    t.bound_player_uuid is null or t.bound_installation_uuid is null) then
  update pup_queue.tickets set last_seen_at=v_now,
   lease_until=v_now+interval '30 minutes',
   bound_player_uuid=p_player,bound_installation_uuid=p_installation
   where id=t.id;
 end if;
 return true;
end $$;

revoke all on function public.pup_queue_guard(uuid,text,uuid,uuid,boolean,uuid)
 from public,anon,authenticated;
grant execute on function public.pup_queue_guard(uuid,text,uuid,uuid,boolean,uuid)
 to service_role;

-- Explicit developer-controlled mode transitions mark the cutoff against
-- existing sessions, without resetting the cutoff on routine capacity edits.
create or replace function public.pup_queue_admin_update(
 p_actor uuid,p_mode text,p_capacity integer,p_rate integer
) returns jsonb language plpgsql security definer set search_path=pg_catalog as $$
declare c pup_queue.control%rowtype; revised bigint; v_now timestamptz:=clock_timestamp();
begin
 if p_actor is null or p_mode not in ('disabled','manual','automatic','maintenance')
 then raise exception 'QUEUE_ADMIN_INVALID' using errcode='22023';end if;
 select * into c from pup_queue.control where id=1 for update;
 if not found then raise exception 'QUEUE_CONTROL_UNAVAILABLE' using errcode='P0001';end if;
 if p_mode in ('manual','automatic') and
  (p_capacity is null or p_capacity<1 or p_rate is null or p_rate<1)
 then raise exception 'QUEUE_CAPACITY_UNMEASURED' using errcode='22023';end if;
 if p_capacity is not null and (p_capacity>200000 or p_capacity<c.slots_used)
 then raise exception 'QUEUE_CAPACITY_BELOW_ACTIVE_LEASES' using errcode='22023';end if;
 if p_rate is not null and p_rate>100000
 then raise exception 'QUEUE_RATE_INVALID' using errcode='22023';end if;
 revised:=c.revision+1;
 update pup_queue.control set mode=p_mode,capacity=p_capacity,
   max_admits_per_minute=p_rate,revision=revised,changed_at=v_now,
   activated_at=case when p_mode is distinct from c.mode then v_now
                     else c.activated_at end
 where id=1;
 insert into pup_queue.audit(actor_player,action,old_mode,new_mode,
  old_revision,new_revision,details) values
  (p_actor,'QUEUE_CONFIG_CHANGE',c.mode,p_mode,c.revision,revised,
   jsonb_build_object('capacity_changed',c.capacity is distinct from p_capacity,
                     'rate_changed',c.max_admits_per_minute is distinct from p_rate));
 return jsonb_build_object('ok',true,'revision',revised,'mode',p_mode);
end $$;
revoke all on function public.pup_queue_admin_update(uuid,text,integer,integer)
 from public,anon,authenticated;
grant execute on function public.pup_queue_admin_update(uuid,text,integer,integer)
 to service_role;
