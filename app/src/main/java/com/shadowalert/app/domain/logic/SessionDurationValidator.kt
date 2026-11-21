package com.shadowalert.app.domain.logic

sealed class DurationValidationResult {
    data class Valid(val durationMinutes: Int) : DurationValidationResult()
    data class Invalid(val message: String) : DurationValidationResult()
}

object SessionDurationValidator {
    const val MIN_DURATION_MINUTES = 1
    const val MAX_DURATION_MINUTES = 480 // 8 hours max per single session

    val presetsMinutes: List<Int> = listOf(5, 10, 15, 20, 30)

    fun validate(minutes: Int?): DurationValidationResult {
        if (minutes == null) {
            return DurationValidationResult.Invalid("Please enter a valid number of minutes.")
        }
        if (minutes < MIN_DURATION_MINUTES) {
            return DurationValidationResult.Invalid(
                "Session duration must be at least $MIN_DURATION_MINUTES minute."
            )
        }
        if (minutes > MAX_DURATION_MINUTES) {
            return DurationValidationResult.Invalid(
                "For mindful pacing, single sessions are capped at $MAX_DURATION_MINUTES minutes."
            )
        }
        return DurationValidationResult.Valid(minutes)
    }

    fun validateDailyLimit(minutes: Int?): DurationValidationResult {
        if (minutes == null || minutes < 1) {
            return DurationValidationResult.Invalid("Daily limit must be at least 1 minute.")
        }
        if (minutes > 1440) {
            return DurationValidationResult.Invalid("Daily limit cannot exceed 24 hours (1440 minutes).")
        }
        return DurationValidationResult.Valid(minutes)
    }
}
