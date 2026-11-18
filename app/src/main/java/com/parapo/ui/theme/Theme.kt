package com.parapo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ParaPoColors = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = DeepBlue,
    tertiary = PinGold,
    surface = androidx.compose.ui.graphics.Color.White,
    surfaceVariant = SurfaceLight,
    error = DangerRed
)

@Composable
fun ParaPoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ParaPoColors,
        typography = ParaPoTypography,
        content = content
    )
}
