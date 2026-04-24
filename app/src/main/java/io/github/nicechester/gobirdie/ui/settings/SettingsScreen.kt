package io.github.nicechester.gobirdie.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nicechester.gobirdie.Config
import io.github.nicechester.gobirdie.core.data.CourseStore
import io.github.nicechester.gobirdie.core.data.api.GolfCourseApiClient
import io.github.nicechester.gobirdie.core.model.ClubType
import io.github.nicechester.gobirdie.core.model.Course
import io.github.nicechester.gobirdie.core.model.GpsPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GolfGreen = Color(0xFF2E7D32)

// ─── Navigation state ───────────────────────────────────────────────

private enum class SettingsNav { Main, Courses, Clubs }

@Composable
fun SettingsScreen() {
    var nav by remember { mutableStateOf(SettingsNav.Main) }
    when (nav) {
        SettingsNav.Main -> SettingsMain(
            onCourses = { nav = SettingsNav.Courses },
            onClubs = { nav = SettingsNav.Clubs },
        )
        SettingsNav.Courses -> CourseManagerScreen(onBack = { nav = SettingsNav.Main })
        SettingsNav.Clubs -> MyClubsScreen(onBack = { nav = SettingsNav.Main })
    }
}

// ─── Main Settings ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMain(onCourses: () -> Unit, onClubs: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("gobirdie_settings", Context.MODE_PRIVATE) }
    var teeColor by remember { mutableStateOf(prefs.getString("teeColor", "Blue") ?: "Blue") }
    var showTeePicker by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        // Courses
        item { SectionHeader("Courses") }
        item {
            SettingsRow(icon = Icons.Default.Map, label = "Manage Courses") { onCourses() }
        }

        // Equipment
        item { SectionHeader("Equipment") }
        item {
            SettingsRow(icon = Icons.Default.SportsGolf, label = "My Clubs") { onClubs() }
        }

        // Tee
        item { SectionHeader("Tee") }
        item {
            SettingsRow(
                icon = Icons.Default.Circle,
                label = "Tee Color",
                trailing = teeColor,
            ) { showTeePicker = true }
        }

        // Tip Jar
        item { SectionHeader("Support Development") }
        item {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp)) {
                    Text("GoBirdie is free with no ads.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "If you enjoy the app, consider buying me a drink! 🍺",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://venmo.com/violkim"))
                        context.startActivity(intent)
                    }) {
                        Text("Venmo @violkim", color = GolfGreen)
                    }
                }
            }
        }
        item {
            Text(
                "Tips help cover development costs and keep the app free.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // About
        item { SectionHeader("About") }
        item {
            val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
            AboutRow("Version", version)
        }
        item {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/nicechester/GoBirdie-Android/blob/main/MANUAL.md")))
            }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Manual", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { AboutRow("Map Data", "© OpenStreetMap contributors") }
        item { AboutRow("Maps", "© MapLibre") }
        item { AboutRow("License", "© 2026 Chester Kim. All rights reserved.") }
    }

    // Tee color picker dialog
    if (showTeePicker) {
        val teeColors = listOf("Black", "Blue", "White", "Yellow", "Red")
        AlertDialog(
            onDismissRequest = { showTeePicker = false },
            title = { Text("Tee Color") },
            text = {
                Column {
                    teeColors.forEach { color ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                teeColor = color
                                prefs.edit().putString("teeColor", color).apply()
                                showTeePicker = false
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Circle, null, Modifier.size(16.dp),
                                tint = teeDisplayColor(color),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(color, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.weight(1f))
                            if (color == teeColor) {
                                Icon(Icons.Default.Check, null, tint = GolfGreen)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

private fun teeDisplayColor(name: String): Color = when (name) {
    "Black" -> Color.DarkGray
    "Blue" -> Color.Blue
    "White" -> Color.LightGray
    "Yellow" -> Color.Yellow
    "Red" -> Color.Red
    else -> Color.Gray
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).clickable(onClick = onClick),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = GolfGreen)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Course Manager ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val courseStore = remember { CourseStore(context) }
    var savedCourses by remember { mutableStateOf(courseStore.loadAll()) }
    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val apiClient = remember { GolfCourseApiClient(Config.GOLF_COURSE_API_KEY) }

    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text("Courses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        // Search bar
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = { Text("Search courses to download") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = { searchText = ""; searchResults = emptyList() }) {
                        Icon(Icons.Default.Clear, "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        // Search button
        if (searchText.isNotBlank()) {
            TextButton(
                onClick = {
                    isSearching = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val results = withContext(Dispatchers.IO) {
                                apiClient.searchCourses(searchText.trim(), GpsPoint(34.0, -118.0))
                            }
                            searchResults = results.map { r ->
                                SearchResult(id = r.id, name = r.name, city = r.city, location = r.location)
                            }
                            if (results.isEmpty()) errorMessage = "No courses found for \"${searchText.trim()}\""
                        } catch (e: Exception) {
                            errorMessage = "Search failed: ${e.message}"
                        }
                        isSearching = false
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text("Search", color = GolfGreen)
            }
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            // Loading
            if (isSearching) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GolfGreen, modifier = Modifier.size(24.dp))
                    }
                }
            }

            // Error
            if (errorMessage != null) {
                item {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                }
            }

            // Search results
            if (searchResults.isNotEmpty()) {
                item { SectionHeader("Search Results") }
                items(searchResults, key = { it.id }) { result ->
                    val alreadySaved = savedCourses.any { it.golfCourseApiId == result.id }
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(result.name, style = MaterialTheme.typography.bodyMedium)
                                if (result.city.isNotBlank()) {
                                    Text(result.city, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (alreadySaved) {
                                Icon(Icons.Default.CheckCircle, null, tint = GolfGreen)
                            } else if (downloadingId == result.id.toString()) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = GolfGreen, strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = {
                                    downloadingId = result.id.toString()
                                    val prefs = context.getSharedPreferences("gobirdie_settings", Context.MODE_PRIVATE)
                                    val teeColor = prefs.getString("teeColor", "Blue") ?: "Blue"
                                    scope.launch {
                                        try {
                                            val apiHoles = withContext(Dispatchers.IO) {
                                                apiClient.fetchHoles(result.id, teeColor.lowercase())
                                            }
                                            // Fetch GPS geometry from Overpass by name
                                            val overpassCourses = withContext(Dispatchers.IO) {
                                                try {
                                                    val overpass = io.github.nicechester.gobirdie.core.data.api.OverpassClient()
                                                    val nearby = overpass.searchCourses(result.location, 5000)
                                                    val match = nearby.firstOrNull { it.name.equals(result.name, ignoreCase = true) }
                                                        ?: nearby.firstOrNull()
                                                    match?.let { overpass.downloadCourse(it.osmId, it.name) }
                                                } catch (e: Exception) { null }
                                            }
                                            val overpassHoles = overpassCourses?.firstOrNull()?.holes ?: emptyList()
                                            val holes = apiHoles.map { h ->
                                                val gpsHole = overpassHoles.firstOrNull { it.number == h.number }
                                                io.github.nicechester.gobirdie.core.model.Hole(
                                                    id = java.util.UUID.randomUUID().toString(),
                                                    number = h.number,
                                                    par = h.par,
                                                    handicap = h.handicap,
                                                    yardage = h.yardage.toString(),
                                                    tee = gpsHole?.tee,
                                                    greenCenter = gpsHole?.greenCenter,
                                                    greenFront = gpsHole?.greenFront,
                                                    greenBack = gpsHole?.greenBack,
                                                    geometry = gpsHole?.geometry,
                                                )
                                            }
                                            val courseLocation = overpassCourses?.firstOrNull()?.location ?: GpsPoint(0.0, 0.0)
                                            val course = Course(
                                                id = java.util.UUID.randomUUID().toString(),
                                                name = result.name,
                                                location = courseLocation,
                                                holes = holes,
                                                golfCourseApiId = result.id,
                                            )
                                            courseStore.save(course)
                                            savedCourses = courseStore.loadAll()
                                        } catch (e: Exception) {
                                            errorMessage = "Download failed: ${e.message}"
                                        }
                                        downloadingId = null
                                    }
                                }) {
                                    Icon(Icons.Default.Download, "Download", tint = GolfGreen)
                                }
                            }
                        }
                    }
                }
            }

            // Saved courses
            item { SectionHeader("Saved Courses (${savedCourses.size})") }
            if (savedCourses.isEmpty()) {
                item {
                    Text("No courses downloaded yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
            items(savedCourses, key = { it.id }) { course ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            courseStore.delete(course.id)
                            savedCourses = courseStore.loadAll()
                            true
                        } else false
                    }
                )
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                ) {
                    Surface(Modifier.fillMaxWidth(), tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(16.dp)) {
                            Text(course.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${course.holes.size} holes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class SearchResult(val id: Int, val name: String, val city: String, val location: GpsPoint = GpsPoint(0.0, 0.0))

// ─── My Clubs ───────────────────────────────────────────────────────

@Composable
private fun MyClubsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("gobirdie_clubs", Context.MODE_PRIVATE) }
    val defaultSet = ClubType.defaultBag.map { it.name }.toSet()
    var enabledClubs by remember {
        mutableStateOf(prefs.getStringSet("enabled", defaultSet) ?: defaultSet)
    }

    fun toggle(club: ClubType) {
        val mutable = enabledClubs.toMutableSet()
        if (mutable.contains(club.name)) mutable.remove(club.name) else mutable.add(club.name)
        enabledClubs = mutable
        prefs.edit().putStringSet("enabled", mutable).apply()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text("My Clubs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(ClubType.allSelectable) { club ->
                val enabled = enabledClubs.contains(club.name)
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                        .clickable { toggle(club) },
                    tonalElevation = 1.dp,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(club.displayName, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        if (enabled) {
                            Icon(Icons.Default.Check, null, tint = GolfGreen)
                        }
                    }
                }
            }

            item {
                Text(
                    "Selected clubs appear in the Mark Shot picker during a round.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            item {
                TextButton(
                    onClick = {
                        enabledClubs = defaultSet
                        prefs.edit().putStringSet("enabled", defaultSet).apply()
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text("Reset to Default")
                }
            }
        }
    }
}
