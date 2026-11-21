package com.shadowalert.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shadowalert.app.data.local.entity.AppLimitEntity
import com.shadowalert.app.data.local.entity.DailyAppUsageEntity
import com.shadowalert.app.data.local.entity.IntentSessionEntity
import com.shadowalert.app.domain.logic.AnalyticsCalculator
import com.shadowalert.app.domain.model.AppCategory
import com.shadowalert.app.domain.model.DailyUsagePoint
import com.shadowalert.app.domain.model.InstalledAppInfo
import com.shadowalert.app.ui.components.AppIconImage
import com.shadowalert.app.ui.components.MetricStatCard
import com.shadowalert.app.ui.components.SevenDayBarChart
import com.shadowalert.app.ui.components.UsageProgressBar
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailsScreen(
    packageName: String,
    installedInfo: InstalledAppInfo?,
    appLimit: AppLimitEntity?,
    sevenDayUsage: List<DailyAppUsageEntity>,
    recentSessions: List<IntentSessionEntity>,
    onBack: () -> Unit,
    onEditDailyLimit: (InstalledAppInfo, Int) -> Unit,
    onUpdateCategory: (String, AppCategory) -> Unit,
    onToggleMonitoring: (InstalledAppInfo, Boolean) -> Unit,
    onStartIntentionSession: (String) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    val appName = appLimit?.appName ?: installedInfo?.appName ?: packageName
    val category = appLimit?.category ?: installedInfo?.suggestedCategory ?: AppCategory.FOCUS
    val todayUsage = appLimit?.currentUsageMinutes ?: 0
    val baseDailyLimit = appLimit?.dailyLimitMinutes ?: 60
    val effectiveLimit = appLimit?.effectiveDailyLimitMinutes ?: 60
    val remaining = (effectiveLimit - todayUsage).coerceAtLeast(0)
    val isMonitoringEnabled = appLimit?.enabled ?: false

    val effectiveInstalled = installedInfo ?: InstalledAppInfo(
        packageName = packageName,
        appName = appName,
        suggestedCategory = category
    )

    val sevenDayPoints = remember(sevenDayUsage, todayUsage) {
        val today = LocalDate.now().toEpochDay()
        val map = sevenDayUsage.associateBy { it.epochDay }
        ((today - 6)..today).map { epochDay ->
            val date = LocalDate.ofEpochDay(epochDay)
            val recorded = map[epochDay]?.usageMinutes ?: 0
            DailyUsagePoint(
                epochDay = epochDay,
                dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                totalMinutes = if (epochDay == today) maxOf(recorded, todayUsage) else recorded
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIconImage(
                                packageName = packageName,
                                contentDescription = appName,
                                size = 56.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = appName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${category.displayName} • ${if (isMonitoringEnabled) "Monitoring Active" else "Monitoring Paused"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        UsageProgressBar(usageMinutes = todayUsage, limitMinutes = effectiveLimit)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = "Today's Usage",
                        value = AnalyticsCalculator.formatMinutes(todayUsage),
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Daily Limit",
                        value = AnalyticsCalculator.formatMinutes(effectiveLimit),
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Remaining",
                        value = AnalyticsCalculator.formatMinutes(remaining),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onStartIntentionSession(packageName) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Intention Session")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Limit")
                        }
                        OutlinedButton(
                            onClick = { onToggleMonitoring(effectiveInstalled, !isMonitoringEnabled) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Rounded.PauseCircleOutline, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isMonitoringEnabled) "Pause" else "Resume")
                        }
                    }
                }
            }

            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "7-Day Usage History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        SevenDayBarChart(points = sevenDayPoints)
                    }
                }
            }

            item {
                Text(
                    text = "Recent Intention Sessions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (recentSessions.isEmpty()) {
                item {
                    Text(
                        text = "No intention sessions recorded for $appName yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(recentSessions.take(8), key = { it.id }) { session ->
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Intended ${session.intendedDuration}m • Actual ${session.actualDuration}m",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (!session.optionalPurpose.isNullOrBlank()) {
                                    Text(
                                        text = session.optionalPurpose,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = session.completionStatus.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        EditAppLimitDialog(
            appName = appName,
            initialLimitMinutes = baseDailyLimit,
            initialCategory = category,
            onDismiss = { showEditDialog = false },
            onSave = { newLimit, newCategory ->
                onEditDailyLimit(effectiveInstalled, newLimit)
                onUpdateCategory(packageName, newCategory)
                showEditDialog = false
            }
        )
    }
}
