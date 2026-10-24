# Part 6 — Real Android Screenshot Parity Workflow

**Status: NOT CERTIFIED.** Source ports and green unit tests do not prove that React looks or behaves exactly like Kotlin. This procedure records comparable screenshots and animation evidence without touching Kotlin production data.

## Prerequisites

1. Obtain the **Kotlin reference APK** from the signed, existing production build or a trusted test build. Keep it installed. Do **not** uninstall the original app or clear its production data.
2. Install only the separately packaged **Puppy Clicker React Native Preview** APK, package `com.harleytg.puppyclicker.preview`, after its GitHub Actions workflow succeeds and verifies the embedded `assets/index.android.bundle`, APK signature and package ID.
3. Compare on **the same physical device and Android user profile**, such as a Galaxy A17 5G. Use the same Android display size, screen resolution, text size, wallpaper-independent appearance, 12/24-hour clock, theme, system bars, brightness and rotation. For screens with random outcomes, use a deterministic **test** source state—not production account data.
4. Install Android Platform Tools (`adb`) and Python 3 with Pillow (`python3 -m pip install Pillow`). Do not commit personal screenshots, keys or save data.

## Screens and states to capture

Use identical filename for each matching Kotlin/React image; replace each screenshot only after confirming it represents the **same screen and the same logical state**.

| Screen | Required reference states | Must compare |
| --- | --- | --- |
| Play | Idle, tapping/combo, XP/currency change | Logo, puppy image, fonts, wallet, scrolling/touch |
| Care | All five meters, Feed/Play/Rest/Groom/Cuddle, Dog Park active/completed | Meter width/spacing, portrait crop, button positions |
| Daily | Day gift, claimed, monthly goals, empty/network fallback | Date, goals, reward copy, scroll viewport |
| Shop | Treat, Bone/ticket upgrades, owned/locked accessories | All tabs, badges, currency, 50K Prestige condition |
| Prestige | Locked, unlocked skills, confirmation dialogs | Cards, ranks, price, resets |
| Roster | V1/V2 locked/unlocked, rename, accessories, advanced browser | Source art, sections, scrims, modal overlays |
| Gacha | Start, knob turn, capsule closed/open, reward dialog | Native machine art, knob/capsule geometry, animation |
| Casino | Hub idle, pending/resumed round | Balance, game tiles, back behavior |
| Blackjack | Pre-deal, dealer hide, split hands, hit/stand/double, win/bust | Table proportions, card placement, action grid, payouts |
| Slots | Idle/lever, three reels moving/stopped, loss/win | Reel symbols, timing, masked cabinet, lever |
| Roulette | Wheel+ball moving, 0–36 picker, straight/outside bet, win/loss | Wheel pocket, pointer, tappable overlay, result |
| Plinko | 8-bounce trajectory, each bin, result | Peg spacing, ball and glass layers, box fit |
| Scratchers | Each tier, untouched 960-mask, 40% scratched, 68% reveal | Original Pup Coin movement, metallic mask, frame |
| Lucky Wheel | Seven segments, pointer clicks, every prize, ineligible Pup refund | Source disc labels, angle, pointer, 2.65s spin |
| Settings / Profile / Admin | Visible menus, toggles, warnings, signed-out/onboarding | Feature completeness; native production auth is not yet ported |

## Capture identical screenshots using ADB

Capture the Kotlin app, then the React preview, *at the same device settings*. Example for a paired Play idle screen:

```bash
mkdir -p screenshots/{kotlin,react,report}
adb shell am force-stop com.harleytg.puppyclicker
adb shell monkey -p com.harleytg.puppyclicker 1
# Navigate to Play and set a deterministic comparison state on the device.
adb exec-out screencap -p > screenshots/kotlin/play_idle.png

adb shell am force-stop com.harleytg.puppyclicker.preview
adb shell monkey -p com.harleytg.puppyclicker.preview 1
# Navigate to the equivalent Play state; use matching demo data.
adb exec-out screencap -p > screenshots/react/play_idle.png
```

**Do not use `pm clear`, uninstall the Kotlin app or copy live account tokens for testing.** Use a test account or staged non-production fixtures. Capture the same frames at the same animation timestamps with `adb shell screenrecord` for moving screens. Record the device model, density, fontScale, resolution, Android version, theme and package/commit versions.

## Compare pixel geometry without hiding differences

```bash
python3 -m pip install Pillow
python3 Mobile/scripts/android_visual_audit.py \
  --kotlin screenshots/kotlin --react screenshots/react \
  --output screenshots/report --tolerance 8 \
  --fail-fraction 0.01
```

The script requires **identical screenshot resolution**; it refuses to silently resize a mismatched pair. By default it considers RGB differences of more than 8/255 in any channel and writes side-by-side `*.comparison.png`, red-highlighted `*.diff.png` and machine-readable `report.json`. The optional 1% threshold is only a quantitative smoke-test **gate**, not sufficient to claim 100% identical visuals. A 0% screenshot difference is a stronger static result but still does not prove animations, accessibility, gameplay, cloud sync or authorization.

Capture additional states in landscape where the Kotlin screen supports it and at system large text. Record a **pass/fail issue** for each mismatch and retest after the corresponding React fix. Attach screenshot and video evidence to a designated private QA workflow when appropriately authorized; do not add sensitive production content to the public repository.

## Release gates

- GitHub React unit tests **green on the exact branch commit**.
- Expo Android export and generated native Gradle build complete.
- Signed **preview** standalone APK produced, `index.android.bundle` verified and preview package validated; physically installs and opens without Metro.
- Matched screenshots and video for every relevant screen/interaction, followed by fixes and re-capture.
- Supabase save migration, Discord sign-in, one-device rule, PupEye bans, secure casino server transactions and restricted puppy entitlements implemented and tested separately in a staging environment.
- Production update *only* with appropriate signing credentials, tested account transfer and explicit owner release approval.

**Current evidence:** Part 5F source port committed. A completed Preview APK run `40068347188` at commit `76c118ad` failed **8 of 147** tests and produced no verified standalone APK. Part 6 fixed those eight detected causes in subsequent commits; newer runs must be independently confirmed green. No user-device screenshots are attached, and 100% parity must not be claimed.
