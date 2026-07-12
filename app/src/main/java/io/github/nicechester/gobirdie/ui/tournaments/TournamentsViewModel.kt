package io.github.nicechester.gobirdie.ui.tournaments

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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class TournamentsViewModel @Inject constructor(
    private val tournamentStore: TournamentStore,
    private val roundStore: RoundStore,
    private val courseStore: CourseStore,
) : ViewModel() {

    private val _tournaments = MutableStateFlow<List<Tournament>>(emptyList())
    val tournaments: StateFlow<List<Tournament>> = _tournaments

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun load() {
        _tournaments.value = tournamentStore.loadAll()
    }

    fun delete(id: String) {
        tournamentStore.delete(id)
        _tournaments.value = _tournaments.value.filter { it.id != id }
    }

    fun save(tournament: Tournament) {
        tournamentStore.save(tournament)
        _tournaments.value = tournamentStore.loadAll()
    }

    fun load(id: String): Tournament? = tournamentStore.load(id)

    fun loadAll(): List<Tournament> = tournamentStore.loadAll()

    fun loadCourses(): List<Course> = courseStore.loadAll()

    fun createTournament(courseId: String, courseName: String, date: String, title: String?, seedRound: Round? = null): Tournament {
        val players = if (seedRound != null) {
            listOf(
                TournamentPlayer(
                    name = "Me",
                    holes = seedRound.holes,
                    source = PlayerSource.SELF.name,
                )
            )
        } else emptyList()
        return Tournament(
            courseId = courseId,
            courseName = courseName,
            date = date,
            title = title?.takeIf { it.isNotBlank() },
            players = players,
        )
    }

    fun todayDate(): String =
        Instant.now().atZone(ZoneId.systemDefault()).format(dateFormatter)
}
