# Puppy Clicker — Website

This folder is reserved for the website and browser edition of Puppy Clicker. A playable website UI and browser PupEye/Discord authentication flow have not been selected yet, but the shared Pup Account realtime synchronization layer is now present.

Future browser clients must follow the [PupEye global enforcement client contract](../docs/pupeye/global-enforcement-client-contract.md). Browser installation identity is not a hardware identity.

## Pup Account realtime sync

The website and Android app share the same authoritative Supabase Pup Account cloud save. Clients must never read or write protected PupEye tables directly.

The synchronization contract is:

1. Authenticate the platform-specific Pup Account/PupEye session.
2. Call the authenticated `pup-account` `account-status` action and read its `realtimeTopic`.
3. Read the authoritative save through `cloud-save-read`.
4. Subscribe to the returned topic with `src/pup-account-realtime.js`.
5. On a `changed` broadcast, re-read through the authenticated Edge Function instead of trusting broadcast payload data.
6. Write through `cloud-save-write` using `expectedRevision` optimistic concurrency.
7. On a revision conflict, re-read the server state before attempting another write.

`src/pup-account-sync.js` implements the framework-neutral read/write/realtime coordinator. The website's eventual authentication layer must provide its `transport` implementation. Never place a Supabase secret/service-role key in browser code.

Supabase Realtime carries only non-sensitive invalidation metadata (`kind`, operation, revision/generation, timestamp) on an opaque per-account topic. Full save JSON, Discord PII, sessions, device reputation, bans, attestations, and other security records remain behind server authorization.

## Intended structure

When development begins, keep the website entry point, frontend source, styles, and website-specific configuration here. Reference the existing shared artwork in [`../assets/`](../assets/) rather than duplicating the canonical V1 and V2 puppy assets. Preserve the original game and character identities.

Do not move the Android source into this folder. Website development and deployment should be independent of the native Android build. No hosting provider, production URL, or frontend framework has been selected by this folder organization.
