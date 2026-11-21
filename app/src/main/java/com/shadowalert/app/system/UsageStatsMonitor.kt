package com.shadowalert.app.system

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class UsageStatsMonitor(private val context: Context) {

    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val keyguardManager: KeyguardManager? =
        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

    fun isScreenInteractive(): Boolean {
        val screenOn = powerManager?.isInteractive ?: true
        val locked = keyguardManager?.isKeyguardLocked ?: false
        return screenOn && !locked
    }

    /**
     * Identifies the currently active foreground package using chronological UsageEvents
     * (tracking both RESUMED and PAUSED/STOPPED/SCREEN_NON_INTERACTIVE transitions).
     * Returns null when the screen is off/locked, Usage Access is denied, or no app is active.
     */
    fun getCurrentForegroundPackage(lookbackMs: Long = 15 * 60_000L): String? {
        if (!isScreenInteractive()) return null
        if (!PermissionHelper.hasUsageAccessPermission(context)) return null
        val manager = usageStatsManager ?: return null

        val endTime = System.currentTimeMillis()
        val startTime = (endTime - lookbackMs).coerceAtLeast(0L)
        val events = manager.queryEvents(startTime, endTime) ?: return null

        val event = UsageEvents.Event()
        var currentForeground: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when {
                isForegroundEvent(event.eventType) -> {
                    currentForeground = event.packageName
                }
                isBackgroundEvent(event.eventType) -> {
                    if (event.packageName == currentForeground) {
                        currentForeground = null
                    }
                }
                isScreenOffOrLockEvent(event.eventType) -> {
                    currentForeground = null
                }
            }
        }
        return currentForeground
    }

    /**
     * Computes foreground usage in minutes per package within [startMs, endMs] using a
     * single-active-window UsageEvents state machine so screen-off time and overlapping
     * transitions are never double-counted.
     */
    fun queryUsageMinutesInWindow(
        targetPackages: Set<String>,
        startMs: Long,
        endMs: Long,
        includeOpenTail: Boolean
    ): Map<String, Int> {
        if (targetPackages.isEmpty() || !PermissionHelper.hasUsageAccessPermission(context)) {
            return emptyMap()
        }
        val manager = usageStatsManager ?: return emptyMap()
        val events = manager.queryEvents(startMs, endMs) ?: return emptyMap()
        val event = UsageEvents.Event()

        var activePackage: String? = null
        var activeStartMs = 0L
        val totalDurationsMs = HashMap<String, Long>()

        fun closeActiveAt(timestampMs: Long) {
            val pkg = activePackage
            if (pkg != null && pkg in targetPackages && timestampMs > activeStartMs) {
                totalDurationsMs[pkg] = (totalDurationsMs[pkg] ?: 0L) + (timestampMs - activeStartMs)
            }
            activePackage = null
            activeStartMs = 0L
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val ts = event.timeStamp.coerceIn(startMs, endMs)
            val pkg = event.packageName

            when {
                isForegroundEvent(event.eventType) -> {
                    if (activePackage != pkg) {
                        closeActiveAt(ts)
                        if (pkg != null && pkg in targetPackages) {
                            activePackage = pkg
                            activeStartMs = ts
                        }
                    }
                }
                isBackgroundEvent(event.eventType) -> {
                    if (pkg != null && pkg == activePackage) {
                        closeActiveAt(ts)
                    }
                }
                isScreenOffOrLockEvent(event.eventType) -> {
                    closeActiveAt(ts)
                }
            }
        }

        if (includeOpenTail && isScreenInteractive()) {
            closeActiveAt(endMs)
        }

        return totalDurationsMs.mapValues { (_, ms) -> (ms / 60_000L).toInt().coerceAtLeast(0) }
    }

    /**
     * Computes today's foreground usage in minutes per package from [resetHour] to now.
     */
    fun queryTodayUsageMinutes(
        targetPackages: Set<String>,
        resetHour: Int = 0
    ): Map<String, Int> {
        val zone = ZoneId.systemDefault()
        val nowTime = LocalTime.now(zone)
        val today = LocalDate.now(zone)
        val startDate = if (nowTime.hour < resetHour) today.minusDays(1) else today
        val startMs = startDate.atTime(resetHour, 0).atZone(zone).toInstant().toEpochMilli()
        val endMs = System.currentTimeMillis()
        return queryUsageMinutesInWindow(
            targetPackages = targetPackages,
            startMs = startMs,
            endMs = endMs,
            includeOpenTail = true
        )
    }

    /**
     * Queries up to 7 days of historical daily usage per package using UsageEvents per day
     * with INTERVAL_DAILY fallback for older days whose raw event logs may have rotated.
     */
    fun querySevenDayUsageMinutes(targetPackages: Set<String>): Map<Pair<String, Long>, Int> {
        if (targetPackages.isEmpty() || !PermissionHelper.hasUsageAccessPermission(context)) {
            return emptyMap()
        }
        val manager = usageStatsManager ?: return emptyMap()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val result = HashMap<Pair<String, Long>, Int>()

        // Prefer accurate local-day UsageEvents for recent days
        for (offset in 1L..6L) {
            val day = today.minusDays(offset)
            val startMs = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val dayUsage = queryUsageMinutesInWindow(
                targetPackages = targetPackages,
                startMs = startMs,
                endMs = endMs,
                includeOpenTail = false
            )
            dayUsage.forEach { (pkg, mins) ->
                if (mins > 0) {
                    result[pkg to day.toEpochDay()] = mins
                }
            }
        }

        // Backfill any older days missing from event retention via INTERVAL_DAILY
        val weekStartMs = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val nowMs = System.currentTimeMillis()
        val statsList = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, weekStartMs, nowMs)
            ?: emptyList()

        for (stats in statsList) {
            val pkg = stats.packageName ?: continue
            if (pkg !in targetPackages) continue
            val minutes = (stats.totalTimeInForeground / 60_000L).toInt()
            if (minutes <= 0) continue

            val epochDay = Instant.ofEpochMilli(stats.firstTimeStamp)
                .atZone(zone)
                .toLocalDate()
                .toEpochDay()
            if (epochDay < today.toEpochDay()) {
                val key = pkg to epochDay
                if (!result.containsKey(key)) {
                    result[key] = minutes
                }
            }
        }
        return result
    }

    private fun isForegroundEvent(eventType: Int): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            eventType == UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
        }
    }

    private fun isBackgroundEvent(eventType: Int): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                eventType == UsageEvents.Event.ACTIVITY_STOPPED
        } else {
            @Suppress("DEPRECATION")
            eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
        }
    }

    private fun isScreenOffOrLockEvent(eventType: Int): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
                eventType == UsageEvents.Event.KEYGUARD_SHOWN
        } else {
            false
        }
    }
}
