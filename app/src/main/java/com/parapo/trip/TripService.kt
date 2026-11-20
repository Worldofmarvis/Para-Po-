package com.parapo.trip

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Foreground service that keeps trip tracking alive when the screen is off (FR-05).
 * Full implementation is completed in Phase 5.
 */
class TripService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
