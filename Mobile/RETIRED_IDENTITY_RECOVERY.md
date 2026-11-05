# Beta startup: retired PupEye local identity (November 2026)

## What the original code obscured
A native Android 16 beta request reached `pupeye-register` and received HTTP **409**. The registration's insert into `pupeye_players` returned SQLSTATE `23514` from `puppy_pupeye.reject_retired_pupeye_identity()`, which deliberately rejects previously deleted Player IDs / Friend Codes. The next `pup-beta-link` request was not authorized (HTTP 401). The app could mislabel this as `PUPPY-NET-701` when a cached preview was present, even though the server was reachable.

## Resolution
- `PUPEYE-ID-402` reports a retired **local beta** identity instead of network unavailability.
- No player identity is silently reminted or restored.
- The startup screen offers **Create New Beta Identity** only for this verified error, with a confirmation explaining the consequences.
- Native code first repeats a signed registration request with the existing installation. Only a server `PLAYER_ID_RETIRED` or `FRIEND_CODE_RETIRED` rejection authorizes local Player ID/Friend Code regeneration.
- Android's signing key and original installation ID are **preserved**. If an existing installation or ban prevents registration, server enforcement still applies.
- The old beta-only sealed session and cached presentation are discarded, but the production Play installation, Supabase Pup Account, and cloud saves are **never deleted or overwritten**.
- Users must authenticate again with Pup username/password or their linked Discord account.
- A generic network failure and an actual offline cached preview still report their original network error codes.

## QA checklist
1. Fresh installation: ordinary sign-in still works; recovery action is not offered.
2. Simulated retired Player ID: startup shows `PUPEYE-ID-402`; no user or save is created automatically.
3. Simulated retired Friend Code: same behavior.
4. Cancel confirmation: no local identity changes.
5. Confirm: only local identifier values change, with unchanged Android Keystore signing key and installation ID; return to sign-in.
6. Sign into an existing Pup Account and verify the same authoritative cloud save as the website.
7. A valid active installation, device transfer denial, revoked account, or global ban must **never** be bypassed through this path.
8. Network timeout with no retired-code response: stays `PUPPY-NET-701` and offers ordinary retry.

Live database schema and deletion tombstones were not altered.
