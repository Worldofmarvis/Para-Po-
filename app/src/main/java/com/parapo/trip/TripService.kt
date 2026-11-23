package com.parapo.trip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.parapo.MainActivity
import com.parapo.R
import com.parapo.alert.AlertManager
import com.parapo.storage.SettingsStore
import com.parapo.ui.TripSetupResult
import com.parapo.watchdog.WatchdogReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

/** Serializable snapshot persisted to active_trip.json so WatchdogReceiver can revive mid-trip (TC-11). */
@Serializable
data class ActiveTripSnapshot(
    val destLat: Double,
    val destLon: Double,
    val vehicle: String,
    val alertDistanceM: Int,
    val routeKm: Double,
    val originLat: Double,
    val originLon: Double,
    val destName: String? = null
) {
    fun toSetupResult(): TripSetupResult = TripSetupResult(
        destLat = destLat,
        destLon = destLon,
        vehicle = vehicle,
        alertDistanceM = alertDistanceM,
        routeKm = routeKm,
        originLat = originLat,
        originLon = originLon,
        destName = destName
    )

    companion object {
        fun from(s: TripSetupResult): ActiveTripSnapshot = ActiveTripSnapshot(
            destLat = s.destLat,
            destLon = s.destLon,
            vehicle = s.vehicle,
            alertDistanceM = s.alertDistanceM,
            routeKm = s.routeKm,
            originLat = s.originLat,
            originLon = s.originLon,
            destName = s.destName
        )
    }
}

/**
 * FR-05 & FR-14: Foreground location service that keeps trip tracking alive with the screen off,
 * updates the live status-bar notification, triggers AlertManager on ALERTING, and coordinates
 * with WatchdogReceiver for process-kill recovery (TC-11).
 */
class TripService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            clearActiveSnapshot(applicationContext)
            WatchdogReceiver.cancel(applicationContext)
            AlertManager.stop(applicationContext)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        ensureChannel(applicationContext)
        val initialNotif = buildNotification(applicationContext, TripController.state.value)
        startForeground(
            NOTIFICATION_ID,
            initialNotif,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )

        // Recovery path (TC-11): if OS killed the process and Watchdog/START_STICKY restarted us, restore TripController
        if (TripController.state.value.phase == TripPhase.IDLE) {
            val saved = loadActiveSnapshot(applicationContext)
            if (saved != null) {
                TripController.start(saved.toSetupResult(), applicationContext, startService = false)
            } else {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        WatchdogReceiver.schedule(applicationContext)

        if (observerJob == null) {
            observerJob = serviceScope.launch {
                val nm = getSystemService(NotificationManager::class.java)
                TripController.state.collect { state ->
                    when (state.phase) {
                        TripPhase.ARMED, TripPhase.TRACKING -> {
                            nm?.notify(NOTIFICATION_ID, buildNotification(applicationContext, state))
                        }
                        TripPhase.ALERTING -> {
                            nm?.notify(NOTIFICATION_ID, buildNotification(applicationContext, state))
                            AlertManager.start(
                                context = applicationContext,
                                remainingM = state.remainingM,
                                settings = SettingsStore.current(applicationContext),
                                allowSms = true
                            )
                        }
                        TripPhase.IDLE, TripPhase.COMPLETED -> {
                            clearActiveSnapshot(applicationContext)
                            WatchdogReceiver.cancel(applicationContext)
                            AlertManager.stop(applicationContext)
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        observerJob?.cancel()
        observerJob = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "parapo_trip"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.parapo.action.START_TRIP"
        const val ACTION_STOP = "com.parapo.action.STOP_TRIP"
        private const val SNAPSHOT_FILE = "active_trip.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun startService(context: Context, setup: TripSetupResult) {
            saveActiveSnapshot(context, ActiveTripSnapshot.from(setup))
            val intent = Intent(context, TripService::class.java).setAction(ACTION_START)
            runCatching { context.startForegroundService(intent) }
        }

        fun stopService(context: Context) {
            clearActiveSnapshot(context)
            WatchdogReceiver.cancel(context)
            val intent = Intent(context, TripService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }

        fun saveActiveSnapshot(context: Context, snapshot: ActiveTripSnapshot) {
            runCatching {
                File(context.filesDir, SNAPSHOT_FILE).writeText(json.encodeToString(snapshot))
            }
        }

        fun loadActiveSnapshot(context: Context): ActiveTripSnapshot? {
            val f = File(context.filesDir, SNAPSHOT_FILE)
            if (!f.exists()) return null
            return runCatching { json.decodeFromString<ActiveTripSnapshot>(f.readText()) }.getOrNull()
        }

        fun clearActiveSnapshot(context: Context) {
            runCatching { File(context.filesDir, SNAPSHOT_FILE).delete() }
        }

        internal fun formatNotificationContent(state: TripUiState): String {
            val dist = if (state.remainingM >= 1000.0) {
                String.format(Locale.US, "%.1f km left", state.remainingM / 1000.0)
            } else {
                "${state.remainingM.toInt()} m left"
            }
            val src = when (state.source) {
                PositionSourceKind.GPS -> "GPS"
                PositionSourceKind.CELL -> "Cell fallback"
                PositionSourceKind.DEAD_RECKONING -> if (state.lowConfidence) "Dead reckoning (low conf)" else "Dead reckoning"
            }
            val eta = state.etaMin?.let { " • Arrive in $it min" } ?: ""
            return "$dist$eta • $src"
        }

        private fun buildNotification(context: Context, state: TripUiState): Notification {
            val openApp = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val title = if (state.phase == TripPhase.ALERTING) {
                "Para Po! Malapit na!"
            } else {
                "Para Po! Watching your stop"
            }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_fox_mono)
                .setContentTitle(title)
                .setContentText(formatNotificationContent(state))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp)
                .build()
        }

        private fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Trip Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent status-bar notification while Para Po! tracks your ride."
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }
}
