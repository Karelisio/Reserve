package com.karelisio.reserve.domain

import com.karelisio.reserve.data.model.LatLon
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_KM = 6371.0

    fun distanceKm(a: LatLon, b: LatLon): Double {
        val dLat = (b.lat - a.lat).toRad()
        val dLon = (b.lon - a.lon).toRad()
        val h = sin(dLat / 2).let { it * it } +
            cos(a.lat.toRad()) * cos(b.lat.toRad()) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Shortest distance from [p] to the polyline, with a local equirectangular projection. */
    fun distanceToPolylineKm(p: LatLon, line: List<LatLon>): Double {
        if (line.isEmpty()) return Double.MAX_VALUE
        if (line.size == 1) return distanceKm(p, line[0])
        val kx = cos(p.lat.toRad()) * EARTH_RADIUS_KM * PI / 180
        val ky = EARTH_RADIUS_KM * PI / 180
        var best = Double.MAX_VALUE
        for (i in 0 until line.size - 1) {
            val ax = (line[i].lon - p.lon) * kx
            val ay = (line[i].lat - p.lat) * ky
            val bx = (line[i + 1].lon - p.lon) * kx
            val by = (line[i + 1].lat - p.lat) * ky
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val x = ax + t * dx
            val y = ay + t * dy
            val d = sqrt(x * x + y * y)
            if (d < best) best = d
        }
        return best
    }

    /** Points every [stepKm] along the polyline (first and last included). */
    fun resample(line: List<LatLon>, stepKm: Double): List<LatLon> {
        if (line.size < 2) return line
        val out = mutableListOf(line.first())
        var carried = 0.0
        for (i in 0 until line.size - 1) {
            val a = line[i]
            val b = line[i + 1]
            val seg = distanceKm(a, b)
            if (seg == 0.0) continue
            var pos = stepKm - carried
            while (pos <= seg) {
                val t = pos / seg
                out += LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
                pos += stepKm
            }
            carried = seg - (pos - stepKm)
        }
        if (out.last() != line.last()) out += line.last()
        return out
    }

    /** Coarse grid cell (~20 km) used to group prices of "my zone". */
    fun zoneKey(p: LatLon): String {
        val step = 0.2
        return "${floor(p.lat / step).toInt()}_${floor(p.lon / step).toInt()}"
    }

    private fun Double.toRad() = this * PI / 180
}
