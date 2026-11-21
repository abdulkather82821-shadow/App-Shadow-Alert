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
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shadowalert.app.domain.logic.AnalyticsCalculator
import com.shadowalert.app.domain.model.AnalyticsSummary
import com.shadowalert.app.ui.components.AppUsageHorizontalBars
import com.shadowalert.app.ui.components.DailyLimitDonutChart
import com.shadowalert.app.ui.components.IntendedVsActualChart
import com.shadowalert.app.ui.components.MetricStatCard
import com.shadowalert.app.ui.components.SevenDayBarChart
import com.shadowalert.app.ui.theme.AmberWarning
import com.shadowalert.app.ui.theme.EmeraldOnTrack
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AnalyticsDashboardScreen(
    summary: AnalyticsSummary
) {
    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())
    }

    val trendLabel = when {
        summary.sevenDayAverageMinutes == 0 -> "Building baseline"
        summary.usageTrendPercentVsAvg > 0 -> "+${summary.usageTrendPercentVsAvg}% vs 7d avg"
        summary.usageTrendPercentVsAvg < 0 -> "${summary.usageTrendPercentVsAvg}% vs 7d avg"
        else -> "On par with 7d avg"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Analytics & Reflections",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Real usage metrics computed locally from your device records.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Daily & Weekly Usage + Trend
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricStatCard(
                    title = "Today's Usage",
                    value = AnalyticsCalculator.formatMinutes(summary.todayUsageMinutes),
                    subtitle = trendLabel,
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    title = "Weekly Total",
                    value = AnalyticsCalculator.formatMinutes(summary.weeklyUsageMinutes),
                    subtitle = "Avg ${AnalyticsCalculator.formatMinutes(summary.sevenDayAverageMinutes)}/day",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Session Completion Rate & Break Frequency
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricStatCard(
                    title = "Completion Rate",
                    value = "${summary.sessionCompletionRatePercent}%",
                    subtitle = "${summary.sessionsCompletedWithinIntent} of ${summary.totalCompletedSessions} within intent",
                    accentColor = EmeraldOnTrack,
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    title = "Break Frequency",
                    value = "${summary.breakFrequencyPercent}%",
                    subtitle = "${summary.breaksTakenCount} mindful break${if (summary.breaksTakenCount == 1) "" else "s"}",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Intended vs Actual Totals
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricStatCard(
                    title = "Total Intended",
                    value = AnalyticsCalculator.formatMinutes(summary.totalIntendedMinutes),
                    subtitle = "Planned across sessions",
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    title = "Actual Session Time",
                    value = AnalyticsCalculator.formatMinutes(summary.totalActualMinutes),
                    subtitle = "${summary.sessionsExceedingIntent} exceeded intent",
                    accentColor = if (summary.sessionsExceedingIntent > 0) AmberWarning else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Warnings, Overrides & Recovery Moments
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricStatCard(
                    title = "Warnings",
                    value = summary.warningEventsCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    title = "Overrides",
                    value = summary.overrideEventsCount.toString(),
                    accentColor = AmberWarning,
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    title = "Recoveries",
                    value = summary.recoveryMomentsCount.toString(),
                    accentColor = EmeraldOnTrack,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Daily Target Utilization
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DailyLimitDonutChart(
                        usedMinutes = summary.todayUsageMinutes,
                        targetMinutes = summary.dailyTargetMinutes,
                        size = 118.dp
                    )
                    Spacer(modifier = Modifier.width(18.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Daily Target Progress",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${summary.dailyLimitUtilizationPercent}% of your ${AnalyticsCalculator.formatMinutes(summary.dailyTargetMinutes)} daily target used today.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 7-Day Usage History Chart
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "7-Day Screen Time",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SevenDayBarChart(points = summary.sevenDayUsage)
                }
            }
        }

        // Most-Used Applications
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Most-Used Monitored Apps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AppUsageHorizontalBars(apps = summary.appBreakdown)
                }
            }
        }

        // Intended vs Actual Comparison Chart
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Intended vs. Actual Usage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    IntendedVsActualChart(sessions = summary.intendedVsActualSessions)
                }
            }
        }

        // Override History
        if (summary.recentOverrides.isNotEmpty()) {
            item {
                Text(
                    text = "Override History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(summary.recentOverrides, key = { it.alertId }) { item ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LockOpen,
                            contentDescription = null,
                            tint = AmberWarning
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = item.details ?: "Intentional override confirmed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = timeFormatter.format(Instant.ofEpochMilli(item.timestamp)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Weekly Improvement Insights
        item {
            Text(
                text = "Weekly Improvement Insights",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        items(summary.supportiveInsights) { insight ->
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = insight,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
