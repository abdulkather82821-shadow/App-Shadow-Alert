package com.shadowalert.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shadowalert.app.domain.logic.WarningPreferences
import com.shadowalert.app.domain.model.InterventionMode
import com.shadowalert.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.shadowAlertDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "shadow_alert_preferences"
)

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val userName: String = "",
    val totalDailyTargetMinutes: Int = 240,
    val warning5MinEnabled: Boolean = true,
    val warning2MinEnabled: Boolean = true,
    val limitReachedAlertEnabled: Boolean = true,
    val customWarningMinutes: Int = 0,
    val monitoringEnabled: Boolean = true,
    val pollingIntervalSeconds: Int = 3,
    val dailyResetHour: Int = 0,
    val lastDailyResetEpochDay: Long = -1L,
    val overlayInterventionEnabled: Boolean = true,
    val interventionMode: InterventionMode = InterventionMode.REMINDER_AND_PAUSE,
    val activeSessionId: Long = -1L
) {
    fun toWarningPreferences(): WarningPreferences = WarningPreferences(
        warning5MinEnabled = warning5MinEnabled,
        warning2MinEnabled = warning2MinEnabled,
        limitReachedAlertEnabled = limitReachedAlertEnabled,
        customWarningMinutes = customWarningMinutes
    )
}

class UserPreferencesDataStore(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USER_NAME = stringPreferencesKey("user_name")
        val DAILY_TARGET_MINUTES = intPreferencesKey("daily_target_minutes")
        val WARNING_5_MIN = booleanPreferencesKey("warning_5_min")
        val WARNING_2_MIN = booleanPreferencesKey("warning_2_min")
        val LIMIT_REACHED_ALERT = booleanPreferencesKey("limit_reached_alert")
        val CUSTOM_WARNING_MIN = intPreferencesKey("custom_warning_min")
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val POLLING_INTERVAL_SEC = intPreferencesKey("polling_interval_sec")
        val DAILY_RESET_HOUR = intPreferencesKey("daily_reset_hour")
        val LAST_RESET_EPOCH_DAY = longPreferencesKey("last_reset_epoch_day")
        val OVERLAY_INTERVENTION = booleanPreferencesKey("overlay_intervention")
        val INTERVENTION_MODE = stringPreferencesKey("intervention_mode")
        val ACTIVE_SESSION_ID = longPreferencesKey("active_session_id")
    }

    val preferencesFlow: Flow<UserPreferences> = context.shadowAlertDataStore.data.map { prefs ->
        UserPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            themeMode = ThemeMode.entries.firstOrNull {
                it.name == prefs[Keys.THEME_MODE]
            } ?: ThemeMode.SYSTEM,
            userName = prefs[Keys.USER_NAME] ?: "",
            totalDailyTargetMinutes = (prefs[Keys.DAILY_TARGET_MINUTES] ?: 240).coerceIn(15, 1440),
            warning5MinEnabled = prefs[Keys.WARNING_5_MIN] ?: true,
            warning2MinEnabled = prefs[Keys.WARNING_2_MIN] ?: true,
            limitReachedAlertEnabled = prefs[Keys.LIMIT_REACHED_ALERT] ?: true,
            customWarningMinutes = (prefs[Keys.CUSTOM_WARNING_MIN] ?: 0).coerceIn(0, 60),
            monitoringEnabled = prefs[Keys.MONITORING_ENABLED] ?: true,
            pollingIntervalSeconds = (prefs[Keys.POLLING_INTERVAL_SEC] ?: 3).coerceIn(2, 15),
            dailyResetHour = (prefs[Keys.DAILY_RESET_HOUR] ?: 0).coerceIn(0, 23),
            lastDailyResetEpochDay = prefs[Keys.LAST_RESET_EPOCH_DAY] ?: -1L,
            overlayInterventionEnabled = prefs[Keys.OVERLAY_INTERVENTION] ?: true,
            interventionMode = InterventionMode.entries.firstOrNull {
                it.name == prefs[Keys.INTERVENTION_MODE]
            } ?: InterventionMode.REMINDER_AND_PAUSE,
            activeSessionId = prefs[Keys.ACTIVE_SESSION_ID] ?: -1L
        )
    }

    suspend fun getSnapshot(): UserPreferences = preferencesFlow.first()

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.shadowAlertDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.shadowAlertDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setUserName(name: String) {
        context.shadowAlertDataStore.edit { it[Keys.USER_NAME] = name.trim() }
    }

    suspend fun setDailyTargetMinutes(minutes: Int) {
        context.shadowAlertDataStore.edit {
            it[Keys.DAILY_TARGET_MINUTES] = minutes.coerceIn(15, 1440)
        }
    }

    suspend fun setWarningPreferences(
        warning5Min: Boolean,
        warning2Min: Boolean,
        limitReached: Boolean,
        customMinutes: Int
    ) {
        context.shadowAlertDataStore.edit {
            it[Keys.WARNING_5_MIN] = warning5Min
            it[Keys.WARNING_2_MIN] = warning2Min
            it[Keys.LIMIT_REACHED_ALERT] = limitReached
            it[Keys.CUSTOM_WARNING_MIN] = customMinutes.coerceIn(0, 60)
        }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.shadowAlertDataStore.edit { it[Keys.MONITORING_ENABLED] = enabled }
    }

    suspend fun setPollingIntervalSeconds(seconds: Int) {
        context.shadowAlertDataStore.edit { it[Keys.POLLING_INTERVAL_SEC] = seconds.coerceIn(2, 15) }
    }

    suspend fun setDailyResetHour(hour: Int) {
        context.shadowAlertDataStore.edit { it[Keys.DAILY_RESET_HOUR] = hour.coerceIn(0, 23) }
    }

    suspend fun setLastDailyResetEpochDay(epochDay: Long) {
        context.shadowAlertDataStore.edit { it[Keys.LAST_RESET_EPOCH_DAY] = epochDay }
    }

    suspend fun setOverlayInterventionEnabled(enabled: Boolean) {
        context.shadowAlertDataStore.edit { it[Keys.OVERLAY_INTERVENTION] = enabled }
    }

    suspend fun setInterventionMode(mode: InterventionMode) {
        context.shadowAlertDataStore.edit {
            it[Keys.INTERVENTION_MODE] = mode.name
            it[Keys.OVERLAY_INTERVENTION] = (mode == InterventionMode.STRONG_SHIELD)
        }
    }

    suspend fun setActiveSessionId(sessionId: Long) {
        context.shadowAlertDataStore.edit { it[Keys.ACTIVE_SESSION_ID] = sessionId }
    }

    suspend fun clearAll() {
        context.shadowAlertDataStore.edit { prefs ->
            val keepOnboarding = prefs[Keys.ONBOARDING_COMPLETED] ?: false
            prefs.clear()
            prefs[Keys.ONBOARDING_COMPLETED] = keepOnboarding
        }
    }
}
