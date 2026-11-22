package com.parapo.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parapo.alert.AlertManager
import com.parapo.alert.EscalationStep
import com.parapo.trip.TripController
import com.parapo.trip.TripPhase
import com.parapo.ui.components.Sulo
import com.parapo.ui.components.SuloPose
import com.parapo.ui.theme.InkNavy
import com.parapo.util.Constants
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val AlertBlue = Color(0xFF0A8CFF)
private val DeepBlue = Color(0xFF0B5FB5)
private val PillBg = Color(0xFFDDEEFF)
private val SparkAmber = Color(0xFFFFB84D)
private val PulseBlue = Color(0xFF9AD2FF)

/**
 * S6 Alert (matches the updated design): blue gradient screen with soft waves, location-pin
 * watermarks and dashed route lines, "!" badge with spark marks, a large excited Sulo centered
 * in the middle of the screen over a calm blue ripple pulse, "Malapit na!", the remaining distance in a pill, a hint line and
 * the GISING NA AKO button.
 *
 * - Vibrates right away, repeating every Constants.VIBRATE_REPEAT_S seconds.
 * - Alarm sound starts after Constants.SOUND_AFTER_S seconds.
 * - Screen stays on; Back is blocked, only the button dismisses.
 */
@Composable
fun AlertScreen(onAwake: () -> Unit) {
    val context = LocalContext.current
    val trip by TripController.state.collectAsState()
    val escalationStep by AlertManager.step.collectAsState()

    BackHandler(enabled = true) { /* only "GISING NA AKO" can dismiss */ }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Delegate escalation (vibrate -> sound -> full-screen) to AlertManager so it works with screen off too.
    // For the S3 "Test alert" shortcut (phase == IDLE), starts AlertManager with SMS disabled.
    val periodMs = Constants.VIBRATE_REPEAT_S * 1000L
    DisposableEffect(Unit) {
        val appCtx = context.applicationContext
        val startedLocally = !com.parapo.alert.AlertManager.isActive()
        if (startedLocally) {
            val remaining = if (trip.phase == TripPhase.ALERTING) trip.remainingM else null
            com.parapo.alert.AlertManager.start(
                context = appCtx,
                remainingM = remaining,
                allowSms = false
            )
        }
        onDispose {
            if (startedLocally) {
                com.parapo.alert.AlertManager.stop(appCtx)
            }
        }
    }

    // Calm blue pulse behind Sulo: two slow ripples plus a faintly breathing glow (always running)
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ripple by pulse.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "ripple"
    )
    val breathe by pulse.animateFloat(
        initialValue = 0.96f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )

    // Buzz: Sulo shakes while the phone is vibrating (0.8 s), then rests until the next buzz
    val shake = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            repeat(4) {
                shake.animateTo(4f, tween(100, easing = LinearEasing))
                shake.animateTo(-4f, tween(100, easing = LinearEasing))
            }
            shake.animateTo(0f, tween(60))
            delay((periodMs - 860L).coerceAtLeast(200L))
        }
    }

    val hasTrip = trip.setup != null && trip.phase != TripPhase.IDLE
    val distanceLine = if (hasTrip) "${fmtAlertDistance(trip.remainingM)} na lang" else "Malapit na ang stop mo"

    Box(modifier = Modifier.fillMaxSize()) {
        AlertBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))

            // "!" badge with two spark marks at the top right
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .drawBehind {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val radius = size.width / 2f
                        val inner = radius + 10.dp.toPx()
                        val outer = radius + 26.dp.toPx()
                        listOf(-62.0, -28.0).forEach { deg ->
                            val a = Math.toRadians(deg)
                            val dx = cos(a).toFloat()
                            val dy = sin(a).toFloat()
                            drawLine(
                                color = SparkAmber,
                                start = Offset(c.x + dx * inner, c.y + dy * inner),
                                end = Offset(c.x + dx * outer, c.y + dy * outer),
                                strokeWidth = 6.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("!", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = AlertBlue)
            }

            // Sulo: big, centered in all the free space between the badge and the text
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val suloSize = minOf(maxWidth * 0.95f, maxHeight * 0.98f)

                // blue pulse centered behind Sulo
                Canvas(Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val maxR = minOf(size.width, size.height) * 0.62f

                    // soft inner glow, barely breathing
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to PulseBlue.copy(alpha = 0.30f),
                            0.65f to PulseBlue.copy(alpha = 0.10f),
                            1.0f to Color.Transparent,
                            center = c, radius = maxR * 0.9f * breathe
                        ),
                        radius = maxR * 0.9f * breathe,
                        center = c
                    )

                    // two staggered ripples that expand and fade out
                    listOf(0f, 0.5f).forEach { offset ->
                        val t = (ripple + offset) % 1f
                        val fade = (1f - t) * (1f - t)
                        val r = maxR * (0.45f + 0.55f * t)
                        drawCircle(
                            color = PulseBlue.copy(alpha = 0.10f * fade),
                            radius = r, center = c
                        )
                        drawCircle(
                            color = PulseBlue.copy(alpha = 0.38f * fade),
                            radius = r, center = c,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                Box(Modifier.size(suloSize), contentAlignment = Alignment.Center) {
                    // ground shadow under the feet
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = suloSize * 0.045f)
                            .width(suloSize * 0.66f)
                            .height(suloSize * 0.075f)
                            .background(Color(0x400A2A8C), CircleShape)
                    )
                    Box(Modifier.graphicsLayer { rotationZ = shake.value }) {
                        Sulo(SuloPose.EXCITED, size = suloSize)
                    }
                }
            }

            Text(
                "Malapit na!",
                fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                textAlign = TextAlign.Center
            )

            // Distance pill: pin icon | divider | text
            Row(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .background(PillBg, RoundedCornerShape(50))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(Modifier.size(width = 28.dp, height = 34.dp)) {
                    val r = size.width * 0.46f
                    drawPin(Offset(size.width / 2f, r + 1.dp.toPx()), r, DeepBlue, 1f)
                }
                Spacer(Modifier.width(14.dp))
                Box(
                    Modifier
                        .width(2.dp)
                        .height(30.dp)
                        .background(DeepBlue.copy(alpha = 0.55f))
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    distanceLine,
                    fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                    color = DeepBlue, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }

            // 3 escalation step dots (Build Guide 6.3: vibrate -> sound -> full-screen)
            val activeDots = when (escalationStep) {
                EscalationStep.FULL_SCREEN -> 3
                EscalationStep.SOUND -> 2
                EscalationStep.VIBRATING -> 1
                EscalationStep.IDLE -> 1
            }
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { idx ->
                    val filled = idx < activeDots
                    Box(
                        modifier = Modifier
                            .size(if (filled) 11.dp else 9.dp)
                            .background(
                                color = if (filled) Color.White else Color.White.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Tap the button to stop the alert",
                fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onAwake,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = InkNavy),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .shadow(10.dp, RoundedCornerShape(50), ambientColor = Color(0xFF003C99), spotColor = Color(0xFF003C99))
            ) {
                Text("GISING NA AKO", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = InkNavy)
            }
        }
    }
}

