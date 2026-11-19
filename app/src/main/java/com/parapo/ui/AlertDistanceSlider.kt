package com.parapo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val SliderActive = Color(0xFF0A8CFF)
private val SliderInactive = Color(0xFFD9EAFB)
private val SliderShadow = Color(0x33062A55)

/**
 * Clean, thin slider for the alert distance: slim rounded track, no tick dots, round thumb with
 * a white ring and soft shadow. Snaps to [step] increments (default 0.5 km over 1..5 km).
 * Drop-in replacement for the Material3 Slider (no Material version dependency).
 */
@Composable
fun AlertDistanceSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 1f..5f,
    step: Float = 0.5f
) {
    val onChange by rememberUpdatedState(onValueChange)
    val min = valueRange.start
    val max = valueRange.endInclusive
    val fraction = ((value - min) / (max - min)).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics {
                contentDescription = "Alert distance"
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
            }
            .pointerInput(min, max, step) {
                val pad = 12.dp.toPx()
                detectTapGestures { o -> onChange(snapToStep(o.x, size.width.toFloat(), pad, min, max, step)) }
            }
            .pointerInput(min, max, step) {
                val pad = 12.dp.toPx()
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onChange(snapToStep(change.position.x, size.width.toFloat(), pad, min, max, step))
                }
            }
    ) {
        val thumbR = 12.dp.toPx()
        val trackH = 6.dp.toPx()
        val cy = size.height / 2f
        val x0 = thumbR
        val x1 = size.width - thumbR
        val tx = x0 + (x1 - x0) * fraction
        val corner = CornerRadius(trackH / 2f)

        // inactive track
        drawRoundRect(SliderInactive, Offset(x0, cy - trackH / 2f), Size(x1 - x0, trackH), corner)
        // active track
        if (tx > x0) {
            drawRoundRect(SliderActive, Offset(x0, cy - trackH / 2f), Size(tx - x0, trackH), corner)
        }
        // thumb: soft shadow, white ring, blue core
        drawCircle(SliderShadow, thumbR + 1.dp.toPx(), Offset(tx, cy + 1.5.dp.toPx()))
        drawCircle(Color.White, thumbR, Offset(tx, cy))
        drawCircle(SliderActive, thumbR - 4.dp.toPx(), Offset(tx, cy))
    }
}

private fun snapToStep(x: Float, width: Float, pad: Float, min: Float, max: Float, step: Float): Float {
    val track = width - 2f * pad
    if (track <= 0f) return min
    val f = ((x - pad) / track).coerceIn(0f, 1f)
    val count = ((max - min) / step).roundToInt().coerceAtLeast(1)
    return (min + (f * count).roundToInt() * ((max - min) / count)).coerceIn(min, max)
}
