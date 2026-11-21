package com.shadowalert.app

import com.shadowalert.app.domain.logic.DurationValidationResult
import com.shadowalert.app.domain.logic.SessionDurationValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDurationValidatorTest {

    @Test
    fun `presets match expected mindful durations`() {
        assertEquals(listOf(5, 10, 15, 20, 30), SessionDurationValidator.presetsMinutes)
    }

    @Test
    fun `validates positive durations within cap`() {
        val result = SessionDurationValidator.validate(15)
        assertTrue(result is DurationValidationResult.Valid)
        assertEquals(15, (result as DurationValidationResult.Valid).durationMinutes)
    }

    @Test
    fun `rejects null zero negative and excessive durations`() {
        assertTrue(SessionDurationValidator.validate(null) is DurationValidationResult.Invalid)
        assertTrue(SessionDurationValidator.validate(0) is DurationValidationResult.Invalid)
        assertTrue(SessionDurationValidator.validate(-5) is DurationValidationResult.Invalid)
        assertTrue(SessionDurationValidator.validate(481) is DurationValidationResult.Invalid)
    }

    @Test
    fun `validates daily limits between 1 and 1440 minutes`() {
        assertTrue(SessionDurationValidator.validateDailyLimit(60) is DurationValidationResult.Valid)
        assertTrue(SessionDurationValidator.validateDailyLimit(0) is DurationValidationResult.Invalid)
        assertTrue(SessionDurationValidator.validateDailyLimit(1441) is DurationValidationResult.Invalid)
    }
}
