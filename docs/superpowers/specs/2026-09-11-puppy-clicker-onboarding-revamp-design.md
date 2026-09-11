# Puppy Clicker Onboarding Revamp Design

**Date:** 2026-09-11

## Goal

Replace the current six-step, card-heavy onboarding with a cleaner five-step flow that is more compact, easier to scan, better on small screens, and clearer about profile setup choices while preserving Puppy Clicker's existing save, identity, Discord, notification, and security behavior.

## Approved Flow

1. Welcome
2. Player Setup
3. Personalize
4. Notifications
5. Ready

The existing six-step setup is migrated to five steps. Existing installs that have already completed setup remain completed and are not forced through onboarding again.

## Shared Onboarding Shell

Every step uses one consistent shell.

- Show `Step X of 5`, the current step title, and a thin progress indicator.
- Use a constrained maximum content width so tablets do not stretch controls excessively.
- Keep horizontal padding compact and consistent.
- Allow the main step body to scroll independently.
- Keep Back / Continue controls visually consistent and reachable near the bottom of the screen.
- Respect safe drawing insets.
- Preserve the existing reduced-motion behavior for transitions.
- Keep entered state when navigating backward and forward.
- Persist the current setup step so reopening the app resumes at the same place.
- Use fewer nested cards and fewer explanatory paragraphs.
- Avoid emoji-heavy section headers when an existing app icon or plain label is clearer.
- Keep PupEye as a small secondary status/branding element rather than a major setup card.
- Present legal links and the active-development notice as lightweight supporting content.

## Step 1: Welcome

The welcome screen is intentionally minimal.

### Content

- Smaller Puppy Clicker logo.
- Heading: `Welcome to Puppy Clicker`.
- One concise sentence describing the game.
- Primary action: `Get Started`.
- Secondary access to Discord, Terms of Use, and Privacy Policy.
- Lightweight active-development notice.
- Small PupEye branding/status indicator.

### Removed visual weight

- Remove the large standalone PupEye information card.
- Avoid large stacked support cards.
- Keep Discord as a secondary link/action rather than a dominant card.

## Step 2: Player Setup

Step 2 is the primary UX cleanup.

### Entry state

A new player sees three compact, mutually exclusive setup methods:

- **Local Profile** — create a username stored on this device.
- **Discord** — optionally verify with Discord OAuth.
- **Import Save** — restore an existing Puppy Clicker `.pupsave`.

Local Profile is selected by default for a fresh setup.

Only the active method's controls are expanded. Switching methods does not erase uncommitted local username text or create destructive side effects.

### Local Profile

Show:

- Username input.
- Concise allowed-character guidance.
- Primary action: `Create Local Profile`.

Behavior:

- Normalize username using the existing PuppyPlayerIdentity rules.
- Reject empty/invalid usernames.
- Preserve the existing banned-word / moderation popup behavior.
- No online account is required.
- Continue immediately to Personalize after successful local profile creation.

### Discord

Show only the compact Discord state needed for setup.

Disconnected state:

- Discord icon.
- Short explanation that Discord verification is optional.
- `Connect Discord` button.

Authorizing/exchanging state:

- Compact progress/status text.
- Disable duplicate taps while OAuth is in progress.

Connected state:

- Display name if available.
- Discord `@username`.
- Connected indicator.
- `Continue with Discord`.
- Secondary `Use a Different Discord Account` action.

Behavior:

- Use the existing Discord PKCE flow.
- Request only `identify`.
- Do not persist the OAuth access token.
- A cancelled OAuth flow returns cleanly to Player Setup without trapping the user.
- Network, callback, or OAuth failures remain recoverable on Step 2.
- The Discord username still passes Puppy Clicker's username moderation/fallback logic.
- Discord does not replace the device-bound Player ID, Friend Code, save ownership model, or PupEye behavior.

### Import Save

Initial state:

- `Choose .pupsave` action.
- No password field until needed by the selected save format.

Encrypted v3 save:

- Reveal backup-password input when required.
- Password is never persisted.

Legacy save:

