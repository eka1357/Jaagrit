package com.jaagrit.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaagrit.app.data.BaselineStore

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jaagrit.app.data.SettingsStore
import com.jaagrit.app.sms.SmsAvailability
import com.jaagrit.app.sms.SmsNotifier

@Composable
fun HomeScreen(
    onStartDrive: () -> Unit,
    onNavigateToCalibration: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val baselineStore = remember { BaselineStore(context) }
    val baseline by baselineStore.baselineFlow.collectAsState(initial = null)
    val settingsStore = remember { SettingsStore(context) }
    val smsNotifier = remember { SmsNotifier(context, settingsStore) }
    val emergencyContact by settingsStore.emergencyContactFlow.collectAsState(initial = "")

    var showUncalibratedDialog by remember { mutableStateOf(false) }

    var resumeTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasCamera = remember(resumeTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val hasLocation = remember(resumeTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val smsAvailability = remember(resumeTick, emergencyContact) {
        smsNotifier.checkAvailability()
    }

    if (showUncalibratedDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUncalibratedDialog = false },
            title = {
                Text(
                    text = "Calibrate Driver Profile?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "No calibrated driver profile found. Calibrating takes only ~15 seconds and tailors eye closure and blink thresholds to your face, reducing false alerts.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUncalibratedDialog = false
                        onNavigateToCalibration()
                    }
                ) {
                    Text("Calibrate Now (Recommended)")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showUncalibratedDialog = false
                        onStartDrive()
                    }
                ) {
                    Text("Start with Defaults")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Section with History and Settings buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // History Icon
                    IconButton(onClick = onNavigateToHistory) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Text(
                                text = "📜",
                                fontSize = 20.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "जागृत",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Jaagrit",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Settings Icon
                    IconButton(onClick = onNavigateToSettings) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Text(
                                text = "⚙",
                                fontSize = 22.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Offline Driver Alertness Assistant",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Calibration Status Chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (baseline?.isValid == true) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (baseline?.isValid == true) Color(0xFF2E7D32) else Color(0xFFE65100),
                            modifier = Modifier.size(8.dp)
                        ) {}

                        Text(
                            text = if (baseline?.isValid == true) {
                                "Profile Calibrated (Threshold: ${"%.3f".format(baseline!!.threshold)})"
                            } else {
                                "Uncalibrated • Default Profile"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (baseline?.isValid == true) Color(0xFF1B5E20) else Color(0xFFBF360C)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Drive Readiness Checklist Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Drive Readiness Checklist",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Line 1: Camera
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Camera",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (hasCamera) "ready" else "permission needed",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasCamera) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }

                    // Line 2: Emergency SMS (Prompt requirement 3: ready / no SIM / no permission / airplane mode)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Emergency SMS",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = smsAvailability.reason,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (smsAvailability == SmsAvailability.READY) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }

                    // Line 3: Location (GPS)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Location (GPS)",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (hasLocation) "ready" else "permission needed",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasLocation) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }

                    // Line 4: Emergency Contact
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Emergency Contact",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (emergencyContact.isNotBlank()) SettingsStore.maskPhoneNumber(emergencyContact) else "not set in Settings",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (emergencyContact.isNotBlank()) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "⚠️ Emergency SMS requires cellular mobile signal — airplane mode blocks SMS delivery (D6)",
                        fontSize = 11.sp,
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "🔒 100% on-device & offline • Cellular SMS radio only",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Primary Start Drive Button (Checks calibration first)
                Button(
                    onClick = {
                        if (baseline?.isValid == true) {
                            onStartDrive()
                        } else {
                            showUncalibratedDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Start Drive",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Calibrate / Recalibrate Button
                OutlinedButton(
                    onClick = onNavigateToCalibration,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (baseline?.isValid == true) "Recalibrate Profile" else "Calibrate Profile",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Trip History Button
                OutlinedButton(
                    onClick = onNavigateToHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "📜 Trip History & Logs",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
