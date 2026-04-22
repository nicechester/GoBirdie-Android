package io.github.nicechester.gobirdie.ui.round

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.nicechester.gobirdie.AppState

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
fun RoundScreen(appState: AppState = hiltViewModel()) {
    val session by appState.activeSession.collectAsState()
    val course by appState.activeCourse.collectAsState()
    val playerLocation by appState.locationService.location.collectAsState()
    val context = LocalContext.current

    var showStartRound by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.any { it }) {
            appState.locationService.start()
            showStartRound = true
        }
    }

    if (session != null && course != null) {
        ActiveRoundScreen(
            session = session!!,
            course = course!!,
            playerLocation = playerLocation,
            onEndRound = { appState.endActiveRound() },
            onCancelRound = { appState.cancelActiveRound() },
        )
    } else if (showStartRound) {
        val vm: StartRoundViewModel = hiltViewModel()

        // Load saved courses immediately, then update with GPS when available
        LaunchedEffect(Unit) {
            vm.loadWithLocation(null)
        }
        LaunchedEffect(playerLocation) {
            if (playerLocation != null) {
                vm.onLocationReceived(playerLocation!!)
            }
        }

        StartRoundScreen(
            viewModel = vm,
            onStartRound = { selectedCourse, hole ->
                appState.startRound(selectedCourse, hole)
                showStartRound = false
            },
            onDismiss = {
                showStartRound = false
                appState.locationService.stop()
            },
        )
    } else {
        // Empty state
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Flag, null, Modifier.size(64.dp), tint = Color(0xFF2E7D32))
            Spacer(Modifier.height(16.dp))
            Text("No Active Round", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Start a round to track distances\nand mark your shots",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    if (hasLocationPermission(context)) {
                        appState.locationService.start()
                        showStartRound = true
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Start Round")
            }
        }
    }
}
