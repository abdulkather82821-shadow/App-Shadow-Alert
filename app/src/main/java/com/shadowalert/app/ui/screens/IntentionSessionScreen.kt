package com.shadowalert.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shadowalert.app.data.local.entity.AppLimitEntity
import com.shadowalert.app.data.local.entity.IntentSessionEntity
import com.shadowalert.app.domain.logic.AnalyticsCalculator
import com.shadowalert.app.domain.logic.SessionDurationValidator
import com.shadowalert.app.domain.model.AppCategory
import com.shadowalert.app.domain.model.InstalledAppInfo
import com.shadowalert.app.domain.model.MonitoringState
import com.shadowalert.app.domain.model.PermissionStatus
import com.shadowalert.app.ui.components.AppIconImage
import com.shadowalert.app.ui.components.StatusPill
import com.shadowalert.app.ui.components.TrackingStatusBanner
import com.shadowalert.app.ui.components.UsageProgressBar
import com.shadowalert.app.ui.theme.AmberSoft
import com.shadowalert.app.ui.theme.AmberWarning
import com.shadowalert.app.ui.theme.EmeraldOnTrack
import com.shadowalert.app.ui.theme.EmeraldSoft

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun IntentionSessionScreen(
    preselectedPackageName: String?,
    installedApps: List<InstalledAppInfo>,
    enabledAppLimits: List<AppLimitEntity>,
    activeSession: IntentSessionEntity?,
    monitoringState: MonitoringState,
    permissionStatus: PermissionStatus,
    duplicateSessionPrompt: IntentSessionEntity?,
    onStartSession: (packageName: String, appName: String, category: AppCategory, minutes: Int, purpose: String?, launchApp: Boolean) -> Unit,
    onOpenTargetApp: (String) -> Unit,
    onEndSession: (Long) -> Unit,
    onOpenPauseScreen: (Long, String, Int) -> Unit,
    onOpenPermissions: () -> Unit,
    onDismissDuplicatePrompt: () -> Unit,
    onEndDuplicateAndDismiss: () -> Unit
) {
    val allSelectableApps = remember(installedApps, enabledAppLimits) {
        val enabledSet = enabledAppLimits.map { it.packageName }.toSet()
        val monitored = enabledAppLimits.map {
            InstalledAppInfo(it.packageName, it.appName, it.category)
        }
        val others = installedApps.filter { it.packageName !in enabledSet }
        monitored + others
    }

    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf<Int?>(15) }
    var customMinutesText by remember { mutableStateOf("") }
    var isCustomSelected by remember { mutableStateOf(false) }
    var optionalPurpose by remember { mutableStateOf("") }
    var launchImmediately by remember { mutableStateOf(true) }

    LaunchedEffect(preselectedPackageName, allSelectableApps) {
        if (selectedApp == null && allSelectableApps.isNotEmpty()) {
            selectedApp = allSelectableApps.firstOrNull { it.packageName == preselectedPackageName }
                ?: allSelectableApps.first()
        } else if (preselectedPackageName != null) {
            allSelectableApps.firstOrNull { it.packageName == preselectedPackageName }?.let {
                selectedApp = it
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Intention Session",
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

        item {
            TrackingStatusBanner(
                monitoringState = monitoringState,
                usageAccessGranted = permissionStatus.usageAccessGranted,
                onSetupPermissions = onOpenPermissions
            )
        }

        if (activeSession != null) {
            item {
                LiveMonitoringSessionCard(
                    session = activeSession,
                    monitoringState = monitoringState,
                    usageAccessGranted = permissionStatus.usageAccessGranted,
                    onOpenTargetApp = { onOpenTargetApp(activeSession.packageName) },
                    onEndSession = { onEndSession(activeSession.id) },
                    onOpenPauseScreen = {
                        onOpenPauseScreen(
                            activeSession.id,
                            activeSession.appName,
                            activeSession.intendedDuration
                        )
                    }
                )
            }
        } else {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "What do you intend to do?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedApp?.appName ?: "Select application",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Target Application") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            )
                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                allSelectableApps.take(40).forEach { app ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                AppIconImage(
                                                    packageName = app.packageName,
                                                    contentDescription = app.appName,
                                                    size = 28.dp
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(app.appName)
                                                    Text(
                                                        text = app.suggestedCategory.displayName,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedApp = app
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = optionalPurpose,
                            onValueChange = { optionalPurpose = it },
                            label = { Text("Optional purpose (e.g., Reply to messages, Check lecture)") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            shape = MaterialTheme.shapes.medium
                        )

                        Text(
                            text = "How long do you plan to use this application?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SessionDurationValidator.presetsMinutes.forEach { preset ->
                                FilterChip(
                                    selected = !isCustomSelected && selectedPreset == preset,
                                    onClick = {
                                        isCustomSelected = false
                                        selectedPreset = preset
                                    },
                                    label = { Text("${preset}m") }
                                )
                            }
                            FilterChip(
                                selected = isCustomSelected,
                                onClick = { isCustomSelected = true },
                                label = { Text("Custom") }
                            )
                        }

                        if (isCustomSelected) {
                            OutlinedTextField(
                                value = customMinutesText,
                                onValueChange = { text ->
                                    if (text.all { it.isDigit() }) customMinutesText = text
                                },
                                label = { Text("Custom duration (minutes)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Open app after starting",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Launches the target app immediately with monitoring active.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = launchImmediately,
                                onCheckedChange = { launchImmediately = it }
                            )
                        }

                        val resolvedMinutes = if (isCustomSelected) {
                            customMinutesText.toIntOrNull() ?: 0
                        } else {
                            selectedPreset ?: 15
                        }

                        Button(
                            onClick = {
                                val app = selectedApp ?: return@Button
                                onStartSession(
                                    app.packageName,
                                    app.appName,
                                    app.suggestedCategory,
                                    resolvedMinutes,
                                    optionalPurpose,
                                    launchImmediately
                                )
                            },
                            enabled = selectedApp != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Start ${if (resolvedMinutes > 0) "${resolvedMinutes}m " else ""}Intention Session",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }

    if (duplicateSessionPrompt != null) {
        AlertDialog(
            onDismissRequest = onDismissDuplicatePrompt,
            title = { Text("Active Session Already Running") },
            text = {
                Text(
                    "You already have an active session for ${duplicateSessionPrompt.appName} " +
                        "(${duplicateSessionPrompt.intendedDuration}m planned). End it before starting a new one?"
                )
            },
            confirmButton = {
                TextButton(onClick = onEndDuplicateAndDismiss) {
                    Text("End Previous Session")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDuplicatePrompt) {
                    Text("Keep Current")
                }
            }
        )
    }
}

@Composable
private fun LiveMonitoringSessionCard(
    session: IntentSessionEntity,
    monitoringState: MonitoringState,
    usageAccessGranted: Boolean,
    onOpenTargetApp: () -> Unit,
    onEndSession: () -> Unit,
    onOpenPauseScreen: () -> Unit
) {
    val elapsedSec = if (monitoringState.activeSessionId == session.id) {
        monitoringState.elapsedSessionSeconds
    } else {
        session.accumulatedActiveMs / 1000L
    }
    val totalSec = session.intendedDuration * 60L
    val remainingSec = (totalSec - elapsedSec).coerceAtLeast(0L)

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIconImage(
                        packageName = session.packageName,
                        contentDescription = session.appName,
                        size = 48.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = session.appName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = monitoringState.trackingMode.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                StatusPill(
                    text = when {
                        remainingSec <= 0L -> "Complete"
                        !usageAccessGranted -> "Manual"
                        monitoringState.isTargetAppInForeground -> "In App"
                        else -> "Paused (Away)"
                    },
                    containerColor = if (remainingSec <= 0L) AmberSoft else EmeraldSoft,
                    contentColor = if (remainingSec <= 0L) AmberWarning else EmeraldOnTrack
                )
            }

            if (!session.optionalPurpose.isNullOrBlank()) {
                Text(
                    text = "Intention: \"${session.optionalPurpose}\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Rounded.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = AnalyticsCalculator.formatSecondsAsClock(remainingSec),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Elapsed: ${AnalyticsCalculator.formatSecondsAsClock(elapsedSec)} of ${session.intendedDuration}m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            UsageProgressBar(
                usageMinutes = (elapsedSec / 60L).toInt(),
                limitMinutes = session.intendedDuration
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenTargetApp,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open App")
                }
                OutlinedButton(
                    onClick = if (remainingSec <= 0L) onOpenPauseScreen else onEndSession,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (remainingSec <= 0L) "Pause Screen" else "End Session")
                }
            }
        }
    }
}
