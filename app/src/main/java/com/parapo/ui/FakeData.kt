package com.parapo.ui

/** SAMPLE DATA ONLY, for building the screens. Replaced by Storage (trips.json) later. */
enum class AlertResult(val label: String) {
    ON_TIME("On time"),
    LATE("Late")
}

data class RecentTrip(
    val from: String,
    val to: String,
    val distanceKm: Double,
    val dateText: String,
    val result: AlertResult
)

val fakeRecentTrips = listOf(
    RecentTrip("Batangas City", "Lipa City", 33.4, "Nov 17", AlertResult.ON_TIME),
    RecentTrip("Alangilan", "Tanauan", 41.2, "Nov 16", AlertResult.ON_TIME),
    RecentTrip("Lipa City", "Batangas City", 33.1, "Nov 15", AlertResult.LATE),
    RecentTrip("Tanauan", "Sto. Tomas", 12.6, "Nov 12", AlertResult.ON_TIME)
)
