package io.github.nicechester.gobirdie.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.nicechester.gobirdie.AppState
import io.github.nicechester.gobirdie.core.data.session.RoundSession
import io.github.nicechester.gobirdie.core.model.*
import io.github.nicechester.gobirdie.ui.components.ClubPickerSheet
import io.github.nicechester.gobirdie.ui.round.StartRoundScreen
import io.github.nicechester.gobirdie.ui.round.StartRoundViewModel
import io.github.nicechester.gobirdie.ui.scorecards.ShotMapCoordinator
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

private val GolfGreen = Color(0xFF2E7D32)

// ─── Map Screen (top-level) ─────────────────────────────────────────

private enum class ExploreState { Inactive, Picking, Exploring }

@Composable
fun MapScreen(appState: AppState) {
    val session by appState.activeSession.collectAsState()
    val course by appState.activeCourse.collectAsState()

    if (session != null && course != null) {
        MapActiveView(session = session!!, course = course!!, appState = appState)
    } else {
        var exploreState by remember { mutableStateOf(ExploreState.Inactive) }
        when (exploreState) {
            ExploreState.Inactive -> ExploreEntryView { exploreState = ExploreState.Picking }
            ExploreState.Picking -> {
                var selectedCourse by remember { mutableStateOf<Course?>(null) }
                if (selectedCourse != null) {
                    ExploreMapView(
                        course = selectedCourse!!,
                        appState = appState,
                        onBack = { selectedCourse = null },
                    )
                } else {
                    ExploreCoursePicker(
                        appState = appState,
                        onCourseSelected = { selectedCourse = it },
                        onCancel = { exploreState = ExploreState.Inactive },
                    )
                }
            }
            ExploreState.Exploring -> {} // handled inside Picking
        }
    }
}

// ─── Explore Entry ──────────────────────────────────────────────────

@Composable
private fun ExploreEntryView(onExplore: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Map, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("Explore Courses", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "View course layouts without tracking a round",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onExplore,
            colors = ButtonDefaults.buttonColors(containerColor = GolfGreen),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text("Start Exploring")
        }
    }
}

// ─── Explore Course Picker ──────────────────────────────────────────

@Composable
private fun ExploreCoursePicker(
    appState: AppState,
    onCourseSelected: (Course) -> Unit,
    onCancel: () -> Unit,
) {
    val vm: StartRoundViewModel = hiltViewModel()

    LaunchedEffect(Unit) {
        vm.reset()
        appState.locationService.start()
        vm.loadWithLocation(null)
        // Collect location updates sequentially after init
        appState.locationService.location.collect { loc ->
            if (loc != null) vm.onLocationReceived(loc)
        }
    }

    StartRoundScreen(
        viewModel = vm,
        onStartRound = { course, _ -> onCourseSelected(course) },
        onDismiss = {
            appState.locationService.stop()
            onCancel()
        },
        title = "Explore Courses",
    )
}

// ─── Explore Map View ───────────────────────────────────────────────

@Composable
private fun ExploreMapView(course: Course, appState: AppState, onBack: () -> Unit) {
    var holeIndex by remember { mutableIntStateOf(0) }
    val playerLocation by appState.locationService.location.collectAsState()
    val hole = course.holes.getOrNull(holeIndex)

    Box(Modifier.fillMaxSize()) {
        CourseMapView(
            course = course,
            holeIndex = holeIndex,
            playerLocation = playerLocation,
            shots = emptyList(),
        )

        // Hole info bar
        HoleInfoBar(
            hole = hole,
            isFirst = holeIndex == 0,
            isLast = holeIndex >= course.holes.size - 1,
            onPrev = { if (holeIndex > 0) holeIndex-- },
            onNext = { if (holeIndex < course.holes.size - 1) holeIndex++ },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp, start = 16.dp, end = 16.dp),
        )

        // Back button
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
        ) {
            Icon(Icons.Default.Close, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pick Another Course")
        }
    }
}

// ─── Active Round Map ───────────────────────────────────────────────

