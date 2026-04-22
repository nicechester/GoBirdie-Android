package io.github.nicechester.gobirdie.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Course(
    val id: String,
    val name: String,
    val location: GpsPoint,
    val holes: List<Hole> = emptyList(),
    @SerialName("downloaded_at") val downloadedAt: String = "", // ISO-8601
    @SerialName("osm_version") val osmVersion: Int = 0,
    @SerialName("golf_course_api_id") val golfCourseApiId: Int? = null,
)
