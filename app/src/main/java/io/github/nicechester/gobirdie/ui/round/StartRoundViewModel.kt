package io.github.nicechester.gobirdie.ui.round

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.nicechester.gobirdie.Config
import io.github.nicechester.gobirdie.R
import io.github.nicechester.gobirdie.core.data.CourseStore
import io.github.nicechester.gobirdie.core.data.api.GolfCourseApiClient
import io.github.nicechester.gobirdie.core.data.api.GolfCourseResult
import io.github.nicechester.gobirdie.core.data.osm.OsmCourseSource
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import io.github.nicechester.gobirdie.core.data.api.OverpassClient
import io.github.nicechester.gobirdie.core.model.Course
import io.github.nicechester.gobirdie.core.model.GpsPoint
import io.github.nicechester.gobirdie.core.model.Hole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

sealed class StartRoundUiState {
    data object Loading : StartRoundUiState()
    data class CourseList(
        val courses: List<CourseItem>,
        val isSearchingOnline: Boolean = false,
        /** Nearby (OSM) search failed, e.g. Overpass unavailable. Saved courses are still listed. */
        val searchFailed: Boolean = false,
    ) : StartRoundUiState()
    data class Downloading(val name: String) : StartRoundUiState()
    data class SelectHole(val course: Course) : StartRoundUiState()
    data class Error(val message: String) : StartRoundUiState()
}

data class CourseItem(
    val id: Int,
    val name: String,
    val location: GpsPoint,
    val city: String,
    val isSaved: Boolean,
    val distanceText: String?,
    /** golfcourseapi.com string ID; null for OSM/saved-only items. */
    val apiId: String? = null,
    /** OSM ID from the static index (e.g. "w174533646"); null for other items. */
    val osmKey: String? = null,
)

/** Positive list-key derived from a string API ID (OSM items use negative IDs, saved items use stableId). */
private fun apiItemId(apiId: String): Int = (apiId.hashCode() and 0x7fffffff).coerceAtLeast(1)

