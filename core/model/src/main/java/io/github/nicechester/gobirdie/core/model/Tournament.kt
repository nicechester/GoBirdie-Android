package io.github.nicechester.gobirdie.core.model

import kotlinx.serialization.Serializable

enum class PlayerSource { SELF, RECEIVED, MANUAL }

@Serializable
data class TournamentPlayer(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val holes: List<HoleScore>,
    val source: String = PlayerSource.MANUAL.name, // serialized as string for simplicity
) {
    val totalStrokes: Int get() = holes.sumOf { it.strokes }
    val totalPutts: Int get() = holes.sumOf { it.putts }
    val scoreVsPar: Int get() = holes.filter { it.strokes > 0 }.let { played ->
        played.sumOf { it.strokes } - played.sumOf { it.par }
    }
}

@Serializable
data class Tournament(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String? = null,
    val courseId: String,
    val courseName: String,
    val date: String,           // yyyy-MM-dd
    val players: List<TournamentPlayer> = emptyList(),
    val createdAt: String = java.time.Instant.now().toString(),
)
