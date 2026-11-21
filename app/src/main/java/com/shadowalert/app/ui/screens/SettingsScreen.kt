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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shadowalert.app.BuildConfig
import com.shadowalert.app.data.preferences.UserPreferences
import com.shadowalert.app.domain.model.InterventionMode
import com.shadowalert.app.domain.model.ThemeMode
import java.util.Locale

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onSetUserName: (String) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDailyTargetMinutes: (Int) -> Unit,
    onUpdateWarningSettings: (Boolean, Boolean, Boolean, Int) -> Unit,
    onSetMonitoringEnabled: (Boolean) -> Unit,
    onSetPollingIntervalSeconds: (Int) -> Unit,
    onSetDailyResetHour: (Int) -> Unit,
    onSetInterventionMode: (InterventionMode) -> Unit,
    onOpenPermissions: () -> Unit,
    onExportSummary: () -> Unit,
    onDeleteAllData: () -> Unit
) {
    var nameInput by remember(preferences.userName) { mutableStateOf(preferences.userName) }
    var targetInput by remember(preferences.totalDailyTargetMinutes) {
        mutableStateOf(preferences.totalDailyTargetMinutes.toString())
    }
    var customWarnInput by remember(preferences.customWarningMinutes) {
        mutableStateOf(preferences.customWarningMinutes.toString())
    }

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "All preferences and usage history remain locally on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Local Profile
        item {
            SettingsSectionCard(title = "Local Profile (Optional)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Display name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onSetUserName(nameInput) }) {
                        Text("Save")
                    }
                }
            }
        }

        // Appearance
        item {
            SettingsSectionCard(title = "Appearance") {
                Text(
                    text = "Theme Mode",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                            label = { Text(mode.label.replace(" Default", "")) }
                        )
                    }
                }
            }
        }

        // Intervention Strength
        item {
            SettingsSectionCard(title = "Intervention Level") {
                InterventionMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetInterventionMode(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = preferences.interventionMode == mode,
                            onClick = { onSetInterventionMode(mode) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = mode.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Warning Preferences
        item {
            SettingsSectionCard(title = "Notification & Warning Preferences") {
                ToggleSettingRow(
                    title = "Level 1: 5-minute warning",
                    subtitle = "Gentle reminder when 5 minutes remain in planned session",
                    checked = preferences.warning5MinEnabled,
                    onCheckedChange = {
                        onUpdateWarningSettings(
                            it,
                            preferences.warning2MinEnabled,
                            preferences.limitReachedAlertEnabled,
                            preferences.customWarningMinutes
                        )
                    }
                )
                HorizontalDivider()
                ToggleSettingRow(
                    title = "Level 2: 2-minute warning",
                    subtitle = "Supportive prompt shortly before planned time ends",
                    checked = preferences.warning2MinEnabled,
                    onCheckedChange = {
                        onUpdateWarningSettings(
                            preferences.warning5MinEnabled,
                            it,
                            preferences.limitReachedAlertEnabled,
                            preferences.customWarningMinutes
                        )
                    }
                )
                HorizontalDivider()
                ToggleSettingRow(
                    title = "Level 3: Limit reached intervention",
                    subtitle = "Trigger configured pause or overlay when duration ends",
                    checked = preferences.limitReachedAlertEnabled,
                    onCheckedChange = {
                        onUpdateWarningSettings(
                            preferences.warning5MinEnabled,
                            preferences.warning2MinEnabled,
                            it,
                            preferences.customWarningMinutes
                        )
                    }
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = customWarnInput,
                        onValueChange = { v -> if (v.all { it.isDigit() }) customWarnInput = v },
                        label = { Text("Custom warning (min before end, 0 = off)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            val mins = customWarnInput.toIntOrNull() ?: 0
                            onUpdateWarningSettings(
                                preferences.warning5MinEnabled,
                                preferences.warning2MinEnabled,
                                preferences.limitReachedAlertEnabled,
                                mins
                            )
                        }
                    ) {
                        Text("Apply")
                    }
                }
            }
        }

        // Monitoring & Daily Reset Settings
        item {
            SettingsSectionCard(title = "Monitoring & Daily Limits") {
                ToggleSettingRow(
                    title = "Background foreground service",
                    subtitle = "Keep real-time monitoring active while screen is on",
                    checked = preferences.monitoringEnabled,
                    onCheckedChange = onSetMonitoringEnabled
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { v -> if (v.all { it.isDigit() }) targetInput = v },
                        label = { Text("Daily screen-time target (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            val mins = targetInput.toIntOrNull() ?: 240
                            onSetDailyTargetMinutes(mins)
                        }
                    ) {
                        Text("Save")
                    }
                }
                HorizontalDivider()
                Text(
                    text = "Usage polling interval: ${preferences.pollingIntervalSeconds}s",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 5, 10).forEach { sec ->
                        FilterChip(
                            selected = preferences.pollingIntervalSeconds == sec,
                            onClick = { onSetPollingIntervalSeconds(sec) },
                            label = { Text("${sec}s") }
                        )
                    }
                }
                HorizontalDivider()
                Text(
                    text = "Daily reset hour: ${String.format(Locale.US, "%02d:00", preferences.dailyResetHour)}",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 4, 6).forEach { hour ->
                        FilterChip(
                            selected = preferences.dailyResetHour == hour,
                            onClick = { onSetDailyResetHour(hour) },
                            label = { Text(if (hour == 0) "Midnight" else "${hour}:00 AM") }
                        )
                    }
                }
            }
        }

        // Permissions, Privacy & Data Management
        item {
            SettingsSectionCard(title = "Privacy & Data Controls") {
                SettingsActionRow(
                    icon = Icons.Rounded.Security,
                    title = "Manage Permissions",
                    subtitle = "Usage Access, Notifications, Overlay, Battery",
                    onClick = onOpenPermissions
                )
                HorizontalDivider()
                SettingsActionRow(
                    icon = Icons.Rounded.Lock,
                    title = "Privacy Policy",
                    subtitle = "100% local storage • No network or telemetry",
                    onClick = { showPrivacyDialog = true }
                )
                HorizontalDivider()
                SettingsActionRow(
                    icon = Icons.Rounded.Download,
                    title = "Export Usage Summary",
                    subtitle = "Share local Markdown summary via Android ShareSheet",
                    onClick = onExportSummary
                )
                HorizontalDivider()
                SettingsActionRow(
                    icon = Icons.Rounded.Info,
                    title = "About Shadow Alert",
                    subtitle = "Version ${BuildConfig.VERSION_NAME} • Research & Architecture",
                    onClick = { showAboutDialog = true }
                )
                HorizontalDivider()
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete All Local Data")
                }
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Shadow Alert Privacy Policy") },
            text = {
                Text(
                    "Shadow Alert operates strictly offline on your device.\n\n" +
                        "• No internet permission is requested.\n" +
                        "• No account, cloud sync, analytics SDK, or telemetry exists.\n" +
                        "• Message contents, browsing history, contacts, and unrelated personal data are never accessed.\n" +
                        "• Only selected app package identifiers, daily durations, intention sessions, alerts, and optional reflections are stored locally in Room & DataStore.\n" +
                        "• You may export or permanently delete all local records at any time."
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("Close") }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Shadow Alert") },
            text = {
                Text(
                    "Shadow Alert v${BuildConfig.VERSION_NAME}\n" +
                        "\"Driven by Intent. Shaped by Excellence.\"\n\n" +
                        "Based on the research manuscript: \"Design and Development of Shadow Alert: An Intent-Based Mobile System for Real-Time Screen Monitoring and Usage Indications.\"\n\n" +
                        "Core Principle:\nPlan before use. Stay aware during use. Take control after use.\n\n" +
                        "Platform Transparency: Uses Android UsageStatsManager, Foreground Service, Overlay prompts (optional), and NotificationManager. Does not misuse Accessibility Services or forcibly lock third-party apps."
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete All Local Data?") },
            text = {
                Text("This permanently clears all monitored app limits, intention sessions, alert logs, reflections, and daily history on this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteAllData()
                    }
                ) {
                    Text("Delete Everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
