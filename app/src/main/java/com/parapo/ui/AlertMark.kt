package com.parapo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.parapo.distance.DistanceEngine
import org.osmdroid.util.GeoPoint

/**
 * The spot on the route where the alert will pop up: [alertDistM] before the stop, measured the
 * same way the trip engine measures it (remaining distance = route length left x [scale]).
 * [scale] is 1.0 for a real road route, or the road factor (1.25) for a straight-line fallback.
 * Returns null when the whole trip is shorter than the alert distance (alert fires right away).
 */
internal fun alertMarkPoint(points: List<GeoPoint>, scale: Double, alertDistM: Double): GeoPoint? {
    if (points.size < 2 || alertDistM <= 0.0 || scale <= 0.0) return null
    val engine = DistanceEngine(points, scale)
    val progress = engine.lengthM - alertDistM / scale
    return if (progress <= 0.0) null else engine.pointAt(progress)
}

/** Orange round badge with a white "!" for the alert spot. */
internal fun alertMarkDrawable(ctx: Context): Drawable {
    val d = ctx.resources.displayMetrics.density
    val s = (44 * d).toInt()
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val m = s / 2f
    val orange = 0xFFFF7A1A.toInt()
    p.color = (orange and 0x00FFFFFF) or 0x40000000
    c.drawCircle(m, m, 20 * d, p)
    p.color = android.graphics.Color.WHITE
    c.drawCircle(m, m, 14 * d, p)
    p.color = orange
    c.drawCircle(m, m, 11 * d, p)
    p.color = android.graphics.Color.WHITE
    p.textAlign = Paint.Align.CENTER
    p.typeface = Typeface.DEFAULT_BOLD
    p.textSize = 16 * d
    c.drawText("!", m, m + 5.5f * d, p)
    return BitmapDrawable(ctx.resources, bmp)
}
