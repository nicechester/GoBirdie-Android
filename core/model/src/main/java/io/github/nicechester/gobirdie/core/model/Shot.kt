package io.github.nicechester.gobirdie.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Shot(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sequence: Int,
    val location: GpsPoint,
    val timestamp: String, // ISO-8601
    val club: ClubType = ClubType.UNKNOWN,
    @SerialName("distance_to_pin_yards") val distanceToPinYards: Int? = null,
    @SerialName("altitude_meters") val altitudeMeters: Double? = null,
    @SerialName("heart_rate_bpm") val heartRateBpm: Int? = null,
    @SerialName("temperature_celsius") val temperatureCelsius: Double? = null,
)
