package io.github.nicechester.gobirdie.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.clearUpdateCallback
import androidx.health.services.client.data.*
import androidx.health.services.client.endExercise
import androidx.health.services.client.getCapabilities
import androidx.health.services.client.startExercise
import kotlinx.coroutines.*

class ExerciseService : Service() {

    companion object {
        private const val TAG = "ExerciseService"
        private const val CHANNEL_ID = "gobirdie_exercise"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ExerciseService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ExerciseService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var exerciseStarted = false
    private var callback: ExerciseUpdateCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!exerciseStarted) {
            exerciseStarted = true
            scope.launch { startExerciseSession() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.launch {
            try {
                val client = HealthServices.getClient(this@ExerciseService).exerciseClient
                callback?.let { client.clearUpdateCallback(it) }
                client.endExercise()
                Log.i(TAG, "Exercise ended")
            } catch (e: Exception) {
                Log.w(TAG, "Error ending exercise: ${e.message}")
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun startExerciseSession() {
        try {
            val client = HealthServices.getClient(this).exerciseClient
            val capabilities = client.getCapabilities()

            val golfSupported = ExerciseType.GOLF in capabilities.supportedExerciseTypes
            val exerciseType = if (golfSupported) ExerciseType.GOLF else ExerciseType.WALKING

            val typeCapabilities = capabilities.getExerciseTypeCapabilities(exerciseType)
            val dataTypes = mutableSetOf<DataType<*, *>>()
            if (DataType.LOCATION in typeCapabilities.supportedDataTypes) dataTypes.add(DataType.LOCATION)
            if (DataType.HEART_RATE_BPM in typeCapabilities.supportedDataTypes) dataTypes.add(DataType.HEART_RATE_BPM)

            val config = ExerciseConfig(
                exerciseType,
                dataTypes,
                isAutoPauseAndResumeEnabled = false,
                isGpsEnabled = true,
            )

            val cb = object : ExerciseUpdateCallback {
                override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
                    val points = update.latestMetrics.getData(DataType.LOCATION)
                    val last = points.lastOrNull()?.value ?: return
                    WearSessionHolder.session?.onExerciseLocation(
                        last.latitude, last.longitude, last.altitude
                    )
                }
                override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
                override fun onRegistered() { Log.i(TAG, "Exercise callback registered") }
                override fun onRegistrationFailed(throwable: Throwable) {
                    Log.e(TAG, "Exercise callback registration failed", throwable)
                }
                override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {}
            }
            callback = cb
            client.setUpdateCallback(cb)
            client.startExercise(config)
            Log.i(TAG, "Exercise started: $exerciseType, dataTypes=$dataTypes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start exercise", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Golf Round", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("GoBirdie")
            .setContentText("Tracking golf round")
            .setOngoing(true)
            .build()
}
