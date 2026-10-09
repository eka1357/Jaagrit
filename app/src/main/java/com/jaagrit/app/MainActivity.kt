package com.jaagrit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jaagrit.app.ui.home.HomeScreen
import com.jaagrit.app.ui.monitoring.MonitoringScreen
import com.jaagrit.app.ui.theme.JaagritTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JaagritTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        HomeScreen(
                            onStartDrive = {
                                navController.navigate("monitoring")
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
                }
            }
        }
    }
}