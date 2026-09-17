# Puppy Clicker Gacha, Profile Progression, and Play Reward Fixes

## Scope

This change addresses three Android app behaviors reported from the 1.7.14 build.

1. Puppy Gacha must never return a puppy that is already unlocked. Once no unowned eligible puppies remain, a pull must be unavailable and must not spend Treats or Common Tickets.
2. Player XP/level progress and V6 Achievements must be shown under Settings > Profile, not on the Rewards tab.
3. The Play screen's "Next Reward" strip must be driven by the current streamed/fallback daily goal data. It must skip already-claimed goals and disappear when there is no unclaimed goal.

## Behavioral requirements

### Gacha

- Redeem-only puppies remain excluded.
- Duplicate roster entries remain de-duplicated by ID.
- `pullPool()` returns only unowned eligible puppies.
- If the collection has no unowned eligible puppy, the ViewModel returns `NO_ELIGIBLE_PUPPIES` before charging either currency.
- The Gacha UI disables both pull buttons when the unowned pool is empty and describes the collection as complete without advertising owned-puppy pulls.

### Profile progression

- The Rewards screen no longer receives the generated Player Level and Achievements sections.
- Settings > Profile observes the current `V6GameState` and renders the Player Level card followed by the Achievements card.
- Existing profile identity, birthday, and release-badge behavior stays intact.

### Play next reward

- The next reward is the first current daily goal whose ID is not in `state.claimedDailyTasks`.
- The strip uses that goal's emoji, title, current progress, target, and Treat reward.
- Completed but unclaimed goals may still appear as the next reward until they are claimed.
- If every current goal has been claimed, the strip is omitted entirely.

## Verification

- Add/adjust JVM tests for Gacha duplicate prevention and next-unclaimed-goal selection.
- Add generated-source patch regression coverage proving progression UI is moved from Rewards to Profile.
- Run Python patch regression tests, JVM unit tests, Android test compilation, and release APK build before signing.