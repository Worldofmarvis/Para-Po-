package com.parapo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

private enum class SignupStage { DETAILS, PIN_CREATE, PIN_CONFIRM }

/**
 * S2b Sign up (FR-01).
 * Step 1: username + password (entered twice). Step 2: create and confirm a 4-digit PIN.
 * Only salted PBKDF2 hashes are stored, on this phone. [onGoToLogin] is shown only if an account already exists.
 * If the app is closed between the two steps, the next login asks for the PIN to be created.
 */
@Composable
fun SignupScreen(onSuccess: () -> Unit, onGoToLogin: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val accountExistedAtStart = remember { AuthGate.hasAccount(context) }

    var stage by remember { mutableStateOf(SignupStage.DETAILS) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(pin, stage) {
        if (stage != SignupStage.DETAILS && pin.length == AuthGate.PIN_LENGTH && !busy) {
            val entered = pin
            busy = true
            errorMsg = null
            when (stage) {
                SignupStage.PIN_CREATE -> {
                    delay(150)
                    firstPin = entered
                    pin = ""
                    stage = SignupStage.PIN_CONFIRM
                    busy = false
                }
                SignupStage.PIN_CONFIRM -> {
                    if (entered == firstPin) {
                        withContext(Dispatchers.Default) { AuthGate.setPin(context, entered) }
                        onSuccess()
                    } else {
                        errorMsg = "PINs didn't match. Create your PIN again."
                        delay(300)
                        firstPin = ""
                        pin = ""
                        stage = SignupStage.PIN_CREATE
                        busy = false
                    }
                }
                SignupStage.DETAILS -> Unit
            }
        }
    }

    fun submitDetails() {
        if (busy) return
        val error = AuthGate.usernameError(username)
            ?: AuthGate.passwordError(password)
            ?: if (password != confirm) "Passwords don't match." else null
        if (error != null) {
            errorMsg = error
            return
        }
        busy = true
        errorMsg = null
        scope.launch {
            withContext(Dispatchers.Default) { AuthGate.register(context, username, password) }
            password = ""
            confirm = ""
            pin = ""
            busy = false
            stage = SignupStage.PIN_CREATE
        }
    }

    if (stage != SignupStage.DETAILS) {
        PinEntryPanel(
            title = if (stage == SignupStage.PIN_CREATE) "Create a 4-digit PIN" else "Confirm your PIN",
            subtitle = if (stage == SignupStage.PIN_CREATE) "You'll use it to unlock Para Po!" else null,
            pin = pin,
            status = if (busy && errorMsg == null) "Saving…" else errorMsg,
            statusIsError = errorMsg != null,
            enabled = !busy,
            onPinChange = { errorMsg = null; pin = it }
        )
        return
    }

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
        Sulo(pose = SuloPose.SETUP, size = 100.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Create your account",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp)
        )
        Text(
            text = "Sign up to start using Para Po!",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 15.sp
        )

        Spacer(Modifier.height(20.dp))
        AuthTextField(
            value = username,
            onValueChange = { if (it.length <= AuthGate.USERNAME_MAX) { username = it; errorMsg = null } },
            label = "Username",
            enabled = !busy
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            value = password,
            onValueChange = { if (it.length <= AuthGate.PASSWORD_MAX) { password = it; errorMsg = null } },
            label = "Password",
            isPassword = true,
            enabled = !busy
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            value = confirm,
            onValueChange = { if (it.length <= AuthGate.PASSWORD_MAX) { confirm = it; errorMsg = null } },
            label = "Confirm password",
            isPassword = true,
            isLast = true,
            onDone = { submitDetails() },
            enabled = !busy
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = errorMsg
                ?: "At least ${AuthGate.PASSWORD_MIN} characters. It's stored only on this phone and can't be recovered if you forget it.",
            color = if (errorMsg != null) PinGold else Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = if (errorMsg != null) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(48.dp)
        )

        AuthPrimaryButton(
            text = if (busy) "Creating…" else "Next",
            onClick = { submitDetails() },
            enabled = !busy
        )

        if (accountExistedAtStart) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text("Already have an account?", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                TextButton(onClick = onGoToLogin) {
                    Text("Log in", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
