package io.github.nicechester.gobirdie.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status

class RoundForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "gobirdie_round"
        private const val NOTIFICATION_ID = 2

        fun start(context: Context) {
            context.startForegroundService(Intent(context, RoundForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RoundForegroundService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, "Golf Round", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun buildNotification(): Notification {
        val tapPendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notifBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("GoBirdie")
            .setContentText("Golf round in progress")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(tapPendingIntent)

        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, notifBuilder)
            .setStaticIcon(R.mipmap.ic_launcher)
            .setTouchIntent(tapPendingIntent)
            .setStatus(Status.forPart(Status.TextPart("Golf Round")))
            .build()
            .apply(applicationContext)

        return notifBuilder.build()
    }
}
