# Account onboarding parity

Restore Kotlin's six-step first-run experience in the React Android beta: Welcome, Set Up Your Account, Personalize, Permissions & Privacy, Notifications, Ready to Play. Source: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboarding*.kt` and `PuppyPrivacyDataUi.kt`.

Use the existing native Discord/PupEye bridge for authentication. Account setup must activate the authenticated cloud snapshot before continuing; failures stay on setup. Ready must reverify the account and cloud activation before completion. The public username, Player ID, Friend Code, and Discord link come from native/server identity, never a nickname. Device conflicts must stay enforced.

Persist step, birthday, theme/accent/scale/reduced motion, optional privacy choices, and notification categories using the original Kotlin `puppy_ui_preferences_v2` keys. Diagnostics/crash/support identity default off. Birthday has no year, may be skipped, and accepts February 29. Privacy is explicitly reviewed before notifications; no permission prompt before that step. Notifications remain optional.

Approach: a dedicated React onboarding surface with a shared native preference bridge. This avoids expanding the existing guest reducer into an account authority and lets the original game hierarchy remain intact. A guest onboarding reskin would keep the wrong identity model; rebuilding the whole game engine is outside this addition.

The existing cloud projection is read-only and cannot become a signed economy by finishing setup. Do not upload fabricated saves or claim cloud-save/checkpoint writes occurred. No .pupsave import/export UI (user requirement). Beta retains `com.harleytg.puppyclicker.beta` and `Puppy Clicker [Beta]`.
