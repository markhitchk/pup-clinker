-- Puppy Exchange: server-owned social state and atomic, opt-in puppy transfers.
-- All exposed tables remain unreadable by the Data API. Only signed PupEye
-- Edge Functions with the service role can access this feature.
create extension if not exists pgcrypto with schema extensions;

create table if not exists public.pup_exchange_connections (
  id uuid primary key default gen_random_uuid(),
  requester uuid not null references public.pup_accounts(id) on delete cascade,
  recipient uuid not null references public.pup_accounts(id) on delete cascade,
  state text not null default 'pending'
    check (state in ('pending','accepted','declined','blocked')),
  blocked_by uuid references public.pup_accounts(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint pup_exchange_no_self check (requester <> recipient),
  constraint pup_exchange_pair_unique unique (requester, recipient)
);
create unique index if not exists pup_exchange_unordered_pair
  on public.pup_exchange_connections
  (least(requester,recipient),greatest(requester,recipient));
create index if not exists pup_exchange_recipient_idx
  on public.pup_exchange_connections (recipient,state);

-- A safe default: no puppies can be exchanged without a reviewed server policy.
-- Adding metadata to a remote manifest never grants transfer rights by itself.
create table if not exists public.pup_exchange_transfer_policy (
  puppy_id text primary key check (puppy_id ~ '^[a-z0-9_]{1,64}$'),
  giftable boolean not null default false,
  tradeable boolean not null default false,
  bound boolean not null default true,
  source_copy boolean not null default false,
  updated_at timestamptz not null default now(),
  check (not bound or (not giftable and not tradeable))
);
create table if not exists public.pup_exchange_offers (
  id uuid primary key default gen_random_uuid(),
  sender uuid not null references public.pup_accounts(id) on delete cascade,
  recipient uuid not null references public.pup_accounts(id) on delete cascade,
  kind text not null check (kind in ('gift','trade')),
  offered text[] not null,
  requested text[] not null default '{}',
  state text not null default 'pending'
    check (state in ('pending','countered','completed','declined','cancelled','expired')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '24 hours'),
  completed_at timestamptz,
  check (sender <> recipient),
  check (array_length(offered,1) between 1 and 5),
  check (coalesce(array_length(requested,1),0) between 0 and 5)
);
create index if not exists pup_exchange_offers_sender_idx
  on public.pup_exchange_offers (sender,created_at desc);
create index if not exists pup_exchange_offers_recipient_idx
  on public.pup_exchange_offers (recipient,created_at desc);

alter table public.pup_exchange_connections enable row level security;
alter table public.pup_exchange_transfer_policy enable row level security;
alter table public.pup_exchange_offers enable row level security;
revoke all on public.pup_exchange_connections from public,anon,authenticated;
revoke all on public.pup_exchange_transfer_policy from public,anon,authenticated;
revoke all on public.pup_exchange_offers from public,anon,authenticated;
grant select,insert,update,delete on public.pup_exchange_connections to service_role;
grant select,insert,update,delete on public.pup_exchange_offers to service_role;
grant select on public.pup_exchange_transfer_policy to service_role;

-- Canonical representation mirrors PupEye canonicalJson for payload SHA256.
create or replace function public.pup_exchange_canonical(v jsonb) returns text
language plpgsql immutable strict set search_path = '' as $$
declare result text;
begin
  if jsonb_typeof(v)='object' then
    select coalesce(string_agg(to_json(k)::text || ':' ||
      public.pup_exchange_canonical(value),',' order by k),'') into result
      from jsonb_each(v) as e(k,value);
    return '{' || result || '}';
  elsif jsonb_typeof(v)='array' then
    select coalesce(string_agg(public.pup_exchange_canonical(value),',' order by ord),'')
      into result from jsonb_array_elements(v) with ordinality as e(value,ord);
    return '[' || result || ']';
  else
    return v::text;
  end if;
end $$;
revoke all on function public.pup_exchange_canonical(jsonb) from public,anon,authenticated;

-- One database transaction locks both save rows and the offer. No partial gift,
-- trade, replay or duplicate save mutation is possible.
create or replace function public.pup_exchange_complete(p_offer uuid,p_actor uuid)
returns jsonb language plpgsql security definer set search_path = ''
as $$
declare o public.pup_exchange_offers%rowtype;
declare s public.pup_account_saves%rowtype;
declare r public.pup_account_saves%rowtype;
declare sv jsonb; rv jsonb; sl jsonb; rl jsonb;
declare offered text[]; requested text[]; puppy text; transfer_kind text;
declare saved_at bigint; connection_exists boolean;
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
  select exists (
    select 1 from public.pup_exchange_connections c
    where c.state='accepted'
      and ((c.requester=o.sender and c.recipient=o.recipient)
        or (c.requester=o.recipient and c.recipient=o.sender))
  ) into connection_exists;
  if not connection_exists then raise exception 'EXCHANGE_FRIENDSHIP_REQUIRED'; end if;
  if not exists(select 1 from public.pup_accounts
     where id=o.sender and status='active' and terms_version>=1 and privacy_version>=1)
    or not exists(select 1 from public.pup_accounts
     where id=o.recipient and status='active' and terms_version>=1 and privacy_version>=1)
  then raise exception 'EXCHANGE_ACCOUNT_NOT_ELIGIBLE'; end if;

  -- Obtain locks in a stable order to avoid deadlocks with simultaneous swaps.
  perform 1 from public.pup_account_saves
    where account_uuid in (o.sender,o.recipient) order by account_uuid for update;
  select * into s from public.pup_account_saves where account_uuid=o.sender;
  select * into r from public.pup_account_saves where account_uuid=o.recipient;
  if s.account_uuid is null or r.account_uuid is null then
    raise exception 'EXCHANGE_CLOUD_SAVE_REQUIRED';
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
      or not (sl ? puppy) or (rl ? puppy) then
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

  -- Prevent a transferred-out current puppy from becoming an invalid equip.
  if o.kind='trade' then
    if (sv #>> '{stores,puppy_clicker_save,values,puppy_style}')=any(offered) then
      sv=jsonb_set(sv,'{stores,puppy_clicker_save,values,puppy_style}','"classic"'::jsonb);
    end if;
    if (rv #>> '{stores,puppy_clicker_save,values,puppy_style}')=any(requested) then
      rv=jsonb_set(rv,'{stores,puppy_clicker_save,values,puppy_style}','"classic"'::jsonb);
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
