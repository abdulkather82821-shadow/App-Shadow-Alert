package com.shadowalert.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shadowalert.app.ui.ShadowAlertViewModel
import com.shadowalert.app.ui.navigation.ShadowAlertAppRoot
import com.shadowalert.app.ui.theme.ShadowAlertTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ShadowAlertViewModel by viewModels {
        ShadowAlertViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by viewModel.preferences.collectAsStateWithLifecycle()
            ShadowAlertTheme(themeMode = prefs.themeMode) {
                ShadowAlertAppRoot(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionsAndSyncUsage()
    }
}
