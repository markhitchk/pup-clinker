package com.harleytg.puppyclicker

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View

enum class PuppySemanticHaptic {
    VIRTUAL_KEY,
    CLOCK_TICK,
    CONTEXT_CLICK,
    LONG_PRESS
}

data class PuppyDirectEffectSpec(
    val durationMs: Long,
    val amplitude: Int
)

object PuppyHaptics {
    private const val TAG = "PuppyHaptics"

    fun directEffectSpec(strength: PuppyHapticStrength): PuppyDirectEffectSpec = when (strength) {
        PuppyHapticStrength.LIGHT -> PuppyDirectEffectSpec(durationMs = 22L, amplitude = 96)
        PuppyHapticStrength.MEDIUM -> PuppyDirectEffectSpec(durationMs = 38L, amplitude = 168)
        PuppyHapticStrength.STRONG -> PuppyDirectEffectSpec(durationMs = 58L, amplitude = 232)
    }

    fun semanticKind(event: PuppyHapticEvent): PuppySemanticHaptic = when (event) {
        PuppyHapticEvent.TAP -> PuppySemanticHaptic.VIRTUAL_KEY
        PuppyHapticEvent.SELECTION,
        PuppyHapticEvent.NAVIGATION,
        PuppyHapticEvent.TOGGLE -> PuppySemanticHaptic.CLOCK_TICK
        PuppyHapticEvent.SUCCESS -> PuppySemanticHaptic.CONTEXT_CLICK
        PuppyHapticEvent.ERROR -> PuppySemanticHaptic.LONG_PRESS
        PuppyHapticEvent.REWARD,
        PuppyHapticEvent.TICKET_DROP,
        PuppyHapticEvent.DANGER_CONFIRM,
        PuppyHapticEvent.TEST -> PuppySemanticHaptic.CONTEXT_CLICK
    }

    fun perform(
        view: View?,
        context: Context,
        event: PuppyHapticEvent,
        enabled: Boolean
    ): PuppyHapticResult {
        val plan = PuppyHapticPolicy.plan(event, enabled)
        if (plan.route == PuppyHapticRoute.NONE) return PuppyHapticResult.DISABLED

        val semanticSucceeded = if (plan.route == PuppyHapticRoute.SEMANTIC_THEN_DIRECT && view != null) {
            try {
                view.performHapticFeedback(toAndroidSemanticConstant(semanticKind(event)))
            } catch (error: Throwable) {
                Log.w(TAG, "Semantic haptic failed for $event; trying direct fallback", error)
                false
            }
        } else {
            false
        }

        if (semanticSucceeded) return PuppyHapticResult.SEMANTIC

        val directSucceeded = performDirect(context, plan.strength, event)
        val result = PuppyHapticExecution.resolve(plan.route, semanticSucceeded, directSucceeded)
        if (result == PuppyHapticResult.UNAVAILABLE) {
            Log.w(TAG, "Haptic unavailable for $event (route=${plan.route}, strength=${plan.strength})")
        }
        return result
    }

    private fun performDirect(
        context: Context,
        strength: PuppyHapticStrength,
        event: PuppyHapticEvent
    ): Boolean {
        return try {
            val vibrator = vibrator(context)
            if (vibrator == null || !vibrator.hasVibrator()) {
                Log.w(TAG, "No vibrator available for $event")
                return false
            }

            val spec = directEffectSpec(strength)
            val amplitude = if (vibrator.hasAmplitudeControl()) {
                spec.amplitude
            } else {
                VibrationEffect.DEFAULT_AMPLITUDE
            }
            vibrator.vibrate(VibrationEffect.createOneShot(spec.durationMs, amplitude))
            true
        } catch (error: Throwable) {
            Log.w(TAG, "Direct vibration failed for $event", error)
            false
        }
    }

    private fun vibrator(context: Context): Vibrator? {
        val appContext = context.applicationContext
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun toAndroidSemanticConstant(kind: PuppySemanticHaptic): Int = when (kind) {
        PuppySemanticHaptic.VIRTUAL_KEY -> HapticFeedbackConstants.VIRTUAL_KEY
        PuppySemanticHaptic.CLOCK_TICK -> HapticFeedbackConstants.CLOCK_TICK
        PuppySemanticHaptic.CONTEXT_CLICK -> HapticFeedbackConstants.CONTEXT_CLICK
        PuppySemanticHaptic.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
    }
}