@Composable
private fun MapActiveView(session: RoundSession, course: Course, appState: AppState) {
    val round by session.round.collectAsState()
    val holeIndex by session.currentHoleIndex.collectAsState()
    val playerLocation by appState.locationService.location.collectAsState()
    val hole = course.holes.getOrNull(holeIndex)
    val holeScore = round.holes.getOrNull(holeIndex)
    var showShotEdit by remember { mutableStateOf(false) }

    if (showShotEdit && holeScore != null && hole != null) {
        ActiveRoundShotEditor(
            session = session,
            holeScore = holeScore,
            courseHole = hole,
            onDismiss = { showShotEdit = false },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        CourseMapView(
            course = course,
            holeIndex = holeIndex,
            playerLocation = playerLocation,
            shots = holeScore?.shots ?: emptyList(),
        )

        HoleInfoBar(
            hole = hole,
            isFirst = holeIndex == 0,
            isLast = holeIndex >= course.holes.size - 1,
            onPrev = { session.navigateTo(session.currentHoleNumber - 1) },
            onNext = { session.navigateTo(session.currentHoleNumber + 1) },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp, start = 16.dp, end = 16.dp),
        )

        if (holeScore != null && holeScore.shots.isNotEmpty()) {
            IconButton(
                onClick = { showShotEdit = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp, bottom = 24.dp)
                    .background(Color.Black.copy(alpha = 0.6f), shape = androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(Icons.Default.Edit, "Edit shots", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ─── Hole Info Bar ──────────────────────────────────────────────────

@Composable
private fun HoleInfoBar(
    hole: Hole?,
    isFirst: Boolean,
    isLast: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.6f),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev, enabled = !isFirst) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "Previous",
                    tint = if (isFirst) Color.Gray else Color.White,
                )
            }
            Spacer(Modifier.weight(1f))
            if (hole != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Hole ${hole.number}", color = Color.White, fontWeight = FontWeight.Bold)
                    val info = buildString {
                        append("Par ${hole.par}")
                        hole.yardage?.let { append("  ·  $it yd") }
                        hole.handicap?.let { append("  ·  HCP $it") }
                    }
                    Text(info, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onNext, enabled = !isLast) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, "Next",
                    tint = if (isLast) Color.Gray else Color.White,
                )
            }
        }
    }
}

// ─── Active Round Shot Editor ───────────────────────────────────────