@HiltViewModel
class StartRoundViewModel @Inject constructor(
    private val courseStore: CourseStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val overpass = OverpassClient()
    private val osm by lazy {
        OsmCourseSource(
            baseDir = File(context.filesDir, "osm"),
            indexHttp = OkHttpClient(),
            regionHttp = OkHttpClient.Builder().readTimeout(5, TimeUnit.MINUTES).build(),
        )
    }
    private val golfApi = GolfCourseApiClient(Config.GOLF_COURSE_API_KEY)

    private val _state = MutableStateFlow<StartRoundUiState>(StartRoundUiState.Loading)
    val state: StateFlow<StartRoundUiState> = _state

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText

    private var playerLocation: GpsPoint? = null
    private var savedCourseIds = mutableSetOf<String>()

    // Course selected and ready to start
    private val _selectedCourse = MutableStateFlow<Pair<Course, Int>?>(null) // course + starting hole
    val selectedCourse: StateFlow<Pair<Course, Int>?> = _selectedCourse

    fun onSearchTextChanged(text: String) { _searchText.value = text }

    fun loadWithLocation(location: GpsPoint?) {
        playerLocation = location
        showSavedCourses(location)
        if (location != null) searchNearby(location)
    }

    /** Called when GPS fix arrives — triggers nearby search without resetting the list */
    fun onLocationReceived(location: GpsPoint) {
        if (playerLocation != null) return // already have a fix
        playerLocation = location
        // Re-sort existing saved courses by distance
        val current = _state.value
        if (current is StartRoundUiState.CourseList) {
            val sorted = current.courses
                .map { it.copy(distanceText = formatDistance(it.location, location)) }
                .sortedBy { it.location.distanceMeters(location) }
            _state.value = StartRoundUiState.CourseList(sorted, isSearchingOnline = true)
        }
        searchNearby(location)
    }

    private fun showSavedCourses(location: GpsPoint?) {
        val saved = courseStore.loadAll()
        savedCourseIds = saved.map { it.id }.toMutableSet()
        val items = saved.take(20).map { c ->
            CourseItem(
                id = stableId(c.id), name = c.name, location = c.location, city = "",
                isSaved = true,
                distanceText = location?.let { formatDistance(c.location, it) },
            )
        }.let { list ->
            if (location != null) list.sortedBy { it.location.distanceMeters(location) } else list
        }
        _state.value = StartRoundUiState.CourseList(items, isSearchingOnline = location != null)
    }

    private fun searchNearby(location: GpsPoint) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = (_state.value as? StartRoundUiState.CourseList)?.courses ?: emptyList()
                // Static index first (no live query); Overpass only when the index has nothing nearby.
                val staticHits = osm.nearby(location, 15_000.0)
                val newItems = if (staticHits.isNotEmpty()) {
                    staticHits
                        .filter { e -> current.none { it.name == e.name } }
                        .map { e ->
                            val point = GpsPoint(e.lat!!, e.lon!!)
                            CourseItem(
                                id = -((e.osmId.hashCode() and 0x7fffffff).coerceAtLeast(1)),
                                name = e.name, location = point, city = e.city,
                                isSaved = false,
                                distanceText = formatDistance(point, location),
                                osmKey = e.osmId,
                            )
                        }
                } else {
                    var results = overpass.searchCourses(location, 15000)
                    if (results.isEmpty()) results = overpass.searchCourses(location, 30000)
                    results
                        .filter { r -> current.none { it.name == r.name } }
                        .map { r ->
                            CourseItem(
                                id = -r.osmId.toInt(), name = r.name, location = r.location, city = "",
                                isSaved = false,
                                distanceText = formatDistance(r.location, location),
                            )
                        }
                }
                val merged = (current + newItems).sortedBy { it.location.distanceMeters(location) }
                _state.value = StartRoundUiState.CourseList(merged, isSearchingOnline = false)
            } catch (e: Exception) {
                // Never replace CourseList with Error — keep saved courses and the search bar,
                // but flag the failure so the user sees it instead of an empty list.
                val current = _state.value
                if (current is StartRoundUiState.CourseList) {
                    _state.value = current.copy(isSearchingOnline = false, searchFailed = true)
                } else {
                    _state.value = StartRoundUiState.CourseList(emptyList(), isSearchingOnline = false, searchFailed = true)
                }
            }
        }
    }

    fun searchByName() {
        val query = _searchText.value.trim()
        if (query.isEmpty()) {
            playerLocation?.let { loadWithLocation(it) }
            return
        }
        _state.value = StartRoundUiState.CourseList(emptyList(), isSearchingOnline = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loc = playerLocation ?: GpsPoint(34.0, -118.0)
                val results = golfApi.searchCourses(query, loc)
                val items = results.map { r ->
                    CourseItem(
                        id = apiItemId(r.id), name = r.name, location = r.location, city = r.city,
                        isSaved = false,
                        distanceText = playerLocation?.let { formatDistance(r.location, it) },
                        apiId = r.id,
                    )
                }
                _state.value = StartRoundUiState.CourseList(items, isSearchingOnline = false)
            } catch (e: Exception) {
                _state.value = StartRoundUiState.CourseList(emptyList(), isSearchingOnline = false)
            }
        }
    }

    fun selectCourse(item: CourseItem) {
        // Check cache
        val cacheId = item.apiId?.let { "api-$it" } ?: "osm-${kotlin.math.abs(item.id)}"
        val cached = courseStore.load(cacheId)
            ?: courseStore.loadAll().firstOrNull { it.name == item.name }
        if (cached != null) {
            _state.value = StartRoundUiState.SelectHole(cached)
            return
        }

        _state.value = StartRoundUiState.Downloading(item.name)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val course = downloadCourse(item)
                courseStore.save(course)
                _state.value = StartRoundUiState.SelectHole(course)
            } catch (e: Exception) {
                _state.value = StartRoundUiState.Error(context.getString(R.string.courses_download_failed, e.message))
            }
        }
    }

    fun confirmStart(course: Course, startingHole: Int) {
        _selectedCourse.value = course to startingHole
    }

    fun reset() {
        _selectedCourse.value = null
        _searchText.value = ""
        playerLocation = null
    }

    fun retry() {
        loadWithLocation(playerLocation)
    }

    private suspend fun downloadCourse(item: CourseItem): Course {
        // Static OSM data first: region file for the user's location, cached on disk.
        item.osmKey?.let { key ->
            osm.loadCourse(key, item.name, playerLocation ?: item.location)?.let { return it }
            // Not in the static data: find it through Overpass near the item.
            val near = overpass.searchCourses(item.location, 5000)
            val match = near.firstOrNull { it.name.equals(item.name, ignoreCase = true) } ?: near.firstOrNull()
                ?: throw Exception("No map data for ${item.name}")
            return overpass.downloadCourse(match.osmId, item.name, playerLocation).first()
        }

        val osmId = if (item.id < 0) kotlin.math.abs(item.id).toLong() else null

        // Try Overpass for OSM courses
        if (osmId != null) {
            val courses = overpass.downloadCourse(osmId, item.name, playerLocation)
            return courses.first()
        }

        // For API courses, fetch hole data + Overpass GPS geometry
        val apiId = item.apiId ?: throw Exception("Missing course id")
        val apiHoles = golfApi.fetchHoles(apiId)
        val overpassHoles = try {
            val nearby = overpass.searchCourses(item.location, 5000)
            val match = nearby.firstOrNull { it.name.equals(item.name, ignoreCase = true) }
                ?: nearby.firstOrNull()
            match?.let { overpass.downloadCourse(it.osmId, item.name, playerLocation) }
                ?.firstOrNull()?.holes ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.w("GoBirdie", "Geometry lookup failed for '${item.name}', saving without map data: ${e.javaClass.simpleName}: ${e.message}", e)
            emptyList()
        }
        // If Overpass has geometry, trust its hole count (e.g. 9-hole courses the API lists as 18)
        val holeCount = if (overpassHoles.isNotEmpty()) overpassHoles.size else apiHoles.size
        val holes = apiHoles.take(holeCount).map { h ->
            val gpsHole = overpassHoles.firstOrNull { it.number == h.number }
            Hole(
                number = h.number, par = h.par, handicap = h.handicap, yardage = h.yardage.toString(),
                tee = gpsHole?.tee,
                greenCenter = gpsHole?.greenCenter,
                greenFront = gpsHole?.greenFront,
                greenBack = gpsHole?.greenBack,
                geometry = gpsHole?.geometry,
            )
        }
        return Course(
            id = "api-$apiId",
            name = item.name,
            location = item.location,
            holes = holes,
            downloadedAt = Instant.now().toString(),
            golfCourseApiId = apiId,
        )
    }

    private fun formatDistance(from: GpsPoint, to: GpsPoint): String {
        val miles = from.distanceMeters(to) / 1609.34
        return if (miles < 0.1) context.getString(R.string.distance_under_tenth_mi)
        else context.getString(R.string.distance_mi, miles)
    }

    private fun stableId(courseId: String): Int {
        val stripped = courseId.replace("osm-", "").replace("gcapi-", "").replace("api-", "")
        return stripped.toIntOrNull()?.let { -it } ?: -kotlin.math.abs(courseId.hashCode())
    }
}
