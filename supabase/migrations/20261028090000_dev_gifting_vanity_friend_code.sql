-- Allow verified server-side DEVELOPER accounts to send gifts to
-- active Pup Accounts without a friend relationship. Recipient acceptance,
-- atomic save settlement and explicit blocks remain mandatory.
create or replace function public.pup_exchange_complete(p_offer uuid,p_actor uuid)
returns jsonb language plpgsql security definer set search_path = ''
as $$
declare o public.pup_exchange_offers%rowtype;
declare s public.pup_account_saves%rowtype;
declare r public.pup_account_saves%rowtype;
declare sv jsonb; rv jsonb; sl jsonb; rl jsonb;
declare offered text[]; requested text[]; puppy text; transfer_kind text;
declare saved_at bigint; connection_exists boolean; privileged_gift boolean; direct_developer_gift boolean;
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
  -- Verified PupEye DEVELOPER role is server-owned and must still be
  -- valid when the recipient accepts the gift (not only when sent).
  select o.kind='gift' and exists (
    select 1 from public.pup_accounts a
    join public.pupeye_players p on p.id=a.player_uuid
    where a.id=o.sender and a.status='active'
      and p.status='active' and p.guild_role='DEVELOPER'
      and p.guild_verified_at is not null
  ) into direct_developer_gift;

  -- Friendship remains mandatory for ordinary gifts and all trades.
  perform 1 from public.pup_exchange_connections c
   where c.state='accepted'
     and ((c.requester=o.sender and c.recipient=o.recipient)
       or (c.requester=o.recipient and c.recipient=o.sender))
   for share;
  if not found and not direct_developer_gift then
    raise exception 'EXCHANGE_FRIENDSHIP_REQUIRED';
  end if;
  -- A developer may skip friend requests, never an explicit block.
  perform 1 from public.pup_exchange_connections c
    where c.state='blocked'
      and ((c.requester=o.sender and c.recipient=o.recipient)
        or (c.requester=o.recipient and c.recipient=o.sender))
    for share;
  if found then raise exception 'EXCHANGE_BLOCKED'; end if;
  if not exists(select 1 from public.pup_accounts
     where id=o.sender and status='active' and terms_version>=1 and privacy_version>=1)
    or not exists(select 1 from public.pup_accounts
     where id=o.recipient and status='active' and terms_version>=1 and privacy_version>=1)
  then raise exception 'EXCHANGE_ACCOUNT_NOT_ELIGIBLE'; end if;

  -- Gift-only owner privilege; the standard rules still apply to trades.
  select o.kind='gift' and (
    direct_developer_gift or exists (
      select 1 from public.pup_exchange_privileged_gifters g
      where g.account_uuid=o.sender and g.enabled
    )
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
    if puppy !~ '^[a-z0-9_]{1,64}$'
      or (not privileged_gift and not (sl ? puppy)) or (rl ? puppy) then
      raise exception 'EXCHANGE_SENDER_INVENTORY_INVALID';
    end if;
    select case when o.kind='gift' then 'gift' else 'trade' end into transfer_kind;
    if not exists (
      select 1 from public.pup_exchange_transfer_policy p
      where p.puppy_id=puppy and (privileged_gift or
        (not p.bound and not p.source_copy and
         case when transfer_kind='gift' then p.giftable else p.tradeable end))
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

-- Reserve a stable vanity code for the existing verified owner. The account's
-- immutable player/account UUID and installation proof are unchanged.
update public.pupeye_players p
  set friend_code='PUP-HARLEYTG-DEV',updated_at=now()
  where p.id='b5c30c98-9130-4e18-b4db-ec65127044af'
    and p.friend_code='PUP-DA98-QKMT-L74H'
    and exists (select 1 from public.pup_accounts a
                where a.player_uuid=p.id and a.username='max_puppy4');
