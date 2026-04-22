package io.github.nicechester.gobirdie.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Hole(
    val id: String = java.util.UUID.randomUUID().toString(),
    val number: Int,
    val par: Int,
    val handicap: Int? = null,
    val yardage: String? = null,
    val tee: GpsPoint? = null,
    @SerialName("green_center") val greenCenter: GpsPoint? = null,
    @SerialName("green_front") val greenFront: GpsPoint? = null,
    @SerialName("green_back") val greenBack: GpsPoint? = null,
    val geometry: HoleGeometry? = null,
)
