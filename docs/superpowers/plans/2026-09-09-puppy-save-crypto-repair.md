# Puppy Save Crypto Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair Android Keystore AES-GCM device-save encryption, prevent operational crypto failures from being counted as tampering, and stop repeated external-save warning floods while preserving the encrypted Android/data mirror.

**Architecture:** Keep the existing SharedPreferences runtime save and Android/data mirror architecture. Let Android Keystore generate the AES-GCM encryption IV, store that generated IV in the existing `PCE1 + IV + ciphertext/tag` container, classify integrity failures separately from provider/storage failures, and coalesce repeated identical operational logs without disabling retries.

**Tech Stack:** Kotlin, Android 14/15 APIs, Android Keystore, AES-256-GCM, SharedPreferences, JUnit 4, AndroidX instrumentation tests, Gradle 8.9.

**Spec:** `docs/superpowers/specs/2026-09-09-puppy-clicker-save-onboarding-ui-repair-design.md`

## Global Constraints

- Internal SharedPreferences remain the runtime source of truth.
- Keep Android Keystore AES-256-GCM and `.setRandomizedEncryptionRequired(true)`.
- Device encryption must not accept a caller-generated IV.
- The Android/data mirror remains non-fatal and stays at `Android/data/com.harleytg.puppyclicker/files/PuppyClicker/puppy_clicker_save.pup`.
- Authentication/ciphertext-integrity failures may increment PupEye tamper state; provider/key/storage failures must not.
- Automatic mirror writes must continue retrying after failure.
- Identical operational failures must be log-coalesced rather than emitted on every save callback.
- Password-protected transfer-save crypto is not changed unless regression tests fail.

## File Structure

- Modify `App/app/src/main/java/com/harleytg/puppyclicker/SecureSaveCrypto.kt` — device encryption IV generation and shared failure classification usage in PupEye verification.
- Modify `App/app/src/main/java/com/harleytg/puppyclicker/ExternalGameSave.kt` — external mirror failure handling, quarantine policy, recovery logging.
- Create `App/app/src/main/java/com/harleytg/puppyclicker/SaveCryptoDiagnostics.kt` — pure failure classification plus repeated-log gate.
- Create `App/app/src/test/java/com/harleytg/puppyclicker/SaveCryptoDiagnosticsTest.kt` — JVM regression coverage for classification/log coalescing.
- Create `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppySaveCryptoInstrumentedTest.kt` — real AndroidKeyStore AES-GCM regression tests.
- Modify `App/app/build.gradle.kts` — add AndroidX instrumentation test dependency required by the new crypto test.
- Modify `.github/workflows/android.yml` — compile Android instrumentation tests and continue running JVM tests before release assembly.

---

### Task 1: Add pure failure classification and log-coalescing tests

**Files:**
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/SaveCryptoDiagnosticsTest.kt`
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/SaveCryptoDiagnostics.kt`

**Interfaces:**
- Produces: `internal enum class SaveCryptoFailureKind { TAMPER, OPERATIONAL }`
- Produces: `internal fun classifySaveCryptoFailure(error: Throwable): SaveCryptoFailureKind`
- Produces: `internal class RepeatedFailureLogGate(private val cooldownMs: Long = 60_000L)` with `synchronized fun shouldLog(error: Throwable, nowMs: Long = System.currentTimeMillis()): Boolean` and `synchronized fun markSuccess(): Boolean`.

- [ ] **Step 1: Write the failing JVM test**

```kotlin
package com.harleytg.puppyclicker

import java.security.InvalidAlgorithmParameterException
import javax.crypto.AEADBadTagException
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveCryptoDiagnosticsTest {
    @Test
    fun authenticationAndContainerFailuresAreTamper() {
        assertEquals(SaveCryptoFailureKind.TAMPER, classifySaveCryptoFailure(AEADBadTagException("bad tag")))
        assertEquals(SaveCryptoFailureKind.TAMPER, classifySaveCryptoFailure(IllegalArgumentException("bad header")))
        assertEquals(SaveCryptoFailureKind.TAMPER, classifySaveCryptoFailure(JSONException("bad json")))
    }

    @Test
    fun providerParameterFailureIsOperational() {
        assertEquals(
            SaveCryptoFailureKind.OPERATIONAL,
            classifySaveCryptoFailure(InvalidAlgorithmParameterException("Caller-provided IV not permitted"))
        )
    }

    @Test
    fun identicalFailuresAreSuppressedUntilCooldownAndRecoveryResetsGate() {
        val gate = RepeatedFailureLogGate(cooldownMs = 60_000L)
        val failure = InvalidAlgorithmParameterException("Caller-provided IV not permitted")

        assertTrue(gate.shouldLog(failure, nowMs = 1_000L))
        assertFalse(gate.shouldLog(failure, nowMs = 10_000L))
        assertTrue(gate.shouldLog(failure, nowMs = 61_001L))
        assertTrue(gate.markSuccess())
        assertFalse(gate.markSuccess())
        assertTrue(gate.shouldLog(failure, nowMs = 62_000L))
    }
}
```

