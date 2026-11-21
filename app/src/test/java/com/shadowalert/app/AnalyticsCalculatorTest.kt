package com.shadowalert.app

import com.shadowalert.app.data.local.entity.AlertEventEntity
import com.shadowalert.app.data.local.entity.AppLimitEntity
import com.shadowalert.app.data.local.entity.DailyAppUsageEntity
import com.shadowalert.app.data.local.entity.IntentSessionEntity
import com.shadowalert.app.domain.logic.AnalyticsCalculator
import com.shadowalert.app.domain.model.AlertType
import com.shadowalert.app.domain.model.AlertUserResponse
import com.shadowalert.app.domain.model.AppCategory
import com.shadowalert.app.domain.model.SessionCompletionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsCalculatorTest {

    @Test
    fun `computes daily weekly session completion break frequency and override history accurately`() {
        val today = 20_000L
        val limits = listOf(
            AppLimitEntity(
                id = 1L,
                packageName = "com.example.social",
                appName = "SocialApp",
                category = AppCategory.SOCIAL,
                dailyLimitMinutes = 60,
                currentUsageMinutes = 45,
                enabled = true
            )
        )
        val dailyUsages = listOf(
            DailyAppUsageEntity(1L, "com.example.social", today - 1, 30),
            DailyAppUsageEntity(2L, "com.example.social", today, 45)
        )
        val sessions = listOf(
            IntentSessionEntity(
                id = 10L,
                appId = 1L,
                packageName = "com.example.social",
                appName = "SocialApp",
                intendedDuration = 15,
                startTime = 1_000L,
                endTime = 2_000L,
                actualDuration = 14,
                completionStatus = SessionCompletionStatus.TOOK_BREAK
            ),
            IntentSessionEntity(
                id = 11L,
                appId = 1L,
                packageName = "com.example.social",
                appName = "SocialApp",
                intendedDuration = 10,
                startTime = 3_000L,
                endTime = 4_000L,
                actualDuration = 18,
                completionStatus = SessionCompletionStatus.OVERRIDDEN
            )
        )
        val alerts = listOf(
            AlertEventEntity(1L, 10L, AlertType.TWO_MIN_REMAINING, 1_500L, AlertUserResponse.TOOK_BREAK),
            AlertEventEntity(
                id = 2L,
                sessionId = 11L,
                alertType = AlertType.INTENDED_DURATION_REACHED,
                timestamp = 3_800L,
                userResponse = AlertUserResponse.OVERRIDDEN,
                packageName = "com.example.social",
                details = "Finishing message"
            )
        )

        val summary = AnalyticsCalculator.computeSummary(
            appLimits = limits,
            sessions = sessions,
            alertEvents = alerts,
            dailyUsages = dailyUsages,
            dailyTargetMinutes = 180,
            todayEpochDay = today
        )

        assertEquals(45, summary.todayUsageMinutes)
        assertEquals(75, summary.weeklyUsageMinutes)
        assertEquals(37, summary.sevenDayAverageMinutes)
        assertEquals(25, summary.totalIntendedMinutes)
        assertEquals(32, summary.totalActualMinutes)
        assertEquals(1, summary.sessionsCompletedWithinIntent)
        assertEquals(1, summary.sessionsExceedingIntent)
        assertEquals(50, summary.sessionCompletionRatePercent)
        assertEquals(1, summary.breaksTakenCount)
        assertEquals(50, summary.breakFrequencyPercent)
        assertEquals(1, summary.warningEventsCount)
        assertEquals(1, summary.overrideEventsCount)
        assertEquals(1, summary.recoveryMomentsCount)
        assertEquals(25, summary.dailyLimitUtilizationPercent)
        assertEquals(1, summary.recentOverrides.size)
        assertEquals("Finishing message", summary.recentOverrides.first().details)
        assertEquals(100, summary.appBreakdown.first().sharePercent)
        assertTrue(summary.supportiveInsights.isNotEmpty())
    }

    @Test
    fun `formats minutes and seconds cleanly`() {
        assertEquals("2h 15m", AnalyticsCalculator.formatMinutes(135))
        assertEquals("4h", AnalyticsCalculator.formatMinutes(240))
        assertEquals("45m", AnalyticsCalculator.formatMinutes(45))
        assertEquals("02:05", AnalyticsCalculator.formatSecondsAsClock(125))
        assertEquals("1:01:05", AnalyticsCalculator.formatSecondsAsClock(3665))
    }
}
