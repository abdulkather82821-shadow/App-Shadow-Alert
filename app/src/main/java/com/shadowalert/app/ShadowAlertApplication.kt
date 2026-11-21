package com.shadowalert.app

import android.app.Application
import com.shadowalert.app.system.NotificationHelper

class ShadowAlertApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createNotificationChannels(this)
    }
}
