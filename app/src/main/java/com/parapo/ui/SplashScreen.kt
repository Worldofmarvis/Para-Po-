package com.parapo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.parapo.R
import com.parapo.ui.components.WaveBackground

/** S1 Splash: wave background and the Para Po! logo. Shows 1.5 s, then moves on. */
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
            // The logo already contains the "Para Po!" wordmark.
            Image(
                painter = painterResource(R.drawable.parapo_splash_logo),
                contentDescription = "Para Po!",
                modifier = Modifier.fillMaxWidth(0.75f),
                contentScale = ContentScale.Fit
            )
        }
    }
}

private suspend fun delay1500() = kotlinx.coroutines.delay(1500)
