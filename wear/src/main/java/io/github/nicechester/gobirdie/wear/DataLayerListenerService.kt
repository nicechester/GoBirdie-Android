package io.github.nicechester.gobirdie.wear

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.serialization.json.*

class DataLayerListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/phone/holeData" || event.path == "/phone/action") {
            val json = String(event.data)
            val map = parseJsonToMap(json)
            WearSessionHolder.session?.handleMessage(map)
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.dataItem.uri.path == "/phone/holeData") {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                WearSessionHolder.session?.handleMessageFromDataMap(dataMap)
            }
        }
        events.release()
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
