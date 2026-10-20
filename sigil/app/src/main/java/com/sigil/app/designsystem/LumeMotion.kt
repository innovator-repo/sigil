package com.sigil.app.designsystem

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.CubicBezierEasing

/** Shared motion tokens: springs for movement and for colour/progress, a slow curve for breath. */
object LumeMotion {
    const val SPATIAL_DAMPING = 0.75f
    const val SPATIAL_STIFFNESS = 380f
    const val EFFECTS_DAMPING = Spring.DampingRatioNoBouncy
    const val EFFECTS_STIFFNESS = 1500f
    const val PRESS_SCALE = 0.97f
    const val BREATH_MS = 4000

    /** Movement, scale, sheets: a touch of overshoot. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(SPATIAL_DAMPING, SPATIAL_STIFFNESS)

    /** Colour shifts, progress fills, opacity: no overshoot, so nothing flickers. */
    fun <T> effects(): FiniteAnimationSpec<T> = spring(EFFECTS_DAMPING, EFFECTS_STIFFNESS)

    /** EaseInOutSine, for 4-second inhale/exhale pacing. */
    val breathing: Easing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
}

/**
 * Scales to 0.97 while a finger is down, with a TICK, and springs back on release. It watches
 * the touch without consuming it, so the element's own click handling is unaffected.
 */
@Composable
fun Modifier.pressable(enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val haptics = LocalLumeHaptics.current
    var down by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (down) LumeMotion.PRESS_SCALE else 1f, LumeMotion.spatial(), label = "pressable")
    return this
        .scale(scale)
        .pointerInput(haptics) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                down = true
                haptics?.play(LumeHaptic.TICK)
                waitForUpOrCancellation(PointerEventPass.Initial)
                down = false
            }
        }
}