@Composable
private fun ActiveRoundShotEditor(
    session: RoundSession,
    holeScore: HoleScore,
    courseHole: Hole,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coordinator = remember { ShotMapCoordinator(context) }
    var styleLoaded by remember { mutableStateOf(false) }
    var selectedShotId by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClubPicker by remember { mutableStateOf(false) }
    var clubPickerShotId by remember { mutableStateOf<String?>(null) }
    var pendingTapPoint by remember { mutableStateOf<GpsPoint?>(null) }

    val shots = holeScore.shots.sortedBy { it.sequence }

    coordinator.onTapShot = { selectedShotId = it }
    coordinator.onTapMap = { gps -> pendingTapPoint = gps }
    coordinator.onMoveShot = { shotId, gps ->
        session.updateShotLocation(holeScore.number, shotId, gps)
    }

    LaunchedEffect(styleLoaded) {
        if (styleLoaded) coordinator.update(shots, courseHole, holeScore, selectedShotId, moveCamera = true)
    }
    LaunchedEffect(shots, selectedShotId) {
        if (styleLoaded) coordinator.update(shots, courseHole, holeScore, selectedShotId)
    }

    if (pendingTapPoint != null) {
        AlertDialog(
            onDismissRequest = { pendingTapPoint = null },
            title = { Text("Add Shot Here?") },
            confirmButton = {
                TextButton(onClick = {
                    val gps = pendingTapPoint ?: return@TextButton
                    pendingTapPoint = null
                    val existing = holeScore.shots.sortedBy { it.sequence }
                    val insertAt = coordinator.insertionIndex(gps, existing, courseHole.greenCenter)
                    session.insertShot(holeScore.number, gps, insertAt)
                    // find the newly inserted shot and open club picker
                    val newShot = session.currentHole?.shots
                        ?.sortedBy { it.sequence }
                        ?.getOrNull(insertAt)
                    if (newShot != null) {
                        clubPickerShotId = newShot.id
                        showClubPicker = true
                    }
                }) { Text("Add", color = GolfGreen, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pendingTapPoint = null }) { Text("Cancel") } },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this shot?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    val sid = selectedShotId ?: return@TextButton
                    session.deleteShot(holeScore.number, sid)
                    selectedShotId = null
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showClubPicker) {
        ClubPickerSheet(
            defaultClub = shots.firstOrNull { it.id == clubPickerShotId }?.club ?: ClubType.UNKNOWN,
            onSelect = { club ->
                showClubPicker = false
                val sid = clubPickerShotId ?: return@ClubPickerSheet
                session.updateLastShotClub(holeScore.number, club, sid)
            },
            onCancel = { showClubPicker = false },
        )
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapLibre.getInstance(ctx)
                MapView(ctx).also { mv ->
                    mv.onCreate(null)
                    mv.onStart()
                    mv.onResume()
                    mv.getMapAsync { mlMap ->
                        coordinator.attach(mlMap)
                        mlMap.setStyle(Style.Builder().fromUri(osmStyleUri(ctx))) {
                            styleLoaded = true
                        }
                        mlMap.uiSettings.isRotateGesturesEnabled = false
                        mv.setOnTouchListener { _, event ->
                            coordinator.onTouchEvent(event)
                            false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.onPause(); it.onStop(); it.onDestroy() },
        )

        // Top bar
        Surface(
            Modifier.align(Alignment.TopCenter).padding(top = 48.dp, start = 16.dp, end = 16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.6f),
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Hole ${courseHole.number} · Edit Shots", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }

        // Bottom toolbar
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (selectedShotId != null) {
                Surface(color = Color.Black.copy(alpha = 0.7f)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { clubPickerShotId = selectedShotId; showClubPicker = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        ) {
                            Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Club", fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                        ) {
                            Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = { selectedShotId = null },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray),
                        ) {
                            Text("Deselect", fontSize = 12.sp)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Done")
                }
            }
        }
    }
}

// ─── Course Map View (MapLibre + Overlay) ───────────────────────────

@Composable
private fun CourseMapView(
    course: Course,
    holeIndex: Int,
    playerLocation: GpsPoint?,
    shots: List<Shot>,
) {
    val context = LocalContext.current
    val coordinator = remember { ShotMapCoordinator(context) }

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleLoaded by remember { mutableStateOf(false) }
    var tapPoint by remember { mutableStateOf<GpsPoint?>(null) }
    var cameraVersion by remember { mutableIntStateOf(0) }

    val hole = course.holes.getOrNull(holeIndex)

    // Hole change: update golf layers, camera, shots
    LaunchedEffect(holeIndex, styleLoaded) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!styleLoaded) return@LaunchedEffect
        updateGolfLayers(map, hole)
        animateToHole(map, hole, course)
        tapPoint = null
        coordinator.update(
            shots = shots,
            courseHole = hole,
            holeScore = HoleScore(number = holeIndex + 1, par = hole?.par ?: 4, greenCenter = hole?.greenCenter),
            selectedShotId = null,
            moveCamera = false,
        )
    }

    // Shot/player/tap overlay updates
    LaunchedEffect(shots, playerLocation, tapPoint, styleLoaded) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!styleLoaded) return@LaunchedEffect
        coordinator.update(
            shots = shots,
            courseHole = hole,
            holeScore = HoleScore(number = holeIndex + 1, par = hole?.par ?: 4, greenCenter = hole?.greenCenter),
            selectedShotId = null,
            moveCamera = false,
        )
        updatePlayerAndTapLayers(map, playerLocation, tapPoint, hole?.greenCenter)
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapLibre.getInstance(ctx)
                val mv = MapView(ctx)
                mv.onCreate(null)
                mv.onStart()
                mv.onResume()
                mv.getMapAsync { mlMap ->
                    mapLibreMap = mlMap
                    coordinator.attach(mlMap)
                    mlMap.setStyle(Style.Builder().fromUri(osmStyleUri(ctx))) { _ ->
                        styleLoaded = true
                    }
                    mlMap.uiSettings.isRotateGesturesEnabled = false
                    mlMap.addOnCameraIdleListener { cameraVersion++ }
                    mlMap.addOnMapClickListener { latLng ->
                        tapPoint = GpsPoint(latLng.latitude, latLng.longitude)
                        true
                    }
                }
                mv
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.onPause(); it.onStop(); it.onDestroy() },
        )

        if (tapPoint != null) {
            Box(Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { tapPoint = null },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp, bottom = 80.dp),
                ) {
                    Icon(Icons.Default.Cancel, "Clear", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }

        // Animated pulse overlay for player dot
        if (playerLocation != null && mapLibreMap != null) {
            val map = mapLibreMap!!
            val px = remember(playerLocation, cameraVersion) {
                map.projection.toScreenLocation(LatLng(playerLocation.lat, playerLocation.lon))
                    .let { Offset(it.x, it.y) }
            }
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseRadius by infiniteTransition.animateFloat(
                initialValue = 24f, targetValue = 52f,
                animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
                label = "pulseRadius",
            )
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f, targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
                label = "pulseAlpha",
            )
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color(0xFF2196F3), pulseRadius, px, alpha = pulseAlpha)
            }
        }
    }
}

