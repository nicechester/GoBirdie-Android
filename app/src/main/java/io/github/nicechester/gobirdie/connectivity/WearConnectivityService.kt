package io.github.nicechester.gobirdie.connectivity

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.*
import io.github.nicechester.gobirdie.core.model.ClubType
import io.github.nicechester.gobirdie.core.model.Hole
import kotlinx.serialization.json.*

private const val TAG = "WearConnectivity"

class WearConnectivityService(private val context: Context) {

    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

    /** Send hole + round data to watch (mirrors iOS sendHoleData). */
    fun sendHoleData(
        hole: Hole,
        holeNumber: Int,
        courseName: String,
        totalStrokes: Int,
        totalHoles: Int = 18,
        enabledClubs: List<ClubType> = emptyList(),
    ) {
        val json = buildJsonObject {
            put("holeNumber", holeNumber)
            put("par", hole.par)
            put("courseName", courseName)
            put("totalStrokes", totalStrokes)
            put("totalHoles", totalHoles)
            putJsonArray("clubBag") { enabledClubs.forEach { add(it.name.lowercase()) } }
            hole.tee?.let { put("tee_lat", it.lat); put("tee_lon", it.lon) }
            hole.greenCenter?.let { put("green_lat", it.lat); put("green_lon", it.lon) }
            hole.greenFront?.let { put("front_lat", it.lat); put("front_lon", it.lon) }
            hole.greenBack?.let { put("back_lat", it.lat); put("back_lon", it.lon) }
        }.toString()

        sendMessage("/phone/holeData", json.toByteArray())
    }

    /** Notify watch that the round ended from phone side. */
    fun sendRoundEnded() {
        val json = buildJsonObject { put("action", "roundEnded") }.toString()
        sendMessage("/phone/action", json.toByteArray())
    }

    /** Notify watch that the round was cancelled from phone side. */
    fun sendRoundCancelled() {
        val json = buildJsonObject { put("action", "roundCancelled") }.toString()
        sendMessage("/phone/action", json.toByteArray())
    }

    /** Send stroke update to watch (when phone-side strokes change). */
    fun sendStrokeUpdate(holeNumber: Int, strokes: Int, putts: Int) {
        val json = buildJsonObject {
            put("action", "strokeUpdate")
            put("holeNumber", holeNumber)
            put("strokes", strokes)
            put("putts", putts)
        }.toString()
        sendMessage("/phone/action", json.toByteArray())
    }

    private fun sendMessage(path: String, data: ByteArray) {
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                messageClient.sendMessage(node.id, path, data)
                    .addOnFailureListener { e -> Log.w(TAG, "sendMessage failed: ${e.message}") }
            }
        }
    }
}
