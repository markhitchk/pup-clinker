# Cartoon Casino Playing Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild Puppy Gacha and the Puppy Casino playing fields to match the approved realistic-cartoon renders while preserving all existing game engines, balances, save/recovery behavior, and feature flags.

**Architecture:** Add one shared Compose visual toolkit for dimensional cartoon surfaces, then refactor only the presentation composables in the five game UI files. Outcome generation and transaction code remain unchanged. Canvas is used for dimensional boards and animation so the implementation stays Kotlin/Java-only and does not depend on baked screenshot assets.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose Canvas, existing Puppy Clicker theme/motion preferences.

**Spec:** Rendered Puppy Gacha, Lucky Pup Wheel, Pup Plinko, Pup Scratchers, and Puppy Blackjack mockups produced in the current design session.

## Global Constraints

- Do not change casino odds, wager presets, RTP values, payout logic, or settlement/recovery behavior.
- Do not change Puppy Gacha selection/unlock logic or Treat/Common Ticket costs.
- Use only Kotlin/Java/Compose drawing for the new playing fields; no Lottie/Rive/WebView or required new bitmap assets.
- Respect the existing light/dark theme, custom accent, reduced motion, and animation settings.
- Preserve current accessibility text and disabled-state behavior.
- Keep the source compatible with the existing Android/Compose versions already declared by the project.

---

### Task 1: Shared cartoon casino visual primitives

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyCasinoCartoonUi.kt`
- Test: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyCasinoCartoonUiTest.kt`

**Interfaces:**
- Produces: `PuppyCasinoCartoonPalette`, `cartoonPanelGradient`, `CartoonStageFrame`, `CartoonBonePlaque`, `CartoonPawBadge`, and shared dimensional drawing helpers.

- [ ] **Step 1: Write failing tests** for deterministic palette alpha/clamping and visual helper math used by the Canvas boards.
- [ ] **Step 2: Run unit tests** and confirm they fail because the shared visual toolkit does not exist.
- [ ] **Step 3: Implement the shared toolkit** with MaterialTheme-derived colors, gradients, highlights, shadows, and reduced-motion-safe helpers.
- [ ] **Step 4: Run unit tests** and confirm they pass.
- [ ] **Step 5: Commit** the shared toolkit.

### Task 2: Puppy Gacha machine

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyGacha.kt`

**Interfaces:**
- Consumes: shared cartoon visual primitives from Task 1.
- Preserves: `PuppyGachaEngine`, `PuppyGachaScreen`, payment logic, reveal result handling.

- [ ] **Step 1: Add/adjust tests** for the existing Gacha cost and pool behavior before presentation edits.
- [ ] **Step 2: Verify RED** for any new pure visual-state helper introduced for the machine.
- [ ] **Step 3: Replace the flat machine presentation** with a dimensional toy capsule machine: glossy dome, layered capsules, chunky rotating knob, chute, tray, highlights, shadows, and staged capsule motion.
- [ ] **Step 4: Run tests** and verify Gacha engine behavior is unchanged.
- [ ] **Step 5: Commit** the Gacha UI refactor.

### Task 3: Lucky Pup Wheel and Pup Plinko

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyLuckyWheelUi.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyPlinkoUi.kt`

**Interfaces:**
- Consumes: shared cartoon visual primitives from Task 1.
- Preserves: current outcome codecs, round commitment, settlement delays, wager presets and RTP text.

- [ ] **Step 1: Add failing pure-math tests** for pointer/segment angle mapping and Plinko interpolation helpers if extracted.
- [ ] **Step 2: Verify RED**.
- [ ] **Step 3: Rebuild Lucky Wheel** with a dimensional rim, lit studs, glossy slices, bone/paw trim, pointer tick animation and reward emphasis.
- [ ] **Step 4: Rebuild Plinko** with a dimensional arcade cabinet, metallic pegs, illuminated bins, Treat-ball shading, per-bounce motion and impact pulses.
- [ ] **Step 5: Run tests** and commit both boards.

### Task 4: Pup Scratchers and Puppy Blackjack

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyScratchersUi.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyBlackjackUi.kt`

**Interfaces:**
- Consumes: shared cartoon visual primitives from Task 1.
- Preserves: scratch coverage threshold, committed outcomes, Blackjack command/state persistence and settlement behavior.

- [ ] **Step 1: Add failing tests** for any newly extracted scratch/animation math helpers.
- [ ] **Step 2: Verify RED**.
- [ ] **Step 3: Upgrade Scratchers** with emerald/gold dimensional frame, metallic coating texture, sparkle/highlight feedback, richer hidden-symbol cells and improved Pup Coin presentation.
- [ ] **Step 4: Upgrade Blackjack** with wood/felt depth, dealer shoe, dimensional cards, paw card backs, chip/bone decoration, active-hand glow and card-deal/flip motion.
- [ ] **Step 5: Run tests** and commit both screens.

### Task 5: Verification

**Files:**
- No production source changes unless verification exposes an issue.

- [ ] **Step 1: Run `./gradlew testDebugUnitTest` from `App/`.**
- [ ] **Step 2: Run `./gradlew assembleDebug` from `App/`.**
- [ ] **Step 3: Inspect compiler output for deprecated/unsupported Compose APIs.**
- [ ] **Step 4: Compare the five screens against the rendered references at phone width and confirm no gameplay controls are clipped.**
- [ ] **Step 5: Review the final diff to confirm engine/odds/persistence code was not unintentionally changed.**
