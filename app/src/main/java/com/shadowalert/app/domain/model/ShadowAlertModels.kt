package com.shadowalert.app.domain.model

enum class AppCategory(val displayName: String) {
    SOCIAL("Social"),
    ENTERTAINMENT("Entertainment"),
    NEWS("News"),
    GAMES("Games"),
    FOCUS("Focus");

    companion object {
        fun fromString(value: String): AppCategory =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FOCUS
    }
}

enum class SessionCompletionStatus(val label: String) {
    ACTIVE("Active"),
    COMPLETED_WITHIN_INTENT("Within Intent"),
    COMPLETED_EARLY("Ended Early"),
    TOOK_BREAK("Took a Break"),
    EXTENDED_TIME("Added Time"),
    OVERRIDDEN("Continued with Override"),
    INTERRUPTED("Interrupted");

    val isFinished: Boolean
        get() = this != ACTIVE
}

enum class AlertType(val displayName: String) {
    FIVE_MIN_REMAINING("5-Minute Warning"),
    TWO_MIN_REMAINING("2-Minute Warning"),
    CUSTOM_WARNING("Custom Warning"),
    INTENDED_DURATION_REACHED("Intended Duration Reached"),
    DAILY_LIMIT_APPROACHING("Daily Limit Approaching"),
    DAILY_LIMIT_REACHED("Daily Limit Reached")
}

enum class AlertUserResponse(val label: String) {
    NONE("No Response Yet"),
    ACKNOWLEDGED("Acknowledged"),
    ENDED_SESSION("Ended Session"),
    TOOK_BREAK("Took a Break"),
    ADDED_TIME("Added More Time"),
    OVERRIDDEN("Continued with Override"),
    DISMISSED("Dismissed")
}

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class InterventionMode(val label: String, val description: String) {
    NOTIFICATION_ONLY(
        "Gentle Notifications",
        "Sends supportive alerts without opening a full-screen pause prompt."
    ),
    REMINDER_AND_PAUSE(
        "Pause & Reflection Prompt",
        "Shows a high-priority pause prompt and opens the intervention screen inside Shadow Alert."
    ),
    STRONG_SHIELD(
        "Strong Overlay Shield",
        "Automatically opens the Pause & Reflection shield over other apps when Overlay permission is granted."
    )
}

enum class InterventionTriggerType {
    INTENTION_COMPLETE,
    DAILY_LIMIT_REACHED
}

enum class TrackingMode(val label: String) {
    AUTOMATIC_USAGE_STATS("Real-Time (Usage Access)"),
    MANUAL_SESSION_TIMER("Manual Session Fallback"),
    PAUSED_PERMISSION_REVOKED("Paused (Permission Required)"),
    IDLE("Idle")
}

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val suggestedCategory: AppCategory
)

data class PermissionStatus(
    val usageAccessGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false
) {
    val canMonitorAutomatically: Boolean
        get() = usageAccessGranted
}

data class MonitoringState(
    val isServiceRunning: Boolean = false,
    val trackingMode: TrackingMode = TrackingMode.IDLE,
    val activeSessionId: Long? = null,
    val activePackageName: String? = null,
    val activeAppName: String? = null,
    val currentForegroundPackage: String? = null,
    val isTargetAppInForeground: Boolean = false,
    val isScreenInteractive: Boolean = true,
    val intendedDurationMinutes: Int = 0,
    val elapsedSessionSeconds: Long = 0L,
    val remainingSessionSeconds: Long = 0L,
    val isPausedForBreak: Boolean = false,
    val breakRemainingSeconds: Long = 0L,
    val permissionError: String? = null
)

data class DailyUsagePoint(
    val epochDay: Long,
    val dayLabel: String,
    val totalMinutes: Int
)

data class AppUsageBreakdown(
    val packageName: String,
    val appName: String,
    val category: AppCategory,
    val usageMinutes: Int,
    val dailyLimitMinutes: Int,
    val sharePercent: Int = 0
)

data class IntendedVsActualPoint(
    val sessionId: Long,
    val appName: String,
    val intendedMinutes: Int,
    val actualMinutes: Int,
    val withinIntent: Boolean
)

data class OverrideHistoryItem(
    val alertId: Long,
    val timestamp: Long,
    val label: String,
    val details: String?
)

data class AnalyticsSummary(
    val todayUsageMinutes: Int = 0,
    val weeklyUsageMinutes: Int = 0,
    val sevenDayAverageMinutes: Int = 0,
    val usageTrendPercentVsAvg: Int = 0,
    val totalIntendedMinutes: Int = 0,
    val totalActualMinutes: Int = 0,
    val sessionsCompletedWithinIntent: Int = 0,
    val sessionsExceedingIntent: Int = 0,
    val totalCompletedSessions: Int = 0,
    val sessionCompletionRatePercent: Int = 0,
    val warningEventsCount: Int = 0,
    val overrideEventsCount: Int = 0,
    val recoveryMomentsCount: Int = 0,
    val breaksTakenCount: Int = 0,
    val breakFrequencyPercent: Int = 0,
    val dailyTargetMinutes: Int = 240,
    val dailyLimitUtilizationPercent: Int = 0,
    val sevenDayUsage: List<DailyUsagePoint> = emptyList(),
    val appBreakdown: List<AppUsageBreakdown> = emptyList(),
    val intendedVsActualSessions: List<IntendedVsActualPoint> = emptyList(),
    val recentOverrides: List<OverrideHistoryItem> = emptyList(),
    val supportiveInsights: List<String> = emptyList()
)
