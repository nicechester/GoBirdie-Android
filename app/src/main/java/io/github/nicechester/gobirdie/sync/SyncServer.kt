package io.github.nicechester.gobirdie.sync

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import io.github.nicechester.gobirdie.core.data.RoundStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "SyncServer"
const val SYNC_PORT = 7743

class SyncServer(private val roundStore: RoundStore) : NanoHTTPD(SYNC_PORT) {

    private val json = Json { ignoreUnknownKeys = true }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        Log.d(TAG, "${session.method} $uri")

        return when {
            uri == "/api/rounds" -> {
                val summaries = roundStore.loadAll()
                    .filter { it.endedAt != null }
                    .map { r ->
                        buildString {
                            append("{")
                            append("\"id\":${json.encodeToString(r.id)},")
                            append("\"source\":${json.encodeToString(r.source)},")
                            append("\"course_name\":${json.encodeToString(r.courseName)},")
                            append("\"started_at\":${json.encodeToString(r.startedAt)},")
                            append("\"ended_at\":${json.encodeToString(r.endedAt)},")
                            append("\"holes_played\":${r.holesPlayed},")
                            append("\"total_strokes\":${r.totalStrokes},")
                            append("\"total_putts\":${r.totalPutts}")
                            append("}")
                        }
                    }
                jsonResponse("[${summaries.joinToString(",")}]")
            }

            uri.startsWith("/api/rounds/") -> {
                val id = uri.removePrefix("/api/rounds/")
                val round = roundStore.load(id)
                    ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
                jsonResponse(json.encodeToString(round))
            }

            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun jsonResponse(body: String) =
        newFixedLengthResponse(Response.Status.OK, "application/json", body)
}
