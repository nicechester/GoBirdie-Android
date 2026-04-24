package io.github.nicechester.gobirdie.connectivity

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.serialization.json.*

private const val TAG = "WearListener"

/** Broadcast actions sent from watch → phone via LocalBroadcast. */
object WatchActions {
    const val ACTION_WATCH_EVENT = "io.github.nicechester.gobirdie.WATCH_EVENT"
    const val EXTRA_ACTION = "watch_action"
    const val EXTRA_HOLE_NUMBER = "holeNumber"
    const val EXTRA_STROKES = "strokes"
    const val EXTRA_PUTTS = "putts"
    const val EXTRA_LAT = "lat"
    const val EXTRA_LON = "lon"
    const val EXTRA_ALTITUDE = "altitude"
    const val EXTRA_CLUB = "club"
}

class PhoneDataLayerListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != "/watch/action") return

        val json = String(event.data)
        Log.d(TAG, "Watch message: $json")

        try {
            val obj = Json.parseToJsonElement(json).jsonObject
            val action = obj["action"]?.jsonPrimitive?.content ?: return

            val intent = Intent(WatchActions.ACTION_WATCH_EVENT).apply {
                putExtra(WatchActions.EXTRA_ACTION, action)
                obj["holeNumber"]?.jsonPrimitive?.intOrNull?.let {
                    putExtra(WatchActions.EXTRA_HOLE_NUMBER, it)
                }
                obj["strokes"]?.jsonPrimitive?.intOrNull?.let {
                    putExtra(WatchActions.EXTRA_STROKES, it)
                }
                obj["putts"]?.jsonPrimitive?.intOrNull?.let {
                    putExtra(WatchActions.EXTRA_PUTTS, it)
                }
                obj["lat"]?.jsonPrimitive?.doubleOrNull?.let {
                    putExtra(WatchActions.EXTRA_LAT, it)
                }
                obj["lon"]?.jsonPrimitive?.doubleOrNull?.let {
                    putExtra(WatchActions.EXTRA_LON, it)
                }
                obj["altitude"]?.jsonPrimitive?.doubleOrNull?.let {
                    putExtra(WatchActions.EXTRA_ALTITUDE, it)
                }
                obj["club"]?.jsonPrimitive?.content?.let {
                    putExtra(WatchActions.EXTRA_CLUB, it)
                }
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse watch message: ${e.message}")
        }
    }
}
