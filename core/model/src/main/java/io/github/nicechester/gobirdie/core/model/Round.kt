package io.github.nicechester.gobirdie.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HeartRateSample(
    val timestamp: String, // ISO-8601
    val bpm: Int,
    @SerialName("altitude_meters") val altitudeMeters: Double? = null,
)

@Serializable
data class Round(
    val id: String,
    val source: String = "android",
    @SerialName("course_id") val courseId: String,
    @SerialName("course_name") val courseName: String,
    @SerialName("started_at") val startedAt: String, // ISO-8601
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("holes_played") val holesPlayed: Int = 0,
    val holes: List<HoleScore> = emptyList(),
    @SerialName("total_strokes") val totalStrokes: Int = 0,
    @SerialName("total_putts") val totalPutts: Int = 0,
    @SerialName("heart_rate_timeline") val heartRateTimeline: List<HeartRateSample> = emptyList(),
    @SerialName("temperature_min_f") val temperatureMinF: Double? = null,
    @SerialName("temperature_max_f") val temperatureMaxF: Double? = null,
    @SerialName("weather_condition") val weatherCondition: String? = null,
)
