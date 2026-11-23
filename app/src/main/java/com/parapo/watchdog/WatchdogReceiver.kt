package com.parapo.watchdog

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.parapo.trip.TripController
import com.parapo.trip.TripPhase
import com.parapo.trip.TripService

/**
 * FR-14 (TC-11): AlarmManager watchdog receiver that checks every 60 s while a trip is active
 * and revives TripService if aggressive OEM battery managers killed the process mid-trip.
 */
class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appCtx = context.applicationContext
        val snapshot = TripService.loadActiveSnapshot(appCtx) ?: return

        if (TripController.state.value.phase == TripPhase.IDLE) {
            val serviceIntent = Intent(appCtx, TripService::class.java)
                .setAction(TripService.ACTION_START)
            runCatching { appCtx.startForegroundService(serviceIntent) }
        }
        // Keep watchdog armed while snapshot exists
        schedule(appCtx)
    }

    companion object {
        private const val REQUEST_CODE = 3001
        private const val INTERVAL_MS = 60_000L

        private fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, WatchdogReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        fun schedule(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val triggerAt = System.currentTimeMillis() + INTERVAL_MS
            val pi = pendingIntent(context)
            runCatching {
                val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.canScheduleExactAlarms()
                } else {
                    true
                }
                if (canExact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            }
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            runCatching { am.cancel(pendingIntent(context)) }
        }
    }
}
