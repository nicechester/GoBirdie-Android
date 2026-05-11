package io.github.nicechester.gobirdie.connectivity

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.*
import com.google.android.gms.wearable.PutDataMapRequest
import io.github.nicechester.gobirdie.core.model.ClubType
import io.github.nicechester.gobirdie.core.model.Hole
import io.github.nicechester.gobirdie.core.model.HoleMapMeta
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
        currentStrokes: Int = 0,
        currentPutts: Int = 0,
    ) {
        val json = buildJsonObject {
            put("holeNumber", holeNumber)
            put("par", hole.par)
            put("courseName", courseName)
            put("totalStrokes", totalStrokes)
            put("totalHoles", totalHoles)
            put("currentStrokes", currentStrokes)
            put("currentPutts", currentPutts)
            putJsonArray("clubBag") {
                enabledClubs.forEach { club ->
                    add(club.serialName)
                }
            }
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

    fun sendMapSnapshot(holeNumber: Int, jpeg: ByteArray, meta: HoleMapMeta) {
        val request = PutDataMapRequest.create("/watch/holeMap/$holeNumber").apply {
            dataMap.putAsset("image", com.google.android.gms.wearable.Asset.createFromBytes(jpeg))
            dataMap.putInt("holeNumber", holeNumber)
            dataMap.putString("version", meta.version)
            dataMap.putLong("timestamp", System.currentTimeMillis())
            dataMap.putDouble("swLat", meta.swLat)
            dataMap.putDouble("swLon", meta.swLon)
            dataMap.putDouble("neLat", meta.neLat)
            dataMap.putDouble("neLon", meta.neLon)
            dataMap.putInt("imageWidth", meta.imageWidth)
            dataMap.putInt("imageHeight", meta.imageHeight)
            dataMap.putDouble("bearing", meta.bearing)
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
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
