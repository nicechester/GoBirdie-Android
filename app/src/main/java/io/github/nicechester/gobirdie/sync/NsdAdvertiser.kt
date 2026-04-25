package io.github.nicechester.gobirdie.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

private const val TAG = "NsdAdvertiser"
private const val SERVICE_TYPE = "_gobirdie._tcp."
private const val SERVICE_NAME = "GoBirdie"

class NsdAdvertiser(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun start() {
        if (registrationListener != null) return
        val info = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            port = SYNC_PORT
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "Registered: ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.e(TAG, "Registration failed: $code")
                registrationListener = null
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(TAG, "Unregistered")
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.e(TAG, "Unregistration failed: $code")
            }
        }
        registrationListener = listener
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        registrationListener?.let {
            runCatching { nsdManager.unregisterService(it) }
            registrationListener = null
        }
    }
}
