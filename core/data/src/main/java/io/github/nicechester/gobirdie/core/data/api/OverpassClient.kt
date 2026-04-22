package io.github.nicechester.gobirdie.core.data.api

import io.github.nicechester.gobirdie.core.model.Course
import io.github.nicechester.gobirdie.core.model.GpsPoint
import io.github.nicechester.gobirdie.core.model.Hole
import io.github.nicechester.gobirdie.core.model.HoleGeometry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos

data class CourseSearchResult(
    val id: String,
    val name: String,
    val location: GpsPoint,
    val osmId: Long,
)

class OverpassClient {

    private val baseUrl = "https://overpass-api.de/api/interpreter"
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var lastRequestTime = 0L
    private val minIntervalMs = 2100L

    fun searchCourses(location: GpsPoint, radiusMeters: Int = 15000): List<CourseSearchResult> {
        val latD = radiusMeters / 111_000.0
        val lonD = radiusMeters / (111_000.0 * cos(Math.toRadians(location.lat)))
        val bbox = "${location.lat - latD},${location.lon - lonD},${location.lat + latD},${location.lon + lonD}"
        val query = """
            [out:json][bbox:$bbox];
            (node["leisure"="golf_course"];way["leisure"="golf_course"];relation["leisure"="golf_course"];);
            out center;
        """.trimIndent()
        val data = post(query)
        val resp = json.decodeFromString(OverpassResponse.serializer(), data)
        return resp.elements
            .filter { it.tags?.get("name") != null }
            .map { el ->
                val loc = el.center ?: GpsPoint(el.lat ?: 0.0, el.lon ?: 0.0)
                CourseSearchResult("osm-${el.id}", el.tags!!["name"]!!, loc, el.id)
            }
            .sortedBy { it.location.distanceMeters(location) }
            .take(20)
    }

    fun downloadCourse(osmId: Long, name: String, playerLocation: GpsPoint? = null): List<Course> {
        val elQuery = "[out:json];(relation($osmId);way($osmId););out geom;"
        val elData = post(elQuery)
        val elResp = json.decodeFromString(OverpassResponse.serializer(), elData)
        val element = elResp.elements.firstOrNull { it.type == "relation" }
            ?: elResp.elements.firstOrNull { it.type == "way" }
            ?: throw Exception("Course not found")

        val bounds = element.bounds
        val geom = element.geometry ?: emptyList()
        val minLat = bounds?.minlat ?: geom.minOfOrNull { it.lat } ?: throw Exception("No geometry")
        val maxLat = bounds?.maxlat ?: geom.maxOf { it.lat }
        val minLon = bounds?.minlon ?: geom.minOfOrNull { it.lon } ?: throw Exception("No geometry")
        val maxLon = bounds?.maxlon ?: geom.maxOf { it.lon }

        val geoQuery = """
            [out:json][bbox:${minLat - 0.01},${minLon - 0.01},${maxLat + 0.01},${maxLon + 0.01}];
            (way["golf"="hole"];way["golf"~"fairway|green|bunker|rough"];);
            out geom tags;
        """.trimIndent()
        val geoData = post(geoQuery)
        val geoResp = json.decodeFromString(OverpassResponse.serializer(), geoData)

        val courseLoc = GpsPoint((minLat + maxLat) / 2, (minLon + maxLon) / 2)
        val anchor = playerLocation ?: courseLoc
        val now = Instant.now().toString()

        val holeLines = geoResp.elements.filter { it.tags?.get("golf") == "hole" }
        val refCounts = holeLines.mapNotNull { it.tags?.get("ref")?.toIntOrNull() }
            .groupBy { it }.mapValues { it.value.size }
        val hasDuplicates = refCounts.values.any { it > 1 }

        if (hasDuplicates) {
            val groups = splitByWayIdGap(holeLines, refCounts.values.maxOrNull() ?: 2)
            return groups.mapIndexed { idx, groupIds ->
                val groupElements = geoResp.elements.filter { el ->
                    if (el.tags?.get("golf") == "hole") groupIds.contains(el.id) else true
                }
                val holes = buildHoles(groupElements, anchor)
                val suffix = if (groups.size > 1) " #${idx + 1}" else ""
                Course(
                    id = "osm-$osmId-${idx + 1}",
                    name = "$name$suffix",
                    location = courseLoc,
                    holes = holes,
                    downloadedAt = now,
                    osmVersion = element.version ?: 1,
                )
            }
        }

        val holes = buildHoles(geoResp.elements, anchor)
        return listOf(
            Course(
                id = "osm-$osmId",
                name = name,
                location = courseLoc,
                holes = holes,
                downloadedAt = now,
                osmVersion = element.version ?: 1,
            )
        )
    }

