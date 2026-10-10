package com.jaagrit.app

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jaagrit.app.data.BaselineStore
import com.jaagrit.app.data.SettingsStore
import com.jaagrit.app.ui.calibration.CalibrationScreen
import com.jaagrit.app.ui.dashboard.DashboardScreen
import com.jaagrit.app.ui.history.HistoryScreen
import com.jaagrit.app.ui.home.HomeScreen
import com.jaagrit.app.ui.monitoring.MonitoringScreen
import com.jaagrit.app.ui.settings.SettingsScreen
import com.jaagrit.app.ui.theme.JaagritTheme
import com.jaagrit.app.ui.theme.LocalAppLanguage
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Custom ContextWrapper that delegates to the underlying Activity (baseContext)
 * while overriding getResources() to return the localized resources.
 * This preserves the Activity reference chain (findActivity(), baseContext traversal)
 * and allows ActivityResultRegistryOwner lookup without breaking Compose.
 */
private class LocalizedContextWrapper(
    base: Context,
    private val localizedConfiguration: Configuration
) : ContextWrapper(base) {
    private val localizedResources: Resources by lazy {
        base.createConfigurationContext(localizedConfiguration).resources
    }

    override fun getResources(): Resources {
        return localizedResources
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val coroutineScope = rememberCoroutineScope()
            val settingsStore = remember { SettingsStore(applicationContext) }
            val language by settingsStore.appLanguageFlow.collectAsState(initial = SettingsStore.DEFAULT_LANGUAGE)
            val isHindi = language != "en"

            val currentLocale = remember(isHindi) {
                if (isHindi) Locale.forLanguageTag("hi-IN") else Locale.US
            }

            val currentConfig = LocalConfiguration.current
            val localizedConfiguration = remember(currentConfig, currentLocale) {
                Configuration(currentConfig).apply {
                    setLocale(currentLocale)
                    setLayoutDirection(currentLocale)
                }
            }

            val localizedContext = remember(this@MainActivity, localizedConfiguration) {
                LocalizedContextWrapper(this@MainActivity, localizedConfiguration)
            }

            SideEffect {
                Locale.setDefault(currentLocale)
                val config = Configuration(resources.configuration).apply {
                    setLocale(currentLocale)
                    setLayoutDirection(currentLocale)
                }
                @Suppress("DEPRECATION")
                resources.updateConfiguration(config, resources.displayMetrics)
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfiguration,
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides this@MainActivity,
                LocalAppLanguage provides language
            ) {
                JaagritTheme(isHindi = isHindi) {
                    val navController = rememberNavController()
                    val baselineStore = remember { BaselineStore(applicationContext) }

                    fun startDriveChecked() {
                        coroutineScope.launch {
                            if (baselineStore.hasSavedBaselineWithCurrentSchema()) {
                                navController.navigate("monitoring")
                            } else {
                                navController.navigate("calibration")
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(
                                currentLanguage = language,
                                onLanguageChange = { newLang ->
                                    coroutineScope.launch {
                                        settingsStore.setAppLanguage(newLang)
                                    }
                                },
                                onStartDrive = {
                                    startDriveChecked()
                                },
                                onNavigateToCalibration = {
                                    navController.navigate("calibration")
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                onNavigateToHistory = {
                                    navController.navigate("history")
                                },
                                onNavigateToDashboard = {
                                    navController.navigate("dashboard")
                                }
                            )
                        }
                        composable("history") {
                            HistoryScreen(
                                onBack = {
                                    navController.popBackStack()
                                },
                                onOpenDashboard = { tripId ->
                                    navController.navigate("dashboard/$tripId")
                                }
                            )
                        }
                        composable("calibration") {
                            CalibrationScreen(
                                onCalibrationFinished = {
                                    navController.navigate("monitoring") {
                                        popUpTo("home")
                                    }
                                },
                                onCancel = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("monitoring") {
                            MonitoringScreen(
                                onEndDrive = {
                                    navController.navigate("dashboard") {
                                        popUpTo("home")
                                    }
                                },
                                onNavigateToCalibration = {
                                    navController.navigate("calibration")
                                }
                            )
                        }
                        composable("dashboard") {
                            DashboardScreen(
                                tripId = null,
                                onBack = {
                                    navController.popBackStack()
                                },
                                onStartDrive = {
                                    startDriveChecked()
                                },
                                onStopDrive = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable(
                            route = "dashboard/{tripId}",
                            arguments = listOf(navArgument("tripId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val tripId = backStackEntry.arguments?.getLong("tripId")
                            DashboardScreen(
                                tripId = tripId,
                                onBack = {
                                    navController.popBackStack()
                                },
                                onStartDrive = {
                                    startDriveChecked()
                                },
                                onStopDrive = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                currentLanguage = language,
                                onLanguageChange = { newLang ->
                                    coroutineScope.launch {
                                        settingsStore.setAppLanguage(newLang)
                                    }
                                },
                                onNavigateToCalibration = {
                                    navController.navigate("calibration")
                                },
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}