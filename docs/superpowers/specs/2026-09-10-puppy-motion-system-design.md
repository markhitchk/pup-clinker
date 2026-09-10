# Puppy Clicker System-Wide Motion & Animation Design

Date: 2026-09-10
Status: Design approved in chat; implementation not started
Repository: `markhitchk/pup-clinker`

## 1. Goal

Create one consistent UI/UX motion system for Puppy Clicker across navigation, gameplay, roster, shop, rewards, settings, onboarding, Puppy Codes, loading states, errors, warnings, dialogs, counters, and Danger Zone flows.

The motion style is **Hybrid / Medium intensity**:
- navigation and menus feel smooth and premium;
- gameplay feedback is more playful;
- puppy artwork itself remains static;
- all motion is interruptible and must never delay user input or state changes;
- every motion category has a Reduce Motion variant;
- haptics are out of scope.

## 2. Core principles

1. Motion communicates state; decoration is secondary.
2. User input must always win over animation.
3. Puppy PNG/SVG artwork is never animated, warped, scaled, bounced, blinked, or simulated as character animation.
4. Animate parent containers, borders, overlays, indicators, shadows, counters, and feedback layers instead.
5. Reuse one central motion system; do not hard-code unrelated `tween(...)` values throughout screens.
6. Reduced Motion is a global override, not a per-screen exception.
7. Adaptive Performance may simplify expensive effects but must not silently change user settings.
8. Repeated or conflicting effects collapse into the newest state instead of queueing.

## 3. Motion modes

### 3.1 Full Motion
Normal runtime mode when animations are enabled and performance is healthy.

Includes:
- contextual navigation transitions;
- card lift / glow / badge animation;
- animated counters;
- contextual celebrations;
- loading skeletons / shimmer where enabled;
- localized error shake / pulse;
- lightweight decorative motion.

### 3.2 Performance Motion
Automatically selected when rendering load is high and Adaptive Performance is enabled.

Simplifications happen in this order:
1. remove particles and decorative bursts;
2. reduce large shadows / expensive elevation changes;
3. reduce simultaneous card animations;
4. shorten large navigation transitions;
5. prefer transforms / alpha changes over layout-heavy animation;
6. retain critical state feedback, confirmations, warnings, and navigation clarity.

This mode must not toggle or overwrite the user's Reduce Motion setting.

### 3.3 Reduced Motion
Explicit accessibility mode controlled from Settings.

Reduce Motion applies to **all** animation categories.

When enabled:
- no shaking;
- no flashing;
- no bouncing;
- no spring overshoot;
- no large slides;
- no particle bursts;
- no animated grid reflow;
- no long count-up sequences;
- no large scale changes.

State feedback remains through short fades, static color emphasis, border emphasis, checkmarks, progress indicators, and immediate value updates.

## 4. Settings UI

Add a dedicated collapsible **Motion & Animations** section to the existing Settings UI.

All motion-system controls live here.

### 4.1 Motion Preset
Options:
- **Minimal**
- **Balanced** (default)
- **Playful**
- **Custom**

Changing an individual motion option switches the active preset to `Custom` automatically.

Presets do not alter Reduce Motion. If Reduce Motion is enabled, the selected preset remains saved underneath and is restored when Reduce Motion is disabled.

### 4.2 Individual controls

- Animated UI — master toggle for non-essential UI animation.
- Animation Intensity — Low / Medium / High.
- Button Animations.
- Screen Transitions.
- Card & Selection Animations.
- Counter Animations.
- Reward & Unlock Celebrations.
- Error & Warning Animations.
- Loading Animations.
- Skeleton / Shimmer Effects.
- Adaptive Performance.
- Reduce Motion.
- Live Motion Preview.
- Reset Motion Settings.

### 4.3 Settings precedence

1. **Reduce Motion ON** → runtime uses Reduced Motion regardless of other motion preferences.
2. **Animated UI OFF** → non-essential UI motion is disabled, while essential state feedback remains.
3. **Button Animations OFF** → removes button press animation while allowing other configured motion.
4. Individual category toggles affect only their categories.
5. Adaptive Performance may simplify active effects without changing stored preferences.

When Reduce Motion is active, other animation options remain visible and retain their values. The UI should indicate that they are **Limited by Reduce Motion** rather than disabling or resetting them.

### 4.4 Runtime status

