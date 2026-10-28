begin;
create extension if not exists pgtap with schema extensions;
select plan(21);

select ok(to_regclass('pup_queue.tickets') is not null,'private ticket table exists');
select ok(to_regclass('pup_queue.control') is not null,'private capacity control exists');
select ok((select relrowsecurity from pg_class where oid='pup_queue.tickets'::regclass),
 'queue tickets have RLS enabled');
select ok(not has_function_privilege('anon','public.pup_queue_dispatch(text,text,uuid)','EXECUTE'),
 'anonymous clients cannot call privileged reservation RPC');
select ok(not has_function_privilege('authenticated','public.pup_queue_guard(uuid,text,uuid,uuid,boolean,uuid)','EXECUTE'),
 'signed client role cannot forge admission RPC');

select is((public.pup_queue_dispatch('bootstrap',repeat('a',64))->>'state'),'ALLOW',
 'disabled mode transparently allows startup');
update pup_queue.control set mode='manual',capacity=2,max_admits_per_minute=50,
 slots_used=0,admissions_in_minute=0,minute_started_at=now() where id=1;
select is((public.pup_queue_dispatch('bootstrap',repeat('a',64))->>'state'),'READY',
 'first ticket is ready');
select is((public.pup_queue_dispatch('bootstrap',repeat('b',64))->>'state'),'READY',
 'second ticket is ready');
select is((public.pup_queue_dispatch('bootstrap',repeat('c',64))->>'state'),'WAITING',
 'third ticket waits behind capacity');
select is((select slots_used from pup_queue.control where id=1),2,
 'capacity does not overbook with three arrivals');
select is((public.pup_queue_dispatch('admit',repeat('a',64),
 (select id from pup_queue.tickets where binding_hash=repeat('a',64) and state='READY'))->>'state'),
 'ADMITTED','ready ticket can claim its lease once');
select is((public.pup_queue_dispatch('admit',repeat('a',64),
 (select id from pup_queue.tickets where binding_hash=repeat('a',64) and state='ADMITTED'))->>'state'),
 'ADMITTED','repeated admit returns same reservation, not a second slot');
select is((select slots_used from pup_queue.control where id=1),2,
 'replayed admission does not consume another slot');
select throws_ok($$select public.pup_queue_dispatch('status',repeat('a',64),
 (select id from pup_queue.tickets where binding_hash=repeat('a',64)))$$,
 '22023','QUEUE_REQUEST_INVALID',
 'capacity RPC rejects status polling; status has separate read-only path');
select is((public.pup_queue_status(repeat('c',64),
 (select id from pup_queue.tickets where binding_hash=repeat('c',64)))->>'state'),'WAITING',
 'read-only status preserves waiting order');
select is((public.pup_queue_dispatch('release',repeat('a',64),
 (select id from pup_queue.tickets where binding_hash=repeat('a',64)))->>'state'),'RELEASED',
 'release closes the admitted slot');
select is((public.pup_queue_dispatch('heartbeat',repeat('c',64),
 (select id from pup_queue.tickets where binding_hash=repeat('c',64)))->>'state'),'READY',
 'oldest waiting player advances into released capacity');

-- Incumbent signed sessions may finish saves during high traffic, but a
-- newly issued or revoked session does NOT bypass new-arrival admission.
insert into public.pupeye_players(id,player_id,friend_code,username)
 values('f0000000-0000-4000-8000-000000000084','QUEUE-TEST-84','QUEUE-TEST-84','queue-test');
insert into public.pupeye_installations(
 id,player_uuid,installation_id,device_key_id,public_key_b64,support_code)
 values('f0000000-0000-4000-8000-000000000085',
 'f0000000-0000-4000-8000-000000000084',
 'f0000000-0000-4000-8000-000000000088',
 repeat('d',64),'QUEUE_TEST_PUBLIC_KEY','Q84-TEST');
insert into public.pupeye_sessions(
 id,player_uuid,installation_uuid,token_hash,created_at,expires_at)
 values
 ('f0000000-0000-4000-8000-000000000086',
  'f0000000-0000-4000-8000-000000000084',
  'f0000000-0000-4000-8000-000000000085',
  repeat('a',64),clock_timestamp()-interval '5 minutes',clock_timestamp()+interval '2 hours'),
 ('f0000000-0000-4000-8000-000000000087',
  'f0000000-0000-4000-8000-000000000084',
  'f0000000-0000-4000-8000-000000000085',
  repeat('b',64),clock_timestamp()+interval '5 minutes',clock_timestamp()+interval '2 hours');
update pup_queue.control set mode='disabled' where id=1;
select ok((public.pup_queue_admin_update(
 'f0000000-0000-4000-8000-000000000084','manual',2,50)->>'ok')::boolean,
 'transition to manual mode stamps an activation cutoff');
select ok(public.pup_queue_guard(null,repeat('d',64),
 'f0000000-0000-4000-8000-000000000084',
 'f0000000-0000-4000-8000-000000000085',true,
 'f0000000-0000-4000-8000-000000000086'),
 'valid pre-activation PupEye session retains save continuity');
select throws_ok($guard$select public.pup_queue_guard(null,repeat('d',64),
 'f0000000-0000-4000-8000-000000000084',
 'f0000000-0000-4000-8000-000000000085',true,
 'f0000000-0000-4000-8000-000000000087')$guard$,
 'P0001','QUEUE_ADMISSION_REQUIRED',
 'sessions issued after activation cannot bypass queue');
update public.pupeye_sessions set revoked_at=clock_timestamp()
 where id='f0000000-0000-4000-8000-000000000086';
select throws_ok($guard$select public.pup_queue_guard(null,repeat('d',64),
 'f0000000-0000-4000-8000-000000000084',
 'f0000000-0000-4000-8000-000000000085',true,
 'f0000000-0000-4000-8000-000000000086')$guard$,
 'P0001','QUEUE_ADMISSION_REQUIRED',
 'revoking a pre-activation session removes the continuity allowance');

select * from finish();
rollback;
