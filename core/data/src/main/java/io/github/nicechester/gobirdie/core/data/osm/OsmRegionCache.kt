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
     * Builds one course from the region file: the course outline is the feature with [osmId], and its
     * holes, greens, fairways and bunkers are the golf features inside that outline.
     * Returns null when the outline or any hole isn't in this region.
     */
    fun extractCourse(gz: File, osmId: String, name: String, anchor: GpsPoint): Course? {
        // Pass 1: the course outline.
        val pass1 = System.currentTimeMillis()
        var outline: List<GpsPoint>? = null
        var outlineName: String? = null
        forEachFeature(gz) { feature ->
            if (feature["id"] == osmId) {
                outline = outlineOf(feature)
                outlineName = propertiesOf(feature)["name"] as? String
                false // found it, stop
            } else {
                true
            }
        }
        val ring = outline?.takeIf { it.size >= 3 }
        if (ring == null) {
            Log.d(TAG, "OSM region: $osmId not in ${gz.name} (pass 1 ${System.currentTimeMillis() - pass1}ms)")
            return null
        }
        Log.d(TAG, "OSM region: outline for $osmId '${outlineName ?: name}' with ${ring.size} points " +
            "(pass 1 ${System.currentTimeMillis() - pass1}ms)")

        // Pass 2: golf features that start inside the outline (plus a small margin).
        val pad = 0.002
        val minLat = ring.minOf { it.lat } - pad
        val maxLat = ring.maxOf { it.lat } + pad
        val minLon = ring.minOf { it.lon } - pad
        val maxLon = ring.maxOf { it.lon } + pad

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
                    when (golf) {
                        "hole" -> if (parts.isNotEmpty() && parts[0].size >= 2) holeLines.add(Feature(props, parts[0]))
                        "green" -> greens.addAll(parts.filter { it.size >= 3 })
                        "fairway" -> fairways.addAll(parts.filter { it.size >= 3 })
                        "bunker" -> bunkers.addAll(parts.filter { it.size >= 3 })
                    }
                }
            }
            true
        }

        Log.d(TAG, "OSM region: pass 2 ${System.currentTimeMillis() - pass2}ms inside outline: " +
            "${holeLines.size} hole lines, ${greens.size} greens, ${fairways.size} fairways, ${bunkers.size} bunkers")

        val holes = buildHoles(holeLines, greens, fairways, bunkers, anchor)
        if (holes.isEmpty()) {
            Log.w(TAG, "OSM region: no holes built for $osmId")
            return null
        }
        Log.d(TAG, "OSM region: built ${holes.size} holes for $osmId " +
            "(${holes.count { it.greenCenter != null && it.greenFront != null }} with green geometry)")

        return Course(
            id = "osm-$osmId",
            name = outlineName ?: name,
            location = centroid(ring),
            holes = holes,
            downloadedAt = Instant.now().toString(),
        )
    }

    // MARK: - Hole building (same rules as the Overpass path)

    private fun buildHoles(
        holeLines: List<Feature>,
        greens: List<Polygon>,
        fairways: List<Polygon>,
        bunkers: List<Polygon>,
        anchor: GpsPoint,
    ): List<Hole> {
        // One line per hole number; keep the way whose tee is closest to the anchor.
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

    private class Feature(val props: Map<String, Any?>, val points: List<GpsPoint>)

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

    /** The outline of a course feature: the first ring. */
    private fun outlineOf(feature: Map<String, Any?>): List<GpsPoint>? = geometryOf(feature).firstOrNull()

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
    }
}
