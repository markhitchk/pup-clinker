-- A beta companion can verify an existing Discord Pup Account without
-- reassigning the primary PupEye installation or changing the cloud-save owner.
-- Read access is available only through the signed server-side pup-beta-link
-- Edge Function; no REST API access or cloud-write permission is granted.
create table if not exists public.pup_beta_account_links (
  installation_uuid uuid primary key references public.pupeye_installations(id) on delete cascade,
  account_uuid uuid not null references public.pup_accounts(id) on delete cascade,
  discord_user_id text not null,
  verified_at timestamptz not null default now(),
  expires_at timestamptz not null
);
create index if not exists pup_beta_account_links_account_idx
  on public.pup_beta_account_links(account_uuid);
alter table public.pup_beta_account_links enable row level security;
revoke all on table public.pup_beta_account_links from anon, authenticated;
comment on table public.pup_beta_account_links is
  'Expiring Discord-OAuth-authorized read-only Puppy Clicker beta companion sessions; no gameplay authority.';
