package com.parapo.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.max

@Serializable
data class Profile(
    val address: String = "",
    val email: String = ""
)

/**
 * Local profile: address, Gmail (profile.json) and an optional round photo
 * (profile_photo.png). Everything stays on this phone, like the rest of the app's data.
 */
object ProfileStore {
    private const val FILE_NAME = "profile.json"
    private const val PHOTO_NAME = "profile_photo.png"
    private const val LEGACY_PHOTO_NAME = "profile_photo.jpg"
    private const val PHOTO_SIZE_PX = 512

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    private val _profile = MutableStateFlow(Profile())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    private val _photo = MutableStateFlow<ImageBitmap?>(null)
    val photo: StateFlow<ImageBitmap?> = _photo.asStateFlow()

    fun load(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        _profile.value = if (file.exists()) {
            runCatching { json.decodeFromString<Profile>(file.readText()) }.getOrNull() ?: Profile()
        } else {
            Profile()
        }
        _photo.value = readPhoto(context)
    }

    fun save(context: Context, profile: Profile) {
        _profile.value = profile
        File(context.filesDir, FILE_NAME).writeText(json.encodeToString(profile))
    }

    /** Decodes a picked image (shrunk, upright) so the crop screen can show it. Null if unreadable. */
    fun loadForCrop(context: Context, uri: Uri): Bitmap? = runCatching {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return@runCatching null

        val orientation = resolver.openInputStream(uri)?.use {
            runCatching {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) {
            decoded
        } else {
            Bitmap.createBitmap(
                decoded, 0, 0, decoded.width, decoded.height,
                Matrix().apply { postRotate(degrees) }, true
            )
        }
    }.getOrNull()

    /** Stores the square the user framed in the crop screen as a round (transparent-corner) PNG. */
    fun savePhoto(context: Context, square: Bitmap): Boolean = runCatching {
        val size = PHOTO_SIZE_PX
        val round = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(round)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(square, Rect(0, 0, square.width, square.height), Rect(0, 0, size, size), paint)

        val tmp = File(context.filesDir, "$PHOTO_NAME.tmp")
        tmp.outputStream().use { round.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val target = File(context.filesDir, PHOTO_NAME)
        if (target.exists()) target.delete()
        tmp.renameTo(target)
        File(context.filesDir, LEGACY_PHOTO_NAME).delete()

        _photo.value = round.asImageBitmap()
        true
    }.getOrDefault(false)

    fun removePhoto(context: Context) {
        File(context.filesDir, PHOTO_NAME).delete()
        File(context.filesDir, LEGACY_PHOTO_NAME).delete()
        _photo.value = null
    }

    /** Used when the account is wiped ("Forgot password?"). */
    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
        File(context.filesDir, PHOTO_NAME).delete()
        File(context.filesDir, LEGACY_PHOTO_NAME).delete()
        _profile.value = Profile()
        _photo.value = null
    }

    private fun readPhoto(context: Context): ImageBitmap? {
        val file = listOf(PHOTO_NAME, LEGACY_PHOTO_NAME)
            .map { File(context.filesDir, it) }
            .firstOrNull { it.exists() } ?: return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
    }
}
