package com.oddley.next.domain.emitter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.TimeZone

/**
 * Tests for EmitterOps pure functions.
 *
 * All epoch-ms values use UTC to avoid DST edge cases. Tests pass a UTC TimeZone
 * explicitly via the optional [tz] parameter on [computeNextEmission].
 */
class EmitterOpsTest {

    private val utc = TimeZone.getTimeZone("UTC")

    // 2025-01-01 09:00:00 UTC
    private val jan1At9am = 1735722000000L

    // 2025-01-02 09:00:00 UTC
    private val jan2At9am = 1735808400000L

    // 2025-01-08 09:00:00 UTC (next Monday after jan1 if jan1 is Wednesday)
    // Actually jan1 2025 is a Wednesday. We'll use DAILY so day-of-week doesn't matter.
    private val jan6At9am = jan1At9am + 5 * 24 * 60 * 60 * 1000L  // +5 days

    // ── computeNextEmission ───────────────────────────────────────────────────

    @Test
    fun `DAILY - first occurrence is dtStart when after is dtStart minus 1`() {
        val result = computeNextEmission(
            rrule = "FREQ=DAILY",
            dtStart = jan1At9am,
            after = jan1At9am - 1,
            tz = utc,
        )
        assertEquals(jan1At9am, result)
    }

    @Test
    fun `DAILY - advances one day when after equals dtStart`() {
        val result = computeNextEmission(
            rrule = "FREQ=DAILY",
            dtStart = jan1At9am,
            after = jan1At9am,
            tz = utc,
        )
        assertEquals(jan2At9am, result)
    }

    @Test
    fun `DAILY - catch-up skips multiple past occurrences`() {
        // after = jan6 → next should be jan7
        val jan7At9am = jan1At9am + 6 * 24 * 60 * 60 * 1000L
        val result = computeNextEmission(
            rrule = "FREQ=DAILY",
            dtStart = jan1At9am,
            after = jan6At9am,
            tz = utc,
        )
        assertEquals(jan7At9am, result)
    }

    @Test
    fun `ONE_TIME (COUNT=1) - returns dtStart when after is dtStart minus 1`() {
        val result = computeNextEmission(
            rrule = "FREQ=DAILY;COUNT=1",
            dtStart = jan1At9am,
            after = jan1At9am - 1,
            tz = utc,
        )
        assertEquals(jan1At9am, result)
    }

    @Test
    fun `ONE_TIME (COUNT=1) - exhausted after first firing`() {
        // after = dtStart means the first (and only) occurrence has passed
        val result = computeNextEmission(
            rrule = "FREQ=DAILY;COUNT=1",
            dtStart = jan1At9am,
            after = jan1At9am,
            tz = utc,
        )
        assertNull(result)
    }

    @Test
    fun `COUNT=3 - exhausted after all occurrences passed`() {
        val jan4At9am = jan1At9am + 3 * 24 * 60 * 60 * 1000L
        val result = computeNextEmission(
            rrule = "FREQ=DAILY;COUNT=3",
            dtStart = jan1At9am,
            after = jan4At9am,  // after the 3rd (jan3)
            tz = utc,
        )
        assertNull(result)
    }

    @Test
    fun `WEEKLY - advances by 7 days`() {
        val jan8At9am = jan1At9am + 7 * 24 * 60 * 60 * 1000L
        val result = computeNextEmission(
            rrule = "FREQ=WEEKLY",
            dtStart = jan1At9am,
            after = jan1At9am,  // just fired
            tz = utc,
        )
        assertEquals(jan8At9am, result)
    }

    @Test
    fun `malformed RRULE - returns null without throwing`() {
        val result = computeNextEmission(
            rrule = "NOT_A_VALID_RRULE",
            dtStart = jan1At9am,
            after = jan1At9am - 1,
            tz = utc,
        )
        assertNull(result)
    }

    // ── shouldEmit ────────────────────────────────────────────────────────────

    @Test
    fun `shouldEmit - true when nextEmission is in the past`() {
        val emitter = TaskEmitter(
            id = 1L, label = "Test", rrule = "FREQ=DAILY",
            dtStart = jan1At9am, nextEmission = jan1At9am,
        )
        assertTrue(shouldEmit(emitter, now = jan1At9am + 1))
    }

    @Test
    fun `shouldEmit - true when nextEmission equals now (boundary)`() {
        val emitter = TaskEmitter(
            id = 1L, label = "Test", rrule = "FREQ=DAILY",
            dtStart = jan1At9am, nextEmission = jan1At9am,
        )
        assertTrue(shouldEmit(emitter, now = jan1At9am))
    }

    @Test
    fun `shouldEmit - false when nextEmission is in the future`() {
        val emitter = TaskEmitter(
            id = 1L, label = "Test", rrule = "FREQ=DAILY",
            dtStart = jan1At9am, nextEmission = jan1At9am,
        )
        assertFalse(shouldEmit(emitter, now = jan1At9am - 1))
    }

    @Test
    fun `shouldEmit - false when nextEmission is null (exhausted)`() {
        val emitter = TaskEmitter(
            id = 1L, label = "Test", rrule = "FREQ=DAILY;COUNT=1",
            dtStart = jan1At9am, nextEmission = null,
        )
        assertFalse(shouldEmit(emitter, now = jan1At9am + 1_000_000L))
    }

    // ── advanceEmitter ────────────────────────────────────────────────────────

    @Test
    fun `advanceEmitter DAILY - nextEmission advances by one day`() {
        val emitter = TaskEmitter(
            id = 1L, label = "Test", rrule = "FREQ=DAILY",
            dtStart = jan1At9am, nextEmission = jan1At9am,
        )
        val advanced = advanceEmitter(emitter, now = jan1At9am, tz = utc)
        assertEquals(jan2At9am, advanced.nextEmission)
    }

    @Test
    fun `advanceEmitter ONE_TIME - nextEmission becomes null`() {
        val emitter = TaskEmitter(
            id = 1L, label = "Test", rrule = "FREQ=DAILY;COUNT=1",
            dtStart = jan1At9am, nextEmission = jan1At9am,
        )
        val advanced = advanceEmitter(emitter, now = jan1At9am, tz = utc)
        assertNull(advanced.nextEmission)
    }

    @Test
    fun `advanceEmitter - id and other fields are preserved`() {
        val emitter = TaskEmitter(
            id = 42L, label = "My Task", rrule = "FREQ=DAILY",
            dtStart = jan1At9am, nextEmission = jan1At9am,
        )
        val advanced = advanceEmitter(emitter, now = jan1At9am, tz = utc)
        assertEquals(42L, advanced.id)
        assertEquals("My Task", advanced.label)
        assertEquals("FREQ=DAILY", advanced.rrule)
        assertEquals(jan1At9am, advanced.dtStart)
    }
}
