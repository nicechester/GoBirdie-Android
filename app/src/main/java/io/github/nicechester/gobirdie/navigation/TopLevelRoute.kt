package io.github.nicechester.gobirdie.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GolfCourse
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Scoreboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.nicechester.gobirdie.R

enum class TopLevelRoute(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val route: String,
) {
    SCORECARDS(R.string.tab_scorecards, Icons.Default.Scoreboard, "scorecards"),
    ROUND(R.string.tab_round, Icons.Default.GolfCourse, "round"),
    MAP(R.string.tab_map, Icons.Default.Map, "map"),
    SETTINGS(R.string.tab_settings, Icons.Default.Settings, "settings"),
}
