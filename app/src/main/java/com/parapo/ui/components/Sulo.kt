package com.parapo.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class SuloPose(val resName: String) {
    CALM("sulo_calm"),
    EXCITED("sulo_excited"),
    ARRIVED("sulo_arrived"),
    SETUP("sulo_setup"),
    GPS_LOST("sulo_gps_lost")
}

/** Shows the Sulo artwork if the PNG exists in res/drawable-nodpi, else the placeholder. */
@Composable
fun Sulo(pose: SuloPose = SuloPose.CALM, size: Dp = 140.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resId = remember(pose) {
        context.resources.getIdentifier(pose.resName, "drawable", context.packageName)
    }
    if (resId != 0) {
        Image(
            painter = painterResource(resId),
            contentDescription = "Sulo the Lanternfox",
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit
        )
    } else {
        SuloPlaceholder(size = size, modifier = modifier)
    }
}