- [ ] **Step 2: Run the focused test and confirm it fails because the interfaces do not exist**

Run from `App/`:

```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.SaveCryptoDiagnosticsTest --stacktrace
```

Expected: compilation failure for `SaveCryptoFailureKind`, `classifySaveCryptoFailure`, and `RepeatedFailureLogGate`.

- [ ] **Step 3: Add the minimal diagnostics implementation**

Create `SaveCryptoDiagnostics.kt`:

```kotlin
package com.harleytg.puppyclicker

import java.security.GeneralSecurityException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.KeyStoreException
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import org.json.JSONException

enum class SaveCryptoFailureKind { TAMPER, OPERATIONAL }

internal fun classifySaveCryptoFailure(error: Throwable): SaveCryptoFailureKind = when (error) {
    is AEADBadTagException,
    is BadPaddingException,
    is IllegalArgumentException,
    is JSONException -> SaveCryptoFailureKind.TAMPER

    is InvalidAlgorithmParameterException,
    is InvalidKeyException,
    is KeyStoreException,
    is GeneralSecurityException -> SaveCryptoFailureKind.OPERATIONAL

    else -> SaveCryptoFailureKind.OPERATIONAL
}

internal class RepeatedFailureLogGate(
    private val cooldownMs: Long = 60_000L
) {
    private var lastSignature: String? = null
    private var lastLoggedAtMs: Long = Long.MIN_VALUE

    @Synchronized
    fun shouldLog(error: Throwable, nowMs: Long = System.currentTimeMillis()): Boolean {
        val signature = "${error.javaClass.name}:${error.message.orEmpty()}"
        val changed = signature != lastSignature
        val cooldownElapsed = lastLoggedAtMs == Long.MIN_VALUE || nowMs - lastLoggedAtMs >= cooldownMs
        if (!changed && !cooldownElapsed) return false
        lastSignature = signature
        lastLoggedAtMs = nowMs
        return true
    }

    @Synchronized
    fun markSuccess(): Boolean {
        val recovering = lastSignature != null
        lastSignature = null
        lastLoggedAtMs = Long.MIN_VALUE
        return recovering
    }
}
```

- [ ] **Step 4: Run the focused JVM test and confirm it passes**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.SaveCryptoDiagnosticsTest --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit the diagnostics helper and tests**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/SaveCryptoDiagnostics.kt \
  App/app/src/test/java/com/harleytg/puppyclicker/SaveCryptoDiagnosticsTest.kt
git commit -m "test: classify save crypto failures"
```

---

### Task 2: Reproduce and fix Android Keystore IV initialization

**Files:**
- Modify: `App/app/build.gradle.kts` dependency block
- Create: `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppySaveCryptoInstrumentedTest.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/SecureSaveCrypto.kt` in `PuppySaveCrypto.encryptDevice()`

**Interfaces:**
- Consumes: existing `PuppySaveCrypto.encryptDevice(ByteArray): ByteArray` and `decryptDevice(ByteArray): ByteArray`.
- Produces: same public/internal signatures and same `PCE1 + 12-byte IV + ciphertext/tag` device-save format.

- [ ] **Step 1: Add the AndroidX JUnit instrumentation dependency**

In `App/app/build.gradle.kts`, extend the dependency block:

```kotlin
androidTestImplementation("androidx.test.ext:junit:1.2.1")
```

Keep the existing Compose BOM and `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`.

- [ ] **Step 2: Write the AndroidKeyStore regression test before changing crypto code**

Create `PuppySaveCryptoInstrumentedTest.kt`:

```kotlin
package com.harleytg.puppyclicker

