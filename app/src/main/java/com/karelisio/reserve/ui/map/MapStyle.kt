package com.karelisio.reserve.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import com.karelisio.reserve.container
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import java.util.Locale

/** MapLibre style using OpenStreetMap raster tiles, tuned to the light or dark theme. */
object MapStyle {

    @Volatile
    private var initialized = false

    /** MapLibre is started on first use of a map, not at app launch (faster startup). */
    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            MapLibre.getInstance(context.applicationContext)
            // Tiles are fetched with the app User-Agent, as required by the OSM tile policy.
            HttpRequestUtil.setOkHttpClient(context.container.http)
            initialized = true
        }
    }

    fun json(dark: Boolean, background: Int): String {
        val bg = String.format(Locale.ROOT, "#%06X", background and 0xFFFFFF)
        // Dark mode: rotate hues then invert brightness, so colors keep their meaning.
        val paint = if (dark) {
            """{"raster-hue-rotate": 180, "raster-brightness-min": 0.9, "raster-brightness-max": 0.08,
                "raster-saturation": -0.35, "raster-contrast": 0.05, "raster-opacity": 0.94}"""
        } else {
            """{"raster-saturation": -0.25, "raster-opacity": 0.92}"""
        }
        return """
        {
          "version": 8,
          "name": "Reserve",
          "sources": {
            "osm": {
              "type": "raster",
              "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
              "tileSize": 256,
              "maxzoom": 19,
              "attribution": "© <a href=\"https://www.openstreetmap.org/copyright\">Contributeurs OpenStreetMap</a>"
            }
          },
          "layers": [
            {"id": "background", "type": "background", "paint": {"background-color": "$bg"}},
            {"id": "osm", "type": "raster", "source": "osm", "paint": $paint}
          ]
        }
        """.trimIndent()
    }

    /** Price "pill" pointing to the station, drawn in the price scale color. */
    fun priceMarker(context: Context, text: String, fill: Int, textColor: Int, outline: Int, large: Boolean = false): Bitmap {
        val density = context.resources.displayMetrics.density
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = (if (large) 15f else 13f) * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val padH = 9 * density
        val padV = 5 * density
        val pointer = 6 * density
        val stroke = 2 * density
        val textWidth = textPaint.measureText(text)
        val metrics = textPaint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val width = (textWidth + 2 * padH + 2 * stroke).toInt()
        val bodyHeight = textHeight + 2 * padV
        val height = (bodyHeight + pointer + 2 * stroke).toInt()

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val body = RectF(stroke, stroke, width - stroke, stroke + bodyHeight)
        val radius = bodyHeight / 2
        val shape = Path().apply {
            addRoundRect(body, radius, radius, Path.Direction.CW)
            moveTo(width / 2f - pointer, body.bottom - 1)
            lineTo(width / 2f, body.bottom + pointer)
            lineTo(width / 2f + pointer, body.bottom - 1)
            close()
        }
        canvas.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = outline
            style = Paint.Style.STROKE
            strokeWidth = stroke * 2
            strokeJoin = Paint.Join.ROUND
        })
        canvas.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        canvas.drawText(text, body.centerX() - textWidth / 2, body.top + padV - metrics.ascent, textPaint)
        return bitmap
    }
}
