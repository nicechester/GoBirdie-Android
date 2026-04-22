package io.github.nicechester.gobirdie.core.data.api

import io.github.nicechester.gobirdie.core.model.GpsPoint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class GolfCourseResult(
    val id: Int,
    val name: String,
    val location: GpsPoint,
    val city: String,
)

data class GolfCourseHole(
    val number: Int,
    val par: Int,
    val yardage: Int,
    val handicap: Int?,
)

class GolfCourseApiClient(private val apiKey: String) {

    private val baseUrl = "https://api.golfcourseapi.com/v1"
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun searchCourses(query: String, playerLocation: GpsPoint): List<GolfCourseResult> {
        val url = "$baseUrl/search?search_query=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val request = Request.Builder().url(url)
            .header("Authorization", "Key $apiKey")
            .build()
        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            resp.body?.string() ?: throw Exception("Empty body")
        }
        val decoded = json.decodeFromString(SearchResponse.serializer(), body)
        return decoded.courses
            .filter { it.location.latitude != null && it.location.longitude != null }
            .map {
                GolfCourseResult(
                    id = it.id,
                    name = it.courseName,
                    location = GpsPoint(it.location.latitude!!, it.location.longitude!!),
                    city = listOfNotNull(it.location.city, it.location.state).joinToString(", "),
                )
            }
            .sortedBy { it.location.distanceMeters(playerLocation) }
    }

    fun fetchHoles(courseId: Int, teeColor: String = "Blue"): List<GolfCourseHole> {
        val request = Request.Builder().url("$baseUrl/courses/$courseId")
            .header("Authorization", "Key $apiKey")
            .build()
        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            resp.body?.string() ?: throw Exception("Empty body")
        }
        val decoded = json.decodeFromString(CourseDetailResponse.serializer(), body)
        val teeList = decoded.course.tees.male ?: decoded.course.tees.female ?: emptyList()
        val tee = teeList.firstOrNull { it.teeName.equals(teeColor, ignoreCase = true) }
            ?: teeList.firstOrNull()
            ?: throw Exception("No hole data")
        return tee.holes.mapIndexed { idx, h ->
            GolfCourseHole(number = idx + 1, par = h.par, yardage = h.yardage, handicap = h.handicap)
        }
    }

    // --- JSON response types ---

    @Serializable
    private data class SearchResponse(val courses: List<ApiCourse>)

    @Serializable
    private data class CourseDetailResponse(val course: ApiCourse)

    @Serializable
    private data class ApiCourse(
        val id: Int,
        @SerialName("course_name") val courseName: String,
        val location: ApiLocation,
        val tees: ApiTees = ApiTees(),
    )

    @Serializable
    private data class ApiLocation(
        val city: String? = null,
        val state: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
    )

    @Serializable
    private data class ApiTees(
        val male: List<ApiTee>? = null,
        val female: List<ApiTee>? = null,
    )

    @Serializable
    private data class ApiTee(
        @SerialName("tee_name") val teeName: String,
        val holes: List<ApiHole>,
    )

    @Serializable
    private data class ApiHole(
        val par: Int,
        val yardage: Int,
        val handicap: Int? = null,
    )
}
