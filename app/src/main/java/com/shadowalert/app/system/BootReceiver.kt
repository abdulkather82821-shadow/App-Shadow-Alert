package com.shadowalert.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shadowalert.app.ShadowAlertApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        val app = context.applicationContext as ShadowAlertApplication

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = app.container.preferencesDataStore.getSnapshot()
                val activeSession = app.container.repository.getActiveSession()
                if (activeSession != null) {
                    val hasUsageAccess = PermissionHelper.hasUsageAccessPermission(context)
                    try {
                        UsageMonitorService.startSessionMonitoring(
                            context = context,
                            sessionId = activeSession.id,
                            manualMode = !hasUsageAccess
                        )
                    } catch (_: Exception) {
                        // Fallback if OS temporarily restricts background FGS start on boot
                        NotificationHelper.showWarningNotification(
                            context = context,
                            appName = activeSession.appName,
                            alertType = com.shadowalert.app.domain.model.AlertType.INTENDED_DURATION_REACHED,
                            remainingSeconds = 0L
                        )
                    }
                } else if (prefs.monitoringEnabled && PermissionHelper.hasUsageAccessPermission(context)) {
                    try {
                        UsageMonitorService.startMonitoring(context)
                    } catch (_: Exception) {
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
