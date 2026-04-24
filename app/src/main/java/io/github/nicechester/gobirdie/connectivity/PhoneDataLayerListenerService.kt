package io.github.nicechester.gobirdie.connectivity

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.serialization.json.*

private const val TAG = "WearListener"

class PhoneDataLayerListenerService : WearableListenerService() {

    companion object {
        /** Callback set by AppState to receive watch actions. */
        var onWatchAction: ((action: String, data: Map<String, Any?>) -> Unit)? = null
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != "/watch/action") return

        val json = String(event.data)
        Log.d(TAG, "Watch message: $json")

        try {
            val obj = Json.parseToJsonElement(json).jsonObject
            val action = obj["action"]?.jsonPrimitive?.content ?: return

            val extras = mutableMapOf<String, Any?>()
            obj["holeNumber"]?.jsonPrimitive?.intOrNull?.let { extras["holeNumber"] = it }
            obj["strokes"]?.jsonPrimitive?.intOrNull?.let { extras["strokes"] = it }
            obj["putts"]?.jsonPrimitive?.intOrNull?.let { extras["putts"] = it }
            obj["lat"]?.jsonPrimitive?.doubleOrNull?.let { extras["lat"] = it }
            obj["lon"]?.jsonPrimitive?.doubleOrNull?.let { extras["lon"] = it }
            obj["altitude"]?.jsonPrimitive?.doubleOrNull?.let { extras["altitude"] = it }
            obj["club"]?.jsonPrimitive?.content?.let { extras["club"] = it }

            Log.d(TAG, "Dispatching watch action: $action extras=$extras")
            onWatchAction?.invoke(action, extras)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse watch message: ${e.message}")
        }
    }
}
