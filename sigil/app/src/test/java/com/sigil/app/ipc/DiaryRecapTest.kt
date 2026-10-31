package com.sigil.app.ipc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryRecapTest {
    @Test fun keepsRulesAndTimeButNeverMomentum() {
        val lines = DiaryRecap.lines(
            kasane = mapOf("rulesKept" to "2", "rulesTotal" to "3", "momentum" to "81"),
            vow = mapOf("reclaimedMinutes" to "45"),
        )
        assertEquals(listOf("Rules kept: 2 of 3", "Time reclaimed: 45 min"), lines)
        assertTrue(lines.none { it.contains("omentum") || it.contains("81") })
    }

    @Test fun absentCompanionsGiveNothing() {
        assertTrue(DiaryRecap.lines(null, null).isEmpty())
        assertEquals(listOf("Time reclaimed: 5 min"), DiaryRecap.lines(null, mapOf("reclaimedMinutes" to "5")))
    }

    @Test fun incompleteKasaneRowAddsNoRulesLine() {
        assertTrue(DiaryRecap.lines(mapOf("momentum" to "10"), null).isEmpty())
    }
}
