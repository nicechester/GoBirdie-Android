package io.github.nicechester.gobirdie.core.data.session

import io.github.nicechester.gobirdie.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

class RoundSession(
    initialRound: Round,
    startingHoleIndex: Int = 0,
) {
    private val _round = MutableStateFlow(initialRound)
    val round: StateFlow<Round> = _round

    private val _currentHoleIndex = MutableStateFlow(startingHoleIndex)
    val currentHoleIndex: StateFlow<Int> = _currentHoleIndex

    private val _isComplete = MutableStateFlow(false)
    val isComplete: StateFlow<Boolean> = _isComplete

    val currentHoleNumber: Int get() = _currentHoleIndex.value + 1

    val currentHole: HoleScore?
        get() = _round.value.holes.getOrNull(_currentHoleIndex.value)

    fun markShot(location: GpsPoint, club: ClubType = ClubType.UNKNOWN, distanceToPinYards: Int? = null, altitudeMeters: Double? = null, heartRateBpm: Int? = null) {
        update { holes ->
            val idx = _currentHoleIndex.value
            val hole = holes[idx]
            val shot = Shot(
                sequence = hole.shots.size + 1,
                location = location,
                timestamp = Instant.now().toString(),
                club = club,
                distanceToPinYards = distanceToPinYards,
                altitudeMeters = altitudeMeters,
                heartRateBpm = heartRateBpm,
            )
            holes[idx] = hole.copy(
                shots = hole.shots + shot,
                strokes = hole.strokes + 1,
            )
        }
    }

    fun setPutts(count: Int) {
        if (count < 0) return
        update { holes ->
            val idx = _currentHoleIndex.value
            val hole = holes[idx]
            val delta = count - hole.putts
            holes[idx] = hole.copy(
                putts = count,
                strokes = maxOf(0, hole.strokes + delta),
            )
        }
    }

    fun addPenalty() {
        update { holes ->
            val idx = _currentHoleIndex.value
            val hole = holes[idx]
            holes[idx] = hole.copy(
                strokes = hole.strokes + 1,
                penalties = hole.penalties + 1,
            )
        }
    }

    fun undoLastAction() {
        update { holes ->
            val idx = _currentHoleIndex.value
            val hole = holes[idx]
            if (hole.strokes <= 0) return@update
            if (hole.shots.isNotEmpty()) {
                holes[idx] = hole.copy(
                    shots = hole.shots.dropLast(1),
                    strokes = hole.strokes - 1,
                )
            } else if (hole.penalties > 0) {
                holes[idx] = hole.copy(
                    strokes = hole.strokes - 1,
                    penalties = hole.penalties - 1,
                )
            } else {
                holes[idx] = hole.copy(strokes = hole.strokes - 1)
            }
        }
    }

    fun navigateTo(holeNumber: Int) {
        val idx = holeNumber - 1
        if (idx in _round.value.holes.indices) {
            _currentHoleIndex.value = idx
        }
    }

    fun endRound() {
        if (_isComplete.value) return
        val r = _round.value
        _round.value = r.copy(
            endedAt = Instant.now().toString(),
            totalStrokes = r.holes.sumOf { it.strokes },
            totalPutts = r.holes.sumOf { it.putts },
            holesPlayed = r.holes.count { it.strokes > 0 },
        )
        _isComplete.value = true
    }

    fun setHeartRateTimeline(samples: List<HeartRateSample>) {
        _round.value = _round.value.copy(heartRateTimeline = samples)
    }

    fun snapshot(): Round = _round.value

    private fun update(block: (MutableList<HoleScore>) -> Unit) {
        val r = _round.value
        val holes = r.holes.toMutableList()
        block(holes)
        _round.value = r.copy(
            holes = holes,
            totalStrokes = holes.sumOf { it.strokes },
            totalPutts = holes.sumOf { it.putts },
        )
    }
}
