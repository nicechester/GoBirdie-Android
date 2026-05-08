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

    fun moveShotsToHole(fromHoleNumber: Int, toHoleNumber: Int) {
        update { holes ->
            val fromIdx = holes.indexOfFirst { it.number == fromHoleNumber }.takeIf { it >= 0 } ?: return@update
            val toIdx = holes.indexOfFirst { it.number == toHoleNumber }.takeIf { it >= 0 } ?: return@update
            val from = holes[fromIdx]
            val to = holes[toIdx]
            val movedShots = from.shots.mapIndexed { i, s -> s.copy(sequence = to.shots.size + i + 1) }
            holes[fromIdx] = from.copy(shots = emptyList(), strokes = from.putts, penalties = 0)
            holes[toIdx] = to.copy(
                shots = to.shots + movedShots,
                strokes = to.strokes + from.shots.size,
                penalties = to.penalties + from.penalties,
            )
        }
    }

    fun updateLastShotClub(holeNumber: Int, club: ClubType, shotId: String? = null) {
        update { holes ->
            val idx = holes.indexOfFirst { it.number == holeNumber }.takeIf { it >= 0 } ?: return@update
            val hole = holes[idx]
            val shots = hole.shots.toMutableList()
            if (shots.isEmpty()) return@update
            val si = if (shotId != null) shots.indexOfFirst { it.id == shotId }.takeIf { it >= 0 } ?: shots.lastIndex else shots.lastIndex
            shots[si] = shots[si].copy(club = club)
            holes[idx] = hole.copy(shots = shots)
        }
    }

    fun insertShot(holeNumber: Int, location: GpsPoint, atIndex: Int) {
        update { holes ->
            val idx = holes.indexOfFirst { it.number == holeNumber }.takeIf { it >= 0 } ?: return@update
            val existing = holes[idx].shots.sortedBy { it.sequence }.toMutableList()
            existing.add(atIndex.coerceIn(0, existing.size), Shot(
                sequence = 0,
                location = location,
                timestamp = java.time.Instant.now().toString(),
                club = ClubType.UNKNOWN,
            ))
            val resequenced = existing.mapIndexed { i, s -> s.copy(sequence = i + 1) }
            holes[idx] = holes[idx].copy(shots = resequenced, strokes = resequenced.size + holes[idx].putts)
        }
    }

    fun updateShotLocation(holeNumber: Int, shotId: String, location: GpsPoint) {
        update { holes ->
            val idx = holes.indexOfFirst { it.number == holeNumber }.takeIf { it >= 0 } ?: return@update
            val shots = holes[idx].shots.toMutableList()
            val si = shots.indexOfFirst { it.id == shotId }.takeIf { it >= 0 } ?: return@update
            shots[si] = shots[si].copy(location = location)
            holes[idx] = holes[idx].copy(shots = shots)
        }
    }

    fun deleteShot(holeNumber: Int, shotId: String) {
        update { holes ->
            val idx = holes.indexOfFirst { it.number == holeNumber }.takeIf { it >= 0 } ?: return@update
            val updatedShots = holes[idx].shots
                .filter { it.id != shotId }
                .mapIndexed { i, s -> s.copy(sequence = i + 1) }
            holes[idx] = holes[idx].copy(shots = updatedShots, strokes = updatedShots.size + holes[idx].putts)
        }
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
