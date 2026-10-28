-- Owner-only gift privilege. The regular transfer policy remains deny-by-default.
-- The account is resolved once by its verified server-side Friend Code; permission
-- checks thereafter use its immutable Pup Account UUID, never a client-supplied ID.
create table if not exists public.pup_exchange_privileged_gifters (
 account_uuid uuid primary key references public.pup_accounts(id) on delete cascade,
 enabled boolean not null default true,
 granted_at timestamptz not null default now()
);
alter table public.pup_exchange_privileged_gifters enable row level security;
revoke all on public.pup_exchange_privileged_gifters from public,anon,authenticated;
grant select on public.pup_exchange_privileged_gifters to service_role;

-- Register the entire bundled catalog for privileged gifts only.
-- These rows are bound and NOT normally giftable/tradeable.
-- A server-validated published dynamic manifest registers future IDs when first
-- requested by an authorized gifter; client requests alone cannot register IDs.
insert into public.pup_exchange_transfer_policy
 (puppy_id,giftable,tradeable,bound,source_copy)
values
 ('classic',false,false,true,false),
 ('golden',false,false,true,false),
 ('poodle',false,false,true,false),
 ('spotty',false,false,true,false),
 ('midnight',false,false,true,false),
 ('cloud',false,false,true,false),
 ('aurora',false,false,true,false),
 ('cocoa',false,false,true,false),
 ('snowball',false,false,true,false),
 ('galaxy',false,false,true,false),
 ('neon_buddy',false,false,true,false),
 ('golden_night',false,false,true,false),
 ('halloween',false,false,true,false),
 ('santa',false,false,true,false),
 ('birthday',false,false,true,false),
 ('dev_pup',false,false,true,false),
 ('secret_snoot',false,false,true,false),
 ('classic_forever',false,false,true,false),
 ('v2_frost',false,false,true,false),
 ('v2_honey',false,false,true,false),
 ('v2_biscuit',false,false,true,false),
 ('v2_onyx',false,false,true,false),
 ('v2_domino',false,false,true,false),
 ('v2_chestnut',false,false,true,false),
 ('v2_prism',false,false,true,false),
 ('v2_flurry',false,false,true,false),
 ('v2_harleytg',false,false,true,false),
 ('v2_dev_pup',false,false,true,false),
 ('v2_rory',false,false,true,false),
 ('v2_discord_pup',false,false,true,false),
 ('test_v1_crash_dummy',false,false,true,false),
 ('test_v2_crash_dummy',false,false,true,false)
on conflict (puppy_id) do nothing;
grant insert on public.pup_exchange_transfer_policy to service_role;

insert into public.pup_exchange_privileged_gifters(account_uuid,enabled)
select a.id,true
 from public.pup_accounts a
 join public.pupeye_players p on p.id=a.player_uuid
 where p.friend_code='PUP-DA98-QKMT-L74H' and a.status='active'
on conflict (account_uuid) do update set enabled=true;

