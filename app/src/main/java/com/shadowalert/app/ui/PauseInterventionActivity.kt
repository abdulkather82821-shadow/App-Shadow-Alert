package com.shadowalert.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shadowalert.app.domain.model.InterventionTriggerType
import com.shadowalert.app.ui.screens.PauseInterventionScreen
import com.shadowalert.app.ui.theme.ShadowAlertTheme

class PauseInterventionActivity : ComponentActivity() {

    private val viewModel: ShadowAlertViewModel by viewModels {
        ShadowAlertViewModel.factory(application)
    }

    private var sessionIdState by mutableLongStateOf(-1L)
    private var packageNameState by mutableStateOf("")
    private var appNameState by mutableStateOf("this application")
    private var intendedMinutesState by mutableIntStateOf(15)
    private var triggerTypeState by mutableStateOf(InterventionTriggerType.INTENTION_COMPLETE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readExtras(intent)

        setContent {
            val prefs by viewModel.preferences.collectAsStateWithLifecycle()
            ShadowAlertTheme(themeMode = prefs.themeMode) {
                PauseInterventionScreen(
                    appName = appNameState,
                    intendedMinutes = intendedMinutesState,
                    triggerType = triggerTypeState,
                    onEndSession = { reflection ->
                        if (triggerTypeState == InterventionTriggerType.INTENTION_COMPLETE && sessionIdState > 0L) {
                            viewModel.endSession(sessionIdState, early = false, reflectionText = reflection)
                        } else {
                            viewModel.returnToDeviceHome()
                        }
                        finish()
                    },
                    onTakeBreak = { reflection ->
                        viewModel.takeBreakFromSession(sessionIdState, 5, reflectionText = reflection)
                        viewModel.returnToDeviceHome()
                        finish()
                    },
                    onAddMoreTime = { additionalMinutes, reflection ->
                        if (triggerTypeState == InterventionTriggerType.DAILY_LIMIT_REACHED && packageNameState.isNotBlank()) {
                            viewModel.grantDailyExtraMinutes(packageNameState, additionalMinutes, reflection)
                        } else if (sessionIdState > 0L) {
                            viewModel.addMoreTimeToSession(sessionIdState, additionalMinutes, reflectionText = reflection)
                        }
                        finish()
                    },
                    onContinueWithOverride = { reason, reflection ->
                        if (triggerTypeState == InterventionTriggerType.DAILY_LIMIT_REACHED && packageNameState.isNotBlank()) {
                            viewModel.grantDailyOverride(packageNameState, appNameState, reason, reflection)
                        } else if (sessionIdState > 0L) {
                            viewModel.continueWithOverride(sessionIdState, reason, reflection)
                        }
                        finish()
                    },
                    onLeaveToHome = {
                        viewModel.returnToDeviceHome()
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readExtras(intent)
    }

    private fun readExtras(intent: Intent?) {
        sessionIdState = intent?.getLongExtra(EXTRA_SESSION_ID, -1L) ?: -1L
        packageNameState = intent?.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        appNameState = intent?.getStringExtra(EXTRA_APP_NAME)?.ifBlank { "this application" } ?: "this application"
        intendedMinutesState = intent?.getIntExtra(EXTRA_INTENDED_MINUTES, 15) ?: 15
        val typeName = intent?.getStringExtra(EXTRA_TRIGGER_TYPE)
        triggerTypeState = InterventionTriggerType.entries.firstOrNull { it.name == typeName }
            ?: InterventionTriggerType.INTENTION_COMPLETE
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_INTENDED_MINUTES = "extra_intended_minutes"
        const val EXTRA_TRIGGER_TYPE = "extra_trigger_type"

        fun createIntent(
            context: Context,
            sessionId: Long,
            packageName: String,
            appName: String,
            intendedMinutes: Int,
            triggerType: InterventionTriggerType = InterventionTriggerType.INTENTION_COMPLETE
        ): Intent {
            return Intent(context, PauseInterventionActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_INTENDED_MINUTES, intendedMinutes)
                putExtra(EXTRA_TRIGGER_TYPE, triggerType.name)
            }
        }
    }
}
