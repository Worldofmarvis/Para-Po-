package com.parapo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parapo.ui.theme.DeepBlue
import com.parapo.ui.theme.PinGold

/** Fallback shown only when a Sulo PNG is missing from res/drawable-nodpi. */
@Composable
fun SuloPlaceholder(size: Dp = 140.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(DeepBlue),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = "Sulo placeholder",
            tint = PinGold,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}
