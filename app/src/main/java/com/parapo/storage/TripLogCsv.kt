package com.parapo.storage

import android.content.Context
import java.io.File

/**
 * FR-15: Exports trip logs to CSV matching Build Guide 7.4 evaluation columns:
 * tripId, startedAt, mode, triggerM, firedAtRemainingM, errorM, source, gpsLostS, batteryStartPct, batteryEndPct
 */
object TripLogCsv {

    const val HEADER =
        "tripId,startedAt,mode,triggerM,firedAtRemainingM,errorM,source,gpsLostS,batteryStartPct,batteryEndPct"

    fun format(trips: List<TripLog>): String = buildString {
        appendLine(HEADER)
        trips.forEach { t ->
            val errorM = t.alertFiredAtRemainingM - t.triggerM
            append(escape(t.id)).append(',')
            append(t.startedAt).append(',')
            append(t.mode.name).append(',')
            append(t.triggerM).append(',')
            append(t.alertFiredAtRemainingM).append(',')
            append(errorM).append(',')
            append(escape(t.source)).append(',')
            append(t.gpsLostS).append(',')
            append(t.batteryStartPct).append(',')
            appendLine(t.batteryEndPct)
        }
    }

    fun exportToFile(context: Context, trips: List<TripLog>): File {
        val dir = context.getExternalFilesDir(null) ?: File(context.filesDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "parapo_trips.csv")
        file.writeText(format(trips))
        return file
    }

    private fun escape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}
