package io.github.nicechester.gobirdie.core.data.osm

import android.util.Log
import io.github.nicechester.gobirdie.core.model.GpsPoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** One course in the osm-golf-courses index. Border courses can appear under more than one [file]. */
@Serializable
data class OsmIndexEntry(
    val name: String,
    val city: String = "",
    val osmId: String,
    val file: String,
    val lat: Double? = null,
    val lon: Double? = null,
)

/**
 * The course index published on GitHub Pages. Cached on disk and refreshed weekly.
 * Entries without coordinates are dropped, since they can't be matched by location.
 */
class OsmStaticIndex(private val cacheFile: File, private val http: OkHttpClient) {

    private var all: List<OsmIndexEntry>? = null
    private var byOsmId: Map<String, OsmIndexEntry> = emptyMap()

    /** All located entries, including border duplicates. */
    @Synchronized
    private fun entries(): List<OsmIndexEntry> {
        all?.let { return it }
        val started = System.currentTimeMillis()
        val text = readOrFetch()
        val raw = Json { ignoreUnknownKeys = true }.decodeFromString<List<OsmIndexEntry>>(text)
        val parsed = raw.filter { it.lat != null && it.lon != null }
        all = parsed
        // De-duplicate by OSM ID: a border course is the same course in both region files.
        byOsmId = LinkedHashMap<String, OsmIndexEntry>().also { map ->
            parsed.forEach { map.putIfAbsent(it.osmId, it) }
        }
        Log.d(TAG, "OSM index parsed in ${System.currentTimeMillis() - started}ms: ${raw.size} entries, " +
            "${parsed.size} with coordinates, ${byOsmId.size} unique courses")
        return parsed
    }

    private fun readOrFetch(): String {
        val fresh = cacheFile.exists() &&
            System.currentTimeMillis() - cacheFile.lastModified() < WEEK_MS
        if (fresh) {
            Log.d(TAG, "OSM index: using cached copy (${cacheFile.length() / 1024}KB)")
            return cacheFile.readText()
        }
        return try {
            val started = System.currentTimeMillis()
            val text = fetchText(http, INDEX_URL)
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(text)
            Log.d(TAG, "OSM index: fetched ${text.length / 1024}KB in ${System.currentTimeMillis() - started}ms")
            text
        } catch (e: Exception) {
            // Offline or Pages down: use the last good copy if there is one.
            if (cacheFile.exists()) {
                Log.w(TAG, "OSM index: fetch failed (${e.message}), using stale cached copy")
                cacheFile.readText()
            } else {
                Log.e(TAG, "OSM index: fetch failed and no cached copy", e)
                throw e
            }
        }
    }

    /** Unique courses within [radiusMeters] of [location], nearest first. */
    @Synchronized
    fun nearby(location: GpsPoint, radiusMeters: Double, limit: Int = 20): List<OsmIndexEntry> {
        entries()
        return byOsmId.values
            .map { it to GpsPoint(it.lat!!, it.lon!!).distanceMeters(location) }
            .filter { it.second <= radiusMeters }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
    }

    /** Every unique course in the index, for name search. */
    @Synchronized
    fun uniqueEntries(): List<OsmIndexEntry> {
        entries()
        return byOsmId.values.toList()
    }

    /** The region file for the entry nearest [location]. Used to pick the region to cache. */
    @Synchronized
    fun regionNear(location: GpsPoint): String? =
        entries().minByOrNull { GpsPoint(it.lat!!, it.lon!!).distanceMeters(location) }?.file

    @Synchronized
    fun find(osmId: String): OsmIndexEntry? {
        entries()
        return byOsmId[osmId]
    }

    companion object {
        private const val TAG = "GoBirdie"
        const val INDEX_URL = "https://nicechester.github.io/osm-golf-courses/indexes/index.json"
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }
}

/** GET a URL as text. Throws [IOException] on a non-2xx response. */
internal fun fetchText(http: OkHttpClient, url: String): String {
    val request = Request.Builder().url(url).build()
    http.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
        return resp.body?.string() ?: throw IOException("Empty body for $url")
    }
}
