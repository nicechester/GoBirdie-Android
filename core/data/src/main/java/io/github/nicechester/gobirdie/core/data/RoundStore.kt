package io.github.nicechester.gobirdie.core.data

import android.content.Context
import io.github.nicechester.gobirdie.core.model.Round
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoundStore @Inject constructor(context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val dir = File(context.filesDir, "GoBirdie/rounds").apply { mkdirs() }

    fun save(round: Round) {
        fileFor(round.id).writeText(json.encodeToString(Round.serializer(), round))
    }

    fun loadAll(): List<Round> =
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { json.decodeFromString(Round.serializer(), it.readText()) }.getOrNull() }
            ?.sortedByDescending { it.startedAt }
            ?: emptyList()

    fun load(id: String): Round? {
        val f = fileFor(id)
        if (!f.exists()) return null
        return json.decodeFromString(Round.serializer(), f.readText())
    }

    fun delete(id: String) {
        fileFor(id).delete()
    }

    private fun fileFor(id: String) =
        File(dir, "${id.replace(":", "_")}.json")
}
