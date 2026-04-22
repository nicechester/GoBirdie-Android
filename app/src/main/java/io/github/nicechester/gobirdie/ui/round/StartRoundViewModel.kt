package io.github.nicechester.gobirdie.ui.round

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nicechester.gobirdie.Config
import io.github.nicechester.gobirdie.core.data.CourseStore
import io.github.nicechester.gobirdie.core.data.api.GolfCourseApiClient
import io.github.nicechester.gobirdie.core.data.api.GolfCourseResult
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
)

@HiltViewModel
class StartRoundViewModel @Inject constructor(
    private val courseStore: CourseStore,
) : ViewModel() {

    private val overpass = OverpassClient()
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
                var results = overpass.searchCourses(location, 15000)
                if (results.isEmpty()) results = overpass.searchCourses(location, 30000)
                val current = (_state.value as? StartRoundUiState.CourseList)?.courses ?: emptyList()
                val newItems = results
                    .filter { r -> current.none { it.name == r.name } }
                    .map { r ->
                        CourseItem(
                            id = -r.osmId.toInt(), name = r.name, location = r.location, city = "",
                            isSaved = false,
                            distanceText = formatDistance(r.location, location),
                        )
                    }
                val merged = (current + newItems).sortedBy { it.location.distanceMeters(location) }
                _state.value = StartRoundUiState.CourseList(merged, isSearchingOnline = false)
            } catch (e: Exception) {
                // Never replace CourseList with Error — just stop the spinner
                // so the search bar stays visible for name-based search
                val current = _state.value
                if (current is StartRoundUiState.CourseList) {
                    _state.value = current.copy(isSearchingOnline = false)
                } else {
                    _state.value = StartRoundUiState.CourseList(emptyList(), isSearchingOnline = false)
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
                        id = r.id, name = r.name, location = r.location, city = r.city,
                        isSaved = false,
                        distanceText = playerLocation?.let { formatDistance(r.location, it) },
                    )
                }
                _state.value = StartRoundUiState.CourseList(items, isSearchingOnline = false)
            } catch (e: Exception) {
                _state.value = StartRoundUiState.Error("Search failed: ${e.message}")
            }
        }
    }

    fun selectCourse(item: CourseItem) {
        // Check cache
        val cacheId = if (item.id > 0) "api-${item.id}" else "osm-${kotlin.math.abs(item.id)}"
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
                _state.value = StartRoundUiState.Error("Download failed: ${e.message}")
            }
        }
    }

    fun confirmStart(course: Course, startingHole: Int) {
        _selectedCourse.value = course to startingHole
    }

    fun retry() {
        playerLocation?.let { loadWithLocation(it) }
            ?: run { _state.value = StartRoundUiState.Error("Location not available") }
    }

    private suspend fun downloadCourse(item: CourseItem): Course {
        val osmId = if (item.id < 0) kotlin.math.abs(item.id).toLong() else null

        // Try Overpass for OSM courses
        if (osmId != null) {
            val courses = overpass.downloadCourse(osmId, item.name, playerLocation)
            return courses.first()
        }

        // For API courses, fetch hole data and build course
        val apiHoles = golfApi.fetchHoles(item.id)
        val holes = apiHoles.map { h ->
            Hole(number = h.number, par = h.par, handicap = h.handicap, yardage = h.yardage.toString())
        }
        return Course(
            id = "api-${item.id}",
            name = item.name,
            location = item.location,
            holes = holes,
            downloadedAt = Instant.now().toString(),
            golfCourseApiId = item.id,
        )
    }

    private fun formatDistance(from: GpsPoint, to: GpsPoint): String {
        val miles = from.distanceMeters(to) / 1609.34
        return if (miles < 0.1) "< 0.1 mi" else "%.1f mi".format(miles)
    }

    private fun stableId(courseId: String): Int {
        val stripped = courseId.replace("osm-", "").replace("gcapi-", "").replace("api-", "")
        return stripped.toIntOrNull()?.let { -it } ?: -kotlin.math.abs(courseId.hashCode())
    }
}
