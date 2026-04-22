package io.github.nicechester.gobirdie.core.data

import android.content.Context
import io.github.nicechester.gobirdie.core.model.Course
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseStore @Inject constructor(context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val dir = File(context.filesDir, "GoBirdie/courses").apply { mkdirs() }

    fun save(course: Course) {
        fileFor(course.id).writeText(json.encodeToString(Course.serializer(), course))
    }

    fun loadAll(): List<Course> =
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { json.decodeFromString(Course.serializer(), it.readText()) }.getOrNull() }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun load(id: String): Course? {
        val f = fileFor(id)
        if (!f.exists()) return null
        return json.decodeFromString(Course.serializer(), f.readText())
    }

    fun delete(id: String) {
        fileFor(id).delete()
    }

    private fun fileFor(id: String) =
        File(dir, "${id.replace(":", "_").replace("/", "_")}.json")
}
