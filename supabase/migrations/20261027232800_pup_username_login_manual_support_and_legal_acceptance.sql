-- Usernames are the public Pup Account sign-in identifier.
-- Email here is for optional unverified MANUAL support contact only.
alter table public.pup_accounts
  add column if not exists contact_email text,
  add column if not exists terms_version integer not null default 0,
  add column if not exists privacy_version integer not null default 0,
  add column if not exists terms_accepted_at timestamptz,
  add column if not exists privacy_accepted_at timestamptz;
do $$ begin
 if not exists (select 1 from pg_constraint where conname='pup_contact_email_format') then
  alter table public.pup_accounts add constraint pup_contact_email_format
  check (contact_email is null or (length(contact_email)<=254 and contact_email ~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$'));
 end if;
end $$;
comment on column public.pup_accounts.contact_email is 'Optional and unverified manual staff support contact, never used for sign-in or password recovery.';
comment on column public.pup_accounts.terms_version is 'Explicit signed acceptance of Terms of Use version.';
comment on column public.pup_accounts.privacy_version is 'Explicit signed acknowledgment of Privacy Policy version.';
