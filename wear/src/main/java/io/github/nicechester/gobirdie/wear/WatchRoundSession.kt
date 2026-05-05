package io.github.nicechester.gobirdie.wear

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import com.google.android.gms.wearable.*
import io.github.nicechester.gobirdie.core.model.ClubType
import io.github.nicechester.gobirdie.core.model.GpsPoint
import kotlinx.coroutines.flow.MutableStateFlow
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
    val clubPickerCountdown = MutableStateFlow(15)

    private var accumulatedStrokes = 0
    val totalStrokes: Int get() = accumulatedStrokes + strokes.value

    val latestHeartRate = MutableStateFlow<Int?>(null)
    private val heartRateSamples = mutableListOf<Map<String, Any>>()

    private var greenFront: GpsPoint? = null
    private var greenCenter: GpsPoint? = null
    private var greenBack: GpsPoint? = null
    private var currentLocation: Location? = null

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var isActive = false
    private var exerciseServiceStarted = false

    private var clubPickerTimer: Timer? = null
    private var countdownTimer: Timer? = null

    // Swing detection
    private var sensorManager: SensorManager? = null
    private var lastSwingTime = 0L

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
        stopSwingDetection()
        stopExerciseService()
        isRoundEnded.value = true
        sendEndRoundToPhone()
    }

    fun cancelRound() {
        stopLocation()
        stopSwingDetection()
        stopExerciseService()
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
        latestHeartRate.value = null
        heartRateSamples.clear()
        hasExerciseLocation = false
        dismissClubPicker()
        clubBag.value = emptyList()
    }

    fun confirmClub() {
        if (!showClubPicker.value) return
        clubPickerTimer?.cancel()
        countdownTimer?.cancel()
        clubPickerTimer = null
        countdownTimer = null
        showClubPicker.value = false
        sendClubToPhone()
    }

    fun cancelClubPicker() {
        if (!showClubPicker.value) return
        clubPickerTimer?.cancel()
        countdownTimer?.cancel()
        clubPickerTimer = null
        countdownTimer = null
        showClubPicker.value = false
        strokes.update { maxOf(0, it - 1) }
        sendStrokesToPhone()
    }

    fun dismissClubPicker() {
        clubPickerTimer?.cancel()
        countdownTimer?.cancel()
        clubPickerTimer = null
        countdownTimer = null
        showClubPicker.value = false
    }

    fun resetClubPickerTimer() {
        clubPickerTimer?.cancel()
        countdownTimer?.cancel()
        clubPickerCountdown.value = 15
        clubPickerTimer = Timer().apply { schedule(15_000L) { confirmClub() } }
        countdownTimer = Timer().apply {
            var remaining = 14
            schedule(1_000L, 1_000L) {
                clubPickerCountdown.value = remaining
                if (remaining-- <= 0) cancel()
            }
        }
    }

    // ── Message handling (from phone) ──

    fun handleMessage(data: Map<String, Any?>) {
        when (data["action"] as? String) {
            "roundCancelled" -> { stopLocation(); stopSwingDetection(); resetToWaiting() }
            "roundEnded" -> { stopLocation(); stopSwingDetection(); isRoundEnded.value = true }
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
        if (!exerciseServiceStarted) {
            exerciseServiceStarted = true
            if (!BuildConfig.DEBUG) ExerciseService.start(context)
        }
        startSwingDetection()
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
                if (hasExerciseLocation) return
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

    private var hasExerciseLocation = false

    fun onExerciseLocation(lat: Double, lon: Double, altitude: Double) {
        hasExerciseLocation = true
        val loc = Location("exercise").apply {
            latitude = lat
            longitude = lon
            this.altitude = altitude
        }
        currentLocation = loc
        recomputeDistances()
    }

    fun onHeartRateUpdate(bpm: Int) {
        latestHeartRate.value = bpm
        val sample = mutableMapOf<String, Any>(
            "timestamp" to (System.currentTimeMillis() / 1000.0),
            "bpm" to bpm,
        )
        currentLocation?.let { if (it.hasAltitude()) sample["altitude"] = it.altitude }
        heartRateSamples.add(sample)
    }

    private fun stopExerciseService() {
        if (exerciseServiceStarted) {
            exerciseServiceStarted = false
            ExerciseService.stop(context)
        }
    }

    // ── Club picker ──

    private fun showClubPickerAfterShot() {
        if (clubBag.value.isEmpty()) return
        selectedClub.value = defaultClubForDistance(pinYards.value)
        clubPickerCountdown.value = 15
        showClubPicker.value = true
        clubPickerTimer?.cancel()
        countdownTimer?.cancel()
        clubPickerTimer = Timer().apply { schedule(15_000L) { confirmClub() } }
        countdownTimer = Timer().apply {
            var remaining = 14
            schedule(1_000L, 1_000L) {
                clubPickerCountdown.value = remaining
                if (remaining-- <= 0) cancel()
            }
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

    // ── Swing detection ──

    private fun startSwingDetection() {
        if (sensorManager != null) return
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager = sm
        sm.registerListener(swingListener, accel, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun stopSwingDetection() {
        sensorManager?.unregisterListener(swingListener)
        sensorManager = null
    }

    private val swingListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
            val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
            if (g <= 8f) return
            val now = System.currentTimeMillis()
            if (now - lastSwingTime < 2_000L) return
            lastSwingTime = now
            if (showClubPicker.value) {
                resetClubPickerTimer()
            } else {
                markShot()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
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
        latestHeartRate.value?.let { msg["heartRate"] = it }
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
        val msg = mutableMapOf<String, Any>("action" to "endRound")
        if (heartRateSamples.isNotEmpty()) {
            val timeline = buildJsonArray {
                heartRateSamples.forEach { sample ->
                    add(buildJsonObject {
                        sample.forEach { (k, v) ->
                            when (v) {
                                is Int -> put(k, v)
                                is Double -> put(k, v)
                                is String -> put(k, v)
                            }
                        }
                    })
                }
            }.toString()
            msg["heartRateTimeline"] = timeline
        }
        sendToPhone("/watch/action", msg)
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
