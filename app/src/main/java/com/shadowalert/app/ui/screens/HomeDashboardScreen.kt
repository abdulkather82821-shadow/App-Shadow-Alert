package com.shadowalert.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shadowalert.app.data.local.entity.AppLimitEntity
import com.shadowalert.app.data.local.entity.IntentSessionEntity
import com.shadowalert.app.domain.logic.AnalyticsCalculator
import com.shadowalert.app.domain.model.MonitoringState
import com.shadowalert.app.domain.model.PermissionStatus
import com.shadowalert.app.ui.components.AppIconImage
import com.shadowalert.app.ui.components.DailyLimitDonutChart
import com.shadowalert.app.ui.components.MetricStatCard
import com.shadowalert.app.ui.components.StatusPill
import com.shadowalert.app.ui.components.TrackingStatusBanner
import com.shadowalert.app.ui.components.UsageProgressBar
import com.shadowalert.app.ui.theme.AmberSoft
import com.shadowalert.app.ui.theme.AmberWarning
import com.shadowalert.app.ui.theme.CoralPause
import com.shadowalert.app.ui.theme.CoralSoft

@Composable
fun HomeDashboardScreen(
    userName: String,
    dailyTargetMinutes: Int,
    enabledApps: List<AppLimitEntity>,
    activeSession: IntentSessionEntity?,
    monitoringState: MonitoringState,
    permissionStatus: PermissionStatus,
    onQuickStartSession: () -> Unit,
    onOpenMonitoredApps: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onOpenTargetApp: (String) -> Unit,
    onOpenPauseReview: (Long, String, Int) -> Unit,
    onEndActiveSession: (Long) -> Unit
) {
    val todayUsageMinutes = enabledApps.sumOf { it.currentUsageMinutes }
    val remainingMinutes = (dailyTargetMinutes - todayUsageMinutes).coerceAtLeast(0)
    val approachingApps = enabledApps.filter {
        it.effectiveDailyLimitMinutes > 0 &&
            it.currentUsageMinutes.toFloat() / it.effectiveDailyLimitMinutes >= 0.75f
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = if (userName.isBlank()) "Welcome back" else "Welcome back, $userName",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Plan before use. Stay aware during use. Take control after use.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            TrackingStatusBanner(
                monitoringState = monitoringState,
                usageAccessGranted = permissionStatus.usageAccessGranted,
                onSetupPermissions = onOpenPermissions
            )
        }

        // Daily usage overview card
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DailyLimitDonutChart(
                            usedMinutes = todayUsageMinutes,
                            targetMinutes = dailyTargetMinutes,
                            size = 132.dp
                        )
                        Spacer(modifier = Modifier.width(18.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DashboardStatLine(
                                label = "Today's Screen Time",
                                value = AnalyticsCalculator.formatMinutes(todayUsageMinutes)
                            )
                            DashboardStatLine(
                                label = "Daily Target",
                                value = AnalyticsCalculator.formatMinutes(dailyTargetMinutes)
                            )
                            DashboardStatLine(
                                label = "Remaining",
                                value = AnalyticsCalculator.formatMinutes(remainingMinutes)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    UsageProgressBar(
                        usageMinutes = todayUsageMinutes,
                        limitMinutes = dailyTargetMinutes
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onQuickStartSession,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Quick Start Intention Session",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // Active Intention Session Card
        if (activeSession != null) {
            item {
                ActiveSessionBannerCard(
                    session = activeSession,
                    monitoringState = monitoringState,
                    onOpenApp = { onOpenTargetApp(activeSession.packageName) },
                    onReviewPause = {
                        onOpenPauseReview(
                            activeSession.id,
                            activeSession.appName,
                            activeSession.intendedDuration
                        )
                    },
                    onEndSession = { onEndActiveSession(activeSession.id) }
                )
            }
        }

        // Quick metric cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricStatCard(
                    title = "Monitored Apps",
                    value = enabledApps.size.toString(),
                    subtitle = if (enabledApps.isEmpty()) "Tap Apps to select" else "Actively tracked",
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    title = "Near Daily Limit",
                    value = approachingApps.size.toString(),
                    subtitle = if (approachingApps.isEmpty()) "All within limits" else "Needs attention",
                    accentColor = if (approachingApps.isNotEmpty()) AmberWarning else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Applications approaching limits
        if (approachingApps.isNotEmpty()) {
            item {
                Text(
                    text = "Approaching Daily Limits",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(approachingApps, key = { "approaching_${it.packageName}" }) { app ->
                val effectiveLimit = app.effectiveDailyLimitMinutes
                val over = app.currentUsageMinutes >= effectiveLimit
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAppDetails(app.packageName) },
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconImage(
                            packageName = app.packageName,
                            contentDescription = app.appName,
                            size = 40.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${AnalyticsCalculator.formatMinutes(app.currentUsageMinutes)} of ${AnalyticsCalculator.formatMinutes(effectiveLimit)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            text = if (over) "Limit Reached" else "Near Limit",
                            containerColor = if (over) CoralSoft else AmberSoft,
                            contentColor = if (over) CoralPause else AmberWarning
                        )
                    }
                }
            }
        }

        // Monitored applications list
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monitored Applications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onOpenMonitoredApps) {
                    Text("Manage")
                }
            }
        }

        if (enabledApps.isEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No apps monitored yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Choose installed apps to set daily limits and track intentional sessions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        FilledTonalButton(onClick = onOpenMonitoredApps) {
                            Icon(Icons.Rounded.AddCircleOutline, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Applications")
                        }
                    }
                }
            }
        } else {
            items(enabledApps, key = { it.packageName }) { app ->
                MonitoredAppSummaryRow(
                    app = app,
                    onClick = { onOpenAppDetails(app.packageName) }
                )
            }
        }
    }
}

