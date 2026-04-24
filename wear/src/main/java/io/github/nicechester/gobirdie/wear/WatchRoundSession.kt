package io.github.nicechester.gobirdie.wear

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import com.google.android.gms.wearable.*
import io.github.nicechester.gobirdie.core.model.ClubType
import io.github.nicechester.gobirdie.core.model.GpsPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.*
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.math.*

class WatchRoundSession(private val context: Context) {

    // Published state
    val holeNumber = MutableStateFlow(1)
    val par = MutableStateFlow(4)
    val strokes = MutableStateFlow(0)
    val putts = MutableStateFlow(0)
    val frontYards = MutableStateFlow<Int?>(null)
    val pinYards = MutableStateFlow<Int?>(null)
    val backYards = MutableStateFlow<Int?>(null)
    val hasHoleData = MutableStateFlow(false)
    val courseName = MutableStateFlow("")
    val totalHoles = MutableStateFlow(18)
    val isRoundEnded = MutableStateFlow(false)
    val showClubPicker = MutableStateFlow(false)
    val selectedClub = MutableStateFlow("unknown")
    val clubBag = MutableStateFlow<List<String>>(emptyList())

    private var accumulatedStrokes = 0
    val totalStrokes: Int get() = accumulatedStrokes + strokes.value

    // Green coordinates for distance computation
    private var greenFront: GpsPoint? = null
    private var greenCenter: GpsPoint? = null
    private var greenBack: GpsPoint? = null
    private var currentLocation: Location? = null

    // Location
    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var isActive = false

    // Club picker auto-dismiss timer
    private var clubPickerTimer: Timer? = null

    // ── Public API ──

    fun markShot() {
        strokes.update { it + 1 }
        sendShotToPhone()
        showClubPickerAfterShot()
    }

    fun addStroke() {
        strokes.update { it + 1 }
        sendStrokesToPhone()
    }

    fun addPutt() {
        putts.update { it + 1 }
        strokes.update { it + 1 }
        sendStrokesToPhone()
    }

    fun removePutt() {
        if (putts.value <= 0) return
        putts.update { it - 1 }
        strokes.update { it - 1 }
        sendStrokesToPhone()
    }

    fun previousHole() {
        if (holeNumber.value <= 1) return
        accumulatedStrokes += strokes.value
        holeNumber.value--
        strokes.value = 0
        putts.value = 0
        sendNavigateToPhone()
    }

    fun nextHole() {
        if (holeNumber.value >= totalHoles.value) return
        accumulatedStrokes += strokes.value
        holeNumber.value++
        strokes.value = 0
        putts.value = 0
        sendNavigateToPhone()
    }

    fun navigateToHole(number: Int) {
        if (number < 1 || number > totalHoles.value || number == holeNumber.value) return
        accumulatedStrokes += strokes.value
        holeNumber.value = number
        strokes.value = 0
        putts.value = 0
        sendNavigateToPhone()
    }

    fun finishRound() {
        accumulatedStrokes += strokes.value
        stopLocation()
        isRoundEnded.value = true
        sendEndRoundToPhone()
    }

    fun cancelRound() {
        stopLocation()
        sendCancelRoundToPhone()
        resetToWaiting()
    }

    fun resetToWaiting() {
        isRoundEnded.value = false
        hasHoleData.value = false
        holeNumber.value = 1
        par.value = 4
        strokes.value = 0
        putts.value = 0
        accumulatedStrokes = 0
        frontYards.value = null
        pinYards.value = null
        backYards.value = null
        courseName.value = ""
        totalHoles.value = 18
        greenFront = null
        greenCenter = null
        greenBack = null
        dismissClubPicker()
        clubBag.value = emptyList()
    }

    fun confirmClub() {
        if (!showClubPicker.value) return
        clubPickerTimer?.cancel()
        clubPickerTimer = null
        showClubPicker.value = false
        sendClubToPhone()
    }

    fun dismissClubPicker() {
        clubPickerTimer?.cancel()
        clubPickerTimer = null
        showClubPicker.value = false
    }

    // ── Message handling (from phone) ──

    fun handleMessage(data: Map<String, Any?>) {
        val action = data["action"] as? String
        when (action) {
            "roundCancelled" -> { stopLocation(); resetToWaiting() }
            "roundEnded" -> { stopLocation(); isRoundEnded.value = true }
            "strokeUpdate" -> {
                (data["strokes"] as? Number)?.let { strokes.value = it.toInt() }
                (data["putts"] as? Number)?.let { putts.value = it.toInt() }
            }
            else -> handleHoleData(data)
        }
    }

    fun handleMessageFromDataMap(dataMap: DataMap) {
        handleMessage(dataMap.toMap())
    }

