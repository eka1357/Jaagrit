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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaagrit.app.R
import com.jaagrit.app.data.BaselineStore
import com.jaagrit.app.data.SettingsStore
import com.jaagrit.app.ui.components.LanguageSwitch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
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
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Language Preference Card (Rule 2)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.settings_language_desc),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LanguageSwitch(
                        currentLanguage = currentLanguage,
                        onLanguageSelected = onLanguageChange
                    )
                }
            }

            // Driver Baseline Card (CAL-4)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_profile_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (baseline?.isValid == true) Color(0xFF2E7D32) else Color(0xFFC62828)
                        ) {
                            Text(
                                text = stringResource(if (baseline?.isValid == true) R.string.settings_badge_calibrated else R.string.settings_badge_uncalibrated),
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
                                SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(b.calibratedAtMs))
                            } else "Default"
                        }

                        Text(
                            text = stringResource(R.string.settings_last_calibrated, formattedDate),
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
                            BaselineRow(stringResource(R.string.calib_metric_threshold), String.format(Locale.US, "%.3f", b.threshold), isBold = true)
                            BaselineRow(stringResource(R.string.calib_metric_open_ear), String.format(Locale.US, "%.3f", b.openEar))
                            BaselineRow(stringResource(R.string.calib_metric_closed_ear), String.format(Locale.US, "%.3f", b.closedEar))
                            BaselineRow(stringResource(R.string.calib_metric_gap), String.format(Locale.US, "%.3f", b.earGap))
                            BaselineRow(stringResource(R.string.calib_metric_blink_rate), String.format(Locale.US, "%.1f / min", b.blinkRate))
                            BaselineRow(stringResource(R.string.calib_metric_reaction), "${b.responseLatencyMs} ms")
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.settings_uncalibrated_desc),
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
                            text = stringResource(if (baseline?.isValid == true) R.string.settings_btn_recalibrate else R.string.settings_btn_start_calib),
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
                                text = stringResource(R.string.settings_btn_reset),
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
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_contact_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = stringResource(R.string.settings_contact_desc),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = inputDriverName,
                        onValueChange = {
                            inputDriverName = it
                            saveSuccessMessage = null
                        },
                        label = { Text(stringResource(R.string.settings_driver_name_label)) },
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
                        label = { Text(stringResource(R.string.settings_phone_label)) },
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
                                text = stringResource(R.string.settings_masked_label),
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

                    val savedSuccessStr = stringResource(R.string.settings_contact_saved)
                    Button(
                        onClick = {
                            scope.launch {
                                settingsStore.saveDriverName(inputDriverName)
                                settingsStore.saveEmergencyContact(inputEmergencyContact)
                                saveSuccessMessage = savedSuccessStr
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_btn_save_contact),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Developer & Demo Flags Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_demo_flags),
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
                                text = stringResource(R.string.settings_demo_timers_desc),
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
                                text = stringResource(R.string.settings_quick_calib_desc),
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
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_privacy_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = stringResource(R.string.settings_privacy_bullets),
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
