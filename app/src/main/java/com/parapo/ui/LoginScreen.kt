package com.parapo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parapo.auth.AuthGate
import com.parapo.ui.components.AuthPrimaryButton
import com.parapo.ui.components.AuthTextField
import com.parapo.ui.components.PinEntryPanel
import com.parapo.ui.components.Sulo
import com.parapo.ui.components.SuloPose
import com.parapo.ui.theme.PinGold
import com.parapo.ui.theme.PrimaryBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_ATTEMPTS = 5
private const val LOCKOUT_SECONDS = 30

private enum class LoginStage { CREDENTIALS, PIN_ENTER, PIN_CREATE, PIN_CONFIRM }
private enum class ResetReason { FORGOT_PASSWORD, NEW_ACCOUNT }

/**
 * S2 Login (FR-01).
 *
 * Flow:
 *  1. Username + password (checked against the salted hash, local only).
 *  2. "Keep this device signed in?" prompt.
 *  3. PIN. Old PIN-only accounts, and "Forgot PIN?", create a new PIN here instead.
 *
 * If the device was kept signed in, launch skips step 1 and 2 and asks only for the PIN.
 * "Forgot password?" and "Sign up" replace the one local account, so they ask for confirmation first.
 */
@Composable
fun LoginScreen(onSuccess: () -> Unit, onGoToSignup: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    var stage by remember {
        mutableStateOf(
            if (AuthGate.isKeepSignedIn(context) && AuthGate.hasPin(context)) LoginStage.PIN_ENTER
            else LoginStage.CREDENTIALS
        )
    }
    var resettingPin by remember { mutableStateOf(false) }
    var showKeepPrompt by remember { mutableStateOf(false) }
    var resetReason by remember { mutableStateOf<ResetReason?>(null) }

    var username by remember { mutableStateOf(AuthGate.getUsername(context)) }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    var lockoutSeconds by remember { mutableIntStateOf(0) }

    // Cooldown after 5 consecutive failed attempts (slows down guessing)
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

    // PIN stages: act as soon as all digits are in
    LaunchedEffect(pin, stage) {
        if (stage != LoginStage.CREDENTIALS && pin.length == AuthGate.PIN_LENGTH && !busy && lockoutSeconds == 0) {
            val entered = pin
            busy = true
            errorMsg = null
            when (stage) {
                LoginStage.PIN_CREATE -> {
                    delay(150)
                    firstPin = entered
                    pin = ""
                    stage = LoginStage.PIN_CONFIRM
                    busy = false
                }
                LoginStage.PIN_CONFIRM -> {
                    if (entered == firstPin) {
                        withContext(Dispatchers.Default) { AuthGate.setPin(context, entered) }
                        resettingPin = false
                        onSuccess()
                    } else {
                        errorMsg = "PINs didn't match. Create your PIN again."
                        delay(300)
                        firstPin = ""
                        pin = ""
                        stage = LoginStage.PIN_CREATE
                        busy = false
                    }
                }
                LoginStage.PIN_ENTER -> {
                    val ok = withContext(Dispatchers.Default) { AuthGate.verifyPin(context, entered) }
                    if (ok) {
                        failedAttempts = 0
                        onSuccess()
                    } else {
                        failedAttempts += 1
                        if (failedAttempts >= MAX_ATTEMPTS) {
                            lockoutSeconds = LOCKOUT_SECONDS
                        } else {
                            errorMsg = "Incorrect PIN (${MAX_ATTEMPTS - failedAttempts} attempts left)."
                        }
                        delay(300)
                        pin = ""
                        busy = false
                    }
                }
                LoginStage.CREDENTIALS -> Unit
            }
        }
    }

    fun submitCredentials() {
        if (busy || lockoutSeconds > 0) return
        if (username.isBlank() || password.isEmpty()) {
            errorMsg = "Enter your username and password."
            return
        }
        busy = true
        errorMsg = null
        scope.launch {
            val ok = withContext(Dispatchers.Default) { AuthGate.login(context, username, password) }
            busy = false
            if (ok) {
                failedAttempts = 0
                password = ""
                pin = ""
                if (resettingPin) {
                    stage = LoginStage.PIN_CREATE // "Forgot PIN?": password proved it's them
                } else {
                    showKeepPrompt = true
                }
            } else {
                failedAttempts += 1
                if (failedAttempts >= MAX_ATTEMPTS) {
                    lockoutSeconds = LOCKOUT_SECONDS
                } else {
                    errorMsg = "Incorrect username or password (${MAX_ATTEMPTS - failedAttempts} attempts left)."
                }
                password = ""
            }
        }
    }

    fun afterKeepChoice(keep: Boolean) {
        AuthGate.setKeepSignedIn(context, keep)
        showKeepPrompt = false
        pin = ""
        stage = if (AuthGate.hasPin(context)) LoginStage.PIN_ENTER else LoginStage.PIN_CREATE
    }

    val status = when {
        lockoutSeconds > 0 -> "Too many attempts. Try again in $lockoutSeconds s."
        errorMsg != null -> errorMsg
        busy -> "Checking…"
        else -> null
    }
    val statusIsError = lockoutSeconds > 0 || errorMsg != null

    if (stage == LoginStage.CREDENTIALS) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PrimaryBlue)
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Sulo(pose = SuloPose.CALM, size = 110.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (resettingPin) "Reset your PIN" else "Welcome back",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp)
            )
            Text(
                text = if (resettingPin) "Log in with your password to set a new PIN" else "Log in to Para Po!",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))
            AuthTextField(
                value = username,
                onValueChange = { if (it.length <= AuthGate.USERNAME_MAX) { username = it; errorMsg = null } },
                label = "Username",
                enabled = !busy && lockoutSeconds == 0
            )
            Spacer(Modifier.height(12.dp))
            AuthTextField(
                value = password,
                onValueChange = { if (it.length <= AuthGate.PASSWORD_MAX) { password = it; errorMsg = null } },
                label = "Password",
                isPassword = true,
                isLast = true,
                onDone = { submitCredentials() },
                enabled = !busy && lockoutSeconds == 0
            )

            Spacer(Modifier.height(10.dp))
            Text(
                text = status ?: " ",
                color = if (statusIsError) PinGold else Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(40.dp)
            )

            AuthPrimaryButton(
                text = "Log in",
                onClick = { submitCredentials() },
                enabled = !busy && lockoutSeconds == 0
            )

            Spacer(Modifier.height(4.dp))
            if (resettingPin) {
                TextButton(
                    onClick = {
                        resettingPin = false
                        errorMsg = null
                        password = ""
                        pin = ""
                        stage = LoginStage.PIN_ENTER
                    }
                ) { Text("Back to PIN", color = Color.White) }
            } else {
                TextButton(onClick = { resetReason = ResetReason.FORGOT_PASSWORD }) {
                    Text("Forgot password?", color = Color.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("Don't have an account?", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    TextButton(onClick = { resetReason = ResetReason.NEW_ACCOUNT }) {
                        Text("Sign up", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    } else {
        PinEntryPanel(
            title = when (stage) {
                LoginStage.PIN_ENTER -> "Enter your PIN"
                LoginStage.PIN_CONFIRM -> "Confirm your PIN"
                else -> if (resettingPin) "Create a new 4-digit PIN" else "Create a 4-digit PIN"
            },
            subtitle = if (stage == LoginStage.PIN_CREATE && !resettingPin) {
                "You'll use it to unlock Para Po!"
            } else null,
            pin = pin,
            status = status,
            statusIsError = statusIsError,
            enabled = !busy && lockoutSeconds == 0,
            onPinChange = { errorMsg = null; pin = it },
            footer = {
                if (stage == LoginStage.PIN_ENTER) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                resettingPin = true
                                errorMsg = null
                                pin = ""
                                stage = LoginStage.CREDENTIALS
                            }
                        ) { Text("Forgot PIN?", color = Color.White) }
                        TextButton(
                            onClick = {
                                AuthGate.setKeepSignedIn(context, false)
                                errorMsg = null
                                pin = ""
                                stage = LoginStage.CREDENTIALS
                            }
                        ) { Text("Use password instead", color = Color.White) }
                    }
                } else {
                    Spacer(Modifier.height(16.dp))
                }
            }
        )
    }

    if (showKeepPrompt) {
        AlertDialog(
            onDismissRequest = { /* a choice is required */ },
            title = { Text("Keep this device signed in?") },
            text = {
                Text(
                    "If you keep it signed in, Para Po! will ask only for your PIN next time. " +
                        "Otherwise you'll log in with your password first, then your PIN. " +
                        "Only choose this on a phone that's yours."
                )
            },
            confirmButton = {
                TextButton(onClick = { afterKeepChoice(true) }) { Text("Keep me signed in") }
            },
            dismissButton = {
                TextButton(onClick = { afterKeepChoice(false) }) { Text("Not now") }
            }
        )
    }

    resetReason?.let { reason ->
        AlertDialog(
            onDismissRequest = { resetReason = null },
            title = {
                Text(if (reason == ResetReason.FORGOT_PASSWORD) "Forgot password?" else "Create a new account?")
            },
            text = {
                Text(
                    if (reason == ResetReason.FORGOT_PASSWORD) {
                        "There is no server, so the password cannot be recovered. " +
                            "Resetting erases all trips, settings, and cached routes stored on this phone."
                    } else {
                        "Para Po! keeps one account per phone. Signing up again replaces the current " +
                            "account and erases all trips, settings, and cached routes stored on this phone."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        resetReason = null
                        AuthGate.resetAllData(context)
                        onGoToSignup()
                    }
                ) { Text(if (reason == ResetReason.FORGOT_PASSWORD) "Reset data" else "Erase and sign up") }
            },
            dismissButton = {
                TextButton(onClick = { resetReason = null }) { Text("Cancel") }
            }
        )
    }
}
