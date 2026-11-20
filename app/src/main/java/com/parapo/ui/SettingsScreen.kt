package com.parapo.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.parapo.auth.AuthGate
import com.parapo.storage.SettingsStore
import com.parapo.ui.theme.ActionBlue
import com.parapo.ui.theme.AlertOrange
import com.parapo.ui.theme.InkNavy
import com.parapo.ui.theme.MetaNavy
import com.parapo.ui.theme.ScreenBg
import com.parapo.ui.theme.SuccessGreen
import com.parapo.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/**
 * S9 Settings (FR-16, FR-17, Build Guide 6.3 & 6.6).
 * Persists tunable alert distance, safety margin (default 500 m), escalation delays,
 * battery exemption shortcut to S10, optional Emergency SMS, and Change PIN.
 */
@Composable
fun SettingsScreen(onOpenPermissions: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val settings by SettingsStore.settings.collectAsState()

    var refreshTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val batteryExempt = remember(refreshTick) { PermissionHelper.isIgnoringBatteryOptimizations(context) }

    var showSmsDialog by remember { mutableStateOf(false) }
    var smsInput by remember(settings.smsNumber) { mutableStateOf(settings.smsNumber.orEmpty()) }
    var showChangePin by remember { mutableStateOf(false) }
    var pinFeedback by remember { mutableStateOf<String?>(null) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showSmsDialog = true
        } else {
            SettingsStore.update(appContext) { it.copy(smsEnabled = false) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                color = InkNavy,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pinFeedback != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SuccessGreen.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = pinFeedback!!,
                        color = InkNavy,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Alert Distance (default 3.0 km)
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Alert distance", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkNavy)
                    Text(
                        text = String.format(Locale.US, "%.1f km", settings.alertDistanceM / 1000.0),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ActionBlue
                    )
                }
                Slider(
                    value = settings.alertDistanceM.toFloat(),
                    onValueChange = { v ->
                        val snapped = (v / 250f).roundToInt() * 250
                        SettingsStore.update(appContext) { it.copy(alertDistanceM = snapped) }
                    },
                    valueRange = 1000f..5000f,
                    colors = SliderDefaults.colors(thumbColor = ActionBlue, activeTrackColor = ActionBlue)
                )
                Text(
                    text = "Maximum distance before your stop to trigger vibration (vibration is always on).",
                    fontSize = 13.sp,
                    color = MetaNavy
                )
            }

            // Safety Margin (default 500 m per Build Guide 6.6)
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Safety margin", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkNavy)
                    Text(
                        text = "${settings.safetyMarginM} m",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ActionBlue
                    )
                }
                Slider(
                    value = settings.safetyMarginM.toFloat(),
                    onValueChange = { v ->
                        val snapped = (v / 100f).roundToInt() * 100
                        SettingsStore.update(appContext) { it.copy(safetyMarginM = snapped) }
                    },
                    valueRange = 200f..1000f,
                    colors = SliderDefaults.colors(thumbColor = ActionBlue, activeTrackColor = ActionBlue)
                )
                Text(
                    text = "Extra distance added when GPS is lost and cell/dead reckoning is active.",
                    fontSize = 13.sp,
                    color = MetaNavy
                )
            }

            // Escalation toggles: Sound & Fullscreen
            SettingsCard {
                ToggleRow(
                    title = "Sound alert",
                    subtitle = "Plays loud alarm ${settings.soundAfterS} s after vibration starts.",
                    checked = settings.soundEnabled,
                    onCheckedChange = { on ->
                        SettingsStore.update(appContext) { it.copy(soundEnabled = on) }
                    }
                )
                if (settings.soundEnabled) {
                    Slider(
                        value = settings.soundAfterS.toFloat(),
                        onValueChange = { v ->
                            SettingsStore.update(appContext) { it.copy(soundAfterS = v.roundToInt()) }
                        },
                        valueRange = 5f..30f,
                        colors = SliderDefaults.colors(thumbColor = ActionBlue, activeTrackColor = ActionBlue)
                    )
                } else {
                    Text(
                        text = "Warning: Turning off sound makes it easier to sleep through your stop.",
                        fontSize = 13.sp,
                        color = AlertOrange,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))

                ToggleRow(
                    title = "Fullscreen alert",
                    subtitle = "Shows wake-up screen over lock screen after ${settings.fullScreenAfterS} s.",
                    checked = settings.fullScreenEnabled,
                    onCheckedChange = { on ->
                        SettingsStore.update(appContext) { it.copy(fullScreenEnabled = on) }
                    }
                )
                if (settings.fullScreenEnabled) {
                    Slider(
                        value = settings.fullScreenAfterS.toFloat(),
                        onValueChange = { v ->
                            SettingsStore.update(appContext) { it.copy(fullScreenAfterS = v.roundToInt()) }
                        },
                        valueRange = 10f..60f,
                        colors = SliderDefaults.colors(thumbColor = ActionBlue, activeTrackColor = ActionBlue)
                    )
                } else {
                    Text(
                        text = "Warning: Without full-screen alert, the wake-up screen won't appear over your lock screen.",
                        fontSize = 13.sp,
                        color = AlertOrange,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Battery exemption & Emergency SMS
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenPermissions)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Battery exemption & permissions", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkNavy)
                        Text("Opens the S10 guided setup wizard.", fontSize = 13.sp, color = MetaNavy)
                    }
                    Text(
                        text = if (batteryExempt) "On" else "Off",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (batteryExempt) SuccessGreen else AlertOrange
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MetaNavy)
                }

                Spacer(Modifier.height(8.dp))

                ToggleRow(
                    title = "Emergency SMS",
                    subtitle = if (settings.smsEnabled && !settings.smsNumber.isNullOrBlank()) {
                        "Sends alert SMS to ${settings.smsNumber}"
                    } else {
                        "Optional: notify a contact when an alert triggers."
                    },
                    checked = settings.smsEnabled,
                    onCheckedChange = { wantOn ->
                        if (wantOn) {
                            if (PermissionHelper.hasSmsPermission(context)) {
                                showSmsDialog = true
                            } else {
                                smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                            }
                        } else {
                            SettingsStore.update(appContext) { it.copy(smsEnabled = false) }
                        }
                    }
                )
            }

            // Change PIN
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showChangePin = true }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Change PIN", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkNavy)
                        Text("Update your 4-digit local login PIN.", fontSize = 13.sp, color = MetaNavy)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MetaNavy)
                }
            }
        }
    }

    if (showSmsDialog) {
        AlertDialog(
            onDismissRequest = { showSmsDialog = false },
            title = { Text("Emergency SMS Contact") },
            text = {
                OutlinedTextField(
                    value = smsInput,
                    onValueChange = { smsInput = it },
                    singleLine = true,
                    label = { Text("Phone number (e.g. 0917...)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = smsInput.trim()
                        if (trimmed.isNotEmpty()) {
                            SettingsStore.update(appContext) {
                                it.copy(smsEnabled = true, smsNumber = trimmed)
                            }
                        }
                        showSmsDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSmsDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showChangePin) {
        ChangePinDialog(
            onDismiss = { showChangePin = false },
            onSubmit = { currentPin, newPin, onError ->
                scope.launch {
                    val ok = withContext(Dispatchers.Default) {
                        AuthGate.changePin(appContext, currentPin, newPin)
                    }
                    if (ok) {
                        pinFeedback = "PIN updated successfully."
                        showChangePin = false
                    } else {
                        onError("Current PIN is incorrect.")
                    }
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkNavy)
            Text(subtitle, fontSize = 13.sp, color = MetaNavy)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ActionBlue)
        )
    }
}

@Composable
private fun ChangePinDialog(
    onDismiss: () -> Unit,
    onSubmit: (currentPin: String, newPin: String, onError: (String) -> Unit) -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) currentPin = it },
                    label = { Text("Current 4-digit PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) newPin = it },
                    label = { Text("New 4-digit PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (error != null) {
                    Text(error!!, color = AlertOrange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (currentPin.length != 4 || newPin.length != 4) {
                        error = "Both PINs must be 4 digits."
                    } else {
                        error = null
                        onSubmit(currentPin, newPin) { error = it }
                    }
                }
            ) { Text("Update") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
