package com.parapo.ui.components

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parapo.auth.AuthGate
import com.parapo.ui.theme.PinGold
import com.parapo.ui.theme.PrimaryBlue

private const val KEY_BACKSPACE = "<"
private const val KEY_EMPTY = ""

private val keyRows = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(KEY_EMPTY, "0", KEY_BACKSPACE)
)

/**
 * Full-screen PIN entry used by Login and Sign up: mascot, title, dots, status line, keypad, footer.
 * The caller owns the PIN text and decides what happens when it reaches [AuthGate.PIN_LENGTH] digits.
 */
@Composable
fun PinEntryPanel(
    title: String,
    pin: String,
    status: String?,
    statusIsError: Boolean,
    enabled: Boolean,
    onPinChange: (String) -> Unit,
    subtitle: String? = null,
    footer: @Composable () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryBlue)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Sulo(pose = SuloPose.CALM, size = 96.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp)
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(12.dp))
        PinDots(filled = pin.length)

        Box(
            modifier = Modifier.height(26.dp).padding(top = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (status != null) {
                Text(
                    text = status,
                    color = if (statusIsError) PinGold else Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = if (statusIsError) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }

        Spacer(Modifier.weight(1f))

        PinPad(
            enabled = enabled,
            onDigit = { if (pin.length < AuthGate.PIN_LENGTH) onPinChange(pin + it) },
            onBackspace = { onPinChange(pin.dropLast(1)) }
        )

        Spacer(Modifier.height(8.dp))
        footer()
    }
}

@Composable
private fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(AuthGate.PIN_LENGTH) { index ->
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
