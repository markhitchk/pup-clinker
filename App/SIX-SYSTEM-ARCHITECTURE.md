# Puppy Clicker — Six-System Kotlin Architecture

The active Android codebase is being consolidated around six canonical Kotlin system files:

1. `PuppyAppSystem.kt` — navigation and application-level link contracts.
2. `PuppyGameSystem.kt` — gameplay state, prestige/economy models, upgrade catalog, and shared save contract.
3. `PuppyUiSystem.kt` — canonical Play, Care, Shop, and Rewards Compose surfaces.
4. `PuppyPlayerSystem.kt` — player preferences, birthday validation, developer unlock, motion, and haptic policy.
5. `PuppyExchangeSystem.kt` — pure exchange models, transfer rules, recovery, and transaction engine.
6. `PuppyDataSystem.kt` — save compatibility, external save handling, crypto diagnostics, and code anti-abuse rules.

Specialized implementation files remain where isolation materially improves safety or testability, including Settings, onboarding flows, WebRTC/session transport, streamed assets, secure-save crypto, and Android notification/runtime integration.

Legacy V1–V5 Activities and legacy ViewModels are removed from this refactor branch. Shared V5-named upgrade identifiers remain source-compatible because current V6 save data and upgrade IDs still depend on those stable serialized names.

## Rules

- New gameplay models belong in `PuppyGameSystem.kt`.
- New main-tab UI belongs in `PuppyUiSystem.kt`.
- Do not reintroduce versioned Activity/ViewModel implementations.
- Preserve serialized preference keys and Puppy IDs unless an explicit migration is added.
- Keep network/crypto implementations isolated when combining them would weaken testability.
