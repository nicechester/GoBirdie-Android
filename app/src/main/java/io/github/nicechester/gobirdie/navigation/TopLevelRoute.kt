package io.github.nicechester.gobirdie.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GolfCourse
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Scoreboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelRoute(
    val label: String,
    val icon: ImageVector,
    val route: String,
) {
    SCORECARDS("Scorecards", Icons.Default.Scoreboard, "scorecards"),
    ROUND("Round", Icons.Default.GolfCourse, "round"),
    MAP("Map", Icons.Default.Map, "map"),
    SETTINGS("Settings", Icons.Default.Settings, "settings"),
}
