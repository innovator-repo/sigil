package com.sigil.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatTest {
    private val now = 1_700_000_000_000L // fixed instant for deterministic tests
    private val DAY = 86_400_000L

    @Test
    fun bucketsByAge() {
        assertEquals(TimeFormat.Bucket.TODAY, TimeFormat.bucket(now - 60_000L, now))
        assertEquals(TimeFormat.Bucket.WEEK, TimeFormat.bucket(now - 3 * DAY, now))
        assertEquals(TimeFormat.Bucket.MONTH, TimeFormat.bucket(now - 15 * DAY, now))
        assertEquals(TimeFormat.Bucket.EARLIER, TimeFormat.bucket(now - 45 * DAY, now))
    }

    @Test
    fun relativeTimePhrasing() {
        assertEquals("just now", TimeFormat.relativeTime(now - 10_000L, now))
        assertEquals("2 minutes ago", TimeFormat.relativeTime(now - 90_000L, now))
        assertEquals("1 hour ago", TimeFormat.relativeTime(now - 3_600_000L, now))
        assertEquals("2 days ago", TimeFormat.relativeTime(now - 2 * DAY, now))
        assertEquals("4 months ago", TimeFormat.relativeTime(now - 120 * DAY, now))
    }
}
