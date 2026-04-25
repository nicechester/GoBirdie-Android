package io.github.nicechester.gobirdie.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import io.github.nicechester.gobirdie.core.data.RoundStore
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncManager"

@Singleton
class SyncManager @Inject constructor(
    private val context: Context,
    private val roundStore: RoundStore,
) {
    private var server: SyncServer? = null
    private var nsd: NsdAdvertiser? = null
    var isRunning = false
        private set

    fun start(): Boolean {
        if (isRunning) return true
        if (!isOnWifi()) {
            Log.w(TAG, "Not on WiFi — refusing to start sync server")
            return false
        }
        server = SyncServer(roundStore).also { it.start() }
        nsd = NsdAdvertiser(context).also { it.start() }
        isRunning = true
        Log.i(TAG, "Sync server started on port $SYNC_PORT")
        return true
    }

    fun stop() {
        server?.stop()
        server = null
        nsd?.stop()
        nsd = null
        isRunning = false
        Log.i(TAG, "Sync server stopped")
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