- Import without requiring a password.

Import behavior:

- Reuse the existing GameSaveTransfer validation, encryption, authentication, and restoration path.
- Reject corrupt, modified, unsupported, or incorrectly passworded files.
- Preserve existing username-ownership validation.
- Reload the active V6 ViewModel after a successful import.
- Restore the save's game progress and display username.
- Keep the current device's Player ID and Friend Code.
- Continue to Personalize after successful restore.
- Import does not mark onboarding complete by itself.
- Setup-time AFK accumulation must not be created as a side effect of import or onboarding.

### Step 2 error behavior

Errors appear near the relevant method controls where practical.

Required cases:

- Empty or invalid local username.
- Banned username.
- Discord cancelled.
- Discord denied.
- Discord malformed callback.
- Discord network failure.
- Wrong save password.
- Corrupt save.
- Unsupported save version.
- Username ownership mismatch.

## Step 3: Personalize

Birthday and appearance are merged into one organized screen.

### Birthday

- Month selector.
- Day selector.
- Explicit `Skip birthday` option.
- Month/day only; no birth year.
- Birthday remains local/private.
- Validate month/day combinations, including February.
- If skipped, clear any partially selected onboarding birthday state rather than persisting an invalid date.

### Appearance

Show compact groups for:

- Theme: System / Light / Dark.
- Accent selection.
- UI scale: Compact / Default / Large.
- Reduced Motion.

Behavior:

- Preference changes preview immediately using the existing reactive PuppyUiPreferences state.
- Reduced Motion takes effect immediately for onboarding transitions.
- Keep the current compact UI scale as the default unless the user changes it.

### Preview

Include one small preview surface demonstrating the active theme/accent choices. It should not become another large card or duplicate the full app UI.

## Step 4: Notifications

Use three compact toggle rows:

- Daily rewards.
- Game events.
- App updates.

### Permission behavior

- Notification preferences can be configured independently of Android runtime permission.
- Request Android notification permission only when needed and supported by the OS.
- If permission is already granted, do not prompt again.
- If permission is denied, setup remains usable.
- Provide `Not Now` / continue behavior so notification permission never blocks onboarding.
- Explain briefly that notification preferences can be changed later in Settings.

## Step 5: Ready

This is a compact summary rather than another settings form.

### Summary fields

Show only relevant values:

- Puppy Clicker username.
- Player ID.
- Friend Code.
- Discord status if connected.
- Imported-save status if a save was restored during this onboarding session.
- Birthday only if provided.
- Theme / UI preference summary.

### Actions

- Primary: `Start Playing`.
- Secondary: `Review Setup`, returning to the appropriate onboarding screen without losing state.

### Completion behavior

- Mark setup complete exactly once when the player starts playing.
- Preserve existing seasonal-intro dismissal behavior.
- Do not allow imported saves or notification decisions to bypass the final completion action.
- Reopening after completion must go to the normal app rather than onboarding.

## Component Boundaries

The revamp should reduce the size and responsibility of the existing monolithic onboarding file.

### `PuppyOnboardingUi.kt`

Responsibilities:

- Own the current onboarding step.
- Map five-step flow transitions.
- Persist/resume setup step.
- Coordinate shared onboarding session state.
- Route to focused step composables.

### `PuppyOnboardingShell.kt`

Responsibilities:

- Progress label and progress indicator.
- Constrained responsive content width.
- Safe-inset handling.
- Scrollable step content.
- Consistent bottom navigation treatment.
- Shared secondary/footer presentation.

### `PuppyOnboardingPlayerSetup.kt`

Responsibilities:

- Player setup method selection.
- Local username flow.
- Discord setup presentation.
- Save-import onboarding presentation.
- Method-local loading and error states.

It must call existing identity, Discord, and save-transfer APIs rather than duplicating their core logic.

### `PuppyOnboardingPersonalize.kt`

Responsibilities:

- Optional birthday.
- Theme.
- Accent.
- UI scale.
- Reduced Motion.
- Lightweight preview.

### `PuppyOnboardingNotifications.kt`

