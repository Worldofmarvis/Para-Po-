package com.parapo

import com.parapo.auth.AuthGate
import com.parapo.storage.AuthData
import com.parapo.storage.TripLog
import com.parapo.storage.TripLogCsv
import com.parapo.util.Constants
import com.parapo.util.VehicleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class Phase2UnitTest {

    @Test
    fun authGate_pbkdf2HashAndVerify_matchesCorrectPinAndRejectsWrongPin() {
        val salt = AuthGate.generateSalt()
        val hash = AuthGate.hashPin("1234", salt, Constants.PBKDF2_ITERATIONS)
        val data = AuthData(
            username = "Juan",
            salt = Base64.getEncoder().encodeToString(salt),
            hash = Base64.getEncoder().encodeToString(hash),
            iterations = Constants.PBKDF2_ITERATIONS
        )

        assertTrue(AuthGate.verifyHash("1234", data))
        assertFalse(AuthGate.verifyHash("9999", data))
        assertFalse(AuthGate.verifyHash("123", data))
    }

    @Test
    fun authGate_generateSalt_producesUniqueSalts() {
        val saltA = Base64.getEncoder().encodeToString(AuthGate.generateSalt())
        val saltB = Base64.getEncoder().encodeToString(AuthGate.generateSalt())
        assertNotEquals(saltA, saltB)
    }

    @Test
    fun tripLogCsv_formatsExactBuildGuideColumnsAndError() {
        val trips = listOf(
            TripLog(
                id = "t_001",
                routeId = "r_8f3a",
                startedAt = 1763430000L,
                endedAt = 1763431200L,
                mode = VehicleMode.BUS,
                triggerM = 3000,
                alertFiredAtRemainingM = 3084,
                source = "GPS+CELL",
                gpsLostS = 100,
                newFingerprints = 14,
                result = "ON_TIME",
                routeLabel = "Batangas City -> Lipa City",
                totalDistanceM = 24310,
                batteryStartPct = 85,
                batteryEndPct = 83
            )
        )

        val lines = TripLogCsv.format(trips).trim().lines()
        assertEquals(2, lines.size)
        assertEquals(
            "tripId,startedAt,mode,triggerM,firedAtRemainingM,errorM,source,gpsLostS,batteryStartPct,batteryEndPct",
            lines[0]
        )
        assertEquals(
            "t_001,1763430000,BUS,3000,3084,84,GPS+CELL,100,85,83",
            lines[1]
        )
    }
}