    private fun buildHoles(elements: List<OverpassElement>, anchor: GpsPoint): List<Hole> {
        val holeLines = elements.filter { it.tags?.get("golf") == "hole" }
        val greens = elements.filter { it.tags?.get("golf") == "green" }
        val fairways = elements.filter { it.tags?.get("golf") == "fairway" }
        val bunkers = elements.filter { it.tags?.get("golf") == "bunker" }

        val byRef = mutableMapOf<Int, OverpassElement>()
        for (line in holeLines) {
            val ref = line.tags?.get("ref")?.toIntOrNull() ?: continue
            val g = line.geometry ?: continue
            if (g.size < 2) continue
            val existing = byRef[ref]
            if (existing != null) {
                val existingG = existing.geometry ?: continue
                if (g[0].distanceMeters(anchor) >= existingG[0].distanceMeters(anchor)) continue
            }
            byRef[ref] = line
        }

        return byRef.map { (num, line) ->
            val g = line.geometry!!
            val tee = g.first()
            val greenCenter = g.last()
            val par = line.tags?.get("par")?.toIntOrNull() ?: 4
            val handicap = line.tags?.get("handicap")?.toIntOrNull()

            val nearestGreen = greens.mapNotNull { it.geometry }
                .filter { it.size >= 3 }
                .minByOrNull { centroid(it).distanceMeters(greenCenter) }

            val greenFront: GpsPoint?
            val greenBack: GpsPoint?
            if (nearestGreen != null) {
                val avgLon = nearestGreen.map { it.lon }.average()
                greenFront = GpsPoint(nearestGreen.minOf { it.lat }, avgLon)
                greenBack = GpsPoint(nearestGreen.maxOf { it.lat }, avgLon)
            } else {
                greenFront = null; greenBack = null
            }

            val holeFairway = fairways.mapNotNull { it.geometry }.firstOrNull { poly ->
                val c = centroid(poly)
                c.distanceMeters(tee) < 700 && c.distanceMeters(greenCenter) < 700
            }
            val holeBunkers = bunkers.mapNotNull { it.geometry }.filter { poly ->
                val c = centroid(poly)
                c.distanceMeters(tee) < 700 && c.distanceMeters(greenCenter) < 700
            }

            Hole(
                number = num, par = par, handicap = handicap,
                tee = tee, greenCenter = greenCenter,
                greenFront = greenFront, greenBack = greenBack,
                geometry = HoleGeometry(fairway = holeFairway, bunkers = holeBunkers),
            )
        }.sortedBy { it.number }
    }

    private fun splitByWayIdGap(holeLines: List<OverpassElement>, numCourses: Int): List<Set<Long>> {
        val ids = holeLines.map { it.id }.sorted()
        if (ids.size <= 1) return listOf(ids.toSet())
        val gaps = (1 until ids.size).map { i -> i to (ids[i] - ids[i - 1]) }
        val splitIndices = gaps.sortedByDescending { it.second }.take(numCourses - 1).map { it.first }.sorted()
        val groups = mutableListOf<Set<Long>>()
        var start = 0
        for (idx in splitIndices) {
            groups.add(ids.subList(start, idx).toSet())
            start = idx
        }
        groups.add(ids.subList(start, ids.size).toSet())
        return groups
    }

    private fun centroid(points: List<GpsPoint>) =
        GpsPoint(points.map { it.lat }.average(), points.map { it.lon }.average())

    private fun post(query: String, retries: Int = 3): String {
        val now = System.currentTimeMillis()
        val wait = minIntervalMs - (now - lastRequestTime)
        if (wait > 0) Thread.sleep(wait)
        lastRequestTime = System.currentTimeMillis()

        val encoded = "data=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val body = encoded.toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val request = Request.Builder().url(baseUrl).post(body)
            .header("User-Agent", "GoBirdie-Android/1.0")
            .build()
        return client.newCall(request).execute().use { resp ->
            when (resp.code) {
                200 -> resp.body?.string() ?: throw Exception("Empty body")
                429, 502, 503, 504 -> {
                    if (retries <= 0) throw Exception("Overpass HTTP ${resp.code} after retries")
                    val backoff = 3000L * (1L shl (3 - retries)) // 3s, 6s, 12s
                    Thread.sleep(backoff)
                    post(query, retries - 1)
                }
                else -> throw Exception("Overpass HTTP ${resp.code}")
            }
        }
    }

    // --- JSON types ---

    @Serializable
    private data class OverpassResponse(val elements: List<OverpassElement>)

    @Serializable
    private data class OverpassElement(
        val type: String = "",
        val id: Long = 0,
        val lat: Double? = null,
        val lon: Double? = null,
        val tags: Map<String, String>? = null,
        val center: GpsPoint? = null,
        val geometry: List<GpsPoint>? = null,
        val bounds: OverpassBounds? = null,
        val version: Int? = null,
    )

    @Serializable
    private data class OverpassBounds(
        val minlat: Double, val minlon: Double,
        val maxlat: Double, val maxlon: Double,
    )
}
