package com.parapo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parapo.auth.AuthGate
import com.parapo.ui.components.Sulo
import com.parapo.ui.components.SuloPose
import com.parapo.ui.theme.PinGold
import com.parapo.ui.theme.PrimaryBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val PIN_LENGTH = 4
private const val KEY_BACKSPACE = "<"
private const val KEY_EMPTY = ""

private val keyRows = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(KEY_EMPTY, "0", KEY_BACKSPACE)
)

private enum class PinStage { ENTER, CREATE, CONFIRM }

/**
 * S2 Login (FR-01).
 * First run: asks for commuter name + creates and confirms a 4-digit PIN (PBKDF2 salted hash).
 * Returning: verifies PIN against auth.json. "Forgot PIN?" wipes local data after confirmation.
 */
@Composable
fun LoginScreen(onSuccess: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var stage by remember {
        mutableStateOf(if (AuthGate.isPinSet(context)) PinStage.ENTER else PinStage.CREATE)
    }
    var username by remember { mutableStateOf("Juan") }
    var firstPin by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    var lockoutSeconds by remember { mutableIntStateOf(0) }
    var showForgot by remember { mutableStateOf(false) }

    // Cooldown timer after 5 consecutive wrong PIN attempts (brute-force protection)
    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0) {
            delay(1000)
            lockoutSeconds -= 1
            if (lockoutSeconds == 0) {
                failedAttempts = 0
                errorMsg = null
            }
        }
    }

    LaunchedEffect(pin, stage) {
        if (pin.length == PIN_LENGTH && !busy && lockoutSeconds == 0) {
            busy = true
            errorMsg = null
            when (stage) {
                PinStage.CREATE -> {
                    delay(150)
                    firstPin = pin
                    pin = ""
                    stage = PinStage.CONFIRM
                    busy = false
                }
                PinStage.CONFIRM -> {
                    if (pin == firstPin) {
                        withContext(Dispatchers.Default) {
                            AuthGate.setupPin(context, username, pin)
                        }
                        onSuccess()
                    } else {
                        errorMsg = "PINs didn't match. Create your PIN again."
                        delay(300)
                        firstPin = ""
                        pin = ""
                        stage = PinStage.CREATE
                        busy = false
                    }
                }
                PinStage.ENTER -> {
                    val ok = withContext(Dispatchers.Default) {
                        AuthGate.verifyPin(context, pin)
                    }
                    if (ok) {
                        failedAttempts = 0
                        onSuccess()
                    } else {
                        failedAttempts += 1
                        if (failedAttempts >= 5) {
                            lockoutSeconds = 30
                            errorMsg = "Too many attempts. Try again in 30 s."
                        } else {
                            val left = 5 - failedAttempts
                            errorMsg = "Incorrect PIN ($left attempts left)."
                        }
                        delay(300)
                        pin = ""
                        busy = false
                    }
                }
            }
        }
    }

    val promptText = when (stage) {
        PinStage.ENTER -> "Enter your PIN"
        PinStage.CREATE -> "Create a 4-digit PIN"
        PinStage.CONFIRM -> "Confirm your PIN"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryBlue)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Sulo(pose = SuloPose.CALM, size = if (stage == PinStage.CREATE) 100.dp else 116.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = promptText,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp)
        )

        if (stage == PinStage.CREATE) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { if (it.length <= 24) username = it },
                singleLine = true,
                label = { Text("Your name", color = Color.White.copy(alpha = 0.85f)) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.6f),
                    cursorColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth(0.78f)
            )
        }

        Spacer(Modifier.height(12.dp))
        PinDots(filled = pin.length)

        Box(
            modifier = Modifier.height(26.dp).padding(top = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (lockoutSeconds > 0) {
                Text(
                    text = "Too many attempts. Try again in $lockoutSeconds s.",
                    color = PinGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else if (errorMsg != null) {
                Text(
                    text = errorMsg!!,
                    color = PinGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else if (busy) {
                Text(
                    text = "Checking…",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.weight(1f))

        PinPad(
            enabled = !busy && lockoutSeconds == 0,
            onDigit = {
                errorMsg = null
                if (pin.length < PIN_LENGTH) pin += it
            },
            onBackspace = {
                errorMsg = null
                pin = pin.dropLast(1)
            }
        )

        Spacer(Modifier.height(8.dp))
        if (stage == PinStage.ENTER) {
            TextButton(onClick = { showForgot = true }) {
                Text("Forgot PIN?", color = Color.White)
            }
        } else {
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showForgot) {
        AlertDialog(
            onDismissRequest = { showForgot = false },
            title = { Text("Forgot PIN?") },
            text = {
                Text(
                    "There is no server, so the PIN cannot be recovered. " +
                        "Resetting erases all trips, settings, and cached routes stored on this phone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        AuthGate.resetAllData(context)
                        showForgot = false
                        firstPin = ""
                        pin = ""
                        errorMsg = null
                        stage = PinStage.CREATE
                    }
                ) { Text("Reset data") }
            },
            dismissButton = {
                TextButton(onClick = { showForgot = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(PIN_LENGTH) { index ->
            val isFilled = index < filled
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (isFilled) Color.White else Color.Transparent)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

@Composable
private fun PinPad(enabled: Boolean, onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        keyRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key ->
                    when (key) {
                        KEY_EMPTY -> Spacer(Modifier.size(70.dp))
                        KEY_BACKSPACE -> PinKey(enabled = enabled, onClick = onBackspace) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Delete",
                                tint = Color.White
                            )
                        }
                        else -> PinKey(enabled = enabled, onClick = { onDigit(key) }) {
                            Text(text = key, color = Color.White, fontSize = 26.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKey(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(70.dp) // above the 48 dp minimum touch target
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
