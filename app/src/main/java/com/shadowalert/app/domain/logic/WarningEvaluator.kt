package com.shadowalert.app.domain.logic

import com.shadowalert.app.domain.model.AlertType

data class WarningPreferences(
    val warning5MinEnabled: Boolean = true,
    val warning2MinEnabled: Boolean = true,
    val limitReachedAlertEnabled: Boolean = true,
    val customWarningMinutes: Int = 0
)

/**
 * Evaluates remaining session time and emits each alert threshold at most once per session
 * (persisting across process restarts when hydrated from Room).
 */
class WarningEvaluator {
    private var currentSessionId: Long? = null
    private val triggeredAlerts = mutableSetOf<AlertType>()

    fun syncSession(sessionId: Long, alreadyTriggered: Set<AlertType> = emptySet()) {
        if (currentSessionId != sessionId) {
            currentSessionId = sessionId
            triggeredAlerts.clear()
            triggeredAlerts.addAll(alreadyTriggered)
        } else if (alreadyTriggered.isNotEmpty()) {
            triggeredAlerts.addAll(alreadyTriggered)
        }
    }

    /**
     * When the user explicitly adds more time, clear thresholds so
     * future warnings can fire accurately for the new target.
     */
    fun onSessionExtended() {
        triggeredAlerts.clear()
    }

    fun reset() {
        currentSessionId = null
        triggeredAlerts.clear()
    }

    fun hasTriggered(alertType: AlertType): Boolean = alertType in triggeredAlerts

    fun evaluate(
        sessionId: Long,
        intendedDurationMinutes: Int,
        elapsedSeconds: Long,
        preferences: WarningPreferences
    ): List<AlertType> {
        syncSession(sessionId)
        val totalSeconds = intendedDurationMinutes.coerceAtLeast(1) * 60L
        val remainingSeconds = (totalSeconds - elapsedSeconds).coerceAtLeast(0L)
        val emitted = mutableListOf<AlertType>()

        if (remainingSeconds <= 0L) {
            if (preferences.limitReachedAlertEnabled &&
                triggeredAlerts.add(AlertType.INTENDED_DURATION_REACHED)
            ) {
                emitted += AlertType.INTENDED_DURATION_REACHED
            }
            return emitted
        }

        if (preferences.customWarningMinutes > 0 &&
            intendedDurationMinutes > preferences.customWarningMinutes &&
            remainingSeconds <= preferences.customWarningMinutes * 60L &&
            remainingSeconds > 120L &&
            triggeredAlerts.add(AlertType.CUSTOM_WARNING)
        ) {
            emitted += AlertType.CUSTOM_WARNING
        }

        if (preferences.warning5MinEnabled &&
            intendedDurationMinutes > 5 &&
            remainingSeconds <= 5 * 60L &&
            remainingSeconds > 2 * 60L &&
            triggeredAlerts.add(AlertType.FIVE_MIN_REMAINING)
        ) {
            emitted += AlertType.FIVE_MIN_REMAINING
        }

        if (preferences.warning2MinEnabled &&
            intendedDurationMinutes > 2 &&
            remainingSeconds <= 2 * 60L &&
            triggeredAlerts.add(AlertType.TWO_MIN_REMAINING)
        ) {
            emitted += AlertType.TWO_MIN_REMAINING
        }

        return emitted
    }
}
