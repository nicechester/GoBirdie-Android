package io.github.nicechester.gobirdie.core.model

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
data class HoleMapMeta(
    val holeNumber: Int,
    val version: String,
    val swLat: Double,
    val swLon: Double,
    val neLat: Double,
    val neLon: Double,
    val imageWidth: Int,
    val imageHeight: Int,
    val bearing: Double,
)

fun teeToPinBearing(tee: GpsPoint, green: GpsPoint): Double {
    val dLon = Math.toRadians(green.lon - tee.lon)
    val lat1 = Math.toRadians(tee.lat)
    val lat2 = Math.toRadians(green.lat)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}