import androidx.test.ext.junit.runners.AndroidJUnit4
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PuppySaveCryptoInstrumentedTest {
    @Test
    fun deviceEncryptionRoundTripsWithProviderGeneratedIv() {
        val plain = "puppy-save-regression".toByteArray()
        val encrypted = PuppySaveCrypto.encryptDevice(plain)
        val decrypted = PuppySaveCrypto.decryptDevice(encrypted)
        assertTrue(plain.contentEquals(decrypted))
    }

    @Test
    fun identicalPlaintextProducesDifferentContainers() {
        val plain = "same-input".toByteArray()
        val first = PuppySaveCrypto.encryptDevice(plain)
        val second = PuppySaveCrypto.encryptDevice(plain)
        assertFalse(first.contentEquals(second))
    }

    @Test(expected = AEADBadTagException::class)
    fun tamperingFailsAuthentication() {
        val encrypted = PuppySaveCrypto.encryptDevice("protected".toByteArray())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()
        PuppySaveCrypto.decryptDevice(encrypted)
    }
}
```

- [ ] **Step 3: Compile the instrumentation test against the current implementation**

```bash
gradle --no-daemon :app:assembleDebugAndroidTest --stacktrace
```

Expected: test APK compiles. On a device/provider enforcing randomized Keystore IVs, running the first test against current code reproduces `InvalidAlgorithmParameterException: Caller-provided IV not permitted`.

- [ ] **Step 4: Change only device encryption initialization**

Replace the start of `encryptDevice()` with:

```kotlin
fun encryptDevice(plain: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, deviceKey())
    val iv = cipher.iv
    require(iv.size == IV_BYTES) { "Unexpected Android Keystore GCM IV length: ${iv.size}" }
    cipher.updateAAD(DEVICE_AAD.toByteArray(Charsets.UTF_8))
    val encrypted = cipher.doFinal(plain)
    return DEVICE_MAGIC.toByteArray(Charsets.US_ASCII) + iv + encrypted
}
```

Do not change `decryptDevice()`; it must continue extracting the stored IV and passing `GCMParameterSpec(GCM_TAG_BITS, iv)` in decrypt mode.

Do not change `encryptTransfer()` or `decryptTransfer()`.

- [ ] **Step 5: Recompile and run the real-device/emulator crypto tests**

Compile:

```bash
gradle --no-daemon :app:assembleDebugAndroidTest --stacktrace
```

Then, with an emulator/device connected:

```bash
gradle --no-daemon :app:connectedDebugAndroidTest --stacktrace
```

Expected: all `PuppySaveCryptoInstrumentedTest` tests PASS; no caller-provided encryption IV exception.

- [ ] **Step 6: Commit the Keystore IV repair**

```bash
git add App/app/build.gradle.kts \
  App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppySaveCryptoInstrumentedTest.kt \
  App/app/src/main/java/com/harleytg/puppyclicker/SecureSaveCrypto.kt
git commit -m "fix: let Android Keystore generate save IVs"
```

---

### Task 3: Prevent false PupEye tamper events and coalesce mirror warnings

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/SecureSaveCrypto.kt` in `PupEyeSaveGuard.verifyAndRecover()`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/ExternalGameSave.kt` in `write()` and `verifyExisting()`
- Test: `App/app/src/test/java/com/harleytg/puppyclicker/SaveCryptoDiagnosticsTest.kt`

**Interfaces:**
- Consumes: `classifySaveCryptoFailure(Throwable)` and `RepeatedFailureLogGate` from Task 1.
- Produces: external mirror writes that retry automatically, quarantine only tamper/integrity failures, and report successful recovery after an operational failure.

- [ ] **Step 1: Extend the JVM test to cover different failure signatures**

Add:

```kotlin
@Test
fun differentFailureSignatureLogsImmediately() {
    val gate = RepeatedFailureLogGate(cooldownMs = 60_000L)
    assertTrue(gate.shouldLog(InvalidAlgorithmParameterException("first"), 1_000L))
    assertTrue(gate.shouldLog(InvalidAlgorithmParameterException("second"), 2_000L))
}
```

- [ ] **Step 2: Run the focused test before production edits**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.SaveCryptoDiagnosticsTest --stacktrace
```

