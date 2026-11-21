package com.shadowalert.app.domain.logic

/**
 * Computes elapsed active session time using monotonic elapsed-realtime deltas
 * so wall-clock adjustments (timezone or manual clock changes) cannot corrupt
 * active session timing.
 */
class ElapsedTimeTracker(
    initialAccumulatedMs: Long = 0L
) {
    private var accumulatedActiveMs: Long = initialAccumulatedMs.coerceAtLeast(0L)
    private var lastTickElapsedRealtimeMs: Long? = null
    private var lastProcessedSessionId: Long? = null

    fun reset(sessionId: Long? = null, initialAccumulatedMs: Long = 0L) {
        lastProcessedSessionId = sessionId
        accumulatedActiveMs = initialAccumulatedMs.coerceAtLeast(0L)
        lastTickElapsedRealtimeMs = null
    }

    /**
     * Hydrates persisted elapsed time when recovering a session after process kill or device reboot.
     */
    fun restoreSessionIfNeeded(sessionId: Long, persistedAccumulatedMs: Long) {
        if (lastProcessedSessionId != sessionId) {
            reset(sessionId = sessionId, initialAccumulatedMs = persistedAccumulatedMs)
        } else if (persistedAccumulatedMs > accumulatedActiveMs) {
            accumulatedActiveMs = persistedAccumulatedMs
        }
    }

    /**
     * Clears the previous tick timestamp when the screen turns off or service pauses
     * so waking up later does not count sleep duration as active usage.
     */
    fun pauseTickAnchor() {
        lastTickElapsedRealtimeMs = null
    }

    /**
     * Advances the tracker on each monitoring tick.
     *
     * @param sessionId Current active session id to guard against duplicate/stale calculations.
     * @param currentElapsedRealtimeMs Monotonic `SystemClock.elapsedRealtime()` value.
     * @param shouldCountTime True when screen is interactive AND (target app is foreground OR manual fallback timer is active).
     * @param maxStepMs Safety cap per tick so long process freezes/doze spikes don't invent phantom usage.
     */
    fun onTick(
        sessionId: Long,
        currentElapsedRealtimeMs: Long,
        shouldCountTime: Boolean,
        maxStepMs: Long = 30_000L
    ): Long {
        if (lastProcessedSessionId != sessionId) {
            reset(sessionId = sessionId, initialAccumulatedMs = accumulatedActiveMs)
        }

        val previousTick = lastTickElapsedRealtimeMs
        lastTickElapsedRealtimeMs = currentElapsedRealtimeMs

        if (previousTick != null && shouldCountTime) {
            val delta = currentElapsedRealtimeMs - previousTick
            if (delta in 1..maxStepMs) {
                accumulatedActiveMs += delta
            }
        }
        return accumulatedActiveMs
    }

    fun getAccumulatedActiveMs(): Long = accumulatedActiveMs

    fun getElapsedSeconds(): Long = accumulatedActiveMs / 1000L

    fun getRemainingSeconds(intendedDurationMinutes: Int): Long {
        val targetSeconds = intendedDurationMinutes.coerceAtLeast(0) * 60L
        return (targetSeconds - getElapsedSeconds()).coerceAtLeast(0L)
    }
}