@Composable
private fun DashboardStatLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActiveSessionBannerCard(
    session: IntentSessionEntity,
    monitoringState: MonitoringState,
    onOpenApp: () -> Unit,
    onReviewPause: () -> Unit,
    onEndSession: () -> Unit
) {
    val remainingSeconds = if (monitoringState.activeSessionId == session.id) {
        monitoringState.remainingSessionSeconds
    } else {
        (session.intendedDuration * 60L - session.accumulatedActiveMs / 1000L).coerceAtLeast(0L)
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIconImage(
                        packageName = session.packageName,
                        contentDescription = session.appName,
                        size = 40.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Active Intention • ${session.appName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (!session.optionalPurpose.isNullOrBlank()) {
                                "Goal: ${session.optionalPurpose}"
                            } else {
                                "Planned: ${session.intendedDuration}m • ${monitoringState.trackingMode.label}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (monitoringState.isPausedForBreak) "Break Time" else "Remaining",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (monitoringState.isPausedForBreak) {
                            AnalyticsCalculator.formatSecondsAsClock(monitoringState.breakRemainingSeconds)
                        } else {
                            AnalyticsCalculator.formatSecondsAsClock(remainingSeconds)
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (remainingSeconds <= 0L) {
                        Button(onClick = onReviewPause) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Review")
                        }
                    } else {
                        FilledTonalButton(onClick = onOpenApp) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open")
                        }
                    }
                    OutlinedButton(onClick = onEndSession) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("End")
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitoredAppSummaryRow(
    app: AppLimitEntity,
    onClick: () -> Unit
) {
    val effectiveLimit = app.effectiveDailyLimitMinutes
    val remaining = (effectiveLimit - app.currentUsageMinutes).coerceAtLeast(0)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconImage(
                    packageName = app.packageName,
                    contentDescription = app.appName,
                    size = 42.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${app.category.displayName} • ${AnalyticsCalculator.formatMinutes(remaining)} left",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${AnalyticsCalculator.formatMinutes(app.currentUsageMinutes)} / ${AnalyticsCalculator.formatMinutes(effectiveLimit)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            UsageProgressBar(
                usageMinutes = app.currentUsageMinutes,
                limitMinutes = effectiveLimit
            )
        }
    }
}
