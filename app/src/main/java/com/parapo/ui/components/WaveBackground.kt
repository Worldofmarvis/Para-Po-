package com.parapo.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.parapo.R

/** Blue wave artwork used behind S1 Splash and S6 Alert. Fills the whole screen, cropping if needed. */
@Composable
fun WaveBackground(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.bg_waves),
        contentDescription = null,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}
