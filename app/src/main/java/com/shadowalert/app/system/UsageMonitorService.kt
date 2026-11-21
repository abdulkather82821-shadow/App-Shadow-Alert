package com.shadowalert.app.system

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.shadowalert.app.ShadowAlertApplication
import com.shadowalert.app.data.preferences.UserPreferences
import com.shadowalert.app.domain.logic.AnalyticsCalculator
import com.shadowalert.app.domain.logic.ElapsedTimeTracker
import com.shadowalert.app.domain.logic.WarningEvaluator
import com.shadowalert.app.domain.model.AlertType
import com.shadowalert.app.domain.model.AlertUserResponse
import com.shadowalert.app.domain.model.InterventionMode
import com.shadowalert.app.domain.model.InterventionTriggerType
import com.shadowalert.app.domain.model.MonitoringState
import com.shadowalert.app.domain.model.SessionCompletionStatus
import com.shadowalert.app.domain.model.TrackingMode
import com.shadowalert.app.ui.PauseInterventionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class UsageMonitorService : LifecycleService() {

    private var monitorJob: Job? = null
    private val elapsedTracker = ElapsedTimeTracker()
    private val warningEvaluator = WarningEvaluator()

    @Volatile
    private var cachedPrefs: UserPreferences = UserPreferences()

    private val screenInteractiveFlow = MutableStateFlow(true)
    private var lastDailySyncElapsedMs = 0L
    private var lastDailyResetCheckElapsedMs = 0L
    private var lastDbPersistElapsedMs = 0L
    private var permissionNoticeShown = false
    private var manualModeSessionId: Long? = null
    private var breakUntilElapsedMs: Long = 0L
    private var hydratedSessionId: Long? = null
    private var interventionTriggeredForSessionId: Long? = null
    private val lastDailyInterventionElapsedMs = ConcurrentHashMap<String, Long>()

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenInteractiveFlow.value = false
                    elapsedTracker.pauseTickAnchor()
                    persistCurrentProgressAsync()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    screenInteractiveFlow.value = UsageStatsMonitor(this@UsageMonitorService).isScreenInteractive()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        val app = application as ShadowAlertApplication

        lifecycleScope.launch {
            app.container.preferencesDataStore.preferencesFlow.collect { prefs ->
                cachedPrefs = prefs
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenInteractiveFlow.value = app.container.usageStatsMonitor.isScreenInteractive()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val app = application as ShadowAlertApplication
        val action = intent?.action ?: ACTION_START_MONITORING

        when (action) {
            ACTION_STOP_MONITORING -> {
                promoteToForeground("Shadow Alert", "Stopping monitoring...")
                stopMonitoringGracefully()
                return START_NOT_STICKY
            }

            ACTION_START_SESSION -> {
                val sessionId = intent?.getLongExtra(EXTRA_SESSION_ID, -1L) ?: -1L
                val manualMode = intent?.getBooleanExtra(EXTRA_MANUAL_MODE, false) ?: false
                if (manualMode && sessionId > 0L) {
                    manualModeSessionId = sessionId
                }
                if (sessionId > 0L && sessionId != hydratedSessionId) {
                    interventionTriggeredForSessionId = null
                    breakUntilElapsedMs = 0L
                    hydratedSessionId = null
                }
            }

            ACTION_EXTEND_SESSION -> {
                interventionTriggeredForSessionId = null
                breakUntilElapsedMs = 0L
                warningEvaluator.onSessionExtended()
                NotificationHelper.cancelPauseNotifications(this)
            }

            ACTION_PAUSE_FOR_BREAK -> {
                val breakMinutes = intent?.getIntExtra(EXTRA_BREAK_MINUTES, 5) ?: 5
                breakUntilElapsedMs = SystemClock.elapsedRealtime() + breakMinutes * 60_000L
                NotificationHelper.cancelPauseNotifications(this)
            }

            ACTION_RESUME_FROM_BREAK -> {
                breakUntilElapsedMs = 0L
            }

            ACTION_END_SESSION_FROM_NOTIFICATION -> {
                val sessionId = intent?.getLongExtra(EXTRA_SESSION_ID, -1L) ?: -1L
                lifecycleScope.launch(Dispatchers.IO) {
                    if (sessionId > 0L) {
                        val hasUsageAccess = PermissionHelper.hasUsageAccessPermission(this@UsageMonitorService)
                        app.container.repository.finishSession(
                            sessionId = sessionId,
                            status = SessionCompletionStatus.COMPLETED_WITHIN_INTENT,
                            userResponse = AlertUserResponse.ENDED_SESSION,
                            accumulatedActiveMsOverride = elapsedTracker.getAccumulatedActiveMs(),
                            isManualFallbackMode = !hasUsageAccess
                        )
                    }
                    NotificationHelper.cancelPauseNotifications(this@UsageMonitorService)
                }
            }
        }

        promoteToForeground("Shadow Alert is active", "Monitoring your planned intentions.")
        startMonitoringLoopIfNeeded()
        return START_STICKY
    }

    private fun promoteToForeground(title: String, content: String) {
        val notification = NotificationHelper.buildMonitoringNotification(
            context = this,
            title = title,
            content = content
        )
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIFICATION_ID_FOREGROUND,
            notification,
            serviceType
        )
    }

    private fun persistCurrentProgressAsync() {
        val sessionId = hydratedSessionId ?: return
        val accumulated = elapsedTracker.getAccumulatedActiveMs()
        if (accumulated <= 0L) return
        val app = application as ShadowAlertApplication
        lifecycleScope.launch(Dispatchers.IO) {
            app.container.repository.updateSessionProgress(sessionId, accumulated)
        }
    }

    private fun startMonitoringLoopIfNeeded() {
        if (monitorJob?.isActive == true) return
        val app = application as ShadowAlertApplication
        val repository = app.container.repository
        val stateHolder = app.container.monitorStateHolder
        val usageStatsMonitor = app.container.usageStatsMonitor
        val installedAppsRepository = app.container.installedAppsRepository

        monitorJob = lifecycleScope.launch {
            while (isActive) {
                // Suspend zero-CPU while device screen is turned off
                if (!screenInteractiveFlow.value) {
                    elapsedTracker.pauseTickAnchor()
                    val current = stateHolder.monitoringState.value
                    if (current.isScreenInteractive) {
                        stateHolder.update(
                            current.copy(
                                isScreenInteractive = false,
                                isTargetAppInForeground = false
                            )
                        )
                    }
                    screenInteractiveFlow.first { it }
                    continue
                }

                val nowElapsed = SystemClock.elapsedRealtime()
                val prefs = cachedPrefs

                if (lastDailyResetCheckElapsedMs == 0L || nowElapsed - lastDailyResetCheckElapsedMs >= 60_000L) {
                    lastDailyResetCheckElapsedMs = nowElapsed
                    withContext(Dispatchers.IO) {
                        repository.checkAndApplyDailyResetIfNeeded()
                    }
                }

                val hasUsageAccess = PermissionHelper.hasUsageAccessPermission(this@UsageMonitorService)
                val activeSession = withContext(Dispatchers.IO) { repository.getActiveSession() }

                val onBreak = breakUntilElapsedMs > nowElapsed
                val breakRemainingSec = if (onBreak) {
                    ((breakUntilElapsedMs - nowElapsed) / 1000L).coerceAtLeast(0L)
                } else {
                    0L
                }

                val trackingMode = when {
                    activeSession != null && !hasUsageAccess -> TrackingMode.MANUAL_SESSION_TIMER
                    !hasUsageAccess -> TrackingMode.PAUSED_PERMISSION_REVOKED
                    else -> TrackingMode.AUTOMATIC_USAGE_STATS
                }

                if (trackingMode == TrackingMode.PAUSED_PERMISSION_REVOKED) {
                    if (!permissionNoticeShown && prefs.monitoringEnabled) {
                        permissionNoticeShown = true
                        NotificationHelper.showPermissionRevokedNotification(this@UsageMonitorService)
                    }
                } else {
                    permissionNoticeShown = false
                }

                val interactive = usageStatsMonitor.isScreenInteractive()
                if (!interactive) {
                    screenInteractiveFlow.value = false
                    continue
                }

                val foregroundPkg = if (hasUsageAccess) {
                    withContext(Dispatchers.IO) { usageStatsMonitor.getCurrentForegroundPackage() }
                } else {
                    null
                }

                // Periodically reconcile today's UsageStatsManager usage (every 20s)
                if (hasUsageAccess && (lastDailySyncElapsedMs == 0L || nowElapsed - lastDailySyncElapsedMs >= 20_000L)) {
                    lastDailySyncElapsedMs = nowElapsed
                    withContext(Dispatchers.IO) {
                        syncDailyUsageAndCheckLimits(
                            repository = repository,
                            usageStatsMonitor = usageStatsMonitor,
                            installedAppsRepository = installedAppsRepository,
                            resetHour = prefs.dailyResetHour,
                            prefs = prefs
                        )
                    }
                }

                // Level 3 Strong Daily Limit Enforcement while actively using a monitored app
                if (hasUsageAccess && foregroundPkg != null && foregroundPkg != packageName && !onBreak) {
                    val appLimit = withContext(Dispatchers.IO) {
                        repository.getAppLimitByPackage(foregroundPkg)
                    }
                    if (appLimit != null &&
                        appLimit.enabled &&
                        appLimit.currentUsageMinutes >= appLimit.effectiveDailyLimitMinutes &&
                        System.currentTimeMillis() > appLimit.overrideUntilMs &&
                        prefs.interventionMode != InterventionMode.NOTIFICATION_ONLY &&
                        activeSession?.packageName != foregroundPkg
                    ) {
                        val lastTriggered = lastDailyInterventionElapsedMs[foregroundPkg] ?: 0L
                        if (nowElapsed - lastTriggered >= 60_000L) {
                            lastDailyInterventionElapsedMs[foregroundPkg] = nowElapsed
                            triggerIntervention(
                                sessionId = 0L,
                                packageName = appLimit.packageName,
                                appName = appLimit.appName,
                                intendedMinutes = appLimit.effectiveDailyLimitMinutes,
                                triggerType = InterventionTriggerType.DAILY_LIMIT_REACHED,
                                prefs = prefs,
                                stateHolder = stateHolder
                            )
                        }
                    }
                }

                if (activeSession == null) {
                    hydratedSessionId = null
                    elapsedTracker.reset(null, 0L)
                    warningEvaluator.reset()
                    interventionTriggeredForSessionId = null
                    manualModeSessionId = null

                    val idleState = MonitoringState(
                        isServiceRunning = true,
                        trackingMode = trackingMode,
                        currentForegroundPackage = foregroundPkg,
                        isScreenInteractive = true,
                        isPausedForBreak = onBreak,
                        breakRemainingSeconds = breakRemainingSec,
                        permissionError = if (!hasUsageAccess) {
                            "Usage Access is disabled. Enable it for automatic tracking, or start a Manual Session."
                        } else null
                    )
                    stateHolder.update(idleState)

                    val notifBody = when {
                        onBreak -> "Mindful break: ${AnalyticsCalculator.formatSecondsAsClock(breakRemainingSec)} left"
                        trackingMode == TrackingMode.AUTOMATIC_USAGE_STATS ->
                            "Monitoring daily limits locally on device."
                        trackingMode == TrackingMode.PAUSED_PERMISSION_REVOKED ->
                            "Usage Access disabled — automatic monitoring paused."
                        else -> "Ready for your next intention session."
                    }
                    updateForegroundNotification("Shadow Alert", notifBody)

                    if (!prefs.monitoringEnabled && !onBreak) {
                        stopMonitoringGracefully()
                        break
                    }
                    delay(prefs.pollingIntervalSeconds * 1000L)
                } else {
                    // Hydrate persisted elapsed time & already-emitted alerts from Room once per session
                    if (hydratedSessionId != activeSession.id) {
                        hydratedSessionId = activeSession.id
                        elapsedTracker.restoreSessionIfNeeded(activeSession.id, activeSession.accumulatedActiveMs)
                        val priorAlerts = withContext(Dispatchers.IO) {
                            repository.getTriggeredAlertTypesForSession(activeSession.id)
                        }
                        warningEvaluator.syncSession(activeSession.id, priorAlerts)
                        if (AlertType.INTENDED_DURATION_REACHED in priorAlerts) {
                            interventionTriggeredForSessionId = activeSession.id
                        }
                    }

                    val targetInForeground = if (hasUsageAccess) {
                        foregroundPkg == activeSession.packageName
                    } else {
                        true
                    }

                    val shouldCount = interactive && !onBreak && targetInForeground
                    val accumulatedMs = elapsedTracker.onTick(
                        sessionId = activeSession.id,
                        currentElapsedRealtimeMs = nowElapsed,
                        shouldCountTime = shouldCount
                    )
                    val elapsedSec = elapsedTracker.getElapsedSeconds()
                    val remainingSec = elapsedTracker.getRemainingSeconds(activeSession.intendedDuration)

                    if (nowElapsed - lastDbPersistElapsedMs >= 5_000L) {
                        lastDbPersistElapsedMs = nowElapsed
                        withContext(Dispatchers.IO) {
                            repository.updateSessionProgress(activeSession.id, accumulatedMs)
                        }
                    }

                    if (!onBreak && interventionTriggeredForSessionId != activeSession.id) {
                        val alerts = warningEvaluator.evaluate(
                            sessionId = activeSession.id,
                            intendedDurationMinutes = activeSession.intendedDuration,
                            elapsedSeconds = elapsedSec,
                            preferences = prefs.toWarningPreferences()
                        )
                        for (alertType in alerts) {
                            withContext(Dispatchers.IO) {
                                repository.recordAlertEvent(
                                    sessionId = activeSession.id,
                                    alertType = alertType,
                                    packageName = activeSession.packageName
                                )
                            }
                            if (alertType == AlertType.INTENDED_DURATION_REACHED) {
                                interventionTriggeredForSessionId = activeSession.id
                                withContext(Dispatchers.IO) {
                                    repository.updateSessionProgress(activeSession.id, accumulatedMs)
                                }
                                triggerIntervention(
                                    sessionId = activeSession.id,
                                    packageName = activeSession.packageName,
                                    appName = activeSession.appName,
                                    intendedMinutes = activeSession.intendedDuration,
                                    triggerType = InterventionTriggerType.INTENTION_COMPLETE,
                                    prefs = prefs,
                                    stateHolder = stateHolder
                                )
                            } else {
                                NotificationHelper.showWarningNotification(
                                    context = this@UsageMonitorService,
                                    appName = activeSession.appName,
                                    alertType = alertType,
                                    remainingSeconds = remainingSec
                                )
                            }
                        }
                    }

                    val state = MonitoringState(
                        isServiceRunning = true,
                        trackingMode = trackingMode,
                        activeSessionId = activeSession.id,
                        activePackageName = activeSession.packageName,
                        activeAppName = activeSession.appName,
                        currentForegroundPackage = foregroundPkg,
                        isTargetAppInForeground = targetInForeground,
                        isScreenInteractive = true,
                        intendedDurationMinutes = activeSession.intendedDuration,
                        elapsedSessionSeconds = elapsedSec,
                        remainingSessionSeconds = remainingSec,
                        isPausedForBreak = onBreak,
                        breakRemainingSeconds = breakRemainingSec,
                        permissionError = if (!hasUsageAccess) {
                            "Manual timer active (Usage Access not granted)."
                        } else null
                    )
                    stateHolder.update(state)

                    val statusLine = when {
                        onBreak -> "Mindful break: ${AnalyticsCalculator.formatSecondsAsClock(breakRemainingSec)} left"
                        remainingSec <= 0L -> "Planned duration complete — tap to review"
                        hasUsageAccess && !targetInForeground ->
                            "Paused (${activeSession.appName} away) • ${AnalyticsCalculator.formatSecondsAsClock(remainingSec)} left"
                        else ->
                            "${AnalyticsCalculator.formatSecondsAsClock(remainingSec)} remaining • ${trackingMode.label}"
                    }
                    updateForegroundNotification("Intent: ${activeSession.appName}", statusLine)
                    delay(1_000L)
                }
            }
        }
    }

    private suspend fun syncDailyUsageAndCheckLimits(
        repository: com.shadowalert.app.data.repository.ShadowAlertRepository,
        usageStatsMonitor: UsageStatsMonitor,
        installedAppsRepository: com.shadowalert.app.data.repository.InstalledAppsRepository,
        resetHour: Int,
        prefs: UserPreferences
    ) {
        val enabledApps = repository.getEnabledAppLimits()
        if (enabledApps.isEmpty()) return

        val activePackages = mutableSetOf<String>()
        for (appLimit in enabledApps) {
            if (!installedAppsRepository.isAppInstalled(appLimit.packageName)) {
                repository.handleUninstalledPackage(appLimit.packageName)
            } else {
                activePackages += appLimit.packageName
            }
        }
        if (activePackages.isEmpty()) return

        val todayEpochDay = repository.currentEffectiveEpochDay(resetHour)
        val todayUsageMap = usageStatsMonitor.queryTodayUsageMinutes(activePackages, resetHour)

        for (appLimit in enabledApps) {
            if (appLimit.packageName !in activePackages) continue
            val usageMinutes = todayUsageMap[appLimit.packageName] ?: 0
            repository.syncDailyUsageForPackage(appLimit.packageName, todayEpochDay, usageMinutes)

            val effectiveLimit = appLimit.effectiveDailyLimitMinutes
            if (usageMinutes >= effectiveLimit &&
                appLimit.dailyLimitNotifiedEpochDay != todayEpochDay
            ) {
                repository.markDailyLimitNotified(appLimit.packageName, todayEpochDay)
                repository.recordAlertEvent(
                    sessionId = 0L,
                    alertType = AlertType.DAILY_LIMIT_REACHED,
                    packageName = appLimit.packageName,
                    details = "Reached ${effectiveLimit}m daily limit"
                )
                if (prefs.interventionMode == InterventionMode.NOTIFICATION_ONLY) {
                    NotificationHelper.showWarningNotification(
                        context = this,
                        appName = appLimit.appName,
                        alertType = AlertType.DAILY_LIMIT_REACHED,
                        remainingSeconds = 0L
                    )
                } else {
                    NotificationHelper.showPauseInterventionNotification(
                        context = this,
                        sessionId = 0L,
                        packageName = appLimit.packageName,
                        appName = appLimit.appName,
                        intendedMinutes = effectiveLimit,
                        triggerType = InterventionTriggerType.DAILY_LIMIT_REACHED
                    )
                }
            }
        }

        val sevenDayMap = usageStatsMonitor.querySevenDayUsageMinutes(activePackages)
        val today = LocalDate.now().toEpochDay()
        for ((key, mins) in sevenDayMap) {
            val (pkg, epochDay) = key
            if (epochDay < today) {
                repository.syncDailyUsageForPackage(pkg, epochDay, mins)
            }
        }
    }

    private fun triggerIntervention(
        sessionId: Long,
        packageName: String,
        appName: String,
        intendedMinutes: Int,
        triggerType: InterventionTriggerType,
        prefs: UserPreferences,
        stateHolder: UsageMonitorStateHolder
    ) {
        if (prefs.interventionMode == InterventionMode.NOTIFICATION_ONLY) {
            NotificationHelper.showWarningNotification(
                context = this,
                appName = appName,
                alertType = if (triggerType == InterventionTriggerType.DAILY_LIMIT_REACHED) {
                    AlertType.DAILY_LIMIT_REACHED
                } else {
                    AlertType.INTENDED_DURATION_REACHED
                },
                remainingSeconds = 0L
            )
            return
        }

        stateHolder.emitPauseEvent(
            PauseTriggerEvent(
                sessionId = sessionId,
                packageName = packageName,
                appName = appName,
                intendedMinutes = intendedMinutes,
                triggerType = triggerType
            )
        )
        NotificationHelper.showPauseInterventionNotification(
            context = this,
            sessionId = sessionId,
            packageName = packageName,
            appName = appName,
            intendedMinutes = intendedMinutes,
            triggerType = triggerType
        )

        val canOverlay = prefs.interventionMode == InterventionMode.STRONG_SHIELD &&
            PermissionHelper.hasOverlayPermission(this)
        if (canOverlay) {
            try {
                val intent = PauseInterventionActivity.createIntent(
                    context = this,
                    sessionId = sessionId,
                    packageName = packageName,
                    appName = appName,
                    intendedMinutes = intendedMinutes,
                    triggerType = triggerType
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            } catch (_: SecurityException) {
                // High-priority notification already serves as compliant Android fallback
            }
        }
    }

    private fun updateForegroundNotification(title: String, content: String) {
        val notification = NotificationHelper.buildMonitoringNotification(
            context = this,
            title = title,
            content = content
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NotificationHelper.NOTIFICATION_ID_FOREGROUND, notification)
    }

    private fun stopMonitoringGracefully() {
        persistCurrentProgressAsync()
        monitorJob?.cancel()
        val app = application as ShadowAlertApplication
        app.container.monitorStateHolder.update(MonitoringState(isServiceRunning = false))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        persistCurrentProgressAsync()
        monitorJob?.cancel()
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: IllegalArgumentException) {
        }
        val app = application as ShadowAlertApplication
        app.container.monitorStateHolder.update(MonitoringState(isServiceRunning = false))
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_MONITORING = "com.shadowalert.app.action.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.shadowalert.app.action.STOP_MONITORING"
        const val ACTION_START_SESSION = "com.shadowalert.app.action.START_SESSION"
        const val ACTION_EXTEND_SESSION = "com.shadowalert.app.action.EXTEND_SESSION"
        const val ACTION_PAUSE_FOR_BREAK = "com.shadowalert.app.action.PAUSE_FOR_BREAK"
        const val ACTION_RESUME_FROM_BREAK = "com.shadowalert.app.action.RESUME_FROM_BREAK"
        const val ACTION_END_SESSION_FROM_NOTIFICATION = "com.shadowalert.app.action.END_SESSION_NOTIFICATION"

        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_MANUAL_MODE = "extra_manual_mode"
        const val EXTRA_BREAK_MINUTES = "extra_break_minutes"

        fun startMonitoring(context: Context) {
            val intent = Intent(context, UsageMonitorService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startSessionMonitoring(context: Context, sessionId: Long, manualMode: Boolean) {
            val intent = Intent(context, UsageMonitorService::class.java).apply {
                action = ACTION_START_SESSION
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_MANUAL_MODE, manualMode)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun notifySessionExtended(context: Context) {
            val intent = Intent(context, UsageMonitorService::class.java).apply {
                action = ACTION_EXTEND_SESSION
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startBreak(context: Context, breakMinutes: Int = 5) {
            val intent = Intent(context, UsageMonitorService::class.java).apply {
                action = ACTION_PAUSE_FOR_BREAK
                putExtra(EXTRA_BREAK_MINUTES, breakMinutes)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun resumeFromBreak(context: Context) {
            val intent = Intent(context, UsageMonitorService::class.java).apply {
                action = ACTION_RESUME_FROM_BREAK
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopMonitoring(context: Context) {
            val intent = Intent(context, UsageMonitorService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
