package com.sigil.app.designsystem

import com.sigil.app.ui.theme.DarkSigilColors
import com.sigil.app.ui.theme.LightSigilColors
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CircadianTest {
    private fun at(h: Int, m: Int = 0) = CircadianPhase.at(LocalTime.of(h, m))

    @Test fun phaseBoundaries() {
        assertEquals(CircadianPhase.SLEEP, at(5, 59))
        assertEquals(CircadianPhase.MORNING, at(6))
        assertEquals(CircadianPhase.MORNING, at(11, 59))
        assertEquals(CircadianPhase.DAY, at(12))
        assertEquals(CircadianPhase.DAY, at(19, 59))
        assertEquals(CircadianPhase.EVENING, at(20))
        assertEquals(CircadianPhase.EVENING, at(21, 59))
        assertEquals(CircadianPhase.SLEEP, at(22))
        assertEquals(CircadianPhase.SLEEP, at(0))
    }

    @Test fun daytimeLeavesThePaletteAlone() {
        assertSame(LightSigilColors, circadianColors(LightSigilColors, DarkSigilColors, CircadianPhase.DAY))
    }

    @Test fun accentsSurviveEveryPhase() {
        for (p in CircadianPhase.entries) {
            val c = circadianColors(DarkSigilColors, DarkSigilColors, p)
            assertEquals(DarkSigilColors.tint, c.tint)
            assertEquals(DarkSigilColors.seal, c.seal)
            assertEquals(DarkSigilColors.critical, c.critical)
        }
    }

    @Test fun sleepDropsAmber() {
        val c = circadianColors(LightSigilColors, DarkSigilColors, CircadianPhase.SLEEP)
        assertEquals(DarkSigilColors.label2, c.caution)
    }
}
