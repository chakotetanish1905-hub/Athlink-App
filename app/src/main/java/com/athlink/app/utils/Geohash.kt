package com.athlink.app.utils

import kotlin.math.roundToLong

/**
 * Minimal geohash encoder for future map-based coach discovery (no extra library needed).
 *
 * Privacy: callers store only the COACHING AREA / venue location, never a home address, and pass
 * it through [coarsen] first. Precision 6 is a cell of about 1.2 km x 0.6 km: enough for
 * "coaches near me" queries without pinpointing anyone.
 */
object Geohash {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    const val DEFAULT_PRECISION = 6

    fun encode(lat: Double, lng: Double, precision: Int = DEFAULT_PRECISION): String {
        require(lat in -90.0..90.0 && lng in -180.0..180.0) { "Invalid coordinates" }
        require(precision in 1..12) { "Precision must be 1..12" }
        var latMin = -90.0; var latMax = 90.0
        var lngMin = -180.0; var lngMax = 180.0
        val sb = StringBuilder()
        var bit = 0; var ch = 0; var even = true
        while (sb.length < precision) {
            if (even) {
                val mid = (lngMin + lngMax) / 2
                if (lng >= mid) { ch = ch or (1 shl (4 - bit)); lngMin = mid } else lngMax = mid
            } else {
                val mid = (latMin + latMax) / 2
                if (lat >= mid) { ch = ch or (1 shl (4 - bit)); latMin = mid } else latMax = mid
            }
            even = !even
            if (bit < 4) bit++ else { sb.append(BASE32[ch]); bit = 0; ch = 0 }
        }
        return sb.toString()
    }

    /** Rounds coordinates to 3 decimal places (about 110 m) before they are stored. */
    fun coarsen(value: Double): Double = (value * 1000).roundToLong() / 1000.0
}
