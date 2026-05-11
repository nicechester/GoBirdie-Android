package io.github.nicechester.gobirdie

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.nicechester.gobirdie.connectivity.PhoneDataLayerListenerService
import io.github.nicechester.gobirdie.connectivity.WearConnectivityService
import io.github.nicechester.gobirdie.connectivity.WearMapSnapshotManager
import io.github.nicechester.gobirdie.core.data.CourseStore
import io.github.nicechester.gobirdie.core.data.InProgressSnapshot
import io.github.nicechester.gobirdie.core.data.InProgressStore
import io.github.nicechester.gobirdie.core.data.RoundStore
import io.github.nicechester.gobirdie.core.data.location.LocationService
import io.github.nicechester.gobirdie.core.data.session.RoundSession
import io.github.nicechester.gobirdie.core.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

private const val TAG = "AppState"
private const val AUTO_SAVE_INTERVAL_MS = 30_000L
private const val IDLE_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes

@HiltViewModel
class AppState @Inject constructor(
    @ApplicationContext private val context: Context,
    private val roundStore: RoundStore,
    private val courseStore: CourseStore,
    private val inProgressStore: InProgressStore,
) : ViewModel() {

    val locationService = LocationService(context)
    val wearService = WearConnectivityService(context)
    val snapshotManager = WearMapSnapshotManager(context)

    private val _activeSession = MutableStateFlow<RoundSession?>(null)
    val activeSession: StateFlow<RoundSession?> = _activeSession

    private val _activeCourse = MutableStateFlow<Course?>(null)
    val activeCourse: StateFlow<Course?> = _activeCourse

    private val _pendingResume = MutableStateFlow<InProgressSnapshot?>(null)
    val pendingResume: StateFlow<InProgressSnapshot?> = _pendingResume

    private val _showIdlePrompt = MutableStateFlow(false)
    val showIdlePrompt: StateFlow<Boolean> = _showIdlePrompt

    private var autoSaveJob: Job? = null
    private var idleJob: Job? = null
    private var holeObserverJob: Job? = null

    init {
        checkForInProgressRound()
        PhoneDataLayerListenerService.onWatchAction = { action, extras ->
            handleWatchAction(action, extras)
        }
    }

    fun startRound(course: Course, startingHole: Int = 1): RoundSession {
        val holeScores = course.holes.map { h ->
            HoleScore(number = h.number, par = h.par, greenCenter = h.greenCenter)
        }
        val round = Round(
            id = UUID.randomUUID().toString(),
            source = "android",
            courseId = course.id,
            courseName = course.name,
            startedAt = Instant.now().toString(),
            holes = holeScores,
        )
        val session = RoundSession(round, startingHoleIndex = startingHole - 1)
        _activeSession.value = session
        _activeCourse.value = course
        locationService.start()
        startAutoSave()
        resetIdleTimer()
        sendHoleDataToWatch()
        observeHoleChanges()
        triggerMapSnapshots(course)
        return session
    }

    fun endActiveRound() {
        val session = _activeSession.value ?: return
        session.endRound()
        roundStore.save(session.snapshot())
        Log.i(TAG, "Round saved: ${session.snapshot().id}")
        wearService.sendRoundEnded()
        cleanup()
    }

    fun cancelActiveRound() {
        wearService.sendRoundCancelled()
        cleanup()
    }

    // Called on any user interaction during a round
    fun resetIdleTimer() {
        _showIdlePrompt.value = false
        idleJob?.cancel()
        if (_activeSession.value == null) return
        idleJob = viewModelScope.launch {
            delay(IDLE_TIMEOUT_MS)
            _showIdlePrompt.value = true
        }
    }

    fun dismissIdlePrompt() {
        resetIdleTimer()
    }

    // Resume

    private fun checkForInProgressRound() {
        val snapshot = inProgressStore.load()
        if (snapshot != null) {
            Log.i(TAG, "Found in-progress round: ${snapshot.round.courseName}")
            _pendingResume.value = snapshot
        }
    }

    fun resumeRound() {
        val snapshot = _pendingResume.value ?: return
        val course = courseStore.load(snapshot.courseId)
        if (course == null) {
            Log.w(TAG, "Cannot resume — course ${snapshot.courseId} not found")
            discardInProgressRound()
            return
        }
        val session = RoundSession(snapshot.round, startingHoleIndex = snapshot.currentHoleIndex)
        _activeSession.value = session
        _activeCourse.value = course
        _pendingResume.value = null
        locationService.start()
        startAutoSave()
        resetIdleTimer()
        Log.i(TAG, "Resumed round on hole ${snapshot.currentHoleIndex + 1}")
        sendHoleDataToWatch()
        observeHoleChanges()
        triggerMapSnapshots(course)
    }

    private fun triggerMapSnapshots(course: Course) {
        viewModelScope.launch {
            val version = computeCourseVersion(course)
            snapshotManager.generateAndSend(course, wearService, version)
        }
    }

    private fun computeCourseVersion(course: Course): String {
        val input = course.holes.joinToString("|") { h ->
            "${h.tee?.lat},${h.tee?.lon},${h.greenCenter?.lat},${h.greenCenter?.lon}"
        }
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
    }

    fun discardInProgressRound() {
        inProgressStore.clear()
        _pendingResume.value = null
    }

    // Auto-save

    private fun saveInProgress() {
        val session = _activeSession.value ?: return
        val course = _activeCourse.value ?: return
        val snapshot = InProgressSnapshot(
            round = session.snapshot(),
            courseId = course.id,
            currentHoleIndex = session.currentHoleIndex.value,
        )
        inProgressStore.save(snapshot)
    }

    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(AUTO_SAVE_INTERVAL_MS)
                saveInProgress()
            }
        }
    }

    private fun cleanup() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        idleJob?.cancel()
        idleJob = null
        holeObserverJob?.cancel()
        holeObserverJob = null
        _showIdlePrompt.value = false
        inProgressStore.clear()
        locationService.stop()
        _activeSession.value = null
        _activeCourse.value = null
    }

    // ── Watch connectivity ──

    private fun sendHoleDataToWatch() {
        val session = _activeSession.value ?: return
        val course = _activeCourse.value ?: return
        val holeIdx = session.currentHoleIndex.value
        val courseHole = course.holes.getOrNull(holeIdx) ?: return
        val round = session.snapshot()
        val prefs = context.getSharedPreferences("gobirdie_clubs", Context.MODE_PRIVATE)
        val defaultSet = ClubType.defaultBag.map { it.name }.toSet()
        val enabledNames = prefs.getStringSet("enabled", defaultSet) ?: defaultSet
        val enabledClubs = ClubType.entries.filter { it.name in enabledNames && it != ClubType.UNKNOWN }
        wearService.sendHoleData(
            hole = courseHole,
            holeNumber = courseHole.number,
            courseName = round.courseName,
            totalStrokes = round.totalStrokes,
            totalHoles = course.holes.size,
            enabledClubs = enabledClubs,
            currentStrokes = round.holes.getOrNull(holeIdx)?.strokes ?: 0,
            currentPutts = round.holes.getOrNull(holeIdx)?.putts ?: 0,
        )
    }

    private fun observeHoleChanges() {
        holeObserverJob?.cancel()
        holeObserverJob = viewModelScope.launch {
            val session = _activeSession.value ?: return@launch
            launch {
                session.currentHoleIndex.collect {
                    sendHoleDataToWatch()
                }
            }
            launch {
                session.round.collect {
                    sendHoleDataToWatch()
                }
            }
        }
    }

    /** Called from BroadcastReceiver when watch sends an action. */
    fun handleWatchAction(action: String, extras: Map<String, Any?>) {
        val session = _activeSession.value ?: return
        when (action) {
            "shot" -> {
                val lat = extras["lat"] as? Double
                val lon = extras["lon"] as? Double
                val loc = if (lat != null && lon != null) GpsPoint(lat, lon)
                    else locationService.location.value ?: GpsPoint(0.0, 0.0)
                val clubRaw = extras["club"] as? String
                val club = ClubType.entries.firstOrNull { it.name.equals(clubRaw, ignoreCase = true) } ?: ClubType.UNKNOWN
                val altitude = extras["altitude"] as? Double
                val heartRate = extras["heartRate"] as? Int
                session.markShot(loc, club, altitudeMeters = altitude, heartRateBpm = heartRate)
                resetIdleTimer()
            }
            "stroke" -> {
                val strokes = extras["strokes"] as? Int ?: return
                val putts = extras["putts"] as? Int ?: return
                session.setPutts(putts)
                // Strokes are derived from putts + shots, so we trust the watch count
                resetIdleTimer()
            }
            "navigate" -> {
                val holeNumber = extras["holeNumber"] as? Int ?: return
                session.navigateTo(holeNumber)
                resetIdleTimer()
            }
            "endRound" -> {
                @Suppress("UNCHECKED_CAST")
                val timeline = extras["heartRateTimeline"] as? List<Map<String, Any?>>
                if (!timeline.isNullOrEmpty()) {
                    val samples = timeline.mapNotNull { m ->
                        val ts = m["timestamp"] as? Double ?: return@mapNotNull null
                        val bpm = m["bpm"] as? Int ?: return@mapNotNull null
                        HeartRateSample(
                            timestamp = Instant.ofEpochSecond(ts.toLong()).toString(),
                            bpm = bpm,
                            altitudeMeters = m["altitude"] as? Double,
                        )
                    }
                    session.setHeartRateTimeline(samples)
                }
                endActiveRound()
            }
            "cancelRound" -> cancelActiveRound()
            "clubSelection" -> {
                val clubRaw = extras["club"] as? String
                val club = ClubType.entries.firstOrNull { it.serialName.equals(clubRaw, ignoreCase = true) } ?: return
                val holeNumber = extras["holeNumber"] as? Int ?: return
                session.updateLastShotClub(holeNumber, club)
                resetIdleTimer()
            }
        }
    }
}