private fun osmStyleUri(context: android.content.Context): String {
    val json = """
        {
            "version": 8,
            "sources": {
                "osm": {
                    "type": "raster",
                    "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                    "tileSize": 256,
                    "attribution": "© OpenStreetMap contributors"
                }
            },
            "layers": [{"id": "osm", "type": "raster", "source": "osm"}]
        }
    """.trimIndent()
    val file = java.io.File(context.cacheDir, "gobirdie-style.json")
    file.writeText(json)
    return "file://${file.absolutePath}"
}

private fun animateToHole(map: MapLibreMap, hole: Hole?, course: Course) {
    val tee = hole?.tee
    val green = hole?.greenCenter

    if (tee != null && green != null) {
        // Offset center ~15% toward green so the hole shifts down on screen (green is at top after rotation)
        val center = LatLng(
            (tee.lat + green.lat) / 2 + (green.lat - tee.lat) * 0.15,
            (tee.lon + green.lon) / 2 + (green.lon - tee.lon) * 0.15,
        )
        val bearing = teeToPinBearing(tee, green)
        val distMeters = tee.distanceMeters(green)
        // Longer holes need more zoom-out; rough heuristic matching iOS altitude * 3.5
        val zoom = when {
            distMeters > 500 -> 15.5
            distMeters > 300 -> 16.0
            distMeters > 150 -> 16.5
            else -> 17.0
        }
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(center)
                    .zoom(zoom)
                    .bearing(bearing)
                    .tilt(0.0)
                    .build()
            ),
            600,
        )
    } else {
        val target = green ?: tee ?: course.location
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(target.lat, target.lon))
                    .zoom(16.0)
                    .build()
            ),
            600,
        )
    }
}

private fun teeToPinBearing(tee: GpsPoint, green: GpsPoint): Double {
    val dLon = Math.toRadians(green.lon - tee.lon)
    val lat1 = Math.toRadians(tee.lat)
    val lat2 = Math.toRadians(green.lat)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    val bearing = Math.toDegrees(atan2(y, x))
    return (bearing + 360) % 360
}

// ─── GeoJSON Golf Layers ────────────────────────────────────────────

private const val GOLF_SOURCE_ID = "golf-geometry"
private val GOLF_LAYER_IDS = listOf("golf-water", "golf-rough", "golf-bunker", "golf-fairway")

private fun updateGolfLayers(map: MapLibreMap, hole: Hole?) {
    val style = map.style ?: return

    // Remove old layers and source
    GOLF_LAYER_IDS.forEach { id -> style.getLayer(id)?.let { style.removeLayer(it) } }
    style.getSource(GOLF_SOURCE_ID)?.let { style.removeSource(it) }

    val geometry = hole?.geometry ?: return

    val geojson = buildGeoJson(geometry)
    val source = GeoJsonSource(GOLF_SOURCE_ID, geojson)
    style.addSource(source)

    addFillLayer(style, "golf-water", "water", "#4A90D9", 0.6f)
    addFillLayer(style, "golf-rough", "rough", "#7CB87C", 0.5f)
    addFillLayer(style, "golf-bunker", "bunker", "#D4B96A", 0.7f)
    addFillLayer(style, "golf-fairway", "fairway", "#2E7D32", 0.5f)
}

