package com.shadowalert.app.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shadowalert.app.ShadowAlertApplication
import com.shadowalert.app.data.local.entity.AlertEventEntity
import com.shadowalert.app.data.local.entity.AppLimitEntity
import com.shadowalert.app.data.local.entity.DailyAppUsageEntity
import com.shadowalert.app.data.local.entity.IntentSessionEntity
import com.shadowalert.app.data.preferences.UserPreferences
import com.shadowalert.app.data.repository.StartSessionResult
import com.shadowalert.app.domain.logic.DurationValidationResult
import com.shadowalert.app.domain.logic.ExportSummaryFormatter
import com.shadowalert.app.domain.model.AlertUserResponse
import com.shadowalert.app.domain.model.AnalyticsSummary
import com.shadowalert.app.domain.model.AppCategory
import com.shadowalert.app.domain.model.InstalledAppInfo
import com.shadowalert.app.domain.model.InterventionMode
import com.shadowalert.app.domain.model.MonitoringState
import com.shadowalert.app.domain.model.PermissionStatus
import com.shadowalert.app.domain.model.SessionCompletionStatus
import com.shadowalert.app.domain.model.ThemeMode
import com.shadowalert.app.system.NotificationHelper
import com.shadowalert.app.system.PauseTriggerEvent
import com.shadowalert.app.system.PermissionHelper
import com.shadowalert.app.system.UsageMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShadowAlertViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as ShadowAlertApplication).container
    private val repository = container.repository
    private val preferencesDataStore = container.preferencesDataStore
    private val installedAppsRepository = container.installedAppsRepository
    private val usageStatsMonitor = container.usageStatsMonitor

    val preferences: StateFlow<UserPreferences> = repository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val allAppLimits: StateFlow<List<AppLimitEntity>> = repository.allAppLimitsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val enabledAppLimits: StateFlow<List<AppLimitEntity>> = repository.enabledAppLimitsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeSession: StateFlow<IntentSessionEntity?> = repository.activeSessionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val allSessions: StateFlow<List<IntentSessionEntity>> = repository.allSessionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentAlerts: StateFlow<List<AlertEventEntity>> = repository.alertEventsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val analyticsSummary: StateFlow<AnalyticsSummary> = repository.observeAnalyticsSummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsSummary())

    val monitoringState: StateFlow<MonitoringState> = container.monitorStateHolder.monitoringState

    val pauseTriggerEvents: SharedFlow<PauseTriggerEvent> = container.monitorStateHolder.pauseEvents

    private val _permissionStatus = MutableStateFlow(PermissionHelper.getPermissionStatus(application))
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiMessages: SharedFlow<String> = _uiMessages.asSharedFlow()

    private val _duplicateSessionPrompt = MutableStateFlow<IntentSessionEntity?>(null)
    val duplicateSessionPrompt: StateFlow<IntentSessionEntity?> = _duplicateSessionPrompt.asStateFlow()

    init {
        refreshPermissionsAndSyncUsage()
        loadInstalledApps()
    }

    fun refreshPermissionsAndSyncUsage() {
        val ctx = getApplication<Application>()
        val status = PermissionHelper.getPermissionStatus(ctx)
        _permissionStatus.value = status

        viewModelScope.launch(Dispatchers.IO) {
            repository.checkAndApplyDailyResetIfNeeded()
            val prefs = preferencesDataStore.getSnapshot()
            val active = repository.getActiveSession()

            if (active != null && !monitoringState.value.isServiceRunning) {
                UsageMonitorService.startSessionMonitoring(
                    context = ctx,
                    sessionId = active.id,
                    manualMode = !status.usageAccessGranted
                )
            } else if (prefs.monitoringEnabled && status.usageAccessGranted && !monitoringState.value.isServiceRunning) {
                UsageMonitorService.startMonitoring(ctx)
            }

            if (status.usageAccessGranted) {
                val enabledApps = repository.getEnabledAppLimits()
                if (enabledApps.isNotEmpty()) {
                    val packages = enabledApps.map { it.packageName }.toSet()
                    val todayEpoch = repository.currentEffectiveEpochDay(prefs.dailyResetHour)
                    val todayUsage = usageStatsMonitor.queryTodayUsageMinutes(packages, prefs.dailyResetHour)
                    todayUsage.forEach { (pkg, mins) ->
                        repository.syncDailyUsageForPackage(pkg, todayEpoch, mins)
                    }
                }
            }
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            try {
                _installedApps.value = installedAppsRepository.getLaunchableApps()
            } finally {
                _isLoadingApps.value = false
            }
        }
    }

    fun observeAppLimit(packageName: String): Flow<AppLimitEntity?> =
        repository.observeAppLimit(packageName)

    fun observeSevenDayUsageForPackage(packageName: String): Flow<List<DailyAppUsageEntity>> =
        repository.observeSevenDayUsageForPackage(packageName)

    fun observeSessionsForPackage(packageName: String): Flow<List<IntentSessionEntity>> =
        repository.observeSessionsForPackage(packageName)

    fun completeOnboarding() {
        viewModelScope.launch {
            preferencesDataStore.setOnboardingCompleted(true)
        }
    }

    fun toggleAppMonitoring(app: InstalledAppInfo, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleOrAddAppMonitoring(app, enabled)
        }
    }

    fun updateAppDailyLimit(app: InstalledAppInfo, limitMinutes: Int) {
        viewModelScope.launch {
            when (val result = repository.updateAppDailyLimit(app, limitMinutes)) {
                is DurationValidationResult.Invalid -> _uiMessages.tryEmit(result.message)
                is DurationValidationResult.Valid -> _uiMessages.tryEmit("Daily limit updated to ${result.durationMinutes}m.")
            }
        }
    }

    fun updateAppCategory(packageName: String, category: AppCategory) {
        viewModelScope.launch {
            repository.updateAppCategory(packageName, category)
        }
    }

    fun startIntentionSession(
        packageName: String,
        appName: String,
        category: AppCategory,
        intendedMinutes: Int,
        purpose: String?,
        launchTargetApp: Boolean = false,
        onStarted: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            when (
                val result = repository.startIntentionSession(
                    packageName = packageName,
                    appName = appName,
                    category = category,
                    intendedDurationMinutes = intendedMinutes,
                    optionalPurpose = purpose
                )
            ) {
                is StartSessionResult.ValidationError -> {
                    _uiMessages.tryEmit(result.message)
                }
                is StartSessionResult.DuplicateActiveSession -> {
                    _duplicateSessionPrompt.value = result.existingSession
                }
                is StartSessionResult.Created -> {
                    val ctx = getApplication<Application>()
                    val hasUsageAccess = PermissionHelper.hasUsageAccessPermission(ctx)
                    UsageMonitorService.startSessionMonitoring(
                        context = ctx,
                        sessionId = result.session.id,
                        manualMode = !hasUsageAccess
                    )
                    if (launchTargetApp) {
                        val launchIntent = installedAppsRepository.getLaunchIntent(packageName)
                        if (launchIntent != null) {
                            ctx.startActivity(launchIntent)
                        } else {
                            _uiMessages.tryEmit("Session started, though $appName could not be launched directly.")
                        }
                    }
                    onStarted(result.session.id)
                }
            }
        }
    }

    fun dismissDuplicateSessionPrompt() {
        _duplicateSessionPrompt.value = null
    }

    fun endExistingActiveSessionAndDismissPrompt() {
        val existing = _duplicateSessionPrompt.value ?: return
        _duplicateSessionPrompt.value = null
        endSession(existing.id, early = true)
    }

    fun openTargetApp(packageName: String) {
        val ctx = getApplication<Application>()
        val intent = installedAppsRepository.getLaunchIntent(packageName)
        if (intent != null) {
            ctx.startActivity(intent)
        } else {
            _uiMessages.tryEmit("Application appears to be uninstalled.")
        }
    }

    fun returnToDeviceHome() {
        val ctx = getApplication<Application>()
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        ctx.startActivity(homeIntent)
    }

    fun endSession(
        sessionId: Long,
        early: Boolean = false,
        reflectionText: String? = null
    ) {
        viewModelScope.launch {
            val status = if (early) {
                SessionCompletionStatus.COMPLETED_EARLY
            } else {
                SessionCompletionStatus.COMPLETED_WITHIN_INTENT
            }
            val elapsedMs = monitoringState.value.takeIf { it.activeSessionId == sessionId }
                ?.let { it.elapsedSessionSeconds * 1000L }
            val manualMode = !PermissionHelper.hasUsageAccessPermission(getApplication())
            repository.finishSession(
                sessionId = sessionId,
                status = status,
                userResponse = AlertUserResponse.ENDED_SESSION,
                accumulatedActiveMsOverride = elapsedMs,
                reflectionText = reflectionText,
                isManualFallbackMode = manualMode
            )
            NotificationHelper.cancelPauseNotifications(getApplication())
            _uiMessages.tryEmit("Session completed. Well done staying intentional.")
        }
    }

    fun takeBreakFromSession(
        sessionId: Long,
        breakMinutes: Int = 5,
        reflectionText: String? = null
    ) {
        viewModelScope.launch {
            if (sessionId > 0L) {
                val elapsedMs = monitoringState.value.takeIf { it.activeSessionId == sessionId }
                    ?.let { it.elapsedSessionSeconds * 1000L }
                val manualMode = !PermissionHelper.hasUsageAccessPermission(getApplication())
                repository.finishSession(
                    sessionId = sessionId,
                    status = SessionCompletionStatus.TOOK_BREAK,
                    userResponse = AlertUserResponse.TOOK_BREAK,
                    accumulatedActiveMsOverride = elapsedMs,
                    reflectionText = reflectionText,
                    isManualFallbackMode = manualMode
                )
            } else {
                repository.saveReflectionIfPresent(0L, reflectionText)
            }
            UsageMonitorService.startBreak(getApplication(), breakMinutes)
            _uiMessages.tryEmit("Taking a ${breakMinutes}-minute mindful break.")
        }
    }

    fun addMoreTimeToSession(
        sessionId: Long,
        additionalMinutes: Int,
        reflectionText: String? = null
    ) {
        viewModelScope.launch {
            repository.extendActiveSession(sessionId, additionalMinutes, reflectionText)
            UsageMonitorService.notifySessionExtended(getApplication())
            _uiMessages.tryEmit("Added ${additionalMinutes}m to your active session.")
        }
    }

    fun continueWithOverride(
        sessionId: Long,
        overrideReason: String? = null,
        reflectionText: String? = null
    ) {
        viewModelScope.launch {
            val elapsedMs = monitoringState.value.takeIf { it.activeSessionId == sessionId }
                ?.let { it.elapsedSessionSeconds * 1000L }
            val manualMode = !PermissionHelper.hasUsageAccessPermission(getApplication())
            repository.finishSession(
                sessionId = sessionId,
                status = SessionCompletionStatus.OVERRIDDEN,
                userResponse = AlertUserResponse.OVERRIDDEN,
                accumulatedActiveMsOverride = elapsedMs,
                reflectionText = reflectionText,
                isManualFallbackMode = manualMode,
                overrideDetails = overrideReason?.trim()?.takeIf { it.isNotEmpty() }
            )
            NotificationHelper.cancelPauseNotifications(getApplication())
            _uiMessages.tryEmit("Intentional override logged privately on your device.")
        }
    }

    fun grantDailyExtraMinutes(
        packageName: String,
        additionalMinutes: Int,
        reflectionText: String? = null
    ) {
        viewModelScope.launch {
            repository.grantDailyExtraMinutes(packageName, additionalMinutes)
            repository.saveReflectionIfPresent(0L, reflectionText)
            NotificationHelper.cancelPauseNotifications(getApplication())
            _uiMessages.tryEmit("Granted +${additionalMinutes}m temporary daily extension.")
        }
    }

    fun grantDailyOverride(
        packageName: String,
        appName: String,
        reason: String?,
        reflectionText: String? = null
    ) {
        viewModelScope.launch {
            repository.grantDailyOverride(
                packageName = packageName,
                appName = appName,
                graceMinutes = 15,
                reason = reason
            )
            repository.saveReflectionIfPresent(0L, reflectionText)
            NotificationHelper.cancelPauseNotifications(getApplication())
            _uiMessages.tryEmit("15-minute deliberate override granted for $appName.")
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesDataStore.setThemeMode(mode) }
    }

    fun setUserName(name: String) {
        viewModelScope.launch { preferencesDataStore.setUserName(name) }
    }

    fun setDailyTargetMinutes(minutes: Int) {
        viewModelScope.launch { preferencesDataStore.setDailyTargetMinutes(minutes) }
    }

    fun updateWarningSettings(
        warning5Min: Boolean,
        warning2Min: Boolean,
        limitReached: Boolean,
        customMinutes: Int
    ) {
        viewModelScope.launch {
            preferencesDataStore.setWarningPreferences(
                warning5Min = warning5Min,
                warning2Min = warning2Min,
                limitReached = limitReached,
                customMinutes = customMinutes
            )
        }
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.setMonitoringEnabled(enabled)
            val ctx = getApplication<Application>()
            if (enabled) {
                UsageMonitorService.startMonitoring(ctx)
            } else if (activeSession.value == null) {
                UsageMonitorService.stopMonitoring(ctx)
            }
        }
    }

    fun setPollingIntervalSeconds(seconds: Int) {
        viewModelScope.launch { preferencesDataStore.setPollingIntervalSeconds(seconds) }
    }

    fun setDailyResetHour(hour: Int) {
        viewModelScope.launch {
            preferencesDataStore.setDailyResetHour(hour)
            refreshPermissionsAndSyncUsage()
        }
    }

    fun setInterventionMode(mode: InterventionMode) {
        viewModelScope.launch { preferencesDataStore.setInterventionMode(mode) }
    }

    fun exportUsageSummary() {
        val summary = analyticsSummary.value
        val sessions = allSessions.value
        viewModelScope.launch(Dispatchers.IO) {
            val allSessionsList = repository.getAllSessionsSnapshot()
            val reflections = repository.getReflectionsSnapshot()
            val markdown = ExportSummaryFormatter.formatMarkdownSummary(
                summary = summary,
                sessions = if (allSessionsList.isNotEmpty()) allSessionsList else sessions,
                reflections = reflections
            )
            withContext(Dispatchers.Main) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Shadow Alert — Local Screen-Time Summary")
                    putExtra(Intent.EXTRA_TEXT, markdown)
                }
                val chooser = Intent.createChooser(shareIntent, "Export Shadow Alert Summary").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                getApplication<Application>().startActivity(chooser)
            }
        }
    }

    fun deleteAllLocalData() {
        viewModelScope.launch {
            UsageMonitorService.stopMonitoring(getApplication())
            repository.deleteAllLocalData()
            _uiMessages.tryEmit("All local usage data, sessions, and limits have been deleted.")
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            return ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        }
    }
}
