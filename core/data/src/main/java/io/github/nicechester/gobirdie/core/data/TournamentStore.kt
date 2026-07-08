package io.github.nicechester.gobirdie.core.data

import android.content.Context
import io.github.nicechester.gobirdie.core.model.Tournament
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TournamentStore @Inject constructor(context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val dir = File(context.filesDir, "GoBirdie/tournaments").apply { mkdirs() }

    fun save(tournament: Tournament) {
        fileFor(tournament.id).writeText(json.encodeToString(Tournament.serializer(), tournament))
    }

    fun loadAll(): List<Tournament> =
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { json.decodeFromString(Tournament.serializer(), it.readText()) }.getOrNull() }
            ?.sortedByDescending { it.date }
            ?: emptyList()

    fun load(id: String): Tournament? {
        val f = fileFor(id)
        if (!f.exists()) return null
        return runCatching { json.decodeFromString(Tournament.serializer(), f.readText()) }.getOrNull()
    }

    fun delete(id: String) {
        fileFor(id).delete()
    }

    private fun fileFor(id: String) = File(dir, "${id.replace(":", "_")}.json")
}