/** Blue gradient, soft waves, pin watermarks and dashed route lines (drawn in code, no extra assets). */
@Composable
private fun AlertBackground() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xFF1C94FF), Color(0xFF0A7BF7), Color(0xFF056FEA))
            )
        )

        // lighter wave, top left
        val topWave = Path().apply {
            moveTo(0f, 0f)
            lineTo(w * 0.55f, 0f)
            cubicTo(w * 0.46f, h * 0.06f, w * 0.20f, h * 0.09f, 0f, h * 0.12f)
            close()
        }
        drawPath(topWave, Color.White, alpha = 0.08f)

        // lighter wave, bottom right
        val bottomWave = Path().apply {
            moveTo(w, h * 0.87f)
            cubicTo(w * 0.78f, h * 0.90f, w * 0.60f, h * 0.95f, w * 0.48f, h)
            lineTo(w, h)
            close()
        }
        drawPath(bottomWave, Color.White, alpha = 0.08f)

        // spotlight behind the mascot
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.47f),
                radius = w * 0.7f
            ),
            radius = w * 0.7f,
            center = Offset(w * 0.5f, h * 0.47f)
        )

        // dashed route lines
        val dash = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 12.dp.toPx()))
        )
        val route1 = Path().apply {
            moveTo(0f, h * 0.185f)
            cubicTo(w * 0.10f, h * 0.150f, w * 0.30f, h * 0.120f, w * 0.43f, h * 0.170f)
            cubicTo(w * 0.46f, h * 0.190f, w * 0.45f, h * 0.230f, w * 0.44f, h * 0.265f)
            cubicTo(w * 0.435f, h * 0.285f, w * 0.43f, h * 0.30f, w * 0.42f, h * 0.32f)
        }
        drawPath(route1, Color.White.copy(alpha = 0.30f), style = dash)
        val route2 = Path().apply {
            moveTo(w * 0.68f, h * 0.552f)
            cubicTo(w * 0.78f, h * 0.558f, w * 0.90f, h * 0.548f, w, h * 0.532f)
        }
        drawPath(route2, Color.White.copy(alpha = 0.30f), style = dash)

        // location pin watermarks
        drawPin(Offset(w * 0.117f, h * 0.126f), w * 0.034f, Color.White, 0.22f)
        drawPin(Offset(w * 0.932f, h * 0.516f), w * 0.032f, Color.White, 0.22f)
        drawPin(Offset(w * 0.915f, h * 0.950f), w * 0.036f, Color.White, 0.20f)
    }
}

/** Map-pin shape (round head, pointed tip, round hole). [center] is the middle of the head. */
private fun DrawScope.drawPin(center: Offset, r: Float, color: Color, alpha: Float) {
    val tipDist = r * 2.25f
    val dx = r * sqrt(1f - (1f / 2.25f) * (1f / 2.25f)) // tangent points on the head circle
    val dy = r / 2.25f
    val head = Path().apply { addOval(Rect(center, r)) }
    val tail = Path().apply {
        moveTo(center.x - dx, center.y + dy)
        lineTo(center.x, center.y + tipDist)
        lineTo(center.x + dx, center.y + dy)
        close()
    }
    val body = Path.combine(PathOperation.Union, head, tail)
    val hole = Path().apply { addOval(Rect(center, r * 0.40f)) }
    drawPath(Path.combine(PathOperation.Difference, body, hole), color, alpha = alpha)
}

private fun fmtAlertDistance(m: Double): String =
    if (m >= 1000.0) String.format(Locale.US, "%.1f km", m / 1000.0)
    else "${(m / 10.0).roundToInt() * 10} m"
