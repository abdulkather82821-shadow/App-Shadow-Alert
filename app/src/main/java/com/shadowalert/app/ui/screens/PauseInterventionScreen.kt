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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAlarm
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shadowalert.app.domain.model.InterventionTriggerType

private val quickReflectionPrompts = listOf(
    "Finished what I planned",
    "Lost track of time",
    "Need 5 more minutes",
    "Ready for a break"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PauseInterventionScreen(
    appName: String,
    intendedMinutes: Int,
    triggerType: InterventionTriggerType = InterventionTriggerType.INTENTION_COMPLETE,
    onEndSession: (reflection: String?) -> Unit,
    onTakeBreak: (reflection: String?) -> Unit,
    onAddMoreTime: (additionalMinutes: Int, reflection: String?) -> Unit,
    onContinueWithOverride: (reason: String?, reflection: String?) -> Unit,
    onLeaveToHome: (() -> Unit)? = null
) {
    var reflectionText by remember { mutableStateOf("") }
    var showAddTimeChoices by remember { mutableStateOf(false) }
    var showOverrideConfirmation by remember { mutableStateOf(false) }
    var overrideConfirmed by remember { mutableStateOf(false) }
    var overrideReason by remember { mutableStateOf("") }

    val headline = if (triggerType == InterventionTriggerType.DAILY_LIMIT_REACHED) {
        "Daily limit reached for $appName"
    } else {
        "Your planned time is complete."
    }

    val subtext = if (triggerType == InterventionTriggerType.DAILY_LIMIT_REACHED) {
        "You have reached today's configured ${intendedMinutes}-minute limit for $appName. Pause and decide mindfully how to proceed."
    } else {
        "You decided to spend $intendedMinutes minutes in $appName. Take a moment before continuing."
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(84.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (triggerType == InterventionTriggerType.DAILY_LIMIT_REACHED) {
                                Icons.Rounded.Shield
                            } else {
                                Icons.Rounded.HourglassBottom
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = headline,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Post-Session Reflection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickReflectionPrompts.forEach { prompt ->
                            FilterChip(
                                selected = reflectionText == prompt,
                                onClick = { reflectionText = prompt },
                                label = { Text(prompt) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = reflectionText,
                        onValueChange = { reflectionText = it },
                        label = { Text("Quick reflection (optional)") },
                        placeholder = { Text("Did you accomplish what you intended?") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onEndSession(reflectionText) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (triggerType == InterventionTriggerType.DAILY_LIMIT_REACHED) {
                                "1. Respect Limit & Step Away"
                            } else {
                                "1. End Session"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    FilledTonalButton(
                        onClick = { onTakeBreak(reflectionText) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Rounded.Coffee, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("2. Take a Mindful Break (5 min)", style = MaterialTheme.typography.titleMedium)
                    }

                    if (onLeaveToHome != null) {
                        OutlinedButton(
                            onClick = onLeaveToHome,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Rounded.Home, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Return to Device Home Screen")
                        }
                    }

                    OutlinedButton(
                        onClick = { showAddTimeChoices = !showAddTimeChoices },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Rounded.AddAlarm, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("3. Add Temporary Extension", style = MaterialTheme.typography.titleMedium)
                    }

                    if (showAddTimeChoices) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(5, 10, 15).forEach { extra ->
                                FilledTonalButton(
                                    onClick = { onAddMoreTime(extra, reflectionText) },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("+${extra}m")
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { showOverrideConfirmation = !showOverrideConfirmation },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.LockOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("4. Request Intentional Override")
                    }

                    if (showOverrideConfirmation) {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Level 4: Deliberate Override Confirmation",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                OutlinedTextField(
                                    value = overrideReason,
                                    onValueChange = { overrideReason = it },
                                    label = { Text("Reason for continuing (optional)") },
                                    placeholder = { Text("e.g., Finishing an important message") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = overrideConfirmed,
                                        onCheckedChange = { overrideConfirmed = it }
                                    )
                                    Text(
                                        text = "I consciously choose to override this reminder and continue.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Button(
                                    onClick = {
                                        onContinueWithOverride(overrideReason, reflectionText)
                                    },
                                    enabled = overrideConfirmed,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("Confirm Intentional Override")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "Android Note: Shadow Alert respects your autonomy and Android security rules. It does not forcibly terminate third-party apps; all choices and reflections are logged privately on-device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}
