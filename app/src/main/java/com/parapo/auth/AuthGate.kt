package com.parapo.auth

import android.content.Context
import com.parapo.storage.AuthData
import com.parapo.storage.SettingsStore
import com.parapo.storage.TripLogStore
import com.parapo.util.Constants
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * FR-01: Local PIN authentication backed by PBKDF2-HMAC-SHA256 salted hash in auth.json.
 * Never shown during an active trip; "Forgot PIN?" wipes local data and restarts PIN setup.
 */
object AuthGate {

    private const val AUTH_FILE = "auth.json"
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun authFile(context: Context): File = File(context.filesDir, AUTH_FILE)

    fun isPinSet(context: Context): Boolean = loadAuth(context) != null

    fun getUsername(context: Context): String =
        loadAuth(context)?.username?.ifBlank { "Juan" } ?: "Juan"

    fun loadAuth(context: Context): AuthData? {
        val f = authFile(context)
        if (!f.exists()) return null
        return runCatching { json.decodeFromString<AuthData>(f.readText()) }.getOrNull()
    }

    fun setupPin(context: Context, username: String, pin: String) {
        val cleanName = username.trim().ifBlank { "Juan" }
        val salt = generateSalt()
        val hash = hashPin(pin, salt, Constants.PBKDF2_ITERATIONS)
        val data = AuthData(
            username = cleanName,
            salt = Base64.getEncoder().encodeToString(salt),
            hash = Base64.getEncoder().encodeToString(hash),
            iterations = Constants.PBKDF2_ITERATIONS
        )
        authFile(context).writeText(json.encodeToString(data))
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val data = loadAuth(context) ?: return false
        return verifyHash(pin, data)
    }

    fun changePin(context: Context, currentPin: String, newPin: String): Boolean {
        val data = loadAuth(context) ?: return false
        if (!verifyHash(currentPin, data)) return false
        setupPin(context, data.username, newPin)
        return true
    }

    /** Wipes auth, settings, trip logs, cached routes, and learned cell fingerprints. */
    fun resetAllData(context: Context) {
        authFile(context).delete()
        SettingsStore.reset(context)
        TripLogStore.clear(context)
        File(context.filesDir, "routes").deleteRecursively()
        File(context.filesDir, "fingerprints").deleteRecursively()
    }

    // --- Pure crypto helpers (unit-testable on JVM without Android Context) ---

    internal fun generateSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    internal fun hashPin(pin: String, salt: ByteArray, iterations: Int = Constants.PBKDF2_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    internal fun verifyHash(pin: String, data: AuthData): Boolean {
        val salt = runCatching { Base64.getDecoder().decode(data.salt) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(data.hash) }.getOrNull() ?: return false
        val actual = hashPin(pin, salt, data.iterations)
        return MessageDigest.isEqual(actual, expected)
    }
}
