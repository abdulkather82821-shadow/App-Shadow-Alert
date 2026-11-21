package com.shadowalert.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shadowalert.app.domain.model.InterventionTriggerType
import com.shadowalert.app.ui.ShadowAlertViewModel
import com.shadowalert.app.ui.screens.AnalyticsDashboardScreen
import com.shadowalert.app.ui.screens.AppDetailsScreen
import com.shadowalert.app.ui.screens.HomeDashboardScreen
import com.shadowalert.app.ui.screens.IntentionSessionScreen
import com.shadowalert.app.ui.screens.MonitoredAppsScreen
import com.shadowalert.app.ui.screens.OnboardingScreen
import com.shadowalert.app.ui.screens.PauseInterventionScreen
import com.shadowalert.app.ui.screens.PermissionSetupScreen
import com.shadowalert.app.ui.screens.SettingsScreen
import com.shadowalert.app.ui.screens.SplashScreen

object ShadowAlertRoutes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val PERMISSIONS = "permissions?fromOnboarding={fromOnboarding}"
    const val HOME = "home"
    const val APPS = "apps"
    const val SESSION = "session?packageName={packageName}"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings"
    const val APP_DETAILS = "app_details/{packageName}"
    const val PAUSE = "pause?sessionId={sessionId}&packageName={packageName}&appName={appName}&intendedMinutes={intendedMinutes}&triggerType={triggerType}"

    fun permissions(fromOnboarding: Boolean = false): String =
        "permissions?fromOnboarding=$fromOnboarding"

    fun session(packageName: String? = null): String =
        if (packageName.isNullOrBlank()) "session"
        else "session?packageName=${Uri.encode(packageName)}"

    fun appDetails(packageName: String): String =
        "app_details/${Uri.encode(packageName)}"

    fun pause(
        sessionId: Long,
        packageName: String,
        appName: String,
        intendedMinutes: Int,
        triggerType: InterventionTriggerType = InterventionTriggerType.INTENTION_COMPLETE
    ): String {
        val safeAppName = Uri.encode(appName.ifBlank { "this app" })
        val safePkg = Uri.encode(packageName)
        return "pause?sessionId=$sessionId&packageName=$safePkg&appName=$safeAppName&intendedMinutes=$intendedMinutes&triggerType=${triggerType.name}"
    }
}

private data class BottomTab(
    val routePrefix: String,
    val navTarget: String,
    val label: String,
    val icon: ImageVector
)

private val bottomTabs = listOf(
    BottomTab("home", ShadowAlertRoutes.HOME, "Home", Icons.Rounded.Home),
    BottomTab("apps", ShadowAlertRoutes.APPS, "Apps", Icons.Rounded.Apps),
    BottomTab("session", "session", "Intent", Icons.Rounded.PlayCircle),
    BottomTab("analytics", ShadowAlertRoutes.ANALYTICS, "Insights", Icons.Rounded.BarChart),
    BottomTab("settings", ShadowAlertRoutes.SETTINGS, "Settings", Icons.Rounded.Settings)
)

