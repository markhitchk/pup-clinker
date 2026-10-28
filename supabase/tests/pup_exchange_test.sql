begin;
create extension if not exists pgtap with schema extensions;
select plan(29);
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

-- Full production-save transaction fixtures; this entire test is rolled back.
insert into public.pupeye_players(id,player_id,friend_code,username)
values
 ('f0000000-0000-4000-8000-000000001111','PC-EXCHANGE-FIXTURE-1111','PUP-ABCD-EFGH-JKLM','exchange_sndr'),
 ('f0000000-0000-4000-8000-000000002222','PC-EXCHANGE-FIXTURE-2222','PUP-NPQR-STUV-WXYZ','exchange_rcpt');
insert into public.pupeye_installations
 (id,player_uuid,installation_id,device_key_id,public_key_b64,support_code)
values
 ('f0000000-0000-4000-8000-000000001112',
  'f0000000-0000-4000-8000-000000001111',
  'f0000000-0000-4000-8000-000000001113',
  repeat('a',64),'EXCHANGE_TEST_KEY_1111','EX11'),
 ('f0000000-0000-4000-8000-000000002223',
  'f0000000-0000-4000-8000-000000002222',
  'f0000000-0000-4000-8000-000000002224',
  repeat('b',64),'EXCHANGE_TEST_KEY_2222','EX22');
insert into public.pup_accounts
 (id,player_uuid,username,active_installation_uuid,status,
  terms_version,privacy_version,terms_accepted_at,privacy_accepted_at)
values
 ('f0000000-0000-4000-8000-000000001114',
  'f0000000-0000-4000-8000-000000001111','exchange_sndr',
  'f0000000-0000-4000-8000-000000001112','active',1,1,now(),now()),
 ('f0000000-0000-4000-8000-000000002225',
  'f0000000-0000-4000-8000-000000002222','exchange_rcpt',
  'f0000000-0000-4000-8000-000000002223','active',1,1,now(),now());
insert into public.pup_account_saves
 (player_uuid,account_uuid,installation_uuid,revision,save_schema,save_data)
values
 ('f0000000-0000-4000-8000-000000001111',
  'f0000000-0000-4000-8000-000000001114',
  'f0000000-0000-4000-8000-000000001112',1,1,
  '{"format":"puppy-clicker-cloud-save","version":1,"stores":{"puppy_clicker_save":{"values":{"puppy_style":"classic","unlocked_puppies":["classic","v3_gift_fixture","v3_sender_fixture"],"treats":100},"types":{"puppy_style":"string","unlocked_puppies":"string_set","treats":"long"}}}}'::jsonb),
 ('f0000000-0000-4000-8000-000000002222',
  'f0000000-0000-4000-8000-000000002225',
  'f0000000-0000-4000-8000-000000002223',1,1,
  '{"format":"puppy-clicker-cloud-save","version":1,"stores":{"puppy_clicker_save":{"values":{"puppy_style":"v3_recipient_fixture","unlocked_puppies":["classic","v3_recipient_fixture"],"treats":250},"types":{"puppy_style":"string","unlocked_puppies":"string_set","treats":"long"}}}}'::jsonb);

insert into public.pup_exchange_connections(requester,recipient,state) values
 ('f0000000-0000-4000-8000-000000001114',
  'f0000000-0000-4000-8000-000000002225','accepted');
insert into public.pup_exchange_transfer_policy(puppy_id,giftable,tradeable,bound)
values
 ('v3_gift_fixture',true,false,false),
 ('v3_sender_fixture',false,true,false),
 ('v3_recipient_fixture',false,true,false);
insert into public.pup_exchange_offers(id,sender,recipient,kind,offered)
values
 ('f0000000-0000-4000-8000-000000007001',
  'f0000000-0000-4000-8000-000000001114',
  'f0000000-0000-4000-8000-000000002225','gift',array['v3_gift_fixture']);
select is((public.pup_exchange_complete(
 'f0000000-0000-4000-8000-000000007001',
 'f0000000-0000-4000-8000-000000002225')->>'state'),'completed',
 'accepted gift commits atomically');
select ok(
 (select save_data #> '{stores,puppy_clicker_save,values,unlocked_puppies}'
  from public.pup_account_saves
  where account_uuid='f0000000-0000-4000-8000-000000001114') ? 'v3_gift_fixture',
 'gift sender keeps a source copy');
select ok(
 (select save_data #> '{stores,puppy_clicker_save,values,unlocked_puppies}'
  from public.pup_account_saves
  where account_uuid='f0000000-0000-4000-8000-000000002225') ? 'v3_gift_fixture',
 'gift recipient gets the original eligible style');
