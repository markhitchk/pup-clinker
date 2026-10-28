begin;
create extension if not exists pgtap with schema extensions;
select plan(17);

select ok(to_regclass('pup_queue.tickets') is not null,'private ticket table exists');
select ok(to_regclass('pup_queue.control') is not null,'private capacity control exists');
select ok((select relrowsecurity from pg_class where oid='pup_queue.tickets'::regclass),
 'queue tickets have RLS enabled');
select ok(not has_function_privilege('anon','public.pup_queue_dispatch(text,text,uuid)','EXECUTE'),
 'anonymous clients cannot call privileged reservation RPC');
select ok(not has_function_privilege('authenticated','public.pup_queue_guard(uuid,text,uuid,uuid,boolean)','EXECUTE'),
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
select is((public.pup_queue_dispatch('status',repeat('a',64),
 (select id from pup_queue.tickets where binding_hash=repeat('a',64)))->>'code'),'PC-Q002',
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

select * from finish();
rollback;
