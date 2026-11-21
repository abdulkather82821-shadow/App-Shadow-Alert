package com.shadowalert.app.data.repository

import com.shadowalert.app.data.local.ShadowAlertDatabase
import com.shadowalert.app.data.local.entity.AlertEventEntity
import com.shadowalert.app.data.local.entity.AppLimitEntity
import com.shadowalert.app.data.local.entity.DailyAppUsageEntity
import com.shadowalert.app.data.local.entity.IntentSessionEntity
import com.shadowalert.app.data.local.entity.ReflectionEntity
import com.shadowalert.app.data.preferences.UserPreferences
import com.shadowalert.app.data.preferences.UserPreferencesDataStore
import com.shadowalert.app.domain.logic.AnalyticsCalculator
import com.shadowalert.app.domain.logic.DurationValidationResult
import com.shadowalert.app.domain.logic.SessionDurationValidator
import com.shadowalert.app.domain.model.AlertType
import com.shadowalert.app.domain.model.AlertUserResponse
import com.shadowalert.app.domain.model.AnalyticsSummary
import com.shadowalert.app.domain.model.AppCategory
import com.shadowalert.app.domain.model.InstalledAppInfo
import com.shadowalert.app.domain.model.SessionCompletionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

sealed class StartSessionResult {
    data class Created(val session: IntentSessionEntity) : StartSessionResult()
    data class DuplicateActiveSession(val existingSession: IntentSessionEntity) : StartSessionResult()
    data class ValidationError(val message: String) : StartSessionResult()
}

