package com.parapo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.parapo.ui.theme.DeepBlue

/** Temporary screen used until the real one is built. Buttons let you test navigation. */
@Composable
fun PlaceholderScreen(
    title: String,
    isTab: Boolean,
    actions: List<Pair<String, () -> Unit>> = emptyList()
) {
    // Tab screens: the bottom bar already handles the bottom inset.
    val insets = if (isTab) Modifier.statusBarsPadding() else Modifier.systemBarsPadding()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(insets)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium, color = DeepBlue)
        Spacer(Modifier.height(8.dp))
        Text(text = "Placeholder - screen not built yet", style = MaterialTheme.typography.bodyLarge)
        actions.forEach { (label, onClick) ->
            Spacer(Modifier.height(12.dp))
            Button(onClick = onClick) { Text(label) }
        }
    }
}
