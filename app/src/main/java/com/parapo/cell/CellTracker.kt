package com.parapo.cell

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Normalized serving cell reading (Build Guide 7.4). */
data class ServingCell(
    val cellKey: String,   // "MCC-MNC-areaCode-cellId-TECH"
    val tech: String,      // "LTE", "NR", "WCDMA", "GSM"
    val areaCode: Int,     // TAC (LTE/NR) or LAC (GSM/WCDMA)
    val rssi: Int          // dBm signal strength
)

/** Abstraction over TelephonyManager so unit tests and outage simulations can inject mock cells. */
interface CellProvider {
    suspend fun readServingCell(): ServingCell?
}

/**
 * FR-10 / FR-12: Reads the currently registered serving cell via TelephonyManager.requestCellInfoUpdate()
 * (API 29+). Gracefully returns null on missing SIM, revoked permission, stale/unavailable IDs, or timeout (TC-13).
 */
class AndroidCellTracker(private val context: Context) : CellProvider {

    private val telephony = context.getSystemService(TelephonyManager::class.java)

    @SuppressLint("MissingPermission")
    override suspend fun readServingCell(): ServingCell? {
        val tm = telephony ?: return null
        val cellList = withTimeoutOrNull(2_000L) {
            suspendCancellableCoroutine<List<CellInfo>?> { cont ->
                try {
                    tm.requestCellInfoUpdate(
                        context.mainExecutor,
                        object : TelephonyManager.CellInfoCallback() {
                            override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                                if (cont.isActive) cont.resume(cellInfo)
                            }

                            override fun onError(errorCode: Int, detail: Throwable?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        }
                    )
                } catch (_: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        } ?: runCatching { tm.allCellInfo }.getOrNull()

        return extractRegisteredCell(cellList)
    }

    companion object {
        private fun isValidInt(v: Int): Boolean = v != CellInfo.UNAVAILABLE && v >= 0 && v != Int.MAX_VALUE
        private fun isValidLong(v: Long): Boolean = v != CellInfo.UNAVAILABLE_LONG && v >= 0L && v != Long.MAX_VALUE

        internal fun extractRegisteredCell(cells: List<CellInfo>?): ServingCell? {
            if (cells.isNullOrEmpty()) return null
            val registered = cells.filter { it.isRegistered }
            val candidates = registered.ifEmpty { cells }
            for (info in candidates) {
                parseCellInfo(info)?.let { return it }
            }
            return null
        }

        private fun parseCellInfo(info: CellInfo): ServingCell? = when (info) {
            is CellInfoLte -> {
                val id: CellIdentityLte = info.cellIdentity
                val ci = id.ci
                val tac = id.tac
                val mcc = id.mccString ?: return null
                val mnc = id.mncString ?: return null
                if (!isValidInt(ci) || !isValidInt(tac)) return null
                ServingCell(
                    cellKey = "$mcc-$mnc-$tac-$ci-LTE",
                    tech = "LTE",
                    areaCode = tac,
                    rssi = info.cellSignalStrength.dbm
                )
            }
            is CellInfoGsm -> {
                val id: CellIdentityGsm = info.cellIdentity
                val cid = id.cid
                val lac = id.lac
                val mcc = id.mccString ?: return null
                val mnc = id.mncString ?: return null
                if (!isValidInt(cid) || !isValidInt(lac)) return null
                ServingCell(
                    cellKey = "$mcc-$mnc-$lac-$cid-GSM",
                    tech = "GSM",
                    areaCode = lac,
                    rssi = info.cellSignalStrength.dbm
                )
            }
            is CellInfoWcdma -> {
                val id: CellIdentityWcdma = info.cellIdentity
                val cid = id.cid
                val lac = id.lac
                val mcc = id.mccString ?: return null
                val mnc = id.mncString ?: return null
                if (!isValidInt(cid) || !isValidInt(lac)) return null
                ServingCell(
                    cellKey = "$mcc-$mnc-$lac-$cid-WCDMA",
                    tech = "WCDMA",
                    areaCode = lac,
                    rssi = info.cellSignalStrength.dbm
                )
            }
            is CellInfoNr -> {
                val id = info.cellIdentity as? CellIdentityNr ?: return null
                val nci = id.nci
                val tac = id.tac
                val mcc = id.mccString ?: return null
                val mnc = id.mncString ?: return null
                if (!isValidLong(nci) || !isValidInt(tac)) return null
                ServingCell(
                    cellKey = "$mcc-$mnc-$tac-$nci-NR",
                    tech = "NR",
                    areaCode = tac,
                    rssi = info.cellSignalStrength.dbm
                )
            }
            else -> null
        }
    }
}