select is((select save_data #>> '{stores,puppy_clicker_save,values,treats}'
 from public.pup_account_saves where account_uuid='f0000000-0000-4000-8000-000000001114'),
 '100','sender Treats unaffected');
select throws_ok($$select public.pup_exchange_complete(
 'f0000000-0000-4000-8000-000000007001',
 'f0000000-0000-4000-8000-000000002225')$$,
 'P0001','EXCHANGE_OFFER_NOT_PENDING','gift receipt is single-use');

insert into public.pup_exchange_offers
 (id,sender,recipient,kind,offered,requested,state)
values
 ('f0000000-0000-4000-8000-000000007002',
  'f0000000-0000-4000-8000-000000001114',
  'f0000000-0000-4000-8000-000000002225',
  'trade',array['v3_sender_fixture'],array['v3_recipient_fixture'],'countered');
select throws_ok($$select public.pup_exchange_complete(
 'f0000000-0000-4000-8000-000000007002',
 'f0000000-0000-4000-8000-000000002225')$$,
 'P0001','EXCHANGE_CONFIRMATION_REQUIRED',
 'recipient cannot forge the sender final confirmation');
select is((public.pup_exchange_complete(
 'f0000000-0000-4000-8000-000000007002',
 'f0000000-0000-4000-8000-000000001114')->>'state'),'completed',
 'sender confirms a matched counteroffer');
select ok(not (select save_data #> '{stores,puppy_clicker_save,values,unlocked_puppies}'
  from public.pup_account_saves
  where account_uuid='f0000000-0000-4000-8000-000000001114') ? 'v3_sender_fixture',
 'sender loses transferred trade item');
select ok((select save_data #> '{stores,puppy_clicker_save,values,unlocked_puppies}'
  from public.pup_account_saves
  where account_uuid='f0000000-0000-4000-8000-000000001114') ? 'v3_recipient_fixture',
 'sender receives recipient trade item');
select is((select save_data #>> '{stores,puppy_clicker_save,values,puppy_style}'
  from public.pup_account_saves
  where account_uuid='f0000000-0000-4000-8000-000000002225'),'classic',
 'transferred-out selected puppy switches to free classic');

select is((select save_data #>> '{stores,puppy_clicker_save,values,puppy_name}'
  from public.pup_account_saves
  where account_uuid='f0000000-0000-4000-8000-000000002225'),'Buddy',
 'transferred-out selected puppy restores the Classic display name');
-- This privileged UUID is obtained only from the server-managed permission table.
-- A protected puppy not owned by the sender can be gifted exactly once to an
-- accepting friend. It never makes that puppy giftable to other players.
insert into public.pup_exchange_privileged_gifters(account_uuid)
values ('f0000000-0000-4000-8000-000000001114');
insert into public.pup_exchange_transfer_policy(puppy_id,giftable,tradeable,bound)
values ('v4_protected_fixture',false,false,true);
select ok(not has_table_privilege('authenticated','public.pup_exchange_privileged_gifters','SELECT'),
 'gift privilege cannot be read or edited by normal clients');
insert into public.pup_exchange_offers(id,sender,recipient,kind,offered)
values ('f0000000-0000-4000-8000-000000007003',
 'f0000000-0000-4000-8000-000000001114',
 'f0000000-0000-4000-8000-000000002225','gift',array['v4_protected_fixture']);
select is((public.pup_exchange_complete(
 'f0000000-0000-4000-8000-000000007003',
 'f0000000-0000-4000-8000-000000002225')->>'state'),'completed',
 'verified owner can gift protected catalog puppy without owning it');
select ok((select save_data #> '{stores,puppy_clicker_save,values,unlocked_puppies}'
 from public.pup_account_saves
 where account_uuid='f0000000-0000-4000-8000-000000002225') ? 'v4_protected_fixture',
 'recipient receives protected puppy from owner');
select ok(not (select save_data #> '{stores,puppy_clicker_save,values,unlocked_puppies}'
 from public.pup_account_saves
 where account_uuid='f0000000-0000-4000-8000-000000001114') ? 'v4_protected_fixture',
 'owner does not need to generate or keep a local puppy copy');
insert into public.pup_exchange_offers(id,sender,recipient,kind,offered)
values ('f0000000-0000-4000-8000-000000007004',
 'f0000000-0000-4000-8000-000000002225',
 'f0000000-0000-4000-8000-000000001114','gift',array['v4_protected_fixture']);
select throws_ok($$select public.pup_exchange_complete(
 'f0000000-0000-4000-8000-000000007004',
 'f0000000-0000-4000-8000-000000001114')$$,
 'P0001','EXCHANGE_PUPPY_NOT_TRANSFERABLE',
 'regular account still cannot gift protected puppy');

select * from finish();
rollback;
