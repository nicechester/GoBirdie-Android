package io.github.nicechester.gobirdie.core.model

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
data class GpsPoint(
    val lat: Double,
    val lon: Double,
) {
    fun distanceMeters(to: GpsPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(to.lat - lat)
        val dLon = Math.toRadians(to.lon - lon)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(to.lat)) *
            sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun distanceYards(to: GpsPoint): Int =
        (distanceMeters(to) * 1.09361).roundToInt()
}
