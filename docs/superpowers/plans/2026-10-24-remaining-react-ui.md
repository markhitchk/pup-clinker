# Remaining React UI fidelity fixes

> Implement inline using executing-plans and test-driven-development.

**Goal:** Match the user's October 24 reference screenshots without changing approved Play, navigation, Shop or casino screens.

**Architecture:** Preserve the existing reducer and guest save boundaries. Port the shipped Kotlin UI, including build patches, using scoped React Native styles. A shared modal owns destructive-action confirmation and cancellation.

**Spec:** User screenshots; `DangerZoneConfirmation.kt`, `PuppyMainScreensRevamp.kt`, `PuppyNotificationInboxUi.kt`, and the final `patch_compact_roster_settings.py` / `patch_main_ui_revamp.py` roster transforms.

## Constraints

- Preserve Play and the Play/Care/Roster/Shop/Rewards navigation.
- All three destructive actions require an uninterrupted 10-second hold.
- Release, moving outside, another touch, app backgrounding, Back or unmount cancel a hold.
- Preserve reducer prices, per-puppy care, ownership, filters and guest/account isolation.
- Developer privileges remain gated; an uploaded native account screenshot is not authentication.
- Do not invent notification grants or import native account history.

## Review focus

Hold timer cleanup and exact-once dispatch; safe area and small-screen scrolling; roster favorites on locked puppies; long names and search by native asset ID; settings changes must not regress Play or other routes.

## Task 1: Destructive confirmation

- [x] Add failing UI tests covering early release, uninterrupted completion, moving out, multitouch, background, dismissal, and all three Settings action routes.
- [x] Replace Settings plain destructive alerts with `DangerHoldDialog.jsx`, retaining the existing reducer actions and accurate guest descriptions. Draw the construction-tape frame; use safe insets, scrolling content and reduced-motion preferences.
- [x] Run focused tests and full suite.

## Task 2: Care and Roster references

- [x] Add rendered-app tests for five compact care meters, 3+2 actions/costs, native asset-ID search, favorites, current puppy rename/accessory, and Exchange navigation.
- [x] Port Care's compact identity, meter panel, bonus note, action grid; preserve Daily functionality in Rewards.
- [x] Port Roster compact controls/selection strip and three-column cards; retain existing ownership, rename, accessory, collection and use behavior.
- [x] Run focused tests and full suite; replace obsolete tests that asserted unused V6 layout text with rendered behavior tests.

## Task 3: Inbox and verification

- [x] Align inbox unread badge, time placement, icon/body rows, safe insets and claim-state presentation with the reference. Preserve actual reducer claims.
- [x] Test claim repeat protection and read/clear/filter interactions.
- [x] Run complete tests and Android export; obtain fresh code review and preview APK from the existing branch.
- [x] Deliver APK, report checks and device-verification limitation.

## Execution record

- User scope supplies implementation authorization; executed inline in the existing clean feature checkout.
- RED: Settings action test reproduced plain alert; Care test reproduced missing progress bars; Roster test reproduced missing compact controls/native ID search.
- GREEN: New gesture and rendered navigation tests; full suite182/182; Android export passed.
- Fresh review found responder child coordinates (resolved with noninteractive label wrapper), tape frame safe-area bounds, and narrow three-column rounding. Frame now lives in an inset inner container; tile width subtracts both gaps from measured available width.
- Scope decisions: original guest-only access and protected account-service boundaries retained; native encrypted backup remains unported; Day/park access preserved in Rewards.
- Final APK build and physical-device verification are separate gates; device QA unavailable in this environment.
