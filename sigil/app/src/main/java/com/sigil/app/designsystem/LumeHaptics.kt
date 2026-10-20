package com.sigil.app.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import com.sigil.app.ui.Haptic
import com.sigil.app.ui.Haptics

/**
 * The seven haptic tokens shared by every Lume app. Sigil keeps its richer 11-primitive
 * [Haptic] set for its own moments; each token here maps onto the recipe in [Haptics].
 */
enum class LumeHaptic(val haptic: Haptic) {
    TICK(Haptic.TICK),
    CLICK(Haptic.CLICK),
    CHECK_COMPLETE(Haptic.CHECK),
    SEAL_LOCK(Haptic.SEAL),
    REVEAL_SWELL(Haptic.REVEAL),
    BREATH_WAVE(Haptic.BREATH),
    GENTLE_NUDGE(Haptic.NUDGE),
}

fun Haptics.play(token: LumeHaptic) = play(token.haptic)

/** The activity's [Haptics], for modifiers like [pressable]; null in previews and tests. */
val LocalLumeHaptics = staticCompositionLocalOf<Haptics?> { null }
