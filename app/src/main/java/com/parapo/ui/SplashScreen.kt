package com.parapo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parapo.ui.components.Sulo
import com.parapo.ui.components.SuloPose
import com.parapo.ui.components.WaveBackground

/** S1 Splash: wave background, Sulo and the wordmark. Shows 1.5 s, then moves on. */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay1500()
        onFinished()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        WaveBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Sulo(pose = SuloPose.CALM, size = 220.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Para Po!",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 40.sp)
            )
        }
    }
}

private suspend fun delay1500() = kotlinx.coroutines.delay(1500)