Responsibilities:

- Notification category preferences.
- Runtime permission request behavior.
- Non-blocking denial / skip behavior.

### `PuppyOnboardingReady.kt`

Responsibilities:

- Setup summary.
- Review routing.
- Final setup completion action.

### `GameSaveTransfer.kt`

Keep:

- Save format handling.
- Encryption/decryption.
- Identity validation.
- Store restoration.
- PupEye sealing.
- External save mirroring.

Onboarding may add a compact adapter/composable around these APIs, but must not duplicate the save format or crypto implementation.

## Session State

The onboarding flow needs transient session state for presentation-only information that should not become permanent profile data.

Examples:

- Selected Player Setup method.
- Whether a save was imported during the current onboarding session.
- Import file selection state.
- Import processing/error status.
- Whether birthday was explicitly skipped.

Do not persist secrets such as save passwords or OAuth access tokens.

## Migration From Six Steps to Five

The current stored setup step range is 0..5. The new range is 0..4.

Required migration behavior for incomplete setup:

- Old 0 -> new 0 (Welcome)
- Old 1 -> new 1 (Player Setup)
- Old 2 -> new 2 (Personalize)
- Old 3 -> new 2 (Personalize)
- Old 4 -> new 3 (Notifications)
- Old 5 -> new 4 (Ready)

Completed setups remain completed.

The migration must be deterministic and covered by unit tests.

## Accessibility and Responsive Requirements

- Support small Android phones without horizontal clipping.
- Support large text/font scaling.
- Use touch targets consistent with Material guidance.
- Avoid relying solely on color to indicate selected setup method.
- Preserve screen-reader-friendly content descriptions for icons.
- Keep primary actions understandable without emoji.
- Respect Reduced Motion immediately.
- Avoid large fixed-height regions that can hide controls behind the keyboard.
- Keep the username and password inputs visible when the keyboard is open.

## Security and Privacy Requirements

- Discord continues to use PKCE and state validation.
- Discord scope remains `identify` only.
- Do not persist Discord OAuth access tokens.
- Save passwords remain transient.
- Do not weaken existing save authentication, username ownership validation, PupEye sealing, or external-save mirroring.
- Player ID and Friend Code remain device-bound.
- Imported save data cannot silently replace device identity.
- Birthday remains month/day only and local.
- No onboarding path may introduce AFK rewards before setup is complete.

## Testing Strategy

Follow test-driven development for new behavior.

### Unit tests

Add or update tests for:

- Six-step to five-step migration mapping.
- Resume-at-current-step behavior.
- Default Player Setup method.
- Player Setup method selection behavior.
- Local username eligibility and moderation delegation.
- Birthday optionality.
- Birthday month/day validation.
- Save import state on success.
- Save import state on failure.
- Setup completion only from Ready.
- Notification permission decision logic.
- Imported-save session summary state.
- No setup-time AFK reward regression.

### Existing regression coverage

Retain and run:

- Discord PKCE URL/challenge tests.
- Save-transfer tests.
- Existing app JVM tests.
- Android instrumentation-test compilation.

## Build and Signing Acceptance Criteria

Before release:

1. GitHub Actions JVM regression tests pass.
2. Android instrumentation tests compile.
3. Release APK builds successfully.
4. Sign the release with the existing permanent Puppy Clicker signing key.
5. Verify APK signature schemes v2 and v3.
6. Generate and verify the v4 `.idsig` sidecar against the final APK.
7. Confirm the signing certificate matches the previous permanent-signed Puppy Clicker APK.
8. Provide:
   - final signed APK,
   - v4 `.idsig`,
   - verification report,
   - optional ZIP bundle containing all three.

## Non-Goals

This revamp does not:

- Replace Puppy Clicker's device-bound Player ID or Friend Code.
- Add a new backend.
- Add email scope to Discord.
- Add QR codes.
- Change the save-transfer file format.
- Change PupEye's core fair-play/integrity model.
- Force existing completed users through onboarding again.
- Add unrelated Settings redesign work.
