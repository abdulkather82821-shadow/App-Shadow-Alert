package com.shadowalert.app

import com.shadowalert.app.domain.logic.ElapsedTimeTracker
import org.junit.Assert.assertEquals
import org.junit.Test

class ElapsedTimeTrackerTest {

    @Test
    fun `accumulates active time only when target app is active`() {
        val tracker = ElapsedTimeTracker()
        tracker.onTick(sessionId = 1L, currentElapsedRealtimeMs = 1_000L, shouldCountTime = true)
        tracker.onTick(sessionId = 1L, currentElapsedRealtimeMs = 4_000L, shouldCountTime = true)
        tracker.onTick(sessionId = 1L, currentElapsedRealtimeMs = 9_000L, shouldCountTime = false)
        tracker.onTick(sessionId = 1L, currentElapsedRealtimeMs = 12_000L, shouldCountTime = true)

        assertEquals(6_000L, tracker.getAccumulatedActiveMs())
        assertEquals(6L, tracker.getElapsedSeconds())
        assertEquals(54L, tracker.getRemainingSeconds(intendedDurationMinutes = 1))
    }

    @Test
    fun `ignores negative deltas or extreme sleep spikes`() {
        val tracker = ElapsedTimeTracker()
        tracker.onTick(sessionId = 2L, currentElapsedRealtimeMs = 10_000L, shouldCountTime = true)
        tracker.onTick(sessionId = 2L, currentElapsedRealtimeMs = 130_000L, shouldCountTime = true)
        tracker.onTick(sessionId = 2L, currentElapsedRealtimeMs = 132_000L, shouldCountTime = true)

        assertEquals(2_000L, tracker.getAccumulatedActiveMs())
    }

    @Test
    fun `restores persisted session progress and drops sleep time on screen off`() {
        val tracker = ElapsedTimeTracker()
        tracker.restoreSessionIfNeeded(sessionId = 7L, persistedAccumulatedMs = 180_000L)
        tracker.onTick(sessionId = 7L, currentElapsedRealtimeMs = 1_000L, shouldCountTime = true)
        tracker.onTick(sessionId = 7L, currentElapsedRealtimeMs = 3_000L, shouldCountTime = true)
        tracker.pauseTickAnchor() // Screen turned off
        tracker.onTick(sessionId = 7L, currentElapsedRealtimeMs = 25_000L, shouldCountTime = true) // Screen back on
        tracker.onTick(sessionId = 7L, currentElapsedRealtimeMs = 27_000L, shouldCountTime = true)

        assertEquals(184_000L, tracker.getAccumulatedActiveMs())
    }

    @Test
    fun `resets state cleanly when switching session ids`() {
        val tracker = ElapsedTimeTracker()
        tracker.onTick(sessionId = 1L, currentElapsedRealtimeMs = 1_000L, shouldCountTime = true)
        tracker.onTick(sessionId = 1L, currentElapsedRealtimeMs = 6_000L, shouldCountTime = true)
        assertEquals(5_000L, tracker.getAccumulatedActiveMs())

        tracker.reset(sessionId = 2L, initialAccumulatedMs = 0L)
        tracker.onTick(sessionId = 2L, currentElapsedRealtimeMs = 7_000L, shouldCountTime = true)
        tracker.onTick(sessionId = 2L, currentElapsedRealtimeMs = 9_000L, shouldCountTime = true)
        assertEquals(2_000L, tracker.getAccumulatedActiveMs())
    }
}
