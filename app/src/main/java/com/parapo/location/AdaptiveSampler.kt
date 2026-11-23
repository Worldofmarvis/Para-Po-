package com.parapo.location

import com.google.android.gms.location.Priority

enum class SamplingTier { NEAR, FAR_SCREEN_ON, FAR_SCREEN_OFF }

data class SamplingProfile(
    val tier: SamplingTier,
    val intervalMs: Long,
    val minUpdateIntervalMs: Long,
    val priority: Int
)

/**
 * FR-06: Battery-saving adaptive GPS sampling policy (Build Guide 7.5).
 * - Near (remaining <= 2 * trigger): 1.5 s interval, high accuracy
 * - Far + screen on (remaining > 2 * trigger): 5 s interval, balanced power
 * - Far + screen off: 10 s interval, balanced power (keeps snap error < ~100 m)
 */
object AdaptiveSampler {

    val NEAR_PROFILE = SamplingProfile(
        tier = SamplingTier.NEAR,
        intervalMs = 1_500L,
        minUpdateIntervalMs = 1_000L,
        priority = Priority.PRIORITY_HIGH_ACCURACY
    )

    val FAR_SCREEN_ON_PROFILE = SamplingProfile(
        tier = SamplingTier.FAR_SCREEN_ON,
        intervalMs = 5_000L,
        minUpdateIntervalMs = 3_000L,
        priority = Priority.PRIORITY_HIGH_ACCURACY
    )

    val FAR_SCREEN_OFF_PROFILE = SamplingProfile(
        tier = SamplingTier.FAR_SCREEN_OFF,
        intervalMs = 10_000L,
        minUpdateIntervalMs = 5_000L,
        priority = Priority.PRIORITY_HIGH_ACCURACY
    )

    fun choose(remainingM: Double, triggerM: Double, isScreenInteractive: Boolean): SamplingProfile {
        val threshold = triggerM * 2.0
        return when {
            remainingM <= threshold -> NEAR_PROFILE
            isScreenInteractive -> FAR_SCREEN_ON_PROFILE
            else -> FAR_SCREEN_OFF_PROFILE
        }
    }
}
