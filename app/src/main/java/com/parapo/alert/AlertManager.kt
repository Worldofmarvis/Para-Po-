package com.parapo.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import com.parapo.R
import com.parapo.storage.Settings
import com.parapo.storage.SettingsStore
import com.parapo.util.Constants
import com.parapo.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** Current stage on the FR-13 escalating alert ladder. */
enum class EscalationStep { IDLE, VIBRATING, SOUND, FULL_SCREEN }

/**
 * Pure, unit-testable escalation schedule (Build Guide 7.5 & TC-07).
 * Vibration is always on at t = 0 s; sound and full-screen respect S9 toggles & delays.
 */
object EscalationSchedule {
    fun stepAt(elapsedSeconds: Int, settings: Settings): EscalationStep {
        if (elapsedSeconds < 0) return EscalationStep.IDLE
        if (settings.fullScreenEnabled && elapsedSeconds >= settings.fullScreenAfterS) {
            return EscalationStep.FULL_SCREEN
        }
        if (settings.soundEnabled && elapsedSeconds >= settings.soundAfterS) {
            return EscalationStep.SOUND
        }
        return EscalationStep.VIBRATING
    }
}

/**
 * FR-13 & FR-17: Drives the escalating stop alert ladder independently of the UI lifecycle
 * so vibration, alarm audio, optional emergency SMS, and lock-screen AlertActivity fire
 * reliably even when the screen is off.
 */
object AlertManager {

    const val ALERT_CHANNEL_ID = "parapo_alerts"
    const val ALERT_NOTIFICATION_ID = 2001

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _step = MutableStateFlow(EscalationStep.IDLE)
    val step: StateFlow<EscalationStep> = _step.asStateFlow()

    private var ladderJob: Job? = null
    private var activeRingtone: Ringtone? = null
    private var activeVibrator: Vibrator? = null
    private var active = false

    fun isActive(): Boolean = active

    fun start(
        context: Context,
        remainingM: Double? = null,
        settings: Settings = SettingsStore.current(context.applicationContext),
        allowSms: Boolean = true
    ) {
        if (active) return
        val appCtx = context.applicationContext
        active = true
        ensureChannel(appCtx)

        // Step 1 (t = 0 s): Vibrate immediately (always on) + alert notification
        _step.value = EscalationStep.VIBRATING
        startVibration(appCtx)
        postAlertNotification(appCtx, remainingM, fullScreen = false)

        // Optional FR-17: Emergency SMS (off by default)
        if (allowSms && settings.smsEnabled && !settings.smsNumber.isNullOrBlank()) {
            sendEmergencySms(appCtx, settings.smsNumber, remainingM)
        }

        ladderJob?.cancel()
        ladderJob = scope.launch {
            var elapsedS = 0
            var soundStarted = false
            var fullScreenStarted = false

            while (active) {
                val target = EscalationSchedule.stepAt(elapsedS, settings)
                if (target == EscalationStep.SOUND && !soundStarted) {
                    soundStarted = true
                    _step.value = EscalationStep.SOUND
                    startAlarmSound(appCtx)
                }
                if (target == EscalationStep.FULL_SCREEN && !fullScreenStarted) {
                    fullScreenStarted = true
                    if (settings.soundEnabled && !soundStarted) {
                        soundStarted = true
                        startAlarmSound(appCtx)
                    }
                    _step.value = EscalationStep.FULL_SCREEN
                    postAlertNotification(appCtx, remainingM, fullScreen = true)
                    launchFullScreenActivity(appCtx)
                }
                if ((!settings.soundEnabled || soundStarted) && (!settings.fullScreenEnabled || fullScreenStarted)) {
                    break
                }
                delay(1_000L)
                elapsedS += 1
            }
        }
    }

    fun stop(context: Context) {
        active = false
        ladderJob?.cancel()
        ladderJob = null
        runCatching { activeVibrator?.cancel() }
        activeVibrator = null
        runCatching { activeRingtone?.stop() }
        activeRingtone = null
        _step.value = EscalationStep.IDLE
        runCatching {
            val nm = context.applicationContext.getSystemService(NotificationManager::class.java)
            nm?.cancel(ALERT_NOTIFICATION_ID)
        }
    }

    private fun startVibration(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        activeVibrator = vibrator
        val periodMs = Constants.VIBRATE_REPEAT_S * 1000L
        val effect = VibrationEffect.createWaveform(longArrayOf(0L, 800L, (periodMs - 800L).coerceAtLeast(200L)), 0)
        runCatching { vibrator.vibrate(effect) }
    }

    private fun startAlarmSound(context: Context) {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        runCatching {
            val ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                isLooping = true
            }
            activeRingtone = ringtone
            ringtone?.play()
        }
    }

    private fun launchFullScreenActivity(context: Context) {
        val intent = Intent(context, AlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun postAlertNotification(context: Context, remainingM: Double?, fullScreen: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val alertIntent = Intent(context, AlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pi = PendingIntent.getActivity(
            context,
            ALERT_NOTIFICATION_ID,
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val distText = remainingM?.let {
            if (it >= 1000.0) String.format(Locale.US, "%.1f km na lang", it / 1000.0)
            else "${it.toInt()} m na lang"
        } ?: "Malapit na ang stop mo"

        val builder = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_fox_mono)
            .setContentTitle("Para Po! Malapit na!")
            .setContentText("$distText — Tap to dismiss alert")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pi)

        if (fullScreen) {
            builder.setFullScreenIntent(pi, true)
        }
        runCatching { nm.notify(ALERT_NOTIFICATION_ID, builder.build()) }
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(ALERT_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "Stop Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Escalating proximity wake-up alerts when approaching your stop."
            setBypassDnd(true)
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }

    private fun sendEmergencySms(context: Context, phoneNumber: String, remainingM: Double?) {
        if (!PermissionHelper.hasSmsPermission(context)) return
        val dist = remainingM?.let { String.format(Locale.US, "%.1f km", it / 1000.0) } ?: "nearby"
        val msg = "Para Po! Alert: Approaching destination stop ($dist remaining)."
        runCatching {
            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager?.sendTextMessage(phoneNumber, null, msg, null, null)
        }
    }
}
