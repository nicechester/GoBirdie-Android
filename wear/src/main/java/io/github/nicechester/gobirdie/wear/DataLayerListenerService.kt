package io.github.nicechester.gobirdie.wear

import android.util.Log
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import io.github.nicechester.gobirdie.wear.BuildConfig
import io.github.nicechester.gobirdie.core.model.HoleMapMeta
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File

private const val TAG = "DataLayerListener"

class DataLayerListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onMessageReceived: path=${event.path} size=${event.data.size}")
        if (event.path == "/phone/holeData" || event.path == "/phone/action") {
            val json = String(event.data)
            val map = parseJsonToMap(json)
            WearSessionHolder.session?.handleMessage(map)
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onDataChanged: ${events.count} events")
        for (event in events) {
            val path = event.dataItem.uri.path ?: continue
            if (BuildConfig.DEBUG) Log.d(TAG, "  dataItem path=$path type=${event.type}")
            when {
                path == "/phone/holeData" -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    WearSessionHolder.session?.handleMessageFromDataMap(dataMap)
                }
                path.startsWith("/watch/holeMap/") -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    saveMapSnapshot(dataMap)
                }
            }
        }
        events.release()
    }

    private fun saveMapSnapshot(dataMap: com.google.android.gms.wearable.DataMap) {
        val holeNumber = dataMap.getInt("holeNumber")
        if (BuildConfig.DEBUG) Log.d(TAG, "saveMapSnapshot: hole=$holeNumber")
        val asset = dataMap.getAsset("image") ?: run {
            Log.w(TAG, "saveMapSnapshot: no image asset for hole $holeNumber")
            return
        }
        val version = dataMap.getString("version") ?: run {
            Log.w(TAG, "saveMapSnapshot: no version for hole $holeNumber")
            return
        }
        try {
            val result = com.google.android.gms.tasks.Tasks.await(
                Wearable.getDataClient(this).getFdForAsset(asset)
            )
            val jpeg = result.inputStream.readBytes()
            result.release()
            if (BuildConfig.DEBUG) Log.d(TAG, "saveMapSnapshot: hole=$holeNumber jpeg=${jpeg.size} bytes")
            val dir = File(filesDir, "maps").also { it.mkdirs() }
            File(dir, "hole_$holeNumber.jpg").writeBytes(jpeg)
            val meta = HoleMapMeta(
                holeNumber = holeNumber,
                version = version,
                swLat = dataMap.getDouble("swLat"),
                swLon = dataMap.getDouble("swLon"),
                neLat = dataMap.getDouble("neLat"),
                neLon = dataMap.getDouble("neLon"),
                imageWidth = dataMap.getInt("imageWidth"),
                imageHeight = dataMap.getInt("imageHeight"),
                bearing = dataMap.getDouble("bearing"),
            )
            File(dir, "hole_$holeNumber.json").writeText(Json.encodeToString(meta))
            if (BuildConfig.DEBUG) Log.d(TAG, "saveMapSnapshot: hole=$holeNumber saved, notifying session")
            WearSessionHolder.session?.onMapReceived(holeNumber)
        } catch (e: Exception) {
            Log.e(TAG, "saveMapSnapshot failed for hole $holeNumber", e)
        }
    }

    private fun parseJsonToMap(json: String): Map<String, Any?> {
        return try {
            val obj = Json.parseToJsonElement(json).jsonObject
            obj.mapValues { (_, v) ->
                when (v) {
                    is JsonPrimitive -> when {
                        v.isString -> v.content
                        v.content.contains('.') -> v.doubleOrNull
                        else -> v.intOrNull
                    }
                    is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.content }
                    else -> null
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}

/** Singleton holder so the WearableListenerService can reach the active session. */
object WearSessionHolder {
    var session: WatchRoundSession? = null
}
