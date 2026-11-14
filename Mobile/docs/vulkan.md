# Vulkan graphics on Android (beta)

Puppy Clicker uses React Native Skia 3.10.0's **Graphite** GPU renderer for a
non-interactive ambient yard layer in both native Play layouts. On Android the
Graphite backend runs through Vulkan 1.1. This does **not** replace React Native
buttons, puppy portraits, gameplay transactions, or native PupEye auth.

- The renderer is enabled automatically when `Skia.gpu.isAvailable` is true.
- Devices without Vulkan retain the existing native Play stage and controls;
  the optional ambient layer is not mounted. The app's Android manifest removes
  Skia's otherwise-required Vulkan `uses-feature` to avoid Play Store exclusion.
- Battery Saver, Reduced Motion, or disabling Animated UI skips the effect.
- Requires Android API 26+ and a custom Expo native build; **Expo Go cannot load
  Skia Graphite v3**.
- Web stays on its existing WebGPU → WebGL → Canvas → CSS pipeline. Browsers
  select Vulkan (or another GPU backend) themselves.

## Build and verification

```sh
cd Mobile
npm install
npm test
PUPPY_NATIVE_BETA=1 npx expo prebuild --platform android
cd android && ./gradlew :app:assembleRelease
```

Regenerate and commit `Mobile/package-lock.json` with `npm install
--package-lock-only` after changing dependencies. Test the beta APK on a
Vulkan-capable Android phone and a device or emulator with Vulkan disabled.
Verify the Play screen renders, taps remain functional, settings disable the
layer, and the PupEye account stays signed in. Visual/performance behavior needs
on-device validation before a production rollout.
