package io.github.nicechester.gobirdie.ui.map

import android.graphics.PointF
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.nicechester.gobirdie.AppState
import io.github.nicechester.gobirdie.core.data.session.RoundSession
import io.github.nicechester.gobirdie.core.model.*
import io.github.nicechester.gobirdie.ui.round.StartRoundScreen
import io.github.nicechester.gobirdie.ui.round.StartRoundViewModel
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
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

// ─── Course Map View (MapLibre + Overlay) ───────────────────────────

@Composable
private fun CourseMapView(
    course: Course,
    holeIndex: Int,
    playerLocation: GpsPoint?,
    shots: List<Shot>,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Track map instance and readiness
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var styleLoaded by remember { mutableStateOf(false) }

    // Tap state
    var tapPoint by remember { mutableStateOf<GpsPoint?>(null) }

    val hole = course.holes.getOrNull(holeIndex)

    // Camera update on hole change
    LaunchedEffect(holeIndex, styleLoaded) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!styleLoaded) return@LaunchedEffect
        updateGolfLayers(map, hole)
        animateToHole(map, hole, course)
        tapPoint = null
    }

    Box(Modifier.fillMaxSize()) {
        // MapLibre view
        AndroidView(
            factory = { ctx ->
                MapLibre.getInstance(ctx)
                val mv = MapView(ctx)
                mv.onCreate(null)
                mv.onStart()
                mv.onResume()
                mv.getMapAsync { mlMap ->
                    mapLibreMap = mlMap
                    mlMap.setStyle(Style.Builder().fromUri(osmStyleUri(ctx))) { _ ->
                        styleLoaded = true
                    }
                    mlMap.uiSettings.isRotateGesturesEnabled = false
                    mlMap.addOnMapClickListener { latLng ->
                        val tapped = GpsPoint(latLng.latitude, latLng.longitude)
                        tapPoint = tapped
                        true
                    }
                }
                mapView = mv
                mv
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = {
                it.onPause()
                it.onStop()
                it.onDestroy()
            },
        )

        // Compose overlay for dots and lines
        if (styleLoaded && mapLibreMap != null) {
            MapOverlay(
                map = mapLibreMap!!,
                mapView = mapView!!,
                hole = hole,
                playerLocation = playerLocation,
                shots = shots,
                tapPoint = tapPoint,
                onClearTap = { tapPoint = null },
            )
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

// ─── Compose Overlay ────────────────────────────────────────────────

@Composable
private fun MapOverlay(
    map: MapLibreMap,
    mapView: MapView,
    hole: Hole?,
    playerLocation: GpsPoint?,
    shots: List<Shot>,
    tapPoint: GpsPoint?,
    onClearTap: () -> Unit,
) {
    val greenCenter = hole?.greenCenter

    // Project GPS points to screen coordinates
    fun project(gps: GpsPoint): Offset {
        val px = map.projection.toScreenLocation(LatLng(gps.lat, gps.lon))
        return Offset(px.x, px.y)
    }

    val playerPx = playerLocation?.let { project(it) }
    val flagPx = greenCenter?.let { project(it) }
    val tapPx = tapPoint?.let { project(it) }
    val shotPxList = shots.map { shot -> shot to project(shot.location) }

    // Pulse animation for player dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "pulseScale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))

    Canvas(Modifier.fillMaxSize()) {
        // Shot connecting lines with distance labels
        for (i in 1 until shotPxList.size) {
            val from = shotPxList[i - 1].second
            val to = shotPxList[i].second
            drawLine(Color.Yellow.copy(alpha = 0.8f), from, to, strokeWidth = 3f, pathEffect = dashedEffect)
            val yards = shotPxList[i - 1].first.location.distanceYards(shotPxList[i].first.location)
            drawDistanceLabel(this, "${yards}y", (from + to) / 2f, Color.Black.copy(alpha = 0.7f))
        }

        // Last shot → green line
        if (shotPxList.isNotEmpty() && flagPx != null) {
            val lastPx = shotPxList.last().second
            drawLine(GolfGreen.copy(alpha = 0.8f), lastPx, flagPx, strokeWidth = 3f, pathEffect = dashedEffect)
            val lastShot = shotPxList.last().first
            if (greenCenter != null) {
                val yards = lastShot.location.distanceYards(greenCenter)
                drawDistanceLabel(this, "${yards}y", (lastPx + flagPx) / 2f, GolfGreen.copy(alpha = 0.7f))
            }
        }

        // Tap mode lines
        if (tapPx != null) {
            if (playerPx != null) {
                drawLine(Color.White, playerPx, tapPx, strokeWidth = 4f, pathEffect = dashedEffect)
                if (playerLocation != null && tapPoint != null) {
                    val yards = playerLocation.distanceYards(tapPoint)
                    drawDistanceLabel(this, "$yards", (playerPx + tapPx) / 2f, Color.Black.copy(alpha = 0.7f))
                }
            }
            if (flagPx != null) {
                drawLine(GolfGreen, tapPx, flagPx, strokeWidth = 4f, pathEffect = dashedEffect)
                if (tapPoint != null && greenCenter != null) {
                    val yards = tapPoint.distanceYards(greenCenter)
                    drawDistanceLabel(this, "$yards", (tapPx + flagPx) / 2f, GolfGreen.copy(alpha = 0.7f))
                }
            }
            // Tap dot
            drawCircle(Color.Red, 10f, tapPx)
            drawCircle(Color.White, 10f, tapPx, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        } else {
            // Default: player → green line
            if (playerPx != null && flagPx != null) {
                drawLine(Color.White, playerPx, flagPx, strokeWidth = 4f, pathEffect = dashedEffect)
                if (playerLocation != null && greenCenter != null) {
                    val yards = playerLocation.distanceYards(greenCenter)
                    drawDistanceLabel(this, "$yards", (playerPx + flagPx) / 2f, Color.Black.copy(alpha = 0.7f))
                }
            }
        }

        // Shot dots
        shotPxList.forEach { (shot, px) ->
            val dotColor = clubColor(shot.club)
            drawCircle(dotColor, 12f, px)
            drawCircle(Color.Black.copy(alpha = 0.6f), 12f, px, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }

        // Flag dot
        if (flagPx != null) {
            drawCircle(GolfGreen, 14f, flagPx)
            drawCircle(Color.White, 14f, flagPx, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
        }

        // Player dot with pulse
        if (playerPx != null) {
            drawCircle(Color.Blue.copy(alpha = pulseAlpha * 0.25f), 44f * pulseScale, playerPx)
            drawCircle(Color.Blue, 16f, playerPx)
            drawCircle(Color.White, 16f, playerPx, style = androidx.compose.ui.graphics.drawscope.Stroke(5f))
        }
    }

    // Clear tap button
    if (tapPoint != null) {
        Box(Modifier.fillMaxSize()) {
            IconButton(
                onClick = onClearTap,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp, bottom = 80.dp),
            ) {
                Icon(
                    Icons.Default.Cancel, "Clear",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

private fun clubColor(club: ClubType): Color = when (club) {
    ClubType.DRIVER -> Color.Red
    ClubType.WOOD_3, ClubType.WOOD_5 -> Color(0xFFFF9800)
    ClubType.HYBRID_3, ClubType.HYBRID_4, ClubType.HYBRID_5 -> Color(0xFF009688)
    ClubType.IRON_4, ClubType.IRON_5, ClubType.IRON_6, ClubType.IRON_7, ClubType.IRON_8, ClubType.IRON_9 -> Color.Yellow
    ClubType.PITCHING_WEDGE, ClubType.GAP_WEDGE, ClubType.SAND_WEDGE, ClubType.LOB_WEDGE -> Color.Cyan
    ClubType.PUTTER -> Color.White
    ClubType.UNKNOWN -> Color.Gray
}

private fun drawDistanceLabel(scope: DrawScope, text: String, center: Offset, bgColor: Color) {
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 44f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    val bgPaint = android.graphics.Paint().apply {
        color = (bgColor.copy(alpha = 0.8f)).toArgb()
        isAntiAlias = true
    }
    val textWidth = paint.measureText(text)
    val padding = 8f
    scope.drawContext.canvas.nativeCanvas.apply {
        drawRoundRect(
            center.x - textWidth / 2 - padding,
            center.y - 16f,
            center.x + textWidth / 2 + padding,
            center.y + 14f,
            8f, 8f, bgPaint,
        )
        drawText(text, center.x, center.y + 8f, paint)
    }
}

private fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )
}
