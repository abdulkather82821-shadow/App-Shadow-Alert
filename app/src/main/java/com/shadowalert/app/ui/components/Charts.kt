package com.shadowalert.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shadowalert.app.domain.logic.AnalyticsCalculator
import com.shadowalert.app.domain.model.AppUsageBreakdown
import com.shadowalert.app.domain.model.DailyUsagePoint
import com.shadowalert.app.domain.model.IntendedVsActualPoint
import com.shadowalert.app.ui.theme.AmberWarning
import com.shadowalert.app.ui.theme.CoralPause
import com.shadowalert.app.ui.theme.EmeraldOnTrack

@Composable
fun DailyLimitDonutChart(
    usedMinutes: Int,
    targetMinutes: Int,
    modifier: Modifier = Modifier,
    size: Dp = 148.dp
) {
    val safeTarget = targetMinutes.coerceAtLeast(1)
    val ratio = (usedMinutes.toFloat() / safeTarget.toFloat()).coerceIn(0f, 1f)
    val animatedSweep by animateFloatAsState(
        targetValue = ratio * 360f,
        animationSpec = tween(durationMillis = 700),
        label = "donut_sweep"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = when {
        usedMinutes >= safeTarget -> CoralPause
        ratio >= 0.8f -> AmberWarning
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = animatedSweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = AnalyticsCalculator.formatMinutes(usedMinutes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "of ${AnalyticsCalculator.formatMinutes(safeTarget)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SevenDayBarChart(
    points: List<DailyUsagePoint>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    if (points.isEmpty()) {
        Text(
            text = "No daily usage data recorded yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val maxMinutes = (points.maxOfOrNull { it.totalMinutes } ?: 0).coerceAtLeast(30)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val count = points.size
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.48f).coerceAtMost(36.dp.toPx())
            val chartHeight = size.height

            points.forEachIndexed { index, point ->
                val x = index * slotWidth + (slotWidth - barWidth) / 2f
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, chartHeight),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
                val fraction = (point.totalMinutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
                val barHeight = (chartHeight * fraction).coerceAtLeast(
                    if (point.totalMinutes > 0) 6.dp.toPx() else 0f
                )
                if (barHeight > 0f) {
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, chartHeight - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            points.forEach { point ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = point.dayLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${point.totalMinutes}m",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun IntendedVsActualChart(
    sessions: List<IntendedVsActualPoint>,
    modifier: Modifier = Modifier
) {
    if (sessions.isEmpty()) {
        Text(
            text = "Complete an intention session to compare planned vs. actual duration.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val maxMinutes = sessions.maxOf { maxOf(it.intendedMinutes, it.actualMinutes) }.coerceAtLeast(10)
    val intendedColor = MaterialTheme.colorScheme.primary
    val withinColor = EmeraldOnTrack
    val overColor = AmberWarning

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(color = intendedColor, label = "Intended")
            LegendItem(color = withinColor, label = "Actual (Within)")
            LegendItem(color = overColor, label = "Actual (Over)")
        }
        Spacer(modifier = Modifier.height(12.dp))

        sessions.forEach { session ->
            val intendedRatio = (session.intendedMinutes.toFloat() / maxMinutes).coerceIn(0f, 1f)
            val actualRatio = (session.actualMinutes.toFloat() / maxMinutes).coerceIn(0f, 1f)
            val actualColor = if (session.withinIntent) withinColor else overColor

            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = session.appName,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${session.intendedMinutes}m planned • ${session.actualMinutes}m actual",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(intendedRatio)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(intendedColor)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(actualRatio)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(actualColor)
                    )
                }
            }
        }
    }
}

@Composable
fun AppUsageHorizontalBars(
    apps: List<AppUsageBreakdown>,
    modifier: Modifier = Modifier
) {
    if (apps.isEmpty()) {
        Text(
            text = "Select monitored applications to see app-wise usage distribution.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        apps.take(6).forEach { app ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${app.appName} (${app.category.displayName})",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${AnalyticsCalculator.formatMinutes(app.usageMinutes)} (${app.sharePercent}%) / ${AnalyticsCalculator.formatMinutes(app.dailyLimitMinutes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                UsageProgressBar(
                    usageMinutes = app.usageMinutes,
                    limitMinutes = app.dailyLimitMinutes
                )
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
