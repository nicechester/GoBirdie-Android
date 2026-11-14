package io.github.nicechester.gobirdie.core.data.osm

import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import io.github.nicechester.gobirdie.core.model.Course
import io.github.nicechester.gobirdie.core.model.GpsPoint
import io.github.nicechester.gobirdie.core.model.Hole
import io.github.nicechester.gobirdie.core.model.HoleGeometry
import io.github.nicechester.gobirdie.core.model.Polygon
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.time.Instant
import java.util.zip.GZIPInputStream

/**
 * One gzipped region file (GeoJSON) on disk, replaced when the user moves to another region.
 * The file stays compressed on disk; it is decompressed as a stream while reading, never as a whole.
 */
class OsmRegionCache(private val dir: File, private val http: OkHttpClient) {

    /** Returns the cached file for [fileName], downloading it first if needed. Deletes other regions. */
    @Synchronized
    fun ensure(fileName: String): File {
        val target = File(dir, fileName)
        if (target.exists() && target.length() > 0) {
            Log.d(TAG, "OSM region: cache hit $fileName (${target.length() / 1024 / 1024}MB)")
            return target
        }

        dir.mkdirs()
        val replaced = dir.listFiles()?.filter { it.name.endsWith(".geojson.gz") }.orEmpty()
        replaced.forEach { it.delete() }
        Log.d(TAG, "OSM region: downloading $fileName (replacing ${replaced.size} cached region file(s))")

        val started = System.currentTimeMillis()
        val part = File(dir, "$fileName.part")
        download("$RELEASE_BASE$fileName", part)
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        Log.d(TAG, "OSM region: downloaded $fileName, ${target.length() / 1024 / 1024}MB in ${System.currentTimeMillis() - started}ms")
        return target
    }

    /** Bytes used by the cached region. */
    @Synchronized
    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /** Deletes the cached region (Settings → Clear temporary files) and returns the bytes freed. Downloaded courses are not affected. */
    @Synchronized
    fun clear(): Long {
        val freed = sizeBytes()
        dir.listFiles()?.forEach { it.delete() }
        return freed
    }

