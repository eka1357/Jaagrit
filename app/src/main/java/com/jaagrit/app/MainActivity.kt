package com.jaagrit.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jaagrit.app.data.SettingsStore
import com.jaagrit.app.ui.calibration.CalibrationScreen
import com.jaagrit.app.ui.history.HistoryScreen
import com.jaagrit.app.ui.home.HomeScreen
import com.jaagrit.app.ui.monitoring.MonitoringScreen
import com.jaagrit.app.ui.settings.SettingsScreen
import com.jaagrit.app.ui.theme.JaagritTheme
import com.jaagrit.app.ui.theme.LocalAppLanguage
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            val settingsStore = remember { SettingsStore(context.applicationContext) }
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

            val localizedContext = remember(context, currentLocale) {
                context.createConfigurationContext(localizedConfiguration)
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
                LocalAppLanguage provides language
            ) {
                JaagritTheme(isHindi = isHindi) {
                    val navController = rememberNavController()
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
                                    navController.navigate("monitoring")
                                },
                                onNavigateToCalibration = {
                                    navController.navigate("calibration")
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                onNavigateToHistory = {
                                    navController.navigate("history")
                                }
                            )
                        }
                        composable("history") {
                            HistoryScreen(
                                onBack = {
                                    navController.popBackStack()
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