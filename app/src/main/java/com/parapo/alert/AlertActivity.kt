package com.parapo.alert

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.parapo.MainActivity
import com.parapo.trip.TripController
import com.parapo.trip.TripPhase
import com.parapo.ui.AlertScreen
import com.parapo.ui.TripSummaryStore
import com.parapo.ui.theme.ParaPoTheme

/**
 * FR-13: Full-screen alert activity shown over the lock screen at the final escalation step.
 * Keeps the screen awake until the commuter taps "GISING NA AKO".
 */
class AlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        runCatching {
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        }

        setContent {
            ParaPoTheme {
                AlertScreen(
                    onAwake = {
                        val trip = TripController.state.value
                        // Only a real trip alert has a summary; the Home "test alert" does not.
                        if (trip.phase == TripPhase.ALERTING) {
                            TripSummaryStore.gpsLostS = trip.gpsLostS
                            TripSummaryStore.newFingerprints = trip.newFingerprints
                            TripSummaryStore.finish(
                                remainingNowM = trip.remainingM,
                                fallbackStartedAtMs = trip.startedAtMs,
                                fallbackFiredRemainingM = trip.firedAtRemainingM ?: trip.remainingM
                            )
                        }
                        AlertManager.stop(applicationContext)
                        TripController.dismissAlert()
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            // CLEAR_TOP + SINGLE_TOP reuses the MainActivity that is already running
                            // (no new instance, so no splash/login/PIN). A new one is only created
                            // if the app process had been closed.
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                        }
                        startActivity(mainIntent)
                        finish()
                    }
                )
            }
        }
    }
}
