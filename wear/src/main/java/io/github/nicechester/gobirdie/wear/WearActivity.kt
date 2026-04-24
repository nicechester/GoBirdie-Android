package io.github.nicechester.gobirdie.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import io.github.nicechester.gobirdie.wear.ui.WatchRoundScreen

class WearActivity : ComponentActivity() {

    private lateinit var session: WatchRoundSession

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted or denied — location will just not work if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        session = WatchRoundSession(applicationContext)
        WearSessionHolder.session = session

        requestPermissionsIfNeeded()

        setContent {
            WatchRoundScreen(session)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        WearSessionHolder.session = null
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.BODY_SENSORS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}
