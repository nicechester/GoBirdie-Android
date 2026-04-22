package io.github.nicechester.gobirdie.core.data

import android.content.Context
import io.github.nicechester.gobirdie.core.model.Round
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class InProgressSnapshot(
    val round: Round,
    val courseId: String,
    val currentHoleIndex: Int,
)

@Singleton
class InProgressStore @Inject constructor(context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file: File

    init {
        val dir = File(context.filesDir, "GoBirdie").apply { mkdirs() }
        file = File(dir, "in_progress.json")
    }

    fun save(snapshot: InProgressSnapshot) {
        file.writeText(json.encodeToString(InProgressSnapshot.serializer(), snapshot))
    }

    fun load(): InProgressSnapshot? {
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString(InProgressSnapshot.serializer(), file.readText())
        }.getOrNull()
    }

    fun clear() {
        file.delete()
    }
}
