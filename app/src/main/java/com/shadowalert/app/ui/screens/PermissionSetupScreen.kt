package com.shadowalert.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shadowalert.app.domain.model.PermissionStatus
import com.shadowalert.app.system.PermissionHelper
import com.shadowalert.app.ui.components.StatusPill
import com.shadowalert.app.ui.theme.AmberSoft
import com.shadowalert.app.ui.theme.AmberWarning
import com.shadowalert.app.ui.theme.EmeraldOnTrack
import com.shadowalert.app.ui.theme.EmeraldSoft

@Composable
fun PermissionSetupScreen(
    permissionStatus: PermissionStatus,
    onRefreshPermissions: () -> Unit,
    onContinueToHome: () -> Unit,
    showContinueButton: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshPermissions()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Permissions & Transparency",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Shadow Alert processes all usage data strictly on your device. Grant only the permissions you are comfortable with.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            PermissionItemCard(
                icon = Icons.Rounded.QueryStats,
                title = "Usage Access (Recommended)",
                requiredLabel = if (permissionStatus.usageAccessGranted) "Granted" else "Needed for Auto-Tracking",
                granted = permissionStatus.usageAccessGranted,
                explanation = "Allows Shadow Alert to inspect foreground app events via Android's UsageStatsManager so timers pause automatically when you leave an app or lock the screen.",
                fallbackNote = "If denied, automatic app detection stays OFF (never simulated). Manual Session Tracking remains available as a transparent fallback.",
                actionLabel = if (permissionStatus.usageAccessGranted) "Manage Usage Access" else "Grant Usage Access",
                onAction = {
                    context.startActivity(PermissionHelper.createUsageAccessIntent())
                }
            )
        }

        item {
            PermissionItemCard(
                icon = Icons.Rounded.Notifications,
                title = "Notifications",
                requiredLabel = if (permissionStatus.notificationsGranted) "Granted" else "Recommended",
                granted = permissionStatus.notificationsGranted,
                explanation = "Delivers Level 1 (5 min), Level 2 (2 min), and Level 3 (Limit Reached) alerts alongside your ongoing foreground monitoring status.",
                fallbackNote = "If disabled, warnings appear only inside Shadow Alert instead of system notifications.",
                actionLabel = if (permissionStatus.notificationsGranted) "Notification Settings" else "Allow Notifications",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !permissionStatus.notificationsGranted
                    ) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(PermissionHelper.createNotificationSettingsIntent(context))
                    }
                }
            )
        }

        item {
            PermissionItemCard(
                icon = Icons.Rounded.Layers,
                title = "Display Over Other Apps (Strong Shield)",
                requiredLabel = if (permissionStatus.overlayGranted) "Granted" else "Optional",
                granted = permissionStatus.overlayGranted,
                explanation = "Required by Android 10+ to open the Pause & Reflection intervention shield directly over other applications when a session or daily limit is reached.",
                fallbackNote = "Without this permission, Shadow Alert falls back to a high-priority Pause & Reflection notification.",
                actionLabel = if (permissionStatus.overlayGranted) "Manage Overlay" else "Enable Strong Shield Overlay",
                onAction = {
                    context.startActivity(PermissionHelper.createOverlaySettingsIntent(context))
                }
            )
        }

        item {
            PermissionItemCard(
                icon = Icons.Rounded.BatteryChargingFull,
                title = "Battery Optimization (Optional)",
                requiredLabel = if (permissionStatus.batteryOptimizationIgnored) "Unrestricted" else "System Managed",
                granted = permissionStatus.batteryOptimizationIgnored,
                explanation = "Reduces OEM background killing during active sessions. Shadow Alert already suspends polling completely while your screen is locked.",
                fallbackNote = "Session progress and triggered warnings are persisted locally in Room so state recovers safely if restarted.",
                actionLabel = "Battery Settings",
                onAction = {
                    context.startActivity(PermissionHelper.createBatteryOptimizationSettingsIntent())
                }
            )
        }

        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Why Accessibility Service Is Not Used",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Google Play policies restrict AccessibilityService strictly to assistive needs and prohibit using it for app blocking or screen-time killing. Ordinary Android apps also cannot forcibly terminate other apps. Shadow Alert respects Android security and your autonomy using UsageStatsManager, Overlay prompts, and supportive notifications.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        if (showContinueButton) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onContinueToHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = if (permissionStatus.usageAccessGranted) {
                            "Continue to Dashboard"
                        } else {
                            "Continue with Manual Session Mode"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionItemCard(
    icon: ImageVector,
    title: String,
    requiredLabel: String,
    granted: Boolean,
    explanation: String,
    fallbackNote: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                StatusPill(
                    text = requiredLabel,
                    containerColor = if (granted) EmeraldSoft else AmberSoft,
                    contentColor = if (granted) EmeraldOnTrack else AmberWarning
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = fallbackNote,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (granted) {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = EmeraldOnTrack,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(actionLabel)
                }
            } else {
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