Expected: PASS for existing helper behavior; this locks the log-gate contract before integration.

- [ ] **Step 3: Integrate the log gate into `ExternalGameSave`**

Add one process-local gate:

```kotlin
private val failureLogGate = RepeatedFailureLogGate()
```

After a successful target replacement, before returning `target`, add:

```kotlin
if (failureLogGate.markSuccess()) {
    Log.i(TAG, "Encrypted Android/data mirror recovered")
}
```

Replace the unconditional catch log with:

```kotlin
}.getOrElse { error ->
    if (failureLogGate.shouldLog(error)) {
        Log.w(TAG, "Encrypted Android/data mirror unavailable; continuing with internal save", error)
    }
    null
}
```

- [ ] **Step 4: Separate tamper failures from operational verification failures**

Replace `verifyExisting()` error handling with:

```kotlin
}.getOrElse { error ->
    if (classifySaveCryptoFailure(error) == SaveCryptoFailureKind.TAMPER) {
        PupEyeSaveGuard.recordTamper(
            context,
            "Android/data save failed PupEye AES-GCM authentication"
        )
        quarantineTamperedFile(target)
    } else if (failureLogGate.shouldLog(error)) {
        Log.w(TAG, "Unable to verify encrypted Android/data mirror; leaving file intact", error)
    }
    false
}
```

Operational verification failures must leave the existing file intact.

- [ ] **Step 5: Apply the same classification rule to `PupEyeSaveGuard.verifyAndRecover()`**

Replace its blanket catch behavior with:

```kotlin
}.getOrElse { error ->
    if (classifySaveCryptoFailure(error) == SaveCryptoFailureKind.TAMPER) {
        recordTamper(context, "Protected save seal failed authentication")
    }
    false
}
```

This prevents local provider/key initialization faults from increasing the tamper counter.

- [ ] **Step 6: Run JVM tests and compile generated app sources**

```bash
gradle --no-daemon :app:testDebugUnitTest :app:generateProtectedPuppySources :app:assembleDebug --stacktrace
```

Expected: PASS with no Python patch-anchor failure.

- [ ] **Step 7: Manual Android regression check**

Install/run the debug build and verify the developer console no longer emits the repeated five-second `Caller-provided IV not permitted` warning. Confirm the file exists under the app-specific Android/data path and survives a relaunch.

- [ ] **Step 8: Commit external-save/PupEye failure handling**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/ExternalGameSave.kt \
  App/app/src/main/java/com/harleytg/puppyclicker/SecureSaveCrypto.kt \
  App/app/src/test/java/com/harleytg/puppyclicker/SaveCryptoDiagnosticsTest.kt
git commit -m "fix: distinguish save tamper from crypto failures"
```

---

### Task 4: Make CI compile the crypto regression test before release builds

**Files:**
- Modify: `.github/workflows/android.yml` build steps

**Interfaces:**
- Consumes: JVM tests and Android instrumentation source from Tasks 1-3.
- Produces: CI that rejects source/test compilation regressions even when no emulator is available.

- [ ] **Step 1: Add an explicit test-compilation step before signing/release assembly**

Insert after Java/Gradle setup and before release build steps:

```yaml
      - name: Run JVM tests and compile Android instrumentation tests
        run: gradle --no-daemon :app:testDebugUnitTest :app:assembleDebugAndroidTest --stacktrace
```

- [ ] **Step 2: Validate workflow syntax and local Gradle tasks**

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:assembleRelease --stacktrace
```

Expected: JVM tests PASS; debug Android test APK compiles; release APK assembles.

- [ ] **Step 3: Commit CI coverage**

```bash
git add .github/workflows/android.yml
git commit -m "ci: compile save crypto regression tests"
```

---

## Plan Self-Review

- Spec coverage: Keystore IV root cause, external mirror preservation, false-tamper prevention, retry behavior, log coalescing, and release-test compilation are covered.
- Placeholder scan: no `TBD`, `TODO`, or unspecified implementation steps remain.
- Type consistency: the failure classifier and gate signatures introduced in Task 1 are used unchanged in Tasks 3-4.
- Non-goal check: transfer-save PBKDF2/AES-GCM behavior is explicitly left unchanged.
