package com.sigil.app.ui

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/**
 * Every moment Sigil marks with touch, from lightest to heaviest. Each has its own feel, so
 * the hand learns them the way the eye learns the indigo of a sealed chip.
 */
enum class Haptic {
    /** Moving around: opening an item, switching a section or a generator style. */
    TICK,
    /** A button that does something small: copy, pin, another password. */
    CLICK,
    /** Ticking off a checklist item: a crisp, satisfying click. */
    CHECK,
    /** Un-ticking it: softer, a step back. */
    UNCHECK,
    /** A secret being sealed: a quick rise into a firm click, like a latch closing. */
    SEAL,
    /** A secret opening after presence: a slow swell. */
    REVEAL,
    /** A secret hiding again: a quick fade. */
    HIDE,
    /** Something worth noticing, e.g. "This looks like a password. Seal it?" */
    NUDGE,
    /** Done and saved: a new password, codes on, recovered, unlocked. */
    SUCCESS,
    /** Heavy and final: deleting, locking. */
    THUD,
    /** That didn't work: two low taps, "no". */
    REJECT,
    /** One slow breath, rise then fall; the only haptic that lasts seconds. */
    BREATH,
}

/**
 * Plays [Haptic]s with the vibrator's composed primitives where the hardware has them
 * (Android 11+ on good actuators), falling back to predefined effects, then to plain
 * short pulses. Follows the system "touch feedback" setting: off means silent.
 */
class Haptics(context: Context) {
    private val app = context.applicationContext
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        app.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        app.getSystemService(Vibrator::class.java)
    }

    private val effects = HashMap<Haptic, VibrationEffect?>()

    fun play(h: Haptic) {
        val v = vibrator ?: return
        if (!v.hasVibrator() || !touchFeedbackOn()) return
        val effect = effects.getOrPut(h) { build(h, v) } ?: return
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect)
        }
    }

    private fun touchFeedbackOn(): Boolean =
        // From Android 13 the USAGE_TOUCH attribute makes the system apply the setting itself.
        Build.VERSION.SDK_INT >= 33 ||
            Settings.System.getInt(app.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    private fun build(h: Haptic, v: Vibrator): VibrationEffect? {
        if (Build.VERSION.SDK_INT >= 30) composed(h, v)?.let { return it }
        if (Build.VERSION.SDK_INT >= 29) return predefined(h)
        return legacy(h)
    }

    /** Rich versions from vibration primitives; null if this actuator can't play all of them. */
    private fun composed(h: Haptic, v: Vibrator): VibrationEffect? {
        if (Build.VERSION.SDK_INT < 30) return null
        val click = VibrationEffect.Composition.PRIMITIVE_CLICK
        val tick = VibrationEffect.Composition.PRIMITIVE_TICK
        val quickRise = VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
        val slowRise = VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
        val quickFall = VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
        val lowTick = if (Build.VERSION.SDK_INT >= 31) VibrationEffect.Composition.PRIMITIVE_LOW_TICK else tick
        val thud = if (Build.VERSION.SDK_INT >= 31) VibrationEffect.Composition.PRIMITIVE_THUD else click

        // (primitive, scale 0..1, delay before it in ms)
        val steps: List<Triple<Int, Float, Int>> = when (h) {
            Haptic.TICK -> listOf(Triple(tick, 0.5f, 0))
            Haptic.CLICK -> listOf(Triple(click, 0.6f, 0))
            Haptic.CHECK -> listOf(Triple(click, 0.9f, 0), Triple(tick, 0.35f, 50))
            Haptic.UNCHECK -> listOf(Triple(lowTick, 0.6f, 0))
            Haptic.SEAL -> listOf(Triple(quickRise, 0.55f, 0), Triple(click, 1f, 30), Triple(tick, 0.4f, 70))
            Haptic.REVEAL -> listOf(Triple(slowRise, 0.6f, 0), Triple(tick, 0.5f, 20))
            Haptic.HIDE -> listOf(Triple(quickFall, 0.5f, 0))
            Haptic.NUDGE -> listOf(Triple(lowTick, 0.75f, 0), Triple(lowTick, 0.75f, 100))
            Haptic.SUCCESS -> listOf(Triple(click, 0.7f, 0), Triple(click, 1f, 110))
            Haptic.THUD -> listOf(Triple(thud, 1f, 0), Triple(lowTick, 0.4f, 60))
            Haptic.REJECT -> listOf(Triple(lowTick, 0.9f, 0), Triple(lowTick, 0.9f, 100))
            Haptic.BREATH -> listOf(Triple(slowRise, 0.7f, 0), Triple(tick, 0.4f, 2000), Triple(quickFall, 0.7f, 2000))
        }
        val ids = steps.map { it.first }.distinct().toIntArray()
        if (!v.areAllPrimitivesSupported(*ids)) return null
        val c = VibrationEffect.startComposition()
        for ((p, scale, delay) in steps) c.addPrimitive(p, scale, delay)
        return c.compose()
    }

    private fun predefined(h: Haptic): VibrationEffect? {
        if (Build.VERSION.SDK_INT < 29) return null
        return VibrationEffect.createPredefined(
            when (h) {
                Haptic.TICK, Haptic.UNCHECK, Haptic.HIDE -> VibrationEffect.EFFECT_TICK
                Haptic.CLICK, Haptic.CHECK, Haptic.REVEAL, Haptic.NUDGE -> VibrationEffect.EFFECT_CLICK
                Haptic.SEAL, Haptic.THUD, Haptic.BREATH -> VibrationEffect.EFFECT_HEAVY_CLICK
                Haptic.SUCCESS, Haptic.REJECT -> VibrationEffect.EFFECT_DOUBLE_CLICK
            },
        )
    }

    /** Android 8-9: short pulses shaped by timing and amplitude. */
    private fun legacy(h: Haptic): VibrationEffect = when (h) {
        Haptic.TICK, Haptic.UNCHECK, Haptic.HIDE -> VibrationEffect.createOneShot(8, 90)
        Haptic.CLICK, Haptic.CHECK, Haptic.NUDGE, Haptic.REVEAL -> VibrationEffect.createOneShot(14, 160)
        Haptic.SEAL, Haptic.THUD, Haptic.BREATH -> VibrationEffect.createOneShot(24, 255)
        Haptic.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 14, 90, 18), intArrayOf(0, 170, 0, 230), -1)
        Haptic.REJECT -> VibrationEffect.createWaveform(longArrayOf(0, 16, 80, 16), intArrayOf(0, 200, 0, 200), -1)
    }
}