private fun addFillLayer(style: Style, layerId: String, featureType: String, color: String, opacity: Float) {
    val layer = FillLayer(layerId, GOLF_SOURCE_ID)
    layer.setProperties(
        PropertyFactory.fillColor(android.graphics.Color.parseColor(color)),
        PropertyFactory.fillOpacity(opacity),
    )
    layer.setFilter(org.maplibre.android.style.expressions.Expression.eq(
        org.maplibre.android.style.expressions.Expression.get("featureType"),
        org.maplibre.android.style.expressions.Expression.literal(featureType),
    ))
    style.addLayer(layer)
}

private fun buildGeoJson(geometry: HoleGeometry): String {
    val features = JSONArray()

    geometry.fairway?.let { poly ->
        features.put(polygonFeature(poly, "fairway"))
    }
    geometry.bunkers.forEach { poly ->
        features.put(polygonFeature(poly, "bunker"))
    }
    geometry.water.forEach { poly ->
        features.put(polygonFeature(poly, "water"))
    }
    geometry.rough?.let { poly ->
        features.put(polygonFeature(poly, "rough"))
    }

    val collection = JSONObject()
    collection.put("type", "FeatureCollection")
    collection.put("features", features)
    return collection.toString()
}

private fun polygonFeature(polygon: Polygon, type: String): JSONObject {
    val coords = JSONArray()
    val ring = JSONArray()
    polygon.forEach { pt -> ring.put(JSONArray().put(pt.lon).put(pt.lat)) }
    // Close the ring
    if (polygon.isNotEmpty()) ring.put(JSONArray().put(polygon.first().lon).put(polygon.first().lat))
    coords.put(ring)

    val geom = JSONObject()
    geom.put("type", "Polygon")
    geom.put("coordinates", coords)

    val props = JSONObject()
    props.put("featureType", type)

    val feature = JSONObject()
    feature.put("type", "Feature")
    feature.put("geometry", geom)
    feature.put("properties", props)
    return feature
}

// ─── Player dot + tap lines as MapLibre layers ──────────────────────

private val PLAYER_TAP_LAYERS = listOf("player-dot", "tap-dot", "tap-line-player", "tap-line-green", "player-line-green", "tap-dist-player", "tap-dist-green", "player-dist-green")
private val PLAYER_TAP_SOURCES = listOf("player-src", "tap-src", "tap-line-player-src", "tap-line-green-src", "player-line-green-src", "tap-dist-player-src", "tap-dist-green-src", "player-dist-green-src")

