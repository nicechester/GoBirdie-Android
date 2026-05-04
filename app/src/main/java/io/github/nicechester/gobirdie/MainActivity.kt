package io.github.nicechester.gobirdie

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.nicechester.gobirdie.navigation.TopLevelRoute
import io.github.nicechester.gobirdie.ui.map.MapScreen
import io.github.nicechester.gobirdie.ui.round.RoundScreen
import io.github.nicechester.gobirdie.ui.scorecards.ScorecardsScreen
import io.github.nicechester.gobirdie.ui.settings.SettingsScreen
import io.github.nicechester.gobirdie.ui.theme.GoBirdieTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted or denied — LocationService handles gracefully */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationPermissionRequest.launch(arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ))
        enableEdgeToEdge()

        val appState = ViewModelProvider(this).get(AppState::class.java)

        setContent {
            GoBirdieTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                val hasActiveRound = appState.activeSession != null
                this@MainActivity.requestedOrientation = if (hasActiveRound)
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                else
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            TopLevelRoute.entries.forEach { dest ->
                                NavigationBarItem(
                                    selected = currentRoute == dest.route,
                                    onClick = {
                                        navController.navigate(dest.route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = { Text(dest.label) },
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = TopLevelRoute.ROUND.route,
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        composable(TopLevelRoute.SCORECARDS.route) { ScorecardsScreen() }
                        composable(TopLevelRoute.ROUND.route) { RoundScreen(appState) }
                        composable(TopLevelRoute.MAP.route) { MapScreen(appState) }
                        composable(TopLevelRoute.SETTINGS.route) { SettingsScreen() }
                    }
                }
            }
        }
    }
}
