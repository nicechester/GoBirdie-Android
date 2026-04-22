package io.github.nicechester.gobirdie.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class HoleScore(
    val id: String = java.util.UUID.randomUUID().toString(),
    val number: Int,
    val par: Int,
    val strokes: Int = 0,
    val putts: Int = 0,
    @SerialName("fairway_hit") val fairwayHit: Boolean? = null,
    val gir: Boolean = false,
    val penalties: Int = 0,
    val shots: List<Shot> = emptyList(),
    @SerialName("green_center") val greenCenter: GpsPoint? = null,
) {
    @Transient
    val computedGir: Boolean = (strokes - putts) <= (par - 2)

    @Transient
    val scoreVsPar: Int = strokes - par
}
