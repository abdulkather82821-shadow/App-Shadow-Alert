package com.shadowalert.app.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shadowalert.app.MainActivity
import com.shadowalert.app.R
import com.shadowalert.app.domain.logic.AnalyticsCalculator
import com.shadowalert.app.domain.model.AlertType
import com.shadowalert.app.domain.model.InterventionTriggerType
import com.shadowalert.app.ui.PauseInterventionActivity

object NotificationHelper {

    const val CHANNEL_MONITORING = "shadow_alert_monitoring"
    const val CHANNEL_WARNINGS = "shadow_alert_warnings"
    const val CHANNEL_PAUSE = "shadow_alert_pause"

    const val NOTIFICATION_ID_FOREGROUND = 1001
    const val NOTIFICATION_ID_WARNING = 1002
    const val NOTIFICATION_ID_LIMIT_REACHED = 1003
    const val NOTIFICATION_ID_DAILY_LIMIT = 1004
    const val NOTIFICATION_ID_PERMISSION = 1005

    fun createNotificationChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val monitoringChannel = NotificationChannel(
            CHANNEL_MONITORING,
            "Active Session Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Transparent ongoing notification while Shadow Alert monitors active usage."
            setShowBadge(false)
        }

        val warningsChannel = NotificationChannel(
            CHANNEL_WARNINGS,
            "Intention Warnings",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Supportive reminders when your planned usage duration or daily limit is approaching."
        }

        val pauseChannel = NotificationChannel(
            CHANNEL_PAUSE,
            "Limit & Intervention Prompts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Intervention prompts displayed when planned duration or daily app limits are reached."
        }

        manager.createNotificationChannels(listOf(monitoringChannel, warningsChannel, pauseChannel))
    }

    fun buildMonitoringNotification(
        context: Context,
        title: String,
        content: String,
        includeStopAction: Boolean = true
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (includeStopAction) {
            val stopIntent = PendingIntent.getForegroundService(
                context,
                1,
                Intent(context, UsageMonitorService::class.java).apply {
                    action = UsageMonitorService.ACTION_STOP_MONITORING
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_notification_shield,
                "Stop",
                stopIntent
            )
        }

        return builder.build()
    }

    fun showWarningNotification(
        context: Context,
        appName: String,
        alertType: AlertType,
        remainingSeconds: Long
    ) {
        if (!PermissionHelper.hasNotificationPermission(context)) return

        val remainingFormatted = AnalyticsCalculator.formatMinutes(
            ((remainingSeconds + 59) / 60).toInt().coerceAtLeast(1)
        )
        val (title, body) = when (alertType) {
            AlertType.FIVE_MIN_REMAINING ->
                "5 minutes remaining in $appName" to
                    "Gentle reminder: your planned session in $appName has 5 minutes left."
            AlertType.TWO_MIN_REMAINING ->
                "2 minutes remaining in $appName" to
                    "Your planned time is nearly up. Take a breath and prepare to wrap up or reflect."
            AlertType.CUSTOM_WARNING ->
                "$remainingFormatted remaining in $appName" to
                    "Your custom reminder threshold was reached."
            AlertType.DAILY_LIMIT_APPROACHING ->
                "Daily limit approaching for $appName" to
                    "You have used most of today's configured limit for $appName."
            AlertType.DAILY_LIMIT_REACHED ->
                "Daily limit reached for $appName" to
                    "You reached today's configured limit for $appName. Tap to pause or request extra time."
            AlertType.INTENDED_DURATION_REACHED ->
                "Planned time complete for $appName" to
                    "Your planned duration is complete. Tap to review your session."
        }

        val openAppIntent = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_WARNINGS)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val id = if (alertType == AlertType.DAILY_LIMIT_REACHED || alertType == AlertType.DAILY_LIMIT_APPROACHING) {
            NOTIFICATION_ID_DAILY_LIMIT
        } else {
            NOTIFICATION_ID_WARNING
        }

        notifySafely(context, id, notification)
    }

    fun showPauseInterventionNotification(
        context: Context,
        sessionId: Long,
        packageName: String,
        appName: String,
        intendedMinutes: Int,
        triggerType: InterventionTriggerType = InterventionTriggerType.INTENTION_COMPLETE
    ) {
        if (!PermissionHelper.hasNotificationPermission(context)) return

        val pauseIntent = PauseInterventionActivity.createIntent(
            context = context,
            sessionId = sessionId,
            packageName = packageName,
            appName = appName,
            intendedMinutes = intendedMinutes,
            triggerType = triggerType
        )

        val contentPendingIntent = PendingIntent.getActivity(
            context,
            if (triggerType == InterventionTriggerType.DAILY_LIMIT_REACHED) 25 else 20,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (triggerType == InterventionTriggerType.DAILY_LIMIT_REACHED) {
            "Daily limit reached for $appName"
        } else {
            "Your planned time is complete"
        }
        val body = if (triggerType == InterventionTriggerType.DAILY_LIMIT_REACHED) {
            "You reached today's ${intendedMinutes}m daily limit for $appName. Pause, take a break, or deliberately request extra time."
        } else {
            "You decided to spend $intendedMinutes minutes in $appName. Take a moment before continuing."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_PAUSE)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(contentPendingIntent, true)
            .addAction(R.drawable.ic_notification_shield, "Review & Pause", contentPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        if (triggerType == InterventionTriggerType.INTENTION_COMPLETE && sessionId > 0L) {
            val endIntent = PendingIntent.getForegroundService(
                context,
                21,
                Intent(context, UsageMonitorService::class.java).apply {
                    action = UsageMonitorService.ACTION_END_SESSION_FROM_NOTIFICATION
                    putExtra(UsageMonitorService.EXTRA_SESSION_ID, sessionId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_notification_shield, "End Session", endIntent)
        }

        val notifId = if (triggerType == InterventionTriggerType.DAILY_LIMIT_REACHED) {
            NOTIFICATION_ID_DAILY_LIMIT
        } else {
            NOTIFICATION_ID_LIMIT_REACHED
        }

        notifySafely(context, notifId, builder.build())
    }

    fun showPermissionRevokedNotification(context: Context) {
        if (!PermissionHelper.hasNotificationPermission(context)) return

        val settingsPendingIntent = PendingIntent.getActivity(
            context,
            30,
            PermissionHelper.createUsageAccessIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_WARNINGS)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setContentTitle("Usage Access permission paused")
            .setContentText("Automatic app monitoring is paused because Usage Access was turned off. Tap to re-enable or switch to manual tracking.")
            .setContentIntent(settingsPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notifySafely(context, NOTIFICATION_ID_PERMISSION, notification)
    }

    private fun notifySafely(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancelPauseNotifications(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(NOTIFICATION_ID_LIMIT_REACHED)
        manager.cancel(NOTIFICATION_ID_DAILY_LIMIT)
    }
}