    private fun handleHoleData(data: Map<String, Any?>) {
        (data["holeNumber"] as? Number)?.let { holeNumber.value = it.toInt() }
        (data["par"] as? Number)?.let { par.value = it.toInt() }
        (data["courseName"] as? String)?.let { courseName.value = it }
        (data["totalStrokes"] as? Number)?.let { accumulatedStrokes = it.toInt() }
        (data["totalHoles"] as? Number)?.let { totalHoles.value = it.toInt() }
            ?: run { totalHoles.value = maxOf(totalHoles.value, holeNumber.value) }

        // Green coordinates
        val frontLat = data["front_lat"] as? Number
        val frontLon = data["front_lon"] as? Number
        if (frontLat != null && frontLon != null) greenFront = GpsPoint(frontLat.toDouble(), frontLon.toDouble())

        val greenLat = data["green_lat"] as? Number
        val greenLon = data["green_lon"] as? Number
        if (greenLat != null && greenLon != null) greenCenter = GpsPoint(greenLat.toDouble(), greenLon.toDouble())

        val backLat = data["back_lat"] as? Number
        val backLon = data["back_lon"] as? Number
        if (backLat != null && backLon != null) greenBack = GpsPoint(backLat.toDouble(), backLon.toDouble())

        @Suppress("UNCHECKED_CAST")
        val clubs = data["clubBag"] as? List<String>
        if (!clubs.isNullOrEmpty()) clubBag.value = clubs

        strokes.value = 0
        putts.value = 0
        hasHoleData.value = true
        isRoundEnded.value = false
        recomputeDistances()

        if (!isActive) startLocation()
    }

    // ── Distance computation ──

    private fun recomputeDistances() {
        val loc = currentLocation ?: return
        val here = GpsPoint(loc.latitude, loc.longitude)
        greenFront?.let { frontYards.value = here.distanceYards(it) }
        greenCenter?.let { pinYards.value = here.distanceYards(it) }
        greenBack?.let { backYards.value = here.distanceYards(it) }
    }

    // ── Location ──

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        if (isActive) return
        isActive = true
        val client = LocationServices.getFusedLocationProviderClient(context)
        fusedClient = client
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    currentLocation = it
                    recomputeDistances()
                }
            }
        }
        locationCallback = callback
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    private fun stopLocation() {
        locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
        locationCallback = null
        fusedClient = null
        isActive = false
    }

    // ── Club picker ──

    private fun showClubPickerAfterShot() {
        if (clubBag.value.isEmpty()) return
        selectedClub.value = defaultClubForDistance(pinYards.value)
        showClubPicker.value = true
        clubPickerTimer?.cancel()
        clubPickerTimer = Timer().apply {
            schedule(10_000L) { confirmClub() }
        }
    }

    private fun defaultClubForDistance(yards: Int?): String {
        val validClubs = clubBag.value.filter { it != "putter" }
        if (yards == null || validClubs.isEmpty()) return validClubs.firstOrNull() ?: "unknown"
        val table = listOf(
            "driver" to 230, "3w" to 210, "5w" to 195,
            "3h" to 190, "4h" to 180, "5h" to 170,
            "4i" to 170, "5i" to 160, "6i" to 150,
            "7i" to 140, "8i" to 130, "9i" to 120,
            "pw" to 110, "gw" to 95, "sw" to 80, "lw" to 60,
        )
        for ((club, minDist) in table) {
            if (club in validClubs && yards >= minDist) return club
        }
        return validClubs.last()
    }

    // ── Phone messaging ──

    private fun sendToPhone(path: String, data: Map<String, Any>) {
        val messageClient = Wearable.getMessageClient(context)
        Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                val json = buildJsonObject {
                    data.forEach { (k, v) ->
                        when (v) {
                            is Int -> put(k, v)
                            is Double -> put(k, v)
                            is String -> put(k, v)
                            is Boolean -> put(k, v)
                        }
                    }
                }.toString()
                messageClient.sendMessage(node.id, path, json.toByteArray())
            }
        }
    }

    private fun sendShotToPhone() {
        val msg = mutableMapOf<String, Any>(
            "action" to "shot",
            "holeNumber" to holeNumber.value,
            "strokes" to strokes.value,
            "putts" to putts.value,
        )
        currentLocation?.let {
            msg["lat"] = it.latitude
            msg["lon"] = it.longitude
            if (it.hasAltitude()) msg["altitude"] = it.altitude
        }
        sendToPhone("/watch/action", msg)
    }

    private fun sendStrokesToPhone() {
        sendToPhone("/watch/action", mapOf(
            "action" to "stroke",
            "holeNumber" to holeNumber.value,
            "strokes" to strokes.value,
            "putts" to putts.value,
        ))
    }

    private fun sendNavigateToPhone() {
        sendToPhone("/watch/action", mapOf(
            "action" to "navigate",
            "holeNumber" to holeNumber.value,
        ))
    }

    private fun sendEndRoundToPhone() {
        sendToPhone("/watch/action", mapOf("action" to "endRound"))
    }

    private fun sendCancelRoundToPhone() {
        sendToPhone("/watch/action", mapOf("action" to "cancelRound"))
    }

    private fun sendClubToPhone() {
        sendToPhone("/watch/action", mapOf(
            "action" to "clubSelection",
            "holeNumber" to holeNumber.value,
            "club" to selectedClub.value,
        ))
    }
}

// Extension to convert DataMap to a simple Map
private fun DataMap.toMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    for (key in keySet()) {
        map[key] = when {
            containsKey(key) -> get<Any>(key)
            else -> null
        }
    }
    return map
}
