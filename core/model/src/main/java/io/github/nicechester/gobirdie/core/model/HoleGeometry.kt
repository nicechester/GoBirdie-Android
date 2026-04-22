package io.github.nicechester.gobirdie.core.model

import kotlinx.serialization.Serializable

typealias Polygon = List<GpsPoint>

@Serializable
data class HoleGeometry(
    val fairway: Polygon? = null,
    val bunkers: List<Polygon> = emptyList(),
    val water: List<Polygon> = emptyList(),
    val rough: Polygon? = null,
)
