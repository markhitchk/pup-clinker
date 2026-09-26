# PupEye Global Enforcement Client Contract

All Puppy Clicker clients must:

1. Generate and hold an installation signing key appropriate to their platform.
2. Call the shared PupEye registration and enforcement endpoints with authenticated requests.
3. Honor `GLOBAL_BANNED` and `REVIEW_REQUIRED` before protected gameplay, account switching, save transfer, or mutations.
4. Keep a known active ban or review state locked while offline, even after a local clock change or session-file deletion.
5. Require a signed, server-authoritative `ALLOWED` response before clearing a temporary ban or review state. Expiration is decided by server time.
6. Show public reasons, a Ban ID, and a Support route without exposing internal evidence.
7. Never ship or expose service-role/admin credentials or webhook secrets to users.

Android stores its private signing key in Android Keystore and caches the last authoritative enforcement result in encrypted device storage. A clean installation without a known ban may report connectivity trouble without inventing a ban.

When a desktop framework is selected, desktop clients must use the strongest suitable OS-secure key storage. Browser clients use a browser installation identity linked to an account or verified Discord identity; browser storage must not be represented as a hardware-grade device identity.

The repository does not yet contain an implemented web or desktop client or a chosen framework. Each future client must consume the existing PupEye API response contract, including `ALLOWED`, `REVIEW_REQUIRED`, and `GLOBAL_BANNED`, and must not attempt to decide ban expiry or device linkage locally.