class ShadowAlertRepository(
    private val database: ShadowAlertDatabase,
    private val preferences: UserPreferencesDataStore
) {
    private val appLimitDao = database.appLimitDao()
    private val sessionDao = database.intentSessionDao()
    private val alertEventDao = database.alertEventDao()
    private val reflectionDao = database.reflectionDao()
    private val dailyUsageDao = database.dailyAppUsageDao()

    val preferencesFlow: Flow<UserPreferences> = preferences.preferencesFlow
    val allAppLimitsFlow: Flow<List<AppLimitEntity>> = appLimitDao.observeAll()
    val enabledAppLimitsFlow: Flow<List<AppLimitEntity>> = appLimitDao.observeEnabled()
    val allSessionsFlow: Flow<List<IntentSessionEntity>> = sessionDao.observeAll()
    val activeSessionFlow: Flow<IntentSessionEntity?> = sessionDao.observeActiveSession()
    val alertEventsFlow: Flow<List<AlertEventEntity>> = alertEventDao.observeAll()
    val reflectionsFlow: Flow<List<ReflectionEntity>> = reflectionDao.observeAll()

    fun observeAppLimit(packageName: String): Flow<AppLimitEntity?> =
        appLimitDao.observeByPackageName(packageName)

    fun observeSevenDayUsageForPackage(packageName: String): Flow<List<DailyAppUsageEntity>> {
        val fromDay = LocalDate.now().toEpochDay() - 6
        return dailyUsageDao.observeForPackageSince(packageName, fromDay)
    }

    fun observeSessionsForPackage(packageName: String): Flow<List<IntentSessionEntity>> =
        sessionDao.observeForPackage(packageName)

    fun observeAnalyticsSummary(): Flow<AnalyticsSummary> {
        val fromDay = LocalDate.now().toEpochDay() - 6
        return combine(
            appLimitDao.observeAll(),
            sessionDao.observeAll(),
            alertEventDao.observeAll(),
            dailyUsageDao.observeSince(fromDay),
            preferences.preferencesFlow
        ) { limits, sessions, alerts, dailyUsages, prefs ->
            AnalyticsCalculator.computeSummary(
                appLimits = limits,
                sessions = sessions,
                alertEvents = alerts,
                dailyUsages = dailyUsages,
                dailyTargetMinutes = prefs.totalDailyTargetMinutes
            )
        }
    }

    suspend fun toggleOrAddAppMonitoring(
        appInfo: InstalledAppInfo,
        enable: Boolean,
        defaultDailyLimitMinutes: Int = 60
    ) {
        val existing = appLimitDao.getByPackageName(appInfo.packageName)
        if (existing == null) {
            if (enable) {
                appLimitDao.upsert(
                    AppLimitEntity(
                        packageName = appInfo.packageName,
                        appName = appInfo.appName,
                        category = appInfo.suggestedCategory,
                        dailyLimitMinutes = defaultDailyLimitMinutes,
                        currentUsageMinutes = 0,
                        enabled = true
                    )
                )
            }
        } else {
            appLimitDao.setEnabled(appInfo.packageName, enable)
        }
    }

    suspend fun updateAppDailyLimit(
        appInfo: InstalledAppInfo,
        limitMinutes: Int
    ): DurationValidationResult {
        val validation = SessionDurationValidator.validateDailyLimit(limitMinutes)
        if (validation is DurationValidationResult.Invalid) return validation

        val existing = appLimitDao.getByPackageName(appInfo.packageName)
        if (existing == null) {
            appLimitDao.upsert(
                AppLimitEntity(
                    packageName = appInfo.packageName,
                    appName = appInfo.appName,
                    category = appInfo.suggestedCategory,
                    dailyLimitMinutes = limitMinutes,
                    currentUsageMinutes = 0,
                    enabled = true
                )
            )
        } else {
            appLimitDao.updateDailyLimit(appInfo.packageName, limitMinutes)
        }
        return validation
    }

    suspend fun updateAppDailyLimit(packageName: String, limitMinutes: Int): DurationValidationResult {
        val validation = SessionDurationValidator.validateDailyLimit(limitMinutes)
        if (validation is DurationValidationResult.Invalid) return validation
        appLimitDao.updateDailyLimit(packageName, limitMinutes)
        return validation
    }

    suspend fun updateAppCategory(packageName: String, category: AppCategory) {
        appLimitDao.updateCategory(packageName, category)
    }

    suspend fun grantDailyExtraMinutes(packageName: String, extraMinutes: Int) {
        if (extraMinutes <= 0) return
        appLimitDao.addDailyExtraMinutes(packageName, extraMinutes)
    }

    suspend fun grantDailyOverride(
        packageName: String,
        appName: String,
        graceMinutes: Int = 15,
        reason: String? = null
    ) {
        val untilMs = System.currentTimeMillis() + graceMinutes * 60_000L
        appLimitDao.setOverrideUntil(packageName, untilMs)
        recordAlertEvent(
            sessionId = 0L,
            alertType = AlertType.DAILY_LIMIT_REACHED,
            userResponse = AlertUserResponse.OVERRIDDEN,
            packageName = packageName,
            details = reason?.trim()?.takeIf { it.isNotEmpty() } ?: "Daily limit override for $appName (+${graceMinutes}m grace)"
        )
    }

    suspend fun startIntentionSession(
        packageName: String,
        appName: String,
        category: AppCategory,
        intendedDurationMinutes: Int,
        optionalPurpose: String?
    ): StartSessionResult {
        val validation = SessionDurationValidator.validate(intendedDurationMinutes)
        if (validation is DurationValidationResult.Invalid) {
            return StartSessionResult.ValidationError(validation.message)
        }

        val existingActive = sessionDao.getActiveSession()
        if (existingActive != null) {
            return StartSessionResult.DuplicateActiveSession(existingActive)
        }

        val existingApp = appLimitDao.getByPackageName(packageName)
        val appId = if (existingApp != null) {
            existingApp.id
        } else {
            appLimitDao.upsert(
                AppLimitEntity(
                    packageName = packageName,
                    appName = appName,
                    category = category,
                    dailyLimitMinutes = 60,
                    currentUsageMinutes = 0,
                    enabled = true
                )
            )
        }

        val session = IntentSessionEntity(
            appId = appId,
            packageName = packageName,
            appName = appName,
            intendedDuration = intendedDurationMinutes,
            startTime = System.currentTimeMillis(),
            endTime = null,
            actualDuration = 0,
            accumulatedActiveMs = 0L,
            completionStatus = SessionCompletionStatus.ACTIVE,
            optionalPurpose = optionalPurpose?.trim()?.takeIf { it.isNotEmpty() }
        )
        val id = sessionDao.insert(session)
        preferences.setActiveSessionId(id)
        return StartSessionResult.Created(session.copy(id = id))
    }

    suspend fun getActiveSession(): IntentSessionEntity? = sessionDao.getActiveSession()

    suspend fun getSessionById(sessionId: Long): IntentSessionEntity? = sessionDao.getById(sessionId)

    suspend fun getTriggeredAlertTypesForSession(sessionId: Long): Set<AlertType> {
        return alertEventDao.getForSession(sessionId).map { it.alertType }.toSet()
    }

    suspend fun updateSessionProgress(sessionId: Long, accumulatedActiveMs: Long) {
        val actualMinutes = ((accumulatedActiveMs + 30_000L) / 60_000L).toInt()
        sessionDao.updateProgress(sessionId, actualMinutes, accumulatedActiveMs)
    }

    suspend fun extendActiveSession(
        sessionId: Long,
        additionalMinutes: Int,
        reflectionText: String? = null
    ) {
        val session = sessionDao.getById(sessionId) ?: return
        val newIntended = (session.intendedDuration + additionalMinutes)
            .coerceAtMost(SessionDurationValidator.MAX_DURATION_MINUTES)
        sessionDao.extendSession(sessionId, newIntended, SessionCompletionStatus.ACTIVE)
        alertEventDao.updateLatestUnansweredForSession(
            sessionId = sessionId,
            response = AlertUserResponse.ADDED_TIME,
            details = "Extended by +${additionalMinutes}m"
        )
        saveReflectionIfPresent(sessionId, reflectionText)
    }

    suspend fun finishSession(
        sessionId: Long,
        status: SessionCompletionStatus,
        userResponse: AlertUserResponse,
        accumulatedActiveMsOverride: Long? = null,
        reflectionText: String? = null,
        isManualFallbackMode: Boolean = false,
        overrideDetails: String? = null
    ) {
        val session = sessionDao.getById(sessionId) ?: return
        val finalMs = (accumulatedActiveMsOverride ?: session.accumulatedActiveMs).coerceAtLeast(0L)
        val finalMinutes = ((finalMs + 30_000L) / 60_000L).toInt().coerceAtLeast(
            if (status == SessionCompletionStatus.COMPLETED_WITHIN_INTENT && finalMs >= (session.intendedDuration * 60_000L - 30_000L)) {
                session.intendedDuration
            } else {
                0
            }
        )
        sessionDao.finishSession(
            id = sessionId,
            endTime = System.currentTimeMillis(),
            actualMinutes = finalMinutes,
            accumulatedMs = finalMs,
            status = status
        )

        val existingAlerts = alertEventDao.getForSession(sessionId)
        val hasUnanswered = existingAlerts.any { it.userResponse == AlertUserResponse.NONE }
        if (hasUnanswered) {
            alertEventDao.updateLatestUnansweredForSession(
                sessionId = sessionId,
                response = userResponse,
                details = overrideDetails
            )
        } else if (userResponse == AlertUserResponse.OVERRIDDEN) {
            recordAlertEvent(
                sessionId = sessionId,
                alertType = AlertType.INTENDED_DURATION_REACHED,
                userResponse = AlertUserResponse.OVERRIDDEN,
                packageName = session.packageName,
                details = overrideDetails ?: "Intentional override"
            )
        }

        saveReflectionIfPresent(sessionId, reflectionText)
        preferences.setActiveSessionId(-1L)

        // When Usage Access is disabled (Manual Session Timer), accumulate manual session duration into today's usage
        if (isManualFallbackMode && finalMinutes > 0) {
            val prefs = preferences.getSnapshot()
            val todayEpoch = currentEffectiveEpochDay(prefs.dailyResetHour)
            val app = appLimitDao.getByPackageName(session.packageName)
            if (app != null) {
                val updatedUsage = app.currentUsageMinutes + finalMinutes
                syncDailyUsageForPackage(session.packageName, todayEpoch, updatedUsage)
            }
        }
    }

    suspend fun recordAlertEvent(
        sessionId: Long,
        alertType: AlertType,
        userResponse: AlertUserResponse = AlertUserResponse.NONE,
        packageName: String? = null,
        details: String? = null
    ): Long {
        return alertEventDao.insert(
            AlertEventEntity(
                sessionId = sessionId,
                alertType = alertType,
                timestamp = System.currentTimeMillis(),
                userResponse = userResponse,
                packageName = packageName,
                details = details
            )
        )
    }

    suspend fun saveReflectionIfPresent(sessionId: Long, response: String?) {
        val trimmed = response?.trim()
        if (!trimmed.isNullOrEmpty()) {
            reflectionDao.insert(
                ReflectionEntity(
                    sessionId = sessionId,
                    response = trimmed,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun syncDailyUsageForPackage(
        packageName: String,
        epochDay: Long,
        usageMinutes: Int
    ) {
        appLimitDao.updateUsageMinutes(packageName, usageMinutes)
        val existing = dailyUsageDao.getByPackageAndDay(packageName, epochDay)
        if (existing == null) {
            dailyUsageDao.upsert(
                DailyAppUsageEntity(
                    packageName = packageName,
                    epochDay = epochDay,
                    usageMinutes = usageMinutes
                )
            )
        } else if (usageMinutes != existing.usageMinutes) {
            dailyUsageDao.upsert(existing.copy(usageMinutes = usageMinutes))
        }
    }

    suspend fun checkAndApplyDailyResetIfNeeded() {
        val prefs = preferences.getSnapshot()
        val effectiveEpochDay = currentEffectiveEpochDay(prefs.dailyResetHour)

        if (prefs.lastDailyResetEpochDay != -1L && prefs.lastDailyResetEpochDay < effectiveEpochDay) {
            appLimitDao.resetAllDailyUsage()
        }
        if (prefs.lastDailyResetEpochDay != effectiveEpochDay) {
            preferences.setLastDailyResetEpochDay(effectiveEpochDay)
        }
    }

    suspend fun handleUninstalledPackage(packageName: String) {
        val active = sessionDao.getActiveSession()
        if (active != null && active.packageName == packageName) {
            finishSession(
                sessionId = active.id,
                status = SessionCompletionStatus.INTERRUPTED,
                userResponse = AlertUserResponse.DISMISSED
            )
        }
        appLimitDao.deleteByPackageName(packageName)
    }

    suspend fun getEnabledAppLimits(): List<AppLimitEntity> = appLimitDao.getEnabledList()

    suspend fun getAppLimitByPackage(packageName: String): AppLimitEntity? =
        appLimitDao.getByPackageName(packageName)

    suspend fun markDailyLimitNotified(packageName: String, epochDay: Long) {
        appLimitDao.markDailyLimitNotified(packageName, epochDay)
    }

    suspend fun getAllSessionsSnapshot(): List<IntentSessionEntity> = sessionDao.getAllList()

    suspend fun getReflectionsSnapshot(): List<ReflectionEntity> = reflectionDao.getAllList()

    suspend fun deleteAllLocalData() {
        sessionDao.deleteAll()
        alertEventDao.deleteAll()
        reflectionDao.deleteAll()
        dailyUsageDao.deleteAll()
        appLimitDao.deleteAll()
        preferences.clearAll()
    }

    fun currentEffectiveEpochDay(resetHour: Int, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val now = LocalDate.now(zoneId)
        val time = LocalTime.now(zoneId)
        return if (time.hour < resetHour) now.minusDays(1).toEpochDay() else now.toEpochDay()
    }
}