private fun updatePlayerAndTapLayers(map: MapLibreMap, player: GpsPoint?, tap: GpsPoint?, green: GpsPoint?) {
    val style = map.style ?: return
    PLAYER_TAP_LAYERS.forEach { style.getLayer(it)?.let { l -> style.removeLayer(l) } }
    PLAYER_TAP_SOURCES.forEach { style.getSource(it)?.let { s -> style.removeSource(s) } }

    fun pointFeature(pt: GpsPoint) = JSONObject().apply {
        put("type", "Feature")
        put("geometry", JSONObject().apply {
            put("type", "Point")
            put("coordinates", JSONArray().put(pt.lon).put(pt.lat))
        })
        put("properties", JSONObject())
    }.toString()

    fun lineFeature(a: GpsPoint, b: GpsPoint) = JSONObject().apply {
        put("type", "Feature")
        put("geometry", JSONObject().apply {
            put("type", "LineString")
            put("coordinates", JSONArray().apply {
                put(JSONArray().put(a.lon).put(a.lat))
                put(JSONArray().put(b.lon).put(b.lat))
            })
        })
        put("properties", JSONObject())
    }.toString()

    fun midPoint(a: GpsPoint, b: GpsPoint) = GpsPoint((a.lat + b.lat) / 2, (a.lon + b.lon) / 2)

    fun addDistLabel(srcId: String, layerId: String, pt: GpsPoint, yards: Int) {
        val imgKey = "map-dist-$yards"
        if (style.getImage(imgKey) == null) style.addImage(imgKey, makeLabelBitmap("${yards}y", AColor.argb(180, 0, 0, 0)))
        style.addSource(GeoJsonSource(srcId, pointFeature(pt)))
        style.addLayer(SymbolLayer(layerId, srcId).apply {
            setProperties(iconImage(literal(imgKey)), iconAllowOverlap(literal(true)))
        })
    }

    // Player dot
    if (player != null) {
        val imgKey = "player-dot-img"
        if (style.getImage(imgKey) == null) style.addImage(imgKey, makePlayerDotBitmap())
        style.addSource(GeoJsonSource("player-src", pointFeature(player)))
        style.addLayer(SymbolLayer("player-dot", "player-src").apply {
            setProperties(iconImage(literal(imgKey)), iconAllowOverlap(literal(true)))
        })
    }

    if (tap != null) {
        // Tap dot
        val tapImgKey = "tap-dot-img"
        if (style.getImage(tapImgKey) == null) style.addImage(tapImgKey, makeTapDotBitmap())
        style.addSource(GeoJsonSource("tap-src", pointFeature(tap)))
        style.addLayer(SymbolLayer("tap-dot", "tap-src").apply {
            setProperties(iconImage(literal(tapImgKey)), iconAllowOverlap(literal(true)))
        })
        // Tap → player line
        if (player != null) {
            style.addSource(GeoJsonSource("tap-line-player-src", lineFeature(tap, player)))
            style.addLayer(LineLayer("tap-line-player", "tap-line-player-src").apply {
                setProperties(lineColor(literal("#FFFFFF")), lineWidth(literal(3f)), lineDasharray(arrayOf(2f, 1.5f)))
            })
            addDistLabel("tap-dist-player-src", "tap-dist-player", midPoint(tap, player), tap.distanceYards(player))
        }
        // Tap → green line
        if (green != null) {
            style.addSource(GeoJsonSource("tap-line-green-src", lineFeature(tap, green)))
            style.addLayer(LineLayer("tap-line-green", "tap-line-green-src").apply {
                setProperties(lineColor(literal("#2E7D32")), lineWidth(literal(3f)), lineDasharray(arrayOf(2f, 1.5f)))
            })
            addDistLabel("tap-dist-green-src", "tap-dist-green", midPoint(tap, green), tap.distanceYards(green))
        }
    } else if (player != null && green != null) {
        // Default: player → green line
        style.addSource(GeoJsonSource("player-line-green-src", lineFeature(player, green)))
        style.addLayer(LineLayer("player-line-green", "player-line-green-src").apply {
            setProperties(lineColor(literal("#FFFFFF")), lineWidth(literal(3f)), lineDasharray(arrayOf(2f, 1.5f)))
        })
        addDistLabel("player-dist-green-src", "player-dist-green", midPoint(player, green), player.distanceYards(green))
    }
}

private fun makePlayerDotBitmap(): Bitmap {
    val size = 48
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = size / 2f; val cy = size / 2f; val r = size / 2f - 4f
    canvas.drawCircle(cx, cy, r * 1.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.argb(60, 0, 0, 255) })
    canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.BLUE })
    canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = AColor.WHITE; strokeWidth = 4f
    })
    return bmp
}

private fun makeTapDotBitmap(): Bitmap {
    val size = 32
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = size / 2f; val cy = size / 2f; val r = size / 2f - 3f
    canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.RED })
    canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = AColor.WHITE; strokeWidth = 3f
    })
    return bmp
}

private fun makeLabelBitmap(text: String, bgColor: Int): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.WHITE; textSize = 36f; isFakeBoldText = true }
    val pad = 12f
    val w = (paint.measureText(text) + pad * 2).toInt()
    val h = 48
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), h / 2f, h / 2f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor })
    canvas.drawText(text, pad, h / 2f + 12f, paint)
    return bmp
}