@Composable
fun ShadowAlertAppRoot(viewModel: ShadowAlertViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val allAppLimits by viewModel.allAppLimits.collectAsStateWithLifecycle()
    val enabledAppLimits by viewModel.enabledAppLimits.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val analyticsSummary by viewModel.analyticsSummary.collectAsStateWithLifecycle()
    val monitoringState by viewModel.monitoringState.collectAsStateWithLifecycle()
    val permissionStatus by viewModel.permissionStatus.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()
    val duplicatePrompt by viewModel.duplicateSessionPrompt.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.uiMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.pauseTriggerEvents.collect { event ->
            navController.navigate(
                ShadowAlertRoutes.pause(
                    sessionId = event.sessionId,
                    packageName = event.packageName,
                    appName = event.appName,
                    intendedMinutes = event.intendedMinutes,
                    triggerType = event.triggerType
                )
            ) {
                launchSingleTop = true
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != null &&
        currentRoute != ShadowAlertRoutes.SPLASH &&
        currentRoute != ShadowAlertRoutes.ONBOARDING &&
        !currentRoute.startsWith("pause") &&
        !currentRoute.startsWith("permissions")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        val selected = navBackStackEntry?.destination?.hierarchy?.any {
                            it.route?.startsWith(tab.routePrefix) == true
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.navTarget) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = (tab.routePrefix != "session")
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ShadowAlertRoutes.SPLASH,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ShadowAlertRoutes.SPLASH) {
                SplashScreen(
                    onboardingCompleted = preferences.onboardingCompleted,
                    onNavigateNext = { completed ->
                        val target = if (completed) {
                            ShadowAlertRoutes.HOME
                        } else {
                            ShadowAlertRoutes.ONBOARDING
                        }
                        navController.navigate(target) {
                            popUpTo(ShadowAlertRoutes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            composable(ShadowAlertRoutes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        viewModel.completeOnboarding()
                        navController.navigate(ShadowAlertRoutes.permissions(fromOnboarding = true)) {
                            popUpTo(ShadowAlertRoutes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = ShadowAlertRoutes.PERMISSIONS,
                arguments = listOf(
                    navArgument("fromOnboarding") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val fromOnboarding = backStackEntry.arguments?.getBoolean("fromOnboarding") ?: false
                PermissionSetupScreen(
                    permissionStatus = permissionStatus,
                    onRefreshPermissions = { viewModel.refreshPermissionsAndSyncUsage() },
                    onContinueToHome = {
                        if (fromOnboarding) {
                            navController.navigate(ShadowAlertRoutes.HOME) {
                                popUpTo(0) { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    showContinueButton = true
                )
            }

            composable(ShadowAlertRoutes.HOME) {
                HomeDashboardScreen(
                    userName = preferences.userName,
                    dailyTargetMinutes = preferences.totalDailyTargetMinutes,
                    enabledApps = enabledAppLimits,
                    activeSession = activeSession,
                    monitoringState = monitoringState,
                    permissionStatus = permissionStatus,
                    onQuickStartSession = {
                        navController.navigate(ShadowAlertRoutes.session())
                    },
                    onOpenMonitoredApps = {
                        navController.navigate(ShadowAlertRoutes.APPS)
                    },
                    onOpenPermissions = {
                        navController.navigate(ShadowAlertRoutes.permissions(fromOnboarding = false))
                    },
                    onOpenAppDetails = { pkg ->
                        navController.navigate(ShadowAlertRoutes.appDetails(pkg))
                    },
                    onOpenTargetApp = { pkg ->
                        viewModel.openTargetApp(pkg)
                    },
                    onOpenPauseReview = { id, appName, intended ->
                        val pkg = activeSession?.packageName.orEmpty()
                        navController.navigate(
                            ShadowAlertRoutes.pause(id, pkg, appName, intended)
                        )
                    },
                    onEndActiveSession = { id ->
                        viewModel.endSession(id, early = true)
                    }
                )
            }

            composable(ShadowAlertRoutes.APPS) {
                MonitoredAppsScreen(
                    installedApps = installedApps,
                    appLimits = allAppLimits,
                    isLoading = isLoadingApps,
                    onToggleMonitoring = { app, enabled ->
                        viewModel.toggleAppMonitoring(app, enabled)
                    },
                    onUpdateDailyLimit = { app, limit ->
                        viewModel.updateAppDailyLimit(app, limit)
                    },
                    onUpdateCategory = { pkg, cat ->
                        viewModel.updateAppCategory(pkg, cat)
                    },
                    onOpenAppDetails = { pkg ->
                        navController.navigate(ShadowAlertRoutes.appDetails(pkg))
                    }
                )
            }

            composable(
                route = ShadowAlertRoutes.APP_DETAILS,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType })
            ) { backStackEntry ->
                val pkg = Uri.decode(backStackEntry.arguments?.getString("packageName").orEmpty())
                val appLimit by viewModel.observeAppLimit(pkg).collectAsStateWithLifecycle(initialValue = null)
                val sevenDayUsage by viewModel.observeSevenDayUsageForPackage(pkg)
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val sessions by viewModel.observeSessionsForPackage(pkg)
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val installedInfo = installedApps.firstOrNull { it.packageName == pkg }

                AppDetailsScreen(
                    packageName = pkg,
                    installedInfo = installedInfo,
                    appLimit = appLimit,
                    sevenDayUsage = sevenDayUsage,
                    recentSessions = sessions,
                    onBack = { navController.popBackStack() },
                    onEditDailyLimit = { info, limit ->
                        viewModel.updateAppDailyLimit(info, limit)
                    },
                    onUpdateCategory = { p, cat ->
                        viewModel.updateAppCategory(p, cat)
                    },
                    onToggleMonitoring = { info, enabled ->
                        viewModel.toggleAppMonitoring(info, enabled)
                    },
                    onStartIntentionSession = { p ->
                        navController.navigate(ShadowAlertRoutes.session(p))
                    }
                )
            }

            composable(
                route = ShadowAlertRoutes.SESSION,
                arguments = listOf(
                    navArgument("packageName") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val rawPkg = backStackEntry.arguments?.getString("packageName")
                val pkg = rawPkg?.let { Uri.decode(it) }
                IntentionSessionScreen(
                    preselectedPackageName = pkg,
                    installedApps = installedApps,
                    enabledAppLimits = enabledAppLimits,
                    activeSession = activeSession,
                    monitoringState = monitoringState,
                    permissionStatus = permissionStatus,
                    duplicateSessionPrompt = duplicatePrompt,
                    onStartSession = { packageName, appName, category, minutes, purpose, launchApp ->
                        viewModel.startIntentionSession(
                            packageName = packageName,
                            appName = appName,
                            category = category,
                            intendedMinutes = minutes,
                            purpose = purpose,
                            launchTargetApp = launchApp
                        )
                    },
                    onOpenTargetApp = { p -> viewModel.openTargetApp(p) },
                    onEndSession = { id -> viewModel.endSession(id, early = true) },
                    onOpenPauseScreen = { id, appName, intended ->
                        val activePkg = activeSession?.packageName.orEmpty()
                        navController.navigate(
                            ShadowAlertRoutes.pause(id, activePkg, appName, intended)
                        )
                    },
                    onOpenPermissions = {
                        navController.navigate(ShadowAlertRoutes.permissions(fromOnboarding = false))
                    },
                    onDismissDuplicatePrompt = { viewModel.dismissDuplicateSessionPrompt() },
                    onEndDuplicateAndDismiss = { viewModel.endExistingActiveSessionAndDismissPrompt() }
                )
            }

            composable(
                route = ShadowAlertRoutes.PAUSE,
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("packageName") { type = NavType.StringType; defaultValue = "" },
                    navArgument("appName") { type = NavType.StringType; defaultValue = "this app" },
                    navArgument("intendedMinutes") { type = NavType.IntType; defaultValue = 15 },
                    navArgument("triggerType") {
                        type = NavType.StringType
                        defaultValue = InterventionTriggerType.INTENTION_COMPLETE.name
                    }
                )
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: -1L
                val pkg = Uri.decode(backStackEntry.arguments?.getString("packageName").orEmpty())
                val appName = Uri.decode(backStackEntry.arguments?.getString("appName").orEmpty())
                    .ifBlank { "this application" }
                val intendedMinutes = backStackEntry.arguments?.getInt("intendedMinutes") ?: 15
                val typeName = backStackEntry.arguments?.getString("triggerType")
                val triggerType = InterventionTriggerType.entries.firstOrNull { it.name == typeName }
                    ?: InterventionTriggerType.INTENTION_COMPLETE

                PauseInterventionScreen(
                    appName = appName,
                    intendedMinutes = intendedMinutes,
                    triggerType = triggerType,
                    onEndSession = { reflection ->
                        if (triggerType == InterventionTriggerType.INTENTION_COMPLETE && sessionId > 0L) {
                            viewModel.endSession(sessionId, early = false, reflectionText = reflection)
                        }
                        navController.popBackStack()
                    },
                    onTakeBreak = { reflection ->
                        viewModel.takeBreakFromSession(sessionId, 5, reflectionText = reflection)
                        navController.popBackStack()
                    },
                    onAddMoreTime = { additionalMinutes, reflection ->
                        if (triggerType == InterventionTriggerType.DAILY_LIMIT_REACHED && pkg.isNotBlank()) {
                            viewModel.grantDailyExtraMinutes(pkg, additionalMinutes, reflection)
                        } else if (sessionId > 0L) {
                            viewModel.addMoreTimeToSession(sessionId, additionalMinutes, reflectionText = reflection)
                        }
                        navController.popBackStack()
                    },
                    onContinueWithOverride = { reason, reflection ->
                        if (triggerType == InterventionTriggerType.DAILY_LIMIT_REACHED && pkg.isNotBlank()) {
                            viewModel.grantDailyOverride(pkg, appName, reason, reflection)
                        } else if (sessionId > 0L) {
                            viewModel.continueWithOverride(sessionId, reason, reflection)
                        }
                        navController.popBackStack()
                    },
                    onLeaveToHome = {
                        viewModel.returnToDeviceHome()
                        navController.popBackStack()
                    }
                )
            }

            composable(ShadowAlertRoutes.ANALYTICS) {
                AnalyticsDashboardScreen(summary = analyticsSummary)
            }

            composable(ShadowAlertRoutes.SETTINGS) {
                SettingsScreen(
                    preferences = preferences,
                    onSetUserName = { viewModel.setUserName(it) },
                    onSetThemeMode = { viewModel.setThemeMode(it) },
                    onSetDailyTargetMinutes = { viewModel.setDailyTargetMinutes(it) },
                    onUpdateWarningSettings = { w5, w2, limit, custom ->
                        viewModel.updateWarningSettings(w5, w2, limit, custom)
                    },
                    onSetMonitoringEnabled = { viewModel.setMonitoringEnabled(it) },
                    onSetPollingIntervalSeconds = { viewModel.setPollingIntervalSeconds(it) },
                    onSetDailyResetHour = { viewModel.setDailyResetHour(it) },
                    onSetInterventionMode = { viewModel.setInterventionMode(it) },
                    onOpenPermissions = {
                        navController.navigate(ShadowAlertRoutes.permissions(fromOnboarding = false))
                    },
                    onExportSummary = { viewModel.exportUsageSummary() },
                    onDeleteAllData = { viewModel.deleteAllLocalData() }
                )
            }
        }
    }
}
