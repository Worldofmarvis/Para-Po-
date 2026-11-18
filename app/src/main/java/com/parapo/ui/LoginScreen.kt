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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parapo.ui.components.Sulo
import com.parapo.ui.components.SuloPose
import com.parapo.ui.theme.PrimaryBlue
import kotlinx.coroutines.delay

private const val PIN_LENGTH = 4
private const val KEY_BACKSPACE = "<"
private const val KEY_EMPTY = ""

private val keyRows = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(KEY_EMPTY, "0", KEY_BACKSPACE)
)

/**
 * S2 Login. FAKE for now: any 4-digit PIN is accepted.
 * TODO (Maeda): replace onSuccess check with AuthGate.verify(pin).
 */
@Composable
fun LoginScreen(onSuccess: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var showForgot by remember { mutableStateOf(false) }

    LaunchedEffect(pin) {
        if (pin.length == PIN_LENGTH) {
            delay(200)
            onSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryBlue)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Sulo(pose = SuloPose.CALM, size = 140.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Enter your PIN",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(16.dp))
        PinDots(filled = pin.length)

        Spacer(Modifier.weight(1f))

        PinPad(
            onDigit = { if (pin.length < PIN_LENGTH) pin += it },
            onBackspace = { pin = pin.dropLast(1) }
        )

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showForgot = true }) {
            Text("Forgot PIN?", color = Color.White)
        }
    }

    if (showForgot) {
        AlertDialog(
            onDismissRequest = { showForgot = false },
            title = { Text("Forgot PIN?") },
            text = {
                Text(
                    "There is no server, so the PIN cannot be recovered. " +
                        "Resetting erases all trips and settings stored on this phone."
                )
            },
            confirmButton = {
                // TODO (Maeda): wipe local storage here, then go to PIN setup.
                TextButton(onClick = { showForgot = false }) { Text("Reset data") }
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
private fun PinPad(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        keyRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key ->
                    when (key) {
                        KEY_EMPTY -> Spacer(Modifier.size(72.dp))
                        KEY_BACKSPACE -> PinKey(onClick = onBackspace) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Delete",
                                tint = Color.White
                            )
                        }
                        else -> PinKey(onClick = { onDigit(key) }) {
                            Text(text = key, color = Color.White, fontSize = 28.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKey(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp) // above the 48 dp minimum touch target
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