-- Replace just the atomic completion RPC; preserve standard gift/trade rules.
create or replace function public.pup_exchange_complete(p_offer uuid,p_actor uuid)
returns jsonb language plpgsql security definer set search_path = ''
as $$
declare o public.pup_exchange_offers%rowtype;
declare s public.pup_account_saves%rowtype;
declare r public.pup_account_saves%rowtype;
declare sv jsonb; rv jsonb; sl jsonb; rl jsonb;
declare offered text[]; requested text[]; puppy text; transfer_kind text;
declare saved_at bigint; connection_exists boolean; privileged_gift boolean;
begin
  select * into o from public.pup_exchange_offers where id=p_offer for update;
  if not found or o.state not in ('pending','countered') then
    raise exception 'EXCHANGE_OFFER_NOT_PENDING';
  end if;
  if o.expires_at<=now() then
    update public.pup_exchange_offers set state='expired',updated_at=now() where id=o.id;
    raise exception 'EXCHANGE_OFFER_EXPIRED';
  end if;
  if (o.kind='gift' and (p_actor<>o.recipient or o.state<>'pending'))
     or (o.kind='trade' and (p_actor<>o.sender or o.state<>'countered')) then
    raise exception 'EXCHANGE_CONFIRMATION_REQUIRED';
  end if;
  -- Lock the friendship while completing the transfer. A concurrent block
  -- cannot revoke consent between its validation and the save mutation.
  perform 1 from public.pup_exchange_connections c
   where c.state='accepted'
     and ((c.requester=o.sender and c.recipient=o.recipient)
       or (c.requester=o.recipient and c.recipient=o.sender))
   for share;
  if not found then raise exception 'EXCHANGE_FRIENDSHIP_REQUIRED'; end if;
  if not exists(select 1 from public.pup_accounts
     where id=o.sender and status='active' and terms_version>=1 and privacy_version>=1)
    or not exists(select 1 from public.pup_accounts
     where id=o.recipient and status='active' and terms_version>=1 and privacy_version>=1)
  then raise exception 'EXCHANGE_ACCOUNT_NOT_ELIGIBLE'; end if;

  -- A server-recorded, account-bound exception allows gift-only creation of
  -- any cataloged puppy without sender ownership or standard transferability.
  -- Account UUID comes from the signed PupEye identity at offer time.
  select o.kind='gift' and exists (
    select 1 from public.pup_exchange_privileged_gifters g
    where g.account_uuid=o.sender and g.enabled
  ) into privileged_gift;

  -- Obtain locks in a stable order to avoid deadlocks with simultaneous swaps.
  perform 1 from public.pup_account_saves
    where account_uuid in (o.sender,o.recipient) order by account_uuid for update;
  select * into s from public.pup_account_saves where account_uuid=o.sender;
  select * into r from public.pup_account_saves where account_uuid=o.recipient;
  if s.account_uuid is null or r.account_uuid is null then
    raise exception 'EXCHANGE_CLOUD_SAVE_REQUIRED';
  end if;
  if s.save_schema<>1 or r.save_schema<>1
     or s.save_data->>'format'<>'puppy-clicker-cloud-save'
     or r.save_data->>'format'<>'puppy-clicker-cloud-save'
     or s.save_data->>'version'<>'1'
     or r.save_data->>'version'<>'1' then
    raise exception 'EXCHANGE_CLOUD_SAVE_UNSUPPORTED';
  end if;
  sv=s.save_data; rv=r.save_data;
  sl=sv #> '{stores,puppy_clicker_save,values,unlocked_puppies}';
  rl=rv #> '{stores,puppy_clicker_save,values,unlocked_puppies}';
  if jsonb_typeof(sl)<>'array' or jsonb_typeof(rl)<>'array' then
    raise exception 'EXCHANGE_INVENTORY_INVALID';
  end if;
  offered=o.offered; requested=o.requested;
  if o.kind='gift' then
    if cardinality(offered)<>1 or cardinality(requested)<>0 then
      raise exception 'EXCHANGE_GIFT_INVALID';
    end if;
  elsif cardinality(offered) not between 1 and 5
     or cardinality(requested) not between 1 and 5 then
    raise exception 'EXCHANGE_TRADE_INVALID';
  end if;

  foreach puppy in array offered loop
    if puppy !~ '^[a-z0-9_]{1,64}
      raise exception 'EXCHANGE_SENDER_INVENTORY_INVALID';
    end if;
    select case when o.kind='gift' then 'gift' else 'trade' end into transfer_kind;
    if not exists (
      select 1 from public.pup_exchange_transfer_policy p
      where p.puppy_id=puppy and
        (privileged_gift or (not p.bound and not p.source_copy
         and case when transfer_kind='gift' then p.giftable else p.tradeable end))
    ) then raise exception 'EXCHANGE_PUPPY_NOT_TRANSFERABLE'; end if;
  end loop;
  if o.kind='trade' then
    foreach puppy in array requested loop
      if puppy !~ '^[a-z0-9_]{1,64}$' or not (rl ? puppy) or (sl ? puppy) then
        raise exception 'EXCHANGE_RECIPIENT_INVENTORY_INVALID';
      end if;
      if not exists (select 1 from public.pup_exchange_transfer_policy p
        where p.puppy_id=puppy and not p.bound and not p.source_copy and p.tradeable)
      then raise exception 'EXCHANGE_PUPPY_NOT_TRANSFERABLE'; end if;
    end loop;
  end if;

  -- Gifts make a non-duplicating recipient copy, as in Kotlin's resolveGiftCopy;
  -- trades move the one existing unlocked style between players.
  if o.kind='trade' then
    select coalesce(jsonb_agg(value),'[]'::jsonb) into sl
      from jsonb_array_elements(sl) as value
      where value #>> '{}' <> all(offered);
    select coalesce(jsonb_agg(value),'[]'::jsonb) into rl
      from jsonb_array_elements(rl) as value
      where value #>> '{}' <> all(requested);
  end if;
  select sl || to_jsonb(requested) into sl;
  select rl || to_jsonb(offered) into rl;

  -- Reset transferred-out current puppies to the permanent Classic style
  -- including the visible name and equipped care state.
  if o.kind='trade' then
    if (sv #>> '{stores,puppy_clicker_save,values,puppy_style}')=any(offered) then
      sv=jsonb_set(sv,'{stores,puppy_clicker_save,values,puppy_style}','"classic"'::jsonb);
      sv=jsonb_set(sv,'{stores,puppy_clicker_save,values,puppy_name}',
        coalesce(sv #> '{stores,puppy_clicker_save,values,puppy_name_for_classic}','"Buddy"'::jsonb),true);
    end if;
    if (rv #>> '{stores,puppy_clicker_save,values,puppy_style}')=any(requested) then
      rv=jsonb_set(rv,'{stores,puppy_clicker_save,values,puppy_style}','"classic"'::jsonb);
      rv=jsonb_set(rv,'{stores,puppy_clicker_save,values,puppy_name}',
        coalesce(rv #> '{stores,puppy_clicker_save,values,puppy_name_for_classic}','"Buddy"'::jsonb),true);
    end if;
  end if;
  sv=jsonb_set(sv,'{stores,puppy_clicker_save,values,unlocked_puppies}',sl);
  rv=jsonb_set(rv,'{stores,puppy_clicker_save,values,unlocked_puppies}',rl);
  saved_at=(extract(epoch from clock_timestamp())*1000)::bigint;
  sv=jsonb_set(sv,'{savedAtEpochMs}',to_jsonb(saved_at),true);
  rv=jsonb_set(rv,'{savedAtEpochMs}',to_jsonb(saved_at),true);
  update public.pup_account_saves set
    revision=revision+1, save_data=sv,
    payload_hash_sha256=encode(extensions.digest(public.pup_exchange_canonical(sv),'sha256'),'hex'),
    updated_at=now()
    where account_uuid=o.sender;
  update public.pup_account_saves set
    revision=revision+1, save_data=rv,
    payload_hash_sha256=encode(extensions.digest(public.pup_exchange_canonical(rv),'sha256'),'hex'),
    updated_at=now()
    where account_uuid=o.recipient;
  update public.pup_exchange_offers set state='completed',completed_at=now(),updated_at=now()
    where id=o.id;
  return jsonb_build_object('ok',true,'offerId',o.id,'state','completed','kind',o.kind);
end $$;

      or (not privileged_gift and not (sl ? puppy)) or (rl ? puppy) then
      raise exception 'EXCHANGE_SENDER_INVENTORY_INVALID';
    end if;
    select case when o.kind='gift' then 'gift' else 'trade' end into transfer_kind;
    if not exists (
      select 1 from public.pup_exchange_transfer_policy p
      where p.puppy_id=puppy and not p.bound and not p.source_copy
        and case when transfer_kind='gift' then p.giftable else p.tradeable end
    ) then raise exception 'EXCHANGE_PUPPY_NOT_TRANSFERABLE'; end if;
  end loop;
  if o.kind='trade' then
    foreach puppy in array requested loop
      if puppy !~ '^[a-z0-9_]{1,64}$' or not (rl ? puppy) or (sl ? puppy) then
        raise exception 'EXCHANGE_RECIPIENT_INVENTORY_INVALID';
      end if;
      if not exists (select 1 from public.pup_exchange_transfer_policy p
        where p.puppy_id=puppy and not p.bound and not p.source_copy and p.tradeable)
      then raise exception 'EXCHANGE_PUPPY_NOT_TRANSFERABLE'; end if;
    end loop;
  end if;

  -- Gifts make a non-duplicating recipient copy, as in Kotlin's resolveGiftCopy;
  -- trades move the one existing unlocked style between players.
  if o.kind='trade' then
    select coalesce(jsonb_agg(value),'[]'::jsonb) into sl
      from jsonb_array_elements(sl) as value
      where value #>> '{}' <> all(offered);
    select coalesce(jsonb_agg(value),'[]'::jsonb) into rl
      from jsonb_array_elements(rl) as value
      where value #>> '{}' <> all(requested);
  end if;
  select sl || to_jsonb(requested) into sl;
  select rl || to_jsonb(offered) into rl;

  -- Reset transferred-out current puppies to the permanent Classic style
  -- including the visible name and equipped care state.
  if o.kind='trade' then
    if (sv #>> '{stores,puppy_clicker_save,values,puppy_style}')=any(offered) then
      sv=jsonb_set(sv,'{stores,puppy_clicker_save,values,puppy_style}','"classic"'::jsonb);
      sv=jsonb_set(sv,'{stores,puppy_clicker_save,values,puppy_name}',
        coalesce(sv #> '{stores,puppy_clicker_save,values,puppy_name_for_classic}','"Buddy"'::jsonb),true);
    end if;
    if (rv #>> '{stores,puppy_clicker_save,values,puppy_style}')=any(requested) then
      rv=jsonb_set(rv,'{stores,puppy_clicker_save,values,puppy_style}','"classic"'::jsonb);
      rv=jsonb_set(rv,'{stores,puppy_clicker_save,values,puppy_name}',
        coalesce(rv #> '{stores,puppy_clicker_save,values,puppy_name_for_classic}','"Buddy"'::jsonb),true);
    end if;
  end if;
  sv=jsonb_set(sv,'{stores,puppy_clicker_save,values,unlocked_puppies}',sl);
  rv=jsonb_set(rv,'{stores,puppy_clicker_save,values,unlocked_puppies}',rl);
  saved_at=(extract(epoch from clock_timestamp())*1000)::bigint;
  sv=jsonb_set(sv,'{savedAtEpochMs}',to_jsonb(saved_at),true);
  rv=jsonb_set(rv,'{savedAtEpochMs}',to_jsonb(saved_at),true);
  update public.pup_account_saves set
    revision=revision+1, save_data=sv,
    payload_hash_sha256=encode(extensions.digest(public.pup_exchange_canonical(sv),'sha256'),'hex'),
    updated_at=now()
    where account_uuid=o.sender;
  update public.pup_account_saves set
    revision=revision+1, save_data=rv,
    payload_hash_sha256=encode(extensions.digest(public.pup_exchange_canonical(rv),'sha256'),'hex'),
    updated_at=now()
    where account_uuid=o.recipient;
  update public.pup_exchange_offers set state='completed',completed_at=now(),updated_at=now()
    where id=o.id;
  return jsonb_build_object('ok',true,'offerId',o.id,'state','completed','kind',o.kind);
end $$;

revoke all on function public.pup_exchange_complete(uuid,uuid) from public,anon,authenticated;
grant execute on function public.pup_exchange_complete(uuid,uuid) to service_role;
