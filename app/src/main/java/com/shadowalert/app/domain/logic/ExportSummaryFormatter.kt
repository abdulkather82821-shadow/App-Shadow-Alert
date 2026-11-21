package com.shadowalert.app.domain.logic

import com.shadowalert.app.data.local.entity.IntentSessionEntity
import com.shadowalert.app.data.local.entity.ReflectionEntity
import com.shadowalert.app.domain.model.AnalyticsSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ExportSummaryFormatter {

    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    fun formatMarkdownSummary(
        summary: AnalyticsSummary,
        sessions: List<IntentSessionEntity>,
        reflections: List<ReflectionEntity>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# Shadow Alert — Local Usage Summary")
        sb.appendLine("Generated: ${dateTimeFormatter.format(Instant.now())}")
        sb.appendLine("Principle: Plan before use. Stay aware during use. Take control after use.")
        sb.appendLine()
        sb.appendLine("## Daily & Weekly Overview")
        sb.appendLine("- Today's Usage: ${AnalyticsCalculator.formatMinutes(summary.todayUsageMinutes)}")
        sb.appendLine("- Daily Target: ${AnalyticsCalculator.formatMinutes(summary.dailyTargetMinutes)} (${summary.dailyLimitUtilizationPercent}% utilized)")
        sb.appendLine("- 7-Day Total Usage: ${AnalyticsCalculator.formatMinutes(summary.weeklyUsageMinutes)} (Avg ${AnalyticsCalculator.formatMinutes(summary.sevenDayAverageMinutes)}/day)")
        sb.appendLine("- Total Intended Duration: ${AnalyticsCalculator.formatMinutes(summary.totalIntendedMinutes)}")
        sb.appendLine("- Total Actual Duration: ${AnalyticsCalculator.formatMinutes(summary.totalActualMinutes)}")
        sb.appendLine("- Session Completion Rate: ${summary.sessionCompletionRatePercent}% (${summary.sessionsCompletedWithinIntent}/${summary.totalCompletedSessions})")
        sb.appendLine("- Breaks Taken: ${summary.breaksTakenCount} (${summary.breakFrequencyPercent}%)")
        sb.appendLine("- Warning Events: ${summary.warningEventsCount}")
        sb.appendLine("- Override Events: ${summary.overrideEventsCount}")
        sb.appendLine("- Recovery Moments: ${summary.recoveryMomentsCount}")
        sb.appendLine()
        sb.appendLine("## Monitored Applications")
        if (summary.appBreakdown.isEmpty()) {
            sb.appendLine("- No monitored applications configured.")
        } else {
            summary.appBreakdown.forEach { app ->
                sb.appendLine(
                    "- ${app.appName} (${app.category.displayName}): " +
                        "${AnalyticsCalculator.formatMinutes(app.usageMinutes)} / " +
                        "${AnalyticsCalculator.formatMinutes(app.dailyLimitMinutes)} (${app.sharePercent}%)"
                )
            }
        }
        sb.appendLine()
        sb.appendLine("## Recent Intention Sessions")
        if (sessions.isEmpty()) {
            sb.appendLine("- No sessions recorded yet.")
        } else {
            sessions.take(20).forEach { s ->
                val start = dateTimeFormatter.format(Instant.ofEpochMilli(s.startTime))
                val purposeNote = if (s.optionalPurpose.isNullOrBlank()) "" else " — \"${s.optionalPurpose}\""
                sb.appendLine(
                    "- [$start] ${s.appName}: intended ${s.intendedDuration}m, actual ${s.actualDuration}m " +
                        "(${s.completionStatus.label})$purposeNote"
                )
            }
        }
        if (summary.recentOverrides.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## Override History")
            summary.recentOverrides.forEach { item ->
                val at = dateTimeFormatter.format(Instant.ofEpochMilli(item.timestamp))
                val detail = if (item.details.isNullOrBlank()) "" else " — ${item.details}"
                sb.appendLine("- [$at] ${item.label}$detail")
            }
        }
        if (reflections.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## Reflections")
            reflections.take(20).forEach { r ->
                val at = dateTimeFormatter.format(Instant.ofEpochMilli(r.createdAt))
                sb.appendLine("- [$at] Session #${r.sessionId}: ${r.response}")
            }
        }
        return sb.toString()
    }
}
