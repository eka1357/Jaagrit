package com.jaagrit.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.jaagrit.app.data.BaselineStore
import com.jaagrit.app.data.SettingsStore
import com.jaagrit.app.engine.Baseline
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings and Privacy Screen (PRODUCT.md, CAL-4, LAD-4, D6, D9).
 * Displays active baseline metrics with a Recalibrate button,
 * emergency contact configuration for L5 SMS, and demo flags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToCalibration: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baselineStore = remember { BaselineStore(context) }
    val settingsStore = remember { SettingsStore(context) }

    val baseline by baselineStore.baselineFlow.collectAsState(initial = null)
    val savedDriverName by settingsStore.driverNameFlow.collectAsState(initial = SettingsStore.DEFAULT_DRIVER_NAME)
    val savedEmergencyContact by settingsStore.emergencyContactFlow.collectAsState(initial = "")
    val demoTimers by settingsStore.demoTimersFlow.collectAsState(initial = false)
    val quickCalibration by settingsStore.quickCalibrationFlow.collectAsState(initial = false)

    var inputDriverName by remember { mutableStateOf("") }
    var inputEmergencyContact by remember { mutableStateOf("") }
    var saveSuccessMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(savedDriverName, savedEmergencyContact) {
        inputDriverName = savedDriverName
        inputEmergencyContact = savedEmergencyContact
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Calibration",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(
                            text = "‹",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Driver Baseline Card (CAL-4)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Driver Baseline Profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (baseline?.isValid == true) Color(0xFF2E7D32) else Color(0xFFC62828)
                        ) {
                            Text(
                                text = if (baseline?.isValid == true) "CALIBRATED" else "UNCALIBRATED",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (baseline != null && baseline?.isValid == true) {
                        val b = baseline!!
                        val formattedDate = remember(b.calibratedAtMs) {
                            if (b.calibratedAtMs > 0) {
                                SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(b.calibratedAtMs))
                            } else "Default"
                        }

                        Text(
                            text = "Last calibrated: $formattedDate",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BaselineRow("Personal Threshold", "%.3f".format(b.threshold), isBold = true)
                            BaselineRow("Open Eye EAR (median)", "%.3f".format(b.openEar))
                            BaselineRow("Closed Eye EAR (median)", "%.3f".format(b.closedEar))
                            BaselineRow("Separation Gap", "%.3f".format(b.earGap))
                            BaselineRow("Baseline Blink Rate", "%.1f / min".format(b.blinkRate))
                            BaselineRow("Reaction Latency", "${b.responseLatencyMs} ms")
                        }
                    } else {
                        Text(
                            text = "No personalized calibration saved. The app is currently using universal default thresholds. Calibrate your face for maximum precision and to adapt to your glasses.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Recalibrate Button (CAL-4)
                    Button(
                        onClick = onNavigateToCalibration,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = if (baseline?.isValid == true) "Recalibrate Face Profile" else "Start Calibration",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (baseline != null) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    baselineStore.clearBaseline()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Reset to Default Baseline",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Emergency Contact & Profile Card (LAD-4, D6)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Emergency Contact (Level 5 Alert)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "If you become unresponsive during driving (after L3 alarm and L4 family voice), Jaagrit sends an emergency SMS with your last known GPS coordinates to this phone number.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = inputDriverName,
                        onValueChange = {
                            inputDriverName = it
                            saveSuccessMessage = null
                        },
                        label = { Text("Driver Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = inputEmergencyContact,
                        onValueChange = {
                            inputEmergencyContact = it
                            saveSuccessMessage = null
                        },
                        label = { Text("Emergency Phone Number") },
                        placeholder = { Text("+91 XXXXX XXXXX") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (savedEmergencyContact.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active Masked Number:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = SettingsStore.maskPhoneNumber(savedEmergencyContact),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (saveSuccessMessage != null) {
                        Text(
                            text = saveSuccessMessage!!,
                            color = Color(0xFF2E7D32),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                settingsStore.saveDriverName(inputDriverName)
                                settingsStore.saveEmergencyContact(inputEmergencyContact)
                                saveSuccessMessage = "Emergency contact saved locally."
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Emergency Contact", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Developer & Demo Flags Card (D9, LAD-7)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Demo & Testing Flags",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DEMO_TIMERS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Shortens escalation: L4 fires in 5s (vs 10s), L5 fires in 8s (vs 20s).",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = demoTimers,
                            onCheckedChange = { isChecked ->
                                scope.launch {
                                    settingsStore.setDemoTimers(isChecked)
                                }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "QUICK_CALIBRATION",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Fast demo calibration: open eyes 5s, closed eyes 2s.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = quickCalibration,
                            onCheckedChange = { isChecked ->
                                scope.launch {
                                    settingsStore.setQuickCalibration(isChecked)
                                }
                            }
                        )
                    }
                }
            }

            // Privacy & Architecture Policy Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Privacy Guarantee",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "• 100% Offline: The app contains no INTERNET permission.\n" +
                                "• Zero Media Retention: Camera frames and mic audio are never saved to storage.\n" +
                                "• Numerical Telemetry Only: Only mathematical metrics (EAR, pitch, timestamps) are computed in memory.\n" +
                                "• Local Persistence: Driver baseline is saved securely in local Android DataStore.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BaselineRow(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            color = if (isBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
