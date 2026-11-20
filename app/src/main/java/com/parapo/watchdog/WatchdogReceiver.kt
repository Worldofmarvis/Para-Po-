package com.parapo.watchdog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * AlarmManager watchdog receiver that revives TripService if killed mid-trip (FR-14).
 * Full implementation is completed in Phase 5.
 */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Restores tracking state in Phase 5.
    }
}
