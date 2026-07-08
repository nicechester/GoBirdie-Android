package io.github.nicechester.gobirdie.ui.scorecards

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nicechester.gobirdie.core.data.CourseStore
import io.github.nicechester.gobirdie.core.data.RoundStore
import io.github.nicechester.gobirdie.core.data.TournamentStore
import io.github.nicechester.gobirdie.core.model.Course
import io.github.nicechester.gobirdie.core.model.PlayerSource
import io.github.nicechester.gobirdie.core.model.Round
import io.github.nicechester.gobirdie.core.model.Tournament
import io.github.nicechester.gobirdie.core.model.TournamentPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ScorecardsViewModel @Inject constructor(
    private val roundStore: RoundStore,
    private val courseStore: CourseStore,
    private val tournamentStore: TournamentStore,
) : ViewModel() {

    private val _rounds = MutableStateFlow<List<Round>>(emptyList())
    val rounds: StateFlow<List<Round>> = _rounds

    fun load() {
        _rounds.value = roundStore.loadAll()
    }

    fun delete(id: String) {
        roundStore.delete(id)
        _rounds.value = _rounds.value.filter { it.id != id }
    }

    fun loadCourse(courseId: String): Course? = courseStore.load(courseId)

    fun loadRound(id: String): Round? = roundStore.load(id)

    fun saveRound(round: Round) {
        roundStore.save(round)
        _rounds.value = roundStore.loadAll()
    }

    fun createTournamentFromRound(round: Round): Tournament {
        val date = runCatching {
            java.time.Instant.parse(round.startedAt)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }.getOrDefault(round.startedAt.take(10))
        val tournament = Tournament(
            courseId = round.courseId,
            courseName = round.courseName,
            date = date,
            players = listOf(
                TournamentPlayer(
                    name = "Me",
                    holes = round.holes,
                    source = PlayerSource.SELF.name,
                )
            ),
        )
        tournamentStore.save(tournament)
        return tournament
    }
}
