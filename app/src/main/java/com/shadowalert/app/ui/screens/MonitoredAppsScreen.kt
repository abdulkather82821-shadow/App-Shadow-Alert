package com.shadowalert.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shadowalert.app.data.local.entity.AppLimitEntity
import com.shadowalert.app.domain.logic.AnalyticsCalculator
import com.shadowalert.app.domain.model.AppCategory
import com.shadowalert.app.domain.model.InstalledAppInfo
import com.shadowalert.app.ui.components.AppIconImage
import com.shadowalert.app.ui.components.UsageProgressBar

@Composable
fun MonitoredAppsScreen(
    installedApps: List<InstalledAppInfo>,
    appLimits: List<AppLimitEntity>,
    isLoading: Boolean,
    onToggleMonitoring: (InstalledAppInfo, Boolean) -> Unit,
    onUpdateDailyLimit: (InstalledAppInfo, Int) -> Unit,
    onUpdateCategory: (String, AppCategory) -> Unit,
    onOpenAppDetails: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<AppCategory?>(null) }
    var editingApp by remember { mutableStateOf<Pair<InstalledAppInfo, AppLimitEntity?>?>(null) }

    val limitsByPackage = remember(appLimits) { appLimits.associateBy { it.packageName } }

    val filteredApps = remember(installedApps, limitsByPackage, searchQuery, selectedCategory) {
        installedApps.filter { app ->
            val saved = limitsByPackage[app.packageName]
            val effectiveCategory = saved?.category ?: app.suggestedCategory
            val matchesQuery = searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == null || effectiveCategory == selectedCategory
            matchesQuery && matchesCategory
        }.sortedWith(
            compareByDescending<InstalledAppInfo> { limitsByPackage[it.packageName]?.enabled == true }
                .thenBy { it.appName.lowercase() }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = "Monitored Applications",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select apps to monitor and configure daily limits.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search installed applications...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All") }
                )
            }
            items(AppCategory.entries) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = {
                        selectedCategory = if (selectedCategory == category) null else category
                    },
                    label = { Text(category.displayName) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading && installedApps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No launchable applications matched your filter.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val savedLimit = limitsByPackage[app.packageName]
                    val isEnabled = savedLimit?.enabled == true
                    val category = savedLimit?.category ?: app.suggestedCategory
                    val dailyLimit = savedLimit?.effectiveDailyLimitMinutes ?: 60
                    val dailyUsage = savedLimit?.currentUsageMinutes ?: 0
                    val remaining = (dailyLimit - dailyUsage).coerceAtLeast(0)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAppDetails(app.packageName) },
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
                                    size = 44.dp
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
                                        text = category.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { editingApp = app to savedLimit }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit limit"
                                    )
                                }
                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = { checked ->
                                        onToggleMonitoring(app, checked)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Used: ${AnalyticsCalculator.formatMinutes(dailyUsage)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Limit: ${AnalyticsCalculator.formatMinutes(dailyLimit)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Left: ${AnalyticsCalculator.formatMinutes(remaining)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            UsageProgressBar(
                                usageMinutes = dailyUsage,
                                limitMinutes = dailyLimit
                            )
                        }
                    }
                }
            }
        }
    }

    editingApp?.let { (app, saved) ->
        EditAppLimitDialog(
            appName = app.appName,
            initialLimitMinutes = saved?.dailyLimitMinutes ?: 60,
            initialCategory = saved?.category ?: app.suggestedCategory,
            onDismiss = { editingApp = null },
            onSave = { limitMinutes, category ->
                onUpdateDailyLimit(app, limitMinutes)
                onUpdateCategory(app.packageName, category)
                editingApp = null
            }
        )
    }
}

@Composable
fun EditAppLimitDialog(
    appName: String,
    initialLimitMinutes: Int,
    initialCategory: AppCategory,
    onDismiss: () -> Unit,
    onSave: (Int, AppCategory) -> Unit
) {
    var limitInput by remember { mutableStateOf(initialLimitMinutes.toString()) }
    var selectedCategory by remember { mutableStateOf(initialCategory) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure $appName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() }) limitInput = value
                    },
                    label = { Text("Daily limit (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AppCategory.entries) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.displayName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = limitInput.toIntOrNull() ?: initialLimitMinutes
                    onSave(parsed, selectedCategory)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
