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
 * FR-01: Local authentication, no server. One account lives on this phone in auth.json:
 *  - username + password (PBKDF2-HMAC-SHA256 salted hash)
 *  - a 4-digit unlock PIN (hashed the same way), created right after sign up
 *  - a "keep this device signed in" flag: when true, launch asks only the PIN
 * Never shown during an active trip. "Forgot password?" wipes local data and restarts signup;
 * "Forgot PIN?" lets the user set a new PIN after logging in with the password.
 *
 * Accounts from the old PIN-only version still work: the username is kept, the old PIN is
 * that account's password, and a PIN is requested at the next login.
 */
object AuthGate {

    private const val AUTH_FILE = "auth.json"
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    const val USERNAME_MIN = 3
    const val USERNAME_MAX = 24
    const val PASSWORD_MIN = 6
    const val PASSWORD_MAX = 64
    const val PIN_LENGTH = 4

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    private fun authFile(context: Context): File = File(context.filesDir, AUTH_FILE)

    private fun save(context: Context, data: AuthData) {
        authFile(context).writeText(json.encodeToString(data))
    }

    /** True once an account has been created on this phone. */
    fun hasAccount(context: Context): Boolean = loadAuth(context) != null

    fun hasPin(context: Context): Boolean = loadAuth(context)?.pinHash != null

    fun isKeepSignedIn(context: Context): Boolean = loadAuth(context)?.keepSignedIn == true

    fun setKeepSignedIn(context: Context, keep: Boolean) {
        val data = loadAuth(context) ?: return
        save(context, data.copy(keepSignedIn = keep))
    }

    fun getUsername(context: Context): String =
        loadAuth(context)?.username?.ifBlank { "Juan" } ?: "Juan"

    fun loadAuth(context: Context): AuthData? {
        val f = authFile(context)
        if (!f.exists()) return null
        return runCatching { json.decodeFromString<AuthData>(f.readText()) }.getOrNull()
    }

    /** Creates (or replaces) the local account. No PIN yet and not kept signed in. Callers validate first. */
    fun register(context: Context, username: String, password: String) {
        val salt = generateSalt()
        val hash = hashPassword(password, salt, Constants.PBKDF2_ITERATIONS)
        save(
            context,
            AuthData(
                username = username.trim().ifBlank { "Juan" },
                salt = b64(salt),
                hash = b64(hash),
                iterations = Constants.PBKDF2_ITERATIONS
            )
        )
    }

    /** Username is matched case-insensitively; the password is checked against the salted hash. */
    fun login(context: Context, username: String, password: String): Boolean {
        val data = loadAuth(context) ?: return false
        // Always hash, so a wrong username and a wrong password take the same time.
        val passwordOk = verifyHash(password, data)
        val userOk = data.username.trim().equals(username.trim(), ignoreCase = true)
        return userOk && passwordOk
    }

    fun changePassword(context: Context, currentPassword: String, newPassword: String): Boolean {
        val data = loadAuth(context) ?: return false
        if (!verifyHash(currentPassword, data)) return false
        val salt = generateSalt()
        val hash = hashPassword(newPassword, salt, Constants.PBKDF2_ITERATIONS)
        save(
            context,
            data.copy(salt = b64(salt), hash = b64(hash), iterations = Constants.PBKDF2_ITERATIONS)
        )
        return true
    }

    /** Sets or replaces the unlock PIN (after sign up, after "Forgot PIN?", or for old PIN-only accounts). */
    fun setPin(context: Context, pin: String) {
        val data = loadAuth(context) ?: return
        val salt = generateSalt()
        val hash = hashPassword(pin, salt, Constants.PBKDF2_ITERATIONS)
        save(
            context,
            data.copy(pinSalt = b64(salt), pinHash = b64(hash), pinIterations = Constants.PBKDF2_ITERATIONS)
        )
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val data = loadAuth(context) ?: return false
        val salt = data.pinSalt ?: return false
        val hash = data.pinHash ?: return false
        return verifySecret(pin, salt, hash, data.pinIterations)
    }

    fun changePin(context: Context, currentPin: String, newPin: String): Boolean {
        if (!verifyPin(context, currentPin)) return false
        setPin(context, newPin)
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

    // --- Pure helpers (unit-testable on JVM without Android Context) ---

    /** Returns an error message, or null if the username is acceptable. */
    fun usernameError(username: String): String? {
        val u = username.trim()
        return when {
            u.length < USERNAME_MIN -> "Username must be at least $USERNAME_MIN characters."
            u.length > USERNAME_MAX -> "Username must be at most $USERNAME_MAX characters."
            !u.all { it.isLetterOrDigit() || it == '_' || it == '.' || it == ' ' } ->
                "Use letters, numbers, spaces, _ or . only."
            else -> null
        }
    }

    /** Returns an error message, or null if the password is acceptable. */
    fun passwordError(password: String): String? = when {
        password.length < PASSWORD_MIN -> "Password must be at least $PASSWORD_MIN characters."
        password.length > PASSWORD_MAX -> "Password must be at most $PASSWORD_MAX characters."
        password.isBlank() -> "Password can't be only spaces."
        else -> null
    }

    /** Returns an error message, or null if the PIN is exactly [PIN_LENGTH] digits. */
    fun pinError(pin: String): String? =
        if (pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }) null
        else "PIN must be $PIN_LENGTH digits."

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    internal fun generateSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    internal fun hashPassword(password: String, salt: ByteArray, iterations: Int = Constants.PBKDF2_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    internal fun verifySecret(secret: String, saltB64: String, hashB64: String, iterations: Int): Boolean {
        val salt = runCatching { Base64.getDecoder().decode(saltB64) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(hashB64) }.getOrNull() ?: return false
        val actual = hashPassword(secret, salt, iterations)
        return MessageDigest.isEqual(actual, expected)
    }

    internal fun verifyHash(password: String, data: AuthData): Boolean =
        verifySecret(password, data.salt, data.hash, data.iterations)
}