    private fun download(url: String, to: File) {
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
            val body = resp.body ?: throw IOException("Empty body for $url")
            body.byteStream().use { input -> to.outputStream().use { input.copyTo(it) } }
        }
    }

    /**
     * Builds the courses for [osmId] from the region file. The course outline is the feature with [osmId].
     * Its holes are the golf features within [MARGIN] degrees of the outline's bounding box, so holes mapped
     * just outside the club polygon are kept. Hole lines are split into layouts by way-ID gap, so a multi-course
     * complex (e.g. Brookside) gives one [Course] per layout. Returns an empty list when the outline or its
     * holes aren't in this region.
     */
    fun extractCourses(gz: File, osmId: String, name: String, anchor: GpsPoint): List<Course> {
        // Pass 1: the outline, as all of its polygons.
        val pass1 = System.currentTimeMillis()
        var outlineRings: List<List<GpsPoint>> = emptyList()
        var outlineName: String? = null
        forEachFeature(gz) { feature ->
            if (feature["id"] == osmId) {
                outlineRings = geometryOf(feature).filter { it.size >= 3 }
                outlineName = propertiesOf(feature)["name"] as? String
                false // found it, stop
            } else {
                true
            }
        }
        if (outlineRings.isEmpty()) {
            Log.d(TAG, "OSM region: $osmId not in ${gz.name} (pass 1 ${System.currentTimeMillis() - pass1}ms)")
            return emptyList()
        }
        val outlinePoints = outlineRings.flatten()
        Log.d(TAG, "OSM region: outline for $osmId '${outlineName ?: name}' with ${outlinePoints.size} points " +
            "in ${outlineRings.size} polygon(s) (pass 1 ${System.currentTimeMillis() - pass1}ms)")

        // Pass 2: golf features that start within MARGIN of the outline's bounds.
        val minLat = outlinePoints.minOf { it.lat } - MARGIN
        val maxLat = outlinePoints.maxOf { it.lat } + MARGIN
        val minLon = outlinePoints.minOf { it.lon } - MARGIN
        val maxLon = outlinePoints.maxOf { it.lon } + MARGIN

        val holeLines = mutableListOf<Feature>()
        val greens = mutableListOf<Polygon>()
        val fairways = mutableListOf<Polygon>()
        val bunkers = mutableListOf<Polygon>()

        val pass2 = System.currentTimeMillis()
        forEachFeature(gz) { feature ->
            val props = propertiesOf(feature)
            val golf = props["golf"] as? String
            if (golf != null && golf in GOLF_KINDS) {
                val parts = geometryOf(feature)
                val first = parts.firstOrNull()?.firstOrNull()
                if (first != null && first.lat in minLat..maxLat && first.lon in minLon..maxLon) {
                    val id = feature["id"] as? String ?: ""
                    when (golf) {
                        "hole" -> if (parts.isNotEmpty() && parts[0].size >= 2) holeLines.add(Feature(id, props, parts[0]))
                        "green" -> greens.addAll(parts.filter { it.size >= 3 })
                        "fairway" -> fairways.addAll(parts.filter { it.size >= 3 })
                        "bunker" -> bunkers.addAll(parts.filter { it.size >= 3 })
                    }
                }
            }
            true
        }

        Log.d(TAG, "OSM region: pass 2 ${System.currentTimeMillis() - pass2}ms near outline: " +
            "${holeLines.size} hole lines, ${greens.size} greens, ${fairways.size} fairways, ${bunkers.size} bunkers")

        // Layouts of a complex. Preferred: the golf:course:name tag on each hole (e.g. "C.W. Koiner Course").
        // Fallback: one layout per duplicate hole ref, split by way-ID gap.
        val tagged = holeLines.isNotEmpty() && holeLines.all { it.props["golf:course:name"] is String }
        val layouts: List<Pair<String?, List<Feature>>> = if (tagged) {
            holeLines.groupBy { it.props["golf:course:name"] as String }.map { (layoutName, lines) -> layoutName to lines }
        } else {
            val layoutCount = holeLines.mapNotNull { it.props["ref"]?.toString()?.toIntOrNull() }
                .groupingBy { it }.eachCount().values.maxOrNull() ?: 1
            splitByWayGap(holeLines, layoutCount).map { null to it }
        }
        val courseName = outlineName ?: name
        val center = centroid(outlinePoints)

        val courses = layouts.mapIndexedNotNull { idx, (layoutName, group) ->
            val holes = buildHoles(group, greens, fairways, bunkers, anchor)
            if (holes.isEmpty()) return@mapIndexedNotNull null
            val multi = layouts.size > 1
            Log.d(TAG, "OSM region: layout ${idx + 1} '${layoutName ?: "-"}' of $osmId has ${holes.size} holes")
            Course(
                id = if (multi) "osm-$osmId-${idx + 1}" else "osm-$osmId",
                // "Club – Layout", e.g. "Brookside Golf Club – C.W. Koiner Course"
                name = when {
                    !multi -> courseName
                    layoutName != null -> "$courseName – $layoutName"
                    else -> "$courseName #${idx + 1}"
                },
                location = center,
                holes = holes,
                downloadedAt = Instant.now().toString(),
            )
        }
        if (courses.isEmpty()) Log.w(TAG, "OSM region: no holes built for $osmId")
        return courses
    }

    // MARK: - Layouts and hole building (same rules as the Overpass path)

    /** Splits hole lines into [layouts] groups at the largest gaps in OSM way IDs, as the Overpass path does. */
    private fun splitByWayGap(lines: List<Feature>, layouts: Int): List<List<Feature>> {
        if (layouts <= 1 || lines.size < 2) return listOf(lines)
        val ordered = lines.sortedBy { wayNumber(it.id) }
        val cuts = (1 until ordered.size)
            .map { i -> i to (wayNumber(ordered[i].id) - wayNumber(ordered[i - 1].id)) }
            .sortedByDescending { it.second }
            .take(layouts - 1)
            .map { it.first }
            .sorted()
        val groups = mutableListOf<List<Feature>>()
        var start = 0
        for (cut in cuts) {
            groups += ordered.subList(start, cut)
            start = cut
        }
        groups += ordered.subList(start, ordered.size)
        return groups
    }

    /** The number in an OSM id, e.g. "w1116223984" -> 1116223984. */
    private fun wayNumber(id: String): Long = id.filter { it.isDigit() }.toLongOrNull() ?: 0L

    private fun buildHoles(
        holeLines: List<Feature>,
        greens: List<Polygon>,
        fairways: List<Polygon>,
        bunkers: List<Polygon>,
        anchor: GpsPoint,
    ): List<Hole> {
        // One line per hole number within a layout; keep the way whose tee is closest to the anchor.
        val byRef = LinkedHashMap<Int, Feature>()
        for (line in holeLines) {
            val ref = line.props["ref"]?.toString()?.toIntOrNull() ?: continue
            val existing = byRef[ref]
            if (existing != null &&
                line.points.first().distanceMeters(anchor) >= existing.points.first().distanceMeters(anchor)
            ) continue
            byRef[ref] = line
        }

        return byRef.map { (number, line) ->
            val tee = line.points.first()
            val greenCenter = line.points.last()
            val par = line.props["par"]?.toString()?.toIntOrNull() ?: 4
            val handicap = line.props["handicap"]?.toString()?.toIntOrNull()

            val green = greens
                .filter { centroid(it).distanceMeters(greenCenter) < 50.0 }
                .minByOrNull { centroid(it).distanceMeters(greenCenter) }
            val front = green?.let { GpsPoint(it.minOf { p -> p.lat }, it.map { p -> p.lon }.average()) }
            val back = green?.let { GpsPoint(it.maxOf { p -> p.lat }, it.map { p -> p.lon }.average()) }

            val fairway = fairways.firstOrNull { poly ->
                val c = centroid(poly)
                c.distanceMeters(tee) < 700 && c.distanceMeters(greenCenter) < 700
            }
            val holeBunkers = bunkers.filter { poly ->
                val c = centroid(poly)
                c.distanceMeters(tee) < 700 && c.distanceMeters(greenCenter) < 700
            }

            Hole(
                number = number,
                par = par,
                handicap = handicap,
                tee = tee,
                greenCenter = greenCenter,
                greenFront = front,
                greenBack = back,
                geometry = HoleGeometry(fairway = fairway, bunkers = holeBunkers),
            )
        }.sortedBy { it.number }
    }

    // MARK: - Streaming read

    private class Feature(val id: String, val props: Map<String, Any?>, val points: List<GpsPoint>)

    /** Visits each feature of the FeatureCollection. [visit] returns false to stop early. */
    private fun forEachFeature(gz: File, visit: (Map<String, Any?>) -> Boolean) {
        val reader = JsonReader(
            InputStreamReader(GZIPInputStream(BufferedInputStream(FileInputStream(gz))), Charsets.UTF_8)
        )
        reader.use { r ->
            r.beginObject()
            while (r.hasNext()) {
                if (r.nextName() == "features") {
                    r.beginArray()
                    while (r.hasNext()) {
                        @Suppress("UNCHECKED_CAST")
                        val feature = readValue(r) as? Map<String, Any?> ?: continue
                        if (!visit(feature)) return
                    }
                    r.endArray()
                } else {
                    r.skipValue()
                }
            }
        }
    }

    /** Reads any JSON value into plain Kotlin maps, lists, strings, numbers and booleans. */
    private fun readValue(r: JsonReader): Any? = when (r.peek()) {
        JsonToken.BEGIN_ARRAY -> {
            r.beginArray()
            val list = ArrayList<Any?>()
            while (r.hasNext()) list.add(readValue(r))
            r.endArray()
            list
        }
        JsonToken.BEGIN_OBJECT -> {
            r.beginObject()
            val map = HashMap<String, Any?>()
            while (r.hasNext()) {
                val key = r.nextName()
                map[key] = readValue(r)
            }
            r.endObject()
            map
        }
        JsonToken.STRING -> r.nextString()
        JsonToken.NUMBER -> r.nextDouble()
        JsonToken.BOOLEAN -> r.nextBoolean()
        JsonToken.NULL -> {
            r.nextNull()
            null
        }
        else -> {
            r.skipValue()
            null
        }
    }

    private fun propertiesOf(feature: Map<String, Any?>): Map<String, Any?> =
        (feature["properties"] as? Map<*, *>)?.entries
            ?.associate { (k, v) -> k.toString() to v } ?: emptyMap()

    /** The features' geometry as a list of rings: LineStrings are one ring, polygons contribute their outer ring. */
    private fun geometryOf(feature: Map<String, Any?>): List<List<GpsPoint>> {
        val geom = feature["geometry"] as? Map<*, *> ?: return emptyList()
        val coords = geom["coordinates"] as? List<*> ?: return emptyList()
        return when (geom["type"]) {
            "LineString" -> listOf(pointsOf(coords))
            "Polygon" -> listOfNotNull((coords.firstOrNull() as? List<*>)?.let { pointsOf(it) })
            "MultiPolygon" -> coords.mapNotNull { poly ->
                (poly as? List<*>)?.firstOrNull()?.let { ring -> (ring as? List<*>)?.let { pointsOf(it) } }
            }
            else -> emptyList()
        }
    }

    private fun pointsOf(ring: List<*>): List<GpsPoint> = ring.mapNotNull { p ->
        val c = p as? List<*> ?: return@mapNotNull null
        val lon = c.getOrNull(0) as? Double ?: return@mapNotNull null
        val lat = c.getOrNull(1) as? Double ?: return@mapNotNull null
        GpsPoint(lat = lat, lon = lon)
    }

    private fun centroid(points: List<GpsPoint>) =
        GpsPoint(points.map { it.lat }.average(), points.map { it.lon }.average())

    companion object {
        private const val TAG = "GoBirdie"
        private const val RELEASE_BASE ="https://github.com/nicechester/osm-golf-courses/releases/latest/download/"
        private val GOLF_KINDS = setOf("hole", "green", "fairway", "bunker")

        /** Degrees (about 660 m) a hole may start outside the outline's bounds. Larger values pull in nearby courses. */
        private const val MARGIN = 0.006
    }
}
