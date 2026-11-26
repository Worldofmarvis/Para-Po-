package com.parapo.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.parapo.ui.theme.ActionBlue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val CropBackdrop = Color(0xFF060B26)

/**
 * Full-screen circle cropper. Drag to move and pinch to zoom the photo under the circle;
 * "Use photo" returns the square that the circle frames.
 */
@Composable
fun PhotoCropDialog(
    source: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    val image = remember(source) { source.asImageBitmap() }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val diameter = min(viewSize.width, viewSize.height) * 0.8f
    val minScale = if (diameter > 0f) diameter / min(source.width, source.height) else 1f

    fun clampOffset(o: Offset, s: Float): Offset {
        val maxX = max(0f, (source.width * s - diameter) / 2f)
        val maxY = max(0f, (source.height * s - diameter) / 2f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    LaunchedEffect(viewSize) {
        if (viewSize != IntSize.Zero) {
            scale = minScale
            offset = Offset.Zero
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CropBackdrop)
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text("Crop photo", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Drag and pinch to fit your face in the circle",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .onSizeChanged { viewSize = it }
                    .pointerInput(minScale, diameter) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (scale > 0f) {
                                val newScale = (scale * zoom).coerceIn(minScale, minScale * 6f)
                                val ratio = newScale / scale
                                offset = clampOffset(offset * ratio + pan, newScale)
                                scale = newScale
                            }
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    if (scale <= 0f) return@Canvas
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val w = source.width * scale
                    val h = source.height * scale
                    drawImage(
                        image = image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(source.width, source.height),
                        dstOffset = IntOffset(
                            (cx + offset.x - w / 2f).roundToInt(),
                            (cy + offset.y - h / 2f).roundToInt()
                        ),
                        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                        filterQuality = FilterQuality.Medium
                    )
                    val radius = diameter / 2f
                    val dim = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(0f, 0f, size.width, size.height))
                        addOval(Rect(Offset(cx, cy), radius))
                    }
                    drawPath(dim, Color.Black.copy(alpha = 0.62f))
                    drawCircle(
                        color = Color.White,
                        radius = radius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text("Cancel", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        if (scale > 0f) {
                            val side = diameter / scale
                            val centerX = source.width / 2f - offset.x / scale
                            val centerY = source.height / 2f - offset.y / scale
                            val left = (centerX - side / 2f).roundToInt().coerceIn(0, source.width - 1)
                            val top = (centerY - side / 2f).roundToInt().coerceIn(0, source.height - 1)
                            val sideInt = side.roundToInt()
                                .coerceAtMost(min(source.width - left, source.height - top))
                                .coerceAtLeast(1)
                            onConfirm(Bitmap.createBitmap(source, left, top, sideInt, sideInt))
                        }
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionBlue, contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text("Use photo", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
