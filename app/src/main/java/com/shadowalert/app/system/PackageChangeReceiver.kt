package com.shadowalert.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shadowalert.app.ShadowAlertApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_PACKAGE_FULLY_REMOVED) return
        val pkg = intent.data?.schemeSpecificPart ?: return
        val pendingResult = goAsync()
        val app = context.applicationContext as ShadowAlertApplication

        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.repository.handleUninstalledPackage(pkg)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
