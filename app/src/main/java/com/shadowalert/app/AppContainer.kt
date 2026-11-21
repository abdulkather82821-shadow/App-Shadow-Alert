package com.shadowalert.app

import android.content.Context
import com.shadowalert.app.data.local.ShadowAlertDatabase
import com.shadowalert.app.data.preferences.UserPreferencesDataStore
import com.shadowalert.app.data.repository.InstalledAppsRepository
import com.shadowalert.app.data.repository.ShadowAlertRepository
import com.shadowalert.app.system.UsageMonitorStateHolder
import com.shadowalert.app.system.UsageStatsMonitor

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: ShadowAlertDatabase by lazy {
        ShadowAlertDatabase.getInstance(appContext)
    }

    val preferencesDataStore: UserPreferencesDataStore by lazy {
        UserPreferencesDataStore(appContext)
    }

    val installedAppsRepository: InstalledAppsRepository by lazy {
        InstalledAppsRepository(appContext)
    }

    val repository: ShadowAlertRepository by lazy {
        ShadowAlertRepository(database, preferencesDataStore)
    }

    val usageStatsMonitor: UsageStatsMonitor by lazy {
        UsageStatsMonitor(appContext)
    }

    val monitorStateHolder: UsageMonitorStateHolder by lazy {
        UsageMonitorStateHolder()
    }
}
