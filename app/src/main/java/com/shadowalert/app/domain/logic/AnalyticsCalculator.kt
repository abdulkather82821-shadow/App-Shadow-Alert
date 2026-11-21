package com.shadowalert.app.domain.logic

import com.shadowalert.app.data.local.entity.AlertEventEntity
import com.shadowalert.app.data.local.entity.AppLimitEntity
import com.shadowalert.app.data.local.entity.DailyAppUsageEntity
import com.shadowalert.app.data.local.entity.IntentSessionEntity
import com.shadowalert.app.domain.model.AlertType
import com.shadowalert.app.domain.model.AlertUserResponse
import com.shadowalert.app.domain.model.AnalyticsSummary
import com.shadowalert.app.domain.model.AppUsageBreakdown
import com.shadowalert.app.domain.model.DailyUsagePoint
import com.shadowalert.app.domain.model.IntendedVsActualPoint
import com.shadowalert.app.domain.model.OverrideHistoryItem
import com.shadowalert.app.domain.model.SessionCompletionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

object AnalyticsCalculator {

    fun computeSummary(
        appLimits: List<AppLimitEntity>,
        sessions: List<IntentSessionEntity>,
        alertEvents: List<AlertEventEntity>,
        dailyUsages: List<DailyAppUsageEntity>,
        dailyTargetMinutes: Int,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): AnalyticsSummary {
        val enabledApps = appLimits.filter { it.enabled }
        val todayUsageFromLimits = enabledApps.sumOf { it.currentUsageMinutes }

        // Build 7-day timeline [today - 6 .. today]
        val startEpochDay = todayEpochDay - 6
        val usageByDay = dailyUsages
            .filter { it.epochDay in startEpochDay..todayEpochDay }
            .groupBy { it.epochDay }
            .mapValues { (_, list) -> list.sumOf { it.usageMinutes } }

        val sevenDayPoints = (startEpochDay..todayEpochDay).map { epochDay ->
            val date = LocalDate.ofEpochDay(epochDay)
            val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val recorded = usageByDay[epochDay] ?: 0
            val minutes = if (epochDay == todayEpochDay) {
                maxOf(recorded, todayUsageFromLimits)
            } else {
                recorded
            }
            DailyUsagePoint(
                epochDay = epochDay,
                dayLabel = label,
                totalMinutes = minutes
            )
        }

        val todayUsage = sevenDayPoints.lastOrNull()?.totalMinutes ?: todayUsageFromLimits
        val weeklyUsage = sevenDayPoints.sumOf { it.totalMinutes }
        val activeDays = sevenDayPoints.count { it.totalMinutes > 0 }.coerceAtLeast(1)
        val sevenDayAverage = weeklyUsage / activeDays
        val usageTrendPercent = if (sevenDayAverage > 0) {
            (((todayUsage - sevenDayAverage).toFloat() / sevenDayAverage.toFloat()) * 100f).toInt()
        } else {
            0
        }

        val completedSessions = sessions.filter { it.completionStatus != SessionCompletionStatus.ACTIVE }
        val totalIntended = completedSessions.sumOf { it.intendedDuration }
        val totalActual = completedSessions.sumOf { it.actualDuration }

        val withinIntentCount = completedSessions.count {
            (it.completionStatus == SessionCompletionStatus.COMPLETED_WITHIN_INTENT ||
                it.completionStatus == SessionCompletionStatus.COMPLETED_EARLY ||
                it.completionStatus == SessionCompletionStatus.TOOK_BREAK) &&
                it.actualDuration <= it.intendedDuration
        }
        val exceedingIntentCount = completedSessions.count {
            it.completionStatus == SessionCompletionStatus.OVERRIDDEN ||
                it.actualDuration > it.intendedDuration
        }
        val completionRatePercent = if (completedSessions.isNotEmpty()) {
            ((withinIntentCount.toFloat() / completedSessions.size.toFloat()) * 100f).toInt()
        } else {
            0
        }

        val breaksTaken = completedSessions.count {
            it.completionStatus == SessionCompletionStatus.TOOK_BREAK
        } + alertEvents.count {
            it.userResponse == AlertUserResponse.TOOK_BREAK && it.sessionId <= 0L
        }
        val breakFreqPercent = if (completedSessions.isNotEmpty()) {
            ((breaksTaken.toFloat() / completedSessions.size.toFloat()) * 100f).toInt().coerceAtMost(100)
        } else {
            0
        }

        val warningEventsCount = alertEvents.count {
            it.alertType == AlertType.FIVE_MIN_REMAINING ||
                it.alertType == AlertType.TWO_MIN_REMAINING ||
                it.alertType == AlertType.CUSTOM_WARNING ||
                it.alertType == AlertType.DAILY_LIMIT_APPROACHING
        }
        val overrideEventsCount = alertEvents.count {
            it.userResponse == AlertUserResponse.OVERRIDDEN
        }
        val recoveryMomentsCount = alertEvents.count {
            it.userResponse == AlertUserResponse.ENDED_SESSION ||
                it.userResponse == AlertUserResponse.TOOK_BREAK
        }

        val sessionById = sessions.associateBy { it.id }
        val recentOverrides = alertEvents
            .filter { it.userResponse == AlertUserResponse.OVERRIDDEN }
            .sortedByDescending { it.timestamp }
            .take(10)
            .map { event ->
                val session = sessionById[event.sessionId]
                val appLabel = session?.appName ?: event.alertType.displayName
                OverrideHistoryItem(
                    alertId = event.id,
                    timestamp = event.timestamp,
                    label = appLabel,
                    details = event.details ?: session?.optionalPurpose
                )
            }

        val safeTarget = dailyTargetMinutes.coerceAtLeast(1)
        val utilizationPercent = ((todayUsage.toFloat() / safeTarget.toFloat()) * 100f)
            .toInt()
            .coerceIn(0, 999)

        val totalAppMinutes = enabledApps.sumOf { it.currentUsageMinutes }.coerceAtLeast(1)
        val appBreakdown = enabledApps
            .sortedByDescending { it.currentUsageMinutes }
            .map {
                AppUsageBreakdown(
                    packageName = it.packageName,
                    appName = it.appName,
                    category = it.category,
                    usageMinutes = it.currentUsageMinutes,
                    dailyLimitMinutes = it.dailyLimitMinutes + it.dailyExtraMinutes,
                    sharePercent = ((it.currentUsageMinutes.toFloat() / totalAppMinutes) * 100f).toInt()
                )
            }

        val recentSessions = completedSessions
            .sortedByDescending { it.startTime }
            .take(8)
            .reversed()
            .map {
                IntendedVsActualPoint(
                    sessionId = it.id,
                    appName = it.appName,
                    intendedMinutes = it.intendedDuration,
                    actualMinutes = it.actualDuration,
                    withinIntent = it.actualDuration <= it.intendedDuration &&
                        it.completionStatus != SessionCompletionStatus.OVERRIDDEN
                )
            }

        val insights = buildSupportiveInsights(
            todayUsage = todayUsage,
            sevenDayAverage = sevenDayAverage,
            usageTrendPercent = usageTrendPercent,
            dailyTarget = safeTarget,
            withinIntent = withinIntentCount,
            totalCompleted = completedSessions.size,
            completionRate = completionRatePercent,
            breaksTaken = breaksTaken,
            recoveryMoments = recoveryMomentsCount,
            overrideCount = overrideEventsCount,
            totalIntended = totalIntended,
            totalActual = totalActual,
            topApp = appBreakdown.firstOrNull()
        )

        return AnalyticsSummary(
            todayUsageMinutes = todayUsage,
            weeklyUsageMinutes = weeklyUsage,
            sevenDayAverageMinutes = sevenDayAverage,
            usageTrendPercentVsAvg = usageTrendPercent,
            totalIntendedMinutes = totalIntended,
            totalActualMinutes = totalActual,
            sessionsCompletedWithinIntent = withinIntentCount,
            sessionsExceedingIntent = exceedingIntentCount,
            totalCompletedSessions = completedSessions.size,
            sessionCompletionRatePercent = completionRatePercent,
            warningEventsCount = warningEventsCount,
            overrideEventsCount = overrideEventsCount,
            recoveryMomentsCount = recoveryMomentsCount,
            breaksTakenCount = breaksTaken,
            breakFrequencyPercent = breakFreqPercent,
            dailyTargetMinutes = safeTarget,
            dailyLimitUtilizationPercent = utilizationPercent,
            sevenDayUsage = sevenDayPoints,
            appBreakdown = appBreakdown,
            intendedVsActualSessions = recentSessions,
            recentOverrides = recentOverrides,
            supportiveInsights = insights
        )
    }

