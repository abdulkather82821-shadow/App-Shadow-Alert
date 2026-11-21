package com.shadowalert.app

import com.shadowalert.app.domain.logic.WarningEvaluator
import com.shadowalert.app.domain.logic.WarningPreferences
import com.shadowalert.app.domain.model.AlertType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarningEvaluatorTest {

    @Test
    fun `emits 5m 2m and limit reached once per session`() {
        val evaluator = WarningEvaluator()
        val prefs = WarningPreferences(
            warning5MinEnabled = true,
            warning2MinEnabled = true,
            limitReachedAlertEnabled = true,
            customWarningMinutes = 0
        )

        assertTrue(evaluator.evaluate(1L, 15, 9 * 60L, prefs).isEmpty())

        assertEquals(
            listOf(AlertType.FIVE_MIN_REMAINING),
            evaluator.evaluate(1L, 15, 10 * 60L, prefs)
        )
        assertTrue(evaluator.evaluate(1L, 15, 10 * 60L + 10, prefs).isEmpty())

        assertEquals(
            listOf(AlertType.TWO_MIN_REMAINING),
            evaluator.evaluate(1L, 15, 13 * 60L, prefs)
        )
        assertTrue(evaluator.evaluate(1L, 15, 13 * 60L + 5, prefs).isEmpty())

        assertEquals(
            listOf(AlertType.INTENDED_DURATION_REACHED),
            evaluator.evaluate(1L, 15, 15 * 60L, prefs)
        )
        assertTrue(evaluator.evaluate(1L, 15, 15 * 60L + 1, prefs).isEmpty())
    }

    @Test
    fun `does not re-emit already persisted alerts after service recovery`() {
        val evaluator = WarningEvaluator()
        val prefs = WarningPreferences()

        evaluator.syncSession(
            sessionId = 42L,
            alreadyTriggered = setOf(AlertType.FIVE_MIN_REMAINING, AlertType.TWO_MIN_REMAINING)
        )

        // At 13m elapsed of 15m (2m remaining), 5m and 2m must NOT re-fire
        assertTrue(evaluator.evaluate(42L, 15, 13 * 60L, prefs).isEmpty())

        // At 15m elapsed, limit reached still fires once
        assertEquals(
            listOf(AlertType.INTENDED_DURATION_REACHED),
            evaluator.evaluate(42L, 15, 15 * 60L, prefs)
        )
    }

    @Test
    fun `emits new warnings after session is extended`() {
        val evaluator = WarningEvaluator()
        val prefs = WarningPreferences()

        assertEquals(
            listOf(AlertType.INTENDED_DURATION_REACHED),
            evaluator.evaluate(1L, 10, 10 * 60L, prefs)
        )

        evaluator.onSessionExtended()
        assertEquals(
            listOf(AlertType.FIVE_MIN_REMAINING),
            evaluator.evaluate(1L, 15, 10 * 60L, prefs)
        )
    }
}
