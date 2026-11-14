package io.github.nicechester.gobirdie.core.data.osm

import android.util.Log
import io.github.nicechester.gobirdie.core.model.Course
import io.github.nicechester.gobirdie.core.model.GpsPoint
import okhttp3.OkHttpClient
import java.io.File

private const val TAG = "GoBirdie"

/**
 * Static OSM course data (osm-golf-courses): the index for nearby courses and the region files for geometry.
 * Blocking calls: run on Dispatchers.IO.
 */
class OsmCourseSource(
    baseDir: File,
    indexHttp: OkHttpClient,
    regionHttp: OkHttpClient,
) {
    private val index = OsmStaticIndex(File(baseDir, "index.json"), indexHttp)
    private val regions = OsmRegionCache(File(baseDir, "regions"), regionHttp)

    /** Courses within [radiusMeters] of [location], nearest first. Empty when the region has no coverage. */
    fun nearby(location: GpsPoint, radiusMeters: Double): List<OsmIndexEntry> =
        index.nearby(location, radiusMeters)

    /**
     * Geometry for one course, one [Course] per layout (a multi-course complex gives several). The region comes
     * from the user's location (the index entry nearest the user), with the course's own region as a fallback.
     * Returns an empty list when neither region has the course.
     */
    fun loadCourse(osmId: String, name: String, userLocation: GpsPoint): List<Course> {
        val started = System.currentTimeMillis()
        val ownFile = index.find(osmId)?.file
        val nearbyFile = index.regionNear(userLocation)
        val candidates = listOfNotNull(nearbyFile, ownFile).distinct()
        Log.d(TAG, "OSM course $osmId '$name': region from user location=$nearbyFile, course's own=$ownFile")
        for (file in candidates) {
            val gz = regions.ensure(file)
            val courses = regions.extractCourses(gz, osmId, name, userLocation)
            if (courses.isNotEmpty()) {
                Log.d(TAG, "OSM course $osmId '$name': ${courses.size} layout(s) from $file in ${System.currentTimeMillis() - started}ms")
                return courses
            }
        }
        Log.w(TAG, "OSM course $osmId '$name': not in static data after ${System.currentTimeMillis() - started}ms, falling back")
        return emptyList()
    }

    /**
     * For Manage Courses: load the course named [name] near [location] (within [radiusMeters]).
     * Names match when every distinctive word of [name] appears in the OSM name, so "Harding" matches
     * "Harding Golf Course" but "Wilson" does not match "Harding Golf Course". With no match, returning
     * null lets the caller fall back to Overpass.
     */
    fun loadNamedNear(name: String, location: GpsPoint, radiusMeters: Double = 5_000.0): Course? {
        val hits = index.nearby(location, radiusMeters)
        val query = distinctiveWords(name)
        val match = hits.firstOrNull { hit ->
            if (query.isEmpty()) hit.name.equals(name, ignoreCase = true)
            else distinctiveWords(hit.name).containsAll(query)
        }
        Log.d(TAG, "OSM name lookup '$name' within ${radiusMeters.toInt()}m: ${hits.size} hits, " +
            "match=${match?.name ?: "none"}")
        if (match == null) return null
        return loadCourse(match.osmId, name, location).firstOrNull()
    }

    /**
     * Name search over the whole index, nearest first. A course matches when its name has every distinctive
     * word of [name] ("Angeles National" matches "Angeles National Golf Club"). Empty for a query made only
     * of generic words.
     */
    fun search(name: String, location: GpsPoint, limit: Int = 30): List<OsmIndexEntry> {
        val query = distinctiveWords(name)
        if (query.isEmpty()) return emptyList()
        return index.uniqueEntries()
            .filter { distinctiveWords(it.name).containsAll(query) }
            .sortedBy { GpsPoint(it.lat!!, it.lon!!).distanceMeters(location) }
            .take(limit)
    }

    /** Bytes used by the cached region (0 when nothing is cached). */
    fun regionCacheBytes(): Long = regions.sizeBytes()

    /** Settings → Clear temporary files. Removes the cached region only. Returns the bytes freed. */
    fun clearRegionCache(): Long = regions.clear()

    private fun distinctiveWords(name: String): Set<String> =
        name.lowercase().split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() && it !in GENERIC_WORDS }
            .toSet()

    private companion object {
        val GENERIC_WORDS = setOf("golf", "course", "club", "country", "municipal", "links", "the", "academy", "par")
    }
}
