package io.github.nicechester.gobirdie.connectivity

import android.content.Context
import com.google.android.gms.wearable.*
import io.github.nicechester.gobirdie.core.model.ClubType
import io.github.nicechester.gobirdie.core.model.Hole
import io.github.nicechester.gobirdie.core.model.HoleMapMeta

class WearConnectivityService(private val context: Context) {

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
        val request = PutDataMapRequest.create("/phone/holeData").apply {
            dataMap.putInt("holeNumber", holeNumber)
            dataMap.putInt("par", hole.par)
            dataMap.putString("courseName", courseName)
            dataMap.putInt("totalStrokes", totalStrokes)
            dataMap.putInt("totalHoles", totalHoles)
            dataMap.putInt("currentStrokes", currentStrokes)
            dataMap.putInt("currentPutts", currentPutts)
            dataMap.putStringArray("clubBag", enabledClubs.map { it.serialName }.toTypedArray())
            hole.tee?.let {
                dataMap.putDouble("tee_lat", it.lat)
                dataMap.putDouble("tee_lon", it.lon)
            }
            hole.greenCenter?.let {
                dataMap.putDouble("green_lat", it.lat)
                dataMap.putDouble("green_lon", it.lon)
            }
            hole.greenFront?.let {
                dataMap.putDouble("front_lat", it.lat)
                dataMap.putDouble("front_lon", it.lon)
            }
            hole.greenBack?.let {
                dataMap.putDouble("back_lat", it.lat)
                dataMap.putDouble("back_lon", it.lon)
            }
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }

    /** Notify watch of round start with course version hash (mirrors iOS sendRoundStartContext). */
    fun sendRoundStartContext(versionHash: String, courseId: String) {
        val request = PutDataMapRequest.create("/phone/action").apply {
            dataMap.putString("action", "roundStart")
            dataMap.putString("versionHash", versionHash)
            dataMap.putString("courseId", courseId)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }

    /** Notify watch that the round ended from phone side. */
    fun sendRoundEnded() {
        val request = PutDataMapRequest.create("/phone/action").apply {
            dataMap.putString("action", "roundEnded")
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }

    /** Notify watch that the round was cancelled from phone side. */
    fun sendRoundCancelled() {
        val request = PutDataMapRequest.create("/phone/action").apply {
            dataMap.putString("action", "roundCancelled")
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }

    /** Send stroke update to watch (when phone-side strokes change). */
    fun sendStrokeUpdate(holeNumber: Int, strokes: Int, putts: Int) {
        val request = PutDataMapRequest.create("/phone/action").apply {
            dataMap.putString("action", "strokeUpdate")
            dataMap.putInt("holeNumber", holeNumber)
            dataMap.putInt("strokes", strokes)
            dataMap.putInt("putts", putts)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
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

}
