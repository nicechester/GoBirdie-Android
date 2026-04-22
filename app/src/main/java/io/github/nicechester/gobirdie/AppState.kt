package io.github.nicechester.gobirdie

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.nicechester.gobirdie.core.data.RoundStore
import io.github.nicechester.gobirdie.core.data.location.LocationService
import io.github.nicechester.gobirdie.core.data.session.RoundSession
import io.github.nicechester.gobirdie.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AppState @Inject constructor(
    @ApplicationContext context: Context,
    private val roundStore: RoundStore,
) : ViewModel() {

    val locationService = LocationService(context)

    private val _activeSession = MutableStateFlow<RoundSession?>(null)
    val activeSession: StateFlow<RoundSession?> = _activeSession

    private val _activeCourse = MutableStateFlow<Course?>(null)
    val activeCourse: StateFlow<Course?> = _activeCourse

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
        return session
    }

    fun endActiveRound() {
        val session = _activeSession.value ?: return
        session.endRound()
        roundStore.save(session.snapshot())
        locationService.stop()
        _activeSession.value = null
        _activeCourse.value = null
    }

    fun cancelActiveRound() {
        locationService.stop()
        _activeSession.value = null
        _activeCourse.value = null
    }
}
