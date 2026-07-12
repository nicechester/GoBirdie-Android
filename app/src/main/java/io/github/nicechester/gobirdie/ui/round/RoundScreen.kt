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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.nicechester.gobirdie.AppState
import io.github.nicechester.gobirdie.ui.tournaments.TournamentsViewModel
import kotlinx.coroutines.launch

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
fun RoundScreen(appState: AppState) {
    val session by appState.activeSession.collectAsState()
    val course by appState.activeCourse.collectAsState()
    val playerLocation by appState.locationService.location.collectAsState()
    val pendingResume by appState.pendingResume.collectAsState()
    val showIdlePrompt by appState.showIdlePrompt.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showStartRound by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.any { it }) {
            appState.locationService.start()
            showStartRound = true
        }
    }

    // Resume prompt
    if (pendingResume != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Resume Round?") },
            text = { Text("You have an unfinished round at ${pendingResume!!.round.courseName}. Resume where you left off?") },
            confirmButton = {
                TextButton(onClick = { appState.resumeRound() }) {
                    Text("Resume")
                }
            },
            dismissButton = {
                TextButton(onClick = { appState.discardInProgressRound() }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    // Idle prompt
    if (showIdlePrompt) {
        AlertDialog(
            onDismissRequest = { appState.dismissIdlePrompt() },
            title = { Text("Still Playing?") },
            text = { Text("No activity detected for 30 minutes. Are you still playing?") },
            confirmButton = {
                TextButton(onClick = { appState.dismissIdlePrompt() }) {
                    Text("Yes, Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { appState.endActiveRound() }) {
                    Text("End Round", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    if (session != null && course != null) {
        val tournamentsVm: TournamentsViewModel = hiltViewModel()
        val activeRound by session!!.round.collectAsState()
        val hasTournament = remember(activeRound.courseId) {
            tournamentsVm.loadAll().any { it.courseId == activeRound.courseId }
        }

        // Sync active round to tournament whenever round updates
        LaunchedEffect(activeRound) {
            val tournaments = tournamentsVm.loadAll()
            val tournament = tournaments.firstOrNull { it.courseId == activeRound.courseId } ?: return@LaunchedEffect
            val idx = tournament.players.indexOfFirst { it.source == "SELF" }
            if (idx >= 0) {
                val updated = tournament.copy(
                    players = tournament.players.toMutableList().also {
                        it[idx] = it[idx].copy(holes = activeRound.holes)
                    }
                )
                tournamentsVm.save(updated)
            }
        }

        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            Box(Modifier.padding(padding)) {
                ActiveRoundScreen(
                    session = session!!,
                    course = course!!,
                    playerLocation = playerLocation,
                    onEndRound = { appState.endActiveRound() },
                    onCancelRound = { appState.cancelActiveRound() },
                    onSyncWatch = { appState.syncWear() },
                    onUserInteraction = { appState.resetIdleTimer() },
                    onCreateTournament = if (!hasTournament) {{
                        val r = session!!.round.value
                        val t = tournamentsVm.createTournament(r.courseId, r.courseName, tournamentsVm.todayDate(), null, seedRound = r)
                        tournamentsVm.save(t)
                        scope.launch { snackbarHostState.showSnackbar("Tournament created for ${r.courseName}") }
                    }} else null,
                )
            }
        }
    } else if (showStartRound) {
        val vm: StartRoundViewModel = hiltViewModel()

        LaunchedEffect(Unit) {
            vm.reset()
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
        var showSettingsNavigation by remember { mutableStateOf(false) }

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
                modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth().height(48.dp)
                    .semantics { testTag = "startRoundButton" },
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Start Round")
            }
        }
    }
}