    private fun buildSupportiveInsights(
        todayUsage: Int,
        sevenDayAverage: Int,
        usageTrendPercent: Int,
        dailyTarget: Int,
        withinIntent: Int,
        totalCompleted: Int,
        completionRate: Int,
        breaksTaken: Int,
        recoveryMoments: Int,
        overrideCount: Int,
        totalIntended: Int,
        totalActual: Int,
        topApp: AppUsageBreakdown?
    ): List<String> {
        val notes = mutableListOf<String>()
        if (totalCompleted == 0) {
            notes += "Start your first intention session to compare planned vs. actual focus time."
        } else {
            notes += "Session completion rate: $completionRate% ($withinIntent of $totalCompleted finished within your planned duration)."
        }

        if (sevenDayAverage > 0 && todayUsage > 0) {
            when {
                usageTrendPercent <= -10 ->
                    notes += "Today's usage is ${-usageTrendPercent}% lower than your 7-day daily average (${formatMinutes(sevenDayAverage)})."
                usageTrendPercent >= 15 ->
                    notes += "Today's usage is $usageTrendPercent% above your 7-day daily average (${formatMinutes(sevenDayAverage)})."
                else ->
                    notes += "Today's screen time aligns closely with your 7-day average (${formatMinutes(sevenDayAverage)})."
            }
        }

        if (topApp != null && topApp.usageMinutes > 0) {
            notes += "Most-used monitored app today: ${topApp.appName} (${formatMinutes(topApp.usageMinutes)}, ${topApp.sharePercent}% of monitored time)."
        }

        if (breaksTaken > 0 || recoveryMoments > 0) {
            notes += "Mindful recoveries: $recoveryMoments alert-guided wrap-ups and $breaksTaken intentional break${if (breaksTaken == 1) "" else "s"}."
        }

        if (totalIntended > 0 && totalActual > totalIntended) {
            val extra = totalActual - totalIntended
            notes += "Actual session time exceeded planned time by ${extra}m overall—try adding a 5-minute buffer when declaring intentions."
        } else if (totalIntended > 0) {
            notes += "Your actual session durations stayed aligned with your planned intentions."
        }

        val remaining = dailyTarget - todayUsage
        if (remaining > 0) {
            notes += "You have ${formatMinutes(remaining)} remaining toward today's screen-time target."
        } else if (todayUsage > 0) {
            notes += "You reached today's target—consider taking a screen-free pause when convenient."
        }

        if (overrideCount > 0) {
            notes += "$overrideCount intentional override${if (overrideCount == 1) "" else "s"} recorded neutrally so you can spot patterns over time."
        }
        return notes
    }

    fun formatMinutes(totalMinutes: Int): String {
        val safe = totalMinutes.coerceAtLeast(0)
        val hours = safe / 60
        val mins = safe % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    fun formatSecondsAsClock(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val hours = safe / 3600L
        val minutes = (safe % 3600L) / 60L
        val seconds = safe % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun epochDayFromTimestamp(timestampMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return Instant.ofEpochMilli(timestampMs).atZone(zoneId).toLocalDate().toEpochDay()
    }
}