Display the currently resolved runtime motion state in Settings:
- Full Motion
- Performance Motion
- Reduced Motion

The user should be able to see when Adaptive Performance is simplifying effects.

## 5. Central component architecture

Create a shared motion layer rather than duplicating animation decisions in each screen.

Suggested responsibilities:

### `PuppyMotion.kt`
Central timing tokens, easing curves, spring definitions, transform distances, scale limits, and semantic animation specs.

### `PuppyMotionMode`
Runtime enum / sealed model:
- `FULL`
- `PERFORMANCE`
- `REDUCED`

### `PuppyMotionPreferences`
Represents user-facing motion settings and selected preset.

### `LocalPuppyMotion`
CompositionLocal that exposes the fully resolved motion policy to composables.

### `PuppyPerformanceState`
Tracks whether expensive effects should be simplified. It must not mutate stored user preferences.

### `PuppyScreenTransition`
Shared destination transition wrapper.

### `PuppyAnimatedCounter`
Reusable counter animation for treats, currencies, XP, streaks, progress values, and reward quantities.

### `PuppySelectableCard`
Reusable selected-card behavior: persistent border/glow, temporary lift/shadow, selection badge, and optional focused dimming of surrounding cards.

### `PuppyFeedbackOverlay`
Shared presentation layer for banners, errors, warnings, unlocks, and major celebrations.

### `PuppyLoadingState`
Shared loading lifecycle:
- placeholder;
- loading;
- content;
- fallback / failure.

## 6. Semantic motion APIs

Screens should request semantic behavior instead of supplying raw animation values.

Examples:
- `motion.navigation.bottomDestination`
- `motion.navigation.modal`
- `motion.card.selection`
- `motion.card.favorite`
- `motion.counter.smallGain`
- `motion.counter.largeGain`
- `motion.feedback.minorSuccess`
- `motion.feedback.majorCelebration`
- `motion.feedback.normalError`
- `motion.feedback.criticalWarning`
- `motion.loading.contentReveal`
- `motion.settings.expandCollapse`

Each semantic spec resolves differently for Full, Performance, and Reduced Motion.

## 7. Standard timing tiers

Use a small common timing scale instead of arbitrary durations.

- **Instant:** 90–120 ms — icon/state feedback, checkbox changes.
- **Quick:** 140–180 ms — button press, favorite heart, small error feedback.
- **Standard:** 200–260 ms — cards, tabs, filters, small panels.
- **Screen:** 260–340 ms — destination transitions, larger surfaces.
- **Celebration:** 350–500 ms — unlocks, rare rewards, milestones.

Normal navigation uses smooth deceleration, not spring physics.
Interactive card selection may use a light spring with minimal overshoot.
Error shakes remain short and localized.

## 8. Visual intensity rules

Balanced / Medium is the default.

Typical limits:
- card lift / scale emphasis: roughly 2–4%;
- navigation rise distance: roughly 24–48 dp;
- selection indicator transitions: roughly 150–220 ms;
- avoid oversized zoom effects;
- major celebrations may exceed normal motion limits briefly but must remain bounded.

## 9. Navigation

### Bottom bar
Navigation is context-aware by destination.

- Play / Care: keep stable chrome where appropriate while gameplay content rises from the bar.
- Roster / Shop / Rewards: stronger destination surface rises from the bottom bar.
- Settings: restrained fade / slight scale transition.
- Re-selecting the current destination does not replay its entrance.
- Rapid destination changes redirect the active transition instead of queueing multiple full transitions.

### Modals
Dialogs and confirmation flows animate independently above the current screen. Opening a modal suppresses non-essential decorative animation behind it.

## 10. Play screen

- Fastest-feeling screen.
- Small treat / click gains produce quick UI pulse feedback around the static puppy area.
- Small counter changes pop; large gains may count smoothly.
- Progress bars interpolate smoothly.
- Newly available actions receive one brief emphasis cue.
- Returning to Play should not replay an oversized entrance.

## 11. Care screen

- Softer motion profile.
- Care actions animate the relevant status / meter.
- Completed actions briefly highlight.
- Status changes crossfade.
- Cooldown / unavailable states transition cleanly rather than abruptly jumping.

## 12. Roster screen

Roster receives the richest card interaction motion.

