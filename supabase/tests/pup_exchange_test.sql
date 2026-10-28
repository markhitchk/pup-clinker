begin;
create extension if not exists pgtap with schema extensions;
select plan(13);
select ok(to_regclass('public.pup_exchange_connections') is not null,'friend connections table exists');
select ok(to_regclass('public.pup_exchange_offers') is not null,'offers table exists');
select ok(to_regclass('public.pup_exchange_transfer_policy') is not null,'transfer policy table exists');
select ok((select relrowsecurity from pg_class where oid='public.pup_exchange_connections'::regclass),
 'connections require RLS');
select ok((select relrowsecurity from pg_class where oid='public.pup_exchange_offers'::regclass),
 'offers require RLS');
select ok((select relrowsecurity from pg_class where oid='public.pup_exchange_transfer_policy'::regclass),
 'transfer policies require RLS');
select ok(not has_table_privilege('anon','public.pup_exchange_offers','SELECT'),
 'no public offer reads');
select ok(not has_table_privilege('authenticated','public.pup_exchange_offers','INSERT'),
 'no client-side forged offers');
select ok(not has_table_privilege('anon','public.pup_exchange_connections','SELECT'),
 'no public friend graph');
select ok(not has_table_privilege('authenticated','public.pup_exchange_transfer_policy','UPDATE'),
 'players cannot mark puppies transferable');
select ok(not has_function_privilege('anon','public.pup_exchange_complete(uuid,uuid)','EXECUTE'),
 'anonymous clients cannot complete trades');
select ok(not has_function_privilege('authenticated','public.pup_exchange_complete(uuid,uuid)','EXECUTE'),
 'authenticated clients cannot settle their own trades');
select throws_ok(
 $$select public.pup_exchange_complete('f0000000-0000-4000-8000-000000000123',
 'f0000000-0000-4000-8000-000000000124')$$,
 'P0001','EXCHANGE_OFFER_NOT_PENDING',
 'missing offers cannot modify either cloud save');
select * from finish();
rollback;