- Screen rises from bottom navigation.
- All / Unlocked / Locked and category filters reflow the grid smoothly.
- Selection combines:
  - persistent border / glow;
  - temporary card lift + shadow;
  - short selected badge / checkmark animation.
- Surrounding cards dim only in focused selection / confirmation flows, not permanently.
- Favorite heart fills and pulses red.
- Selected puppy panel updates via crossfade.
- Locked cards preserve their lock overlay.
- Static puppy art never animates.
- Do not individually animate every card during large filter changes; animate grid structure plus newly appearing / selected cards only.

## 13. Shop screen

- Transactional, restrained motion.
- Screen rises from bottom navigation.
- Item cards use subtle press feedback.
- Successful purchase briefly highlights the purchased item.
- Currency counters animate contextually.
- Insufficient currency uses localized error pulse / shake according to severity settings.

## 14. Rewards and Prestige

- Routine claims use compact success banners and counter updates.
- Major rewards, rare rewards, puppy unlocks, streak milestones, achievements, and special Puppy Code rewards use centered celebration overlays.
- Prestige confirmation is serious and restrained because it resets progression.
- Reduce Motion converts major celebrations to short fades / highlights without large movement.

## 15. Settings screen

- Calmest motion profile.
- Collapsible sections use shared expand / collapse specs.
- Chevron / disclosure indicators animate from the central motion system.
- Toggles / sliders animate state when enabled.
- Theme and accent changes crossfade instead of flashing.
- Motion-setting changes apply immediately to the running UI.
- Live Motion Preview reflects preset, intensity, category toggles, Adaptive Performance, and Reduce Motion.

## 16. Puppy Codes

- Validation uses a contained loading / progress state.
- Successful redemption uses positive state feedback followed by the appropriate reward treatment.
- Normal invalid codes use localized input error motion.
- Cooldown / version mismatch / compatibility problems use stronger banners.
- Flagged or severe conditions use stronger warning treatment.
- No infinite loading state; every request resolves to success, failure, fallback, or timeout state.

## 17. Errors and warnings

Use severity-based motion.

### Minor
- brief red highlight / pulse on affected component.

### Normal
- localized shake + red highlight.

### Serious
- stronger border pulse + modal emphasis + restrained screen-level warning effect.

### Critical / Danger Zone
- strongest warning presentation;
- animated red screen border when normal motion is allowed;
- focused modal;
- hold progress;
- escalating emphasis while hold completes;
- clear completion / cancellation state.

Reduce Motion removes shake and flashing and uses static red hierarchy plus short fades.

## 18. Danger Zone

The existing hold-to-confirm flow remains functionally unchanged.

Motion enhancements:
- warning modal entrance;
- red screen-border warning treatment;
- 10-second hold-progress fill;
- progressive visual emphasis while holding;
- explicit completed state;
- immediate cleanup on cancellation.

Motion must never shorten, bypass, or weaken the confirmation requirement.

## 19. Counters

All counter styles are supported contextually.

- small changes → quick pop / pulse;
- larger changes → smooth count-up / count-down;
- important currency / progression change → count animation + temporary emphasis;
- repeated updates merge toward the newest target instead of spawning overlapping animations.

Reduce Motion uses immediate value changes plus optional brief static color / alpha emphasis.

## 20. Loading and streamed assets

Use both skeletons and progress indicators.

- Skeletons for card / content placeholders.
- Progress indicators only for meaningful operations that genuinely require waiting.
- Loaded content crossfades into place.
- Failed streamed assets transition into an explicit fallback state.
- Never leave a perpetual spinner after an unrecoverable failure.
- Reduce Motion replaces shimmer-heavy states with static placeholders and minimal fades.

## 21. Buttons and controls

Contextual micro-animation:
- normal buttons → subtle press feedback;
- primary actions → stronger emphasis;
- toggles / sliders → animated state changes;
- destructive controls → warning-specific feedback.

Button animation can be disabled independently in Settings.

## 22. Tabs, filters, and collapsible sections

- Tabs use sliding active indicators.
- Filter changes crossfade / reflow content.
- Collapsible sections animate height and content visibility.
- Preserve scroll position.
- Reduced Motion uses immediate layout changes with minimal fade / indicator updates.

## 23. Celebration hierarchy

### Minor event
Use compact banner / toast.

Examples:
- routine save confirmation;
- favorite change;
- minor reward;
- simple confirmation.

### Major event
Use centered celebration overlay.

Examples:
- new puppy unlock;
- rare reward;
- milestone achievement;
- important streak milestone;
- special Puppy Code success.

Only one major celebration may run at once.

## 24. Animation priority

Priority order:

1. critical state / safety feedback;
2. direct user interaction feedback;
3. navigation;
4. decorative motion.

Higher-priority events may cancel lower-priority effects immediately.

## 25. Concurrency and interruption

- maximum one screen transition at once;
- maximum one major celebration at once;
- maximum one warning-level screen effect at once;
- localized minor effects may coexist when they affect different components;
- repeated effects on the same component collapse into the newest state;
- leaving a screen cancels screen-local animation jobs;
- navigation changes redirect active transitions instead of queuing them.

## 26. Error handling

The motion engine itself must be non-critical.

If animation state fails or an effect cannot render:
- underlying game action still completes;
- destination navigation still succeeds;
- reward / purchase / save state remains authoritative;
- UI falls back to immediate/static presentation;
- animation failure must never block gameplay.

Streamed-asset failures resolve to defined fallback states.
No animation should be required to reveal information needed to understand success, failure, or confirmation state.

## 27. Testing strategy

### Unit tests
Test motion preference resolution:
- presets;
- Custom activation;
- Reduced Motion precedence;
- Animated UI master toggle;
- category toggles;
- Adaptive Performance state mapping;
- settings persistence / restoration.

### Compose UI tests
Verify:
- bottom navigation changes destination correctly with animations enabled / disabled;
- repeated rapid tab taps do not queue stale destinations;
- roster selection state remains correct under Full / Performance / Reduced modes;
- favorite state persists independent of visual pulse;
- Settings changes apply live;
- Reduce Motion affects every category;
- Danger Zone confirmation timing / hold requirement is unchanged;
- modal actions remain clickable throughout animation lifecycle;
- loading failure exits spinner state;
- celebration overlays do not block unrelated navigation after dismissal.

### Performance tests
Exercise:
- large dynamic roster;
- rapid filter changes;
- repeated counter updates;
- multiple streamed assets completing together;
- navigation under loading / background work;
- major celebration while counters update.

Target smooth 60 FPS where device capability allows. Adaptive Performance should simplify decoration before critical feedback.

### Regression tests
Ensure game logic, saves, unlocks, purchases, code redemption, Prestige, and Danger Zone behavior remain identical regardless of animation settings.

## 28. Migration strategy

1. Add the central motion preferences model and settings UI.
2. Add central motion tokens / semantic specs.
3. Integrate theme / CompositionLocal resolution.
4. Migrate existing Settings animation code.
5. Migrate existing onboarding / overlay / Danger Zone motion.
6. Add shared screen-transition infrastructure.
7. Migrate Play / Care.
8. Migrate Roster.
9. Migrate Shop / Rewards / Prestige.
10. Migrate Puppy Codes, loading, errors, celebrations, and counters.
11. Remove duplicated raw animation constants after all consumers are migrated.
12. Run full regression, accessibility, and performance verification.

Do not build the new engine on top of duplicated legacy motion indefinitely. The target state is one central policy.

## 29. Explicit exclusions

Do not animate:
- puppy image artwork itself;
- streamed bitmap dimensions;
- puppy facial features, tails, ears, blinking, breathing, or fake sprite behavior;
- security / anti-cheat state in a way that obscures its meaning;
- hidden game-state mutations;
- critical destructive countdown / hold duration;
- background work purely for decorative purposes;
- haptics.

Do not add motion that:
- blocks taps;
- delays navigation;
- changes gameplay timing;
- causes state to depend on animation completion;
- makes content unreadable;
- ignores Reduce Motion;
- causes indefinite loading;
- queues repeated stale interactions.

## 30. Success criteria

The motion update is successful when:
- Puppy Clicker feels like one coherent application across all screens;
- navigation is smooth and interruptible;
- gameplay feedback is playful without animating puppy art;
- settings expose every motion-related control;
- Balanced / Medium is the default experience;
- Reduce Motion affects the entire app;
- Adaptive Performance can simplify effects without modifying preferences;
- dynamic roster performance remains stable as future asset groups are added;
- animation failure never blocks gameplay;
- existing game logic and safety confirmations remain unchanged.
