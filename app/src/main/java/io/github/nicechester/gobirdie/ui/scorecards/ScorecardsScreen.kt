package io.github.nicechester.gobirdie.ui.scorecards

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.nicechester.gobirdie.core.model.*
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

private val GolfGreen = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScorecardsScreen(viewModel: ScorecardsViewModel = hiltViewModel()) {
    val rounds by viewModel.rounds.collectAsState()
    var selectedRound by remember { mutableStateOf<Round?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    if (selectedRound != null) {
        ScorecardDetail(
            round = selectedRound!!,
            viewModel = viewModel,
            onDismiss = { selectedRound = null },
        )
    } else {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Scorecards") }) }
        ) { padding ->
            if (rounds.isEmpty()) {
                // Empty state
                Column(
                    Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("📋", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No Scorecards", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Completed rounds will appear here", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.padding(padding)) {
                    items(rounds, key = { it.id }) { round ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.delete(round.id)
                                    true
                                } else false
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Box(
                                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error).padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Icon(Icons.Default.Delete, "Delete", tint = Color.White)
                                }
                            },
                            enableDismissFromStartToEnd = false,
                        ) {
                            RoundRow(round) { selectedRound = round }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ─── Round Row ──────────────────────────────────────────────────────

@Composable
private fun RoundRow(round: Round, onClick: () -> Unit) {
    val playedPar = round.holes.filter { it.strokes > 0 }.sumOf { it.par }
    val diff = round.totalStrokes - playedPar
    val diffStr = when { diff > 0 -> "+$diff"; diff == 0 -> "E"; else -> "$diff" }
    val diffColor = when { diff < 0 -> GolfGreen; diff == 0 -> MaterialTheme.colorScheme.onSurface; else -> Color.Red }

    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(round.courseName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(formatDate(round.startedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${round.totalStrokes}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(diffStr, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = diffColor)
            }
        }
    }
}

// ─── Scorecard Detail ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScorecardDetail(round: Round, viewModel: ScorecardsViewModel, onDismiss: () -> Unit) {
    var currentRound by remember { mutableStateOf(round) }
    val playedHoles = currentRound.holes.filter { it.strokes > 0 }
    val parTotal = playedHoles.sumOf { it.par }
    val frontNine = currentRound.holes.take(9).let { if (it.any { h -> h.strokes > 0 }) it else emptyList() }
    val backNine = currentRound.holes.drop(9).take(9).let { if (it.any { h -> h.strokes > 0 }) it else emptyList() }
    val holesWithShots = currentRound.holes.filter { it.shots.isNotEmpty() }
    var showShotMap by remember { mutableStateOf(false) }

    if (showShotMap) {
        val course = remember { viewModel.loadCourse(currentRound.courseId) }
        if (course != null) {
            ShotMapScreen(
                holes = holesWithShots,
                courseHoles = course.holes,
                round = currentRound,
                viewModel = viewModel,
                onDismiss = {
                    showShotMap = false
                    currentRound = viewModel.loadRound(currentRound.id) ?: currentRound
                },
            )
        } else {
            showShotMap = false
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentRound.courseName) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            // Date
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("📅 ${formatDate(currentRound.startedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Front 9
            if (frontNine.isNotEmpty()) {
                NineSection("Front 9", frontNine)
            }

            // Back 9
            if (backNine.isNotEmpty()) {
                if (frontNine.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                NineSection("Back 9", backNine)
            }

            // Totals
            HorizontalDivider()
            TotalsRow(currentRound, playedHoles, parTotal)

            // Stats
            HorizontalDivider(Modifier.padding(top = 4.dp))
            StatsSection(playedHoles)

            // Shot Map button
            if (holesWithShots.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showShotMap = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Default.Map, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shot Map")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ─── Nine Section ───────────────────────────────────────────────────

@Composable
private fun NineSection(title: String, holes: List<HoleScore>) {
    val totalStrokes = holes.sumOf { it.strokes }

    Column {
        // Header
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("Putts", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
            Text("Pen", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
            Text("$totalStrokes", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
        }

        holes.forEachIndexed { idx, hole ->
            HoleRow(hole)
            if (idx < holes.size - 1) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp)
        }
    }
}

@Composable
private fun HoleRow(hole: HoleScore) {
    val diff = hole.strokes - hole.par
    val scoreColor = when {
        hole.strokes == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        diff <= -2 -> Color(0xFFFFD600) // eagle
        diff == -1 -> GolfGreen
        diff == 0 -> MaterialTheme.colorScheme.onSurface
        diff == 1 -> Color(0xFFE65100) // bogey orange
        else -> Color.Red
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${hole.number}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
        Text("P${hole.par}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))

        if (hole.strokes > 0) {
            Text("${hole.strokes}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = scoreColor, modifier = Modifier.width(28.dp))
            Spacer(Modifier.weight(1f))
            Text(if (hole.putts > 0) "${hole.putts}" else "—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
            Text(
                if (hole.penalties > 0) "${hole.penalties}" else "—",
                style = MaterialTheme.typography.labelSmall,
                color = if (hole.penalties > 0) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.Center,
            )
            val diffStr = when { diff == 0 -> "E"; diff > 0 -> "+$diff"; else -> "$diff" }
            Text(diffStr, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = scoreColor, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
        } else {
            Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
            Spacer(Modifier.weight(1f))
        }
    }
}

// ─── Totals ─────────────────────────────────────────────────────────

@Composable
private fun TotalsRow(round: Round, playedHoles: List<HoleScore>, parTotal: Int) {
    val diff = round.totalStrokes - parTotal
    val diffStr = when { diff > 0 -> "+$diff"; diff == 0 -> "E"; else -> "$diff" }
    val diffColor = when { diff < 0 -> GolfGreen; diff == 0 -> MaterialTheme.colorScheme.onSurface; else -> Color.Red }

    Surface(tonalElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Total", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
            Text("${round.totalStrokes}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = diffColor, modifier = Modifier.width(28.dp))
            Spacer(Modifier.weight(1f))
            Text("${round.totalPutts}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
            Text("${playedHoles.sumOf { it.penalties }}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFFE65100), modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
            Text(diffStr, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = diffColor, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
        }
    }
}

// ─── Stats ──────────────────────────────────────────────────────────

@Composable
private fun StatsSection(playedHoles: List<HoleScore>) {
    val girCount = playedHoles.count { it.computedGir }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatRow("Holes Played", "${playedHoles.size}")
        StatRow("GIR", "$girCount/${playedHoles.size}")
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

// ─── Helpers ────────────────────────────────────────────────────────

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")

private fun formatDate(iso: String): String =
    runCatching {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).format(dateFormatter)
    }.getOrDefault(iso)

// ─── Shot Map Screen ────────────────────────────────────────────────

@Composable
private fun ShotMapScreen(
    holes: List<HoleScore>,
    courseHoles: List<Hole>,
    round: Round,
    viewModel: ScorecardsViewModel,
    onDismiss: () -> Unit,
) {
    var holeIdx by remember { mutableIntStateOf(0) }
    var editMode by remember { mutableStateOf(false) }
    var editableHoles by remember { mutableStateOf(holes) }
    var selectedShotId by remember { mutableStateOf<String?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClubPicker by remember { mutableStateOf(false) }
    var clubPickerShotId by remember { mutableStateOf<String?>(null) }
    var clubPickerInitialClub by remember { mutableStateOf(ClubType.UNKNOWN) }

    val holeScore = editableHoles.getOrNull(holeIdx) ?: return
    val courseHole = courseHoles.firstOrNull { it.number == holeScore.number }
    val shots = holeScore.shots.sortedBy { it.sequence }

    val context = LocalContext.current
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleLoaded by remember { mutableStateOf(false) }

    fun holeIndex() = editableHoles.indexOfFirst { it.id == holeScore.id }

    fun saveEdits() {
        val updated = round.copy(
            holes = editableHoles,
            totalStrokes = editableHoles.sumOf { it.strokes },
            totalPutts = editableHoles.sumOf { it.putts },
        )
        viewModel.saveRound(updated)
    }

    // Camera update on hole/edit change
    LaunchedEffect(holeIdx, styleLoaded) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!styleLoaded) return@LaunchedEffect
        shotMapCamera(map, courseHole, shots)
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this shot?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    val sid = selectedShotId ?: return@TextButton
                    val hi = holeIndex().takeIf { it >= 0 } ?: return@TextButton
                    val updatedShots = editableHoles[hi].shots
                        .filter { it.id != sid }
                        .mapIndexed { i, s -> s.copy(sequence = i + 1) }
                    editableHoles = editableHoles.toMutableList().also {
                        it[hi] = it[hi].copy(shots = updatedShots, strokes = updatedShots.size + it[hi].putts)
                    }
                    selectedShotId = null
                    dirty = true
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showClubPicker) {
        ClubPickerDialog(
            initial = clubPickerInitialClub,
            onSelect = { club ->
                showClubPicker = false
                val sid = clubPickerShotId ?: return@ClubPickerDialog
                val hi = holeIndex().takeIf { it >= 0 } ?: return@ClubPickerDialog
                val si = editableHoles[hi].shots.indexOfFirst { it.id == sid }.takeIf { it >= 0 } ?: return@ClubPickerDialog
                editableHoles = editableHoles.toMutableList().also {
                    val shots2 = it[hi].shots.toMutableList()
                    shots2[si] = shots2[si].copy(club = club)
                    it[hi] = it[hi].copy(shots = shots2)
                }
                dirty = true
            },
            onDismiss = { showClubPicker = false },
        )
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapLibre.getInstance(ctx)
                val mv = MapView(ctx)
                mv.onCreate(null)
                mv.getMapAsync { mlMap ->
                    mapLibreMap = mlMap
                    val json = """
                        {"version":8,"sources":{"osm":{"type":"raster","tiles":["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256}},"layers":[{"id":"osm","type":"raster","source":"osm"}]}
                    """.trimIndent()
                    val file = java.io.File(ctx.cacheDir, "shotmap-style.json")
                    file.writeText(json)
                    mlMap.setStyle(Style.Builder().fromUri("file://${file.absolutePath}")) {
                        styleLoaded = true
                    }
                    mlMap.uiSettings.isRotateGesturesEnabled = false
                }
                mv
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.onDestroy() },
        )

        // Overlay
        if (styleLoaded && mapLibreMap != null) {
            ShotMapOverlay(
                map = mapLibreMap!!,
                shots = shots,
                courseHole = courseHole,
                holeScore = holeScore,
                editMode = editMode,
                selectedShotId = selectedShotId,
                onTapShot = { shotId ->
                    if (selectedShotId == shotId) {
                        // second tap → club picker
                        clubPickerShotId = shotId
                        clubPickerInitialClub = holeScore.shots.firstOrNull { it.id == shotId }?.club ?: ClubType.UNKNOWN
                        showClubPicker = true
                    } else {
                        selectedShotId = shotId
                    }
                },
                onTapMap = { gps ->
                    if (editMode) {
                        val hi = holeIndex().takeIf { it >= 0 } ?: return@ShotMapOverlay
                        val seq = (editableHoles[hi].shots.maxOfOrNull { it.sequence } ?: 0) + 1
                        val newShot = Shot(
                            sequence = seq,
                            location = gps,
                            timestamp = java.time.Instant.now().toString(),
                            club = ClubType.UNKNOWN,
                        )
                        editableHoles = editableHoles.toMutableList().also {
                            it[hi] = it[hi].copy(shots = it[hi].shots + newShot, strokes = it[hi].shots.size + 1 + it[hi].putts)
                        }
                        selectedShotId = newShot.id
                        clubPickerShotId = newShot.id
                        clubPickerInitialClub = ClubType.UNKNOWN
                        showClubPicker = true
                        dirty = true
                    }
                },
                onMoveShot = { shotId, gps ->
                    val hi = holeIndex().takeIf { it >= 0 } ?: return@ShotMapOverlay
                    val si = editableHoles[hi].shots.indexOfFirst { it.id == shotId }.takeIf { it >= 0 } ?: return@ShotMapOverlay
                    editableHoles = editableHoles.toMutableList().also {
                        val shots2 = it[hi].shots.toMutableList()
                        shots2[si] = shots2[si].copy(location = gps)
                        it[hi] = it[hi].copy(shots = shots2)
                    }
                    dirty = true
                },
            )
        }

        // Hole info bar
        Surface(
            Modifier.align(Alignment.TopCenter).padding(top = 48.dp, start = 16.dp, end = 16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.6f),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (holeIdx > 0) { holeIdx--; selectedShotId = null } }, enabled = holeIdx > 0) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Prev", tint = if (holeIdx > 0) Color.White else Color.Gray)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Hole ${holeScore.number}", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "Par ${holeScore.par}  ·  ${holeScore.strokes} strokes  ·  ${holeScore.putts} putts",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { if (holeIdx < editableHoles.size - 1) { holeIdx++; selectedShotId = null } }, enabled = holeIdx < editableHoles.size - 1) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next", tint = if (holeIdx < editableHoles.size - 1) Color.White else Color.Gray)
                }
            }
        }

        // Bottom bar
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            // Edit hints + delete
            if (editMode) {
                Surface(color = Color.Black.copy(alpha = 0.6f)) {
                    Column(Modifier.fillMaxWidth()) {
                        if (selectedShotId != null) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = { showDeleteConfirm = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                                ) {
                                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Delete Shot", fontSize = 12.sp)
                                }
                                Spacer(Modifier.weight(1f))
                                Text("Drag pin to move", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                            }
                        }
                        // Putts +/-
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text("Putts", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(16.dp))
                            IconButton(
                                onClick = {
                                    val hi = holeIndex().takeIf { it >= 0 } ?: return@IconButton
                                    if (editableHoles[hi].putts > 0) {
                                        editableHoles = editableHoles.toMutableList().also {
                                            it[hi] = it[hi].copy(putts = it[hi].putts - 1, strokes = it[hi].strokes - 1)
                                        }
                                        dirty = true
                                    }
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Text("−", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "${editableHoles.getOrNull(holeIdx)?.putts ?: holeScore.putts}",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.Center,
                            )
                            IconButton(
                                onClick = {
                                    val hi = holeIndex().takeIf { it >= 0 } ?: return@IconButton
                                    editableHoles = editableHoles.toMutableList().also {
                                        it[hi] = it[hi].copy(putts = it[hi].putts + 1, strokes = it[hi].strokes + 1)
                                    }
                                    dirty = true
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Text("+", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Tap map to add shot", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                            Text("Tap pin to select", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }
                }
            }

            // Edit / Close buttons
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (editMode) {
                            selectedShotId = null
                            if (dirty) saveEdits()
                        }
                        editMode = !editMode
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (editMode) Color.White else Color.Black.copy(alpha = 0.6f),
                        contentColor = if (editMode) Color.Black else Color.White,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (editMode) "Done" else "Edit")
                }
                Button(
                    onClick = {
                        if (dirty) saveEdits()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Close")
                }
            }
        }
    }
}

// ─── Club Picker Dialog ──────────────────────────────────────────────

@Composable
private fun ClubPickerDialog(initial: ClubType, onSelect: (ClubType) -> Unit, onDismiss: () -> Unit) {
    val clubs = ClubType.entries.filter { it != ClubType.UNKNOWN }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Club") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn {
                items(clubs) { club ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(club) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (club == initial) {
                            Text("✓", Modifier.width(24.dp), color = GolfGreen, fontWeight = FontWeight.Bold)
                        } else {
                            Spacer(Modifier.width(24.dp))
                        }
                        Text(club.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ShotMapOverlay(
    map: MapLibreMap,
    shots: List<Shot>,
    courseHole: Hole?,
    holeScore: HoleScore,
    editMode: Boolean = false,
    selectedShotId: String? = null,
    onTapShot: (String) -> Unit = {},
    onTapMap: (GpsPoint) -> Unit = {},
    onMoveShot: (String, GpsPoint) -> Unit = { _, _ -> },
) {
    fun project(gps: GpsPoint): Offset {
        val px = map.projection.toScreenLocation(LatLng(gps.lat, gps.lon))
        return Offset(px.x, px.y)
    }

    var draggingShotId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(editMode, shots) {
                detectTapGestures { tapOffset ->
                    val hit = shots.firstOrNull { s ->
                        (project(s.location) - tapOffset).getDistance() < 40f
                    }
                    if (hit != null) {
                        onTapShot(hit.id)
                    } else {
                        val coord = map.projection.fromScreenLocation(
                            android.graphics.PointF(tapOffset.x, tapOffset.y)
                        )
                        onTapMap(GpsPoint(lat = coord.latitude, lon = coord.longitude))
                    }
                }
            }
            .pointerInput(editMode, shots) {
                if (!editMode) return@pointerInput
                detectDragGestures(
                    onDragStart = { startOffset ->
                        draggingShotId = shots.firstOrNull { s ->
                            (project(s.location) - startOffset).getDistance() < 40f
                        }?.id
                        dragOffset = startOffset
                    },
                    onDrag = { change, _ ->
                        if (draggingShotId != null) {
                            dragOffset = change.position
                            change.consume()
                        }
                        // if not dragging a shot, don't consume — let MapLibre pan
                    },
                    onDragEnd = {
                        val sid = draggingShotId
                        if (sid != null) {
                            val coord = map.projection.fromScreenLocation(
                                android.graphics.PointF(dragOffset.x, dragOffset.y)
                            )
                            onMoveShot(sid, GpsPoint(lat = coord.latitude, lon = coord.longitude))
                        }
                        draggingShotId = null
                    },
                    onDragCancel = { draggingShotId = null },
                )
            }
    ) {
        // Build point chain: tee → shots → green
        data class ChainPoint(val offset: Offset, val gps: GpsPoint, val club: ClubType?)
        val chain = mutableListOf<ChainPoint>()

        courseHole?.tee?.let { chain.add(ChainPoint(project(it), it, null)) }
        shots.forEach { s -> chain.add(ChainPoint(project(s.location), s.location, s.club)) }
        courseHole?.greenCenter?.let { chain.add(ChainPoint(project(it), it, null)) }

        // Lines between points
        for (i in 1 until chain.size) {
            val from = chain[i - 1]
            val to = chain[i]
            val color = shotMapClubColor(to.club ?: from.club ?: ClubType.UNKNOWN)
            drawLine(color, from.offset, to.offset, strokeWidth = 4f, pathEffect = dashedEffect)

            // Yardage label
            val yards = from.gps.distanceYards(to.gps)
            if (yards > 0) {
                shotMapLabel(this, "${yards}y", (from.offset + to.offset) / 2f, Color.Black.copy(alpha = 0.7f))
            }
        }

        // Shot dots with club abbreviation
        shots.forEach { s ->
            val isBeingDragged = s.id == draggingShotId
            val px = if (isBeingDragged) dragOffset else project(s.location)
            val isSelected = s.id == selectedShotId
            val color = shotMapClubColor(s.club)
            drawCircle(color, 28f, px)
            drawCircle(if (isSelected) Color.White else Color.White, 28f, px, style = Stroke(if (isSelected) 4f else 2f))
            if (isSelected) drawCircle(Color.White, 32f, px, style = Stroke(2f))
            val paint = android.graphics.Paint().apply {
                this.color = android.graphics.Color.WHITE
                textSize = 48f
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText(s.club.shortName, px.x, px.y + 12f, paint)
        }

        // Tee marker
        courseHole?.tee?.let {
            val px = project(it)
            drawCircle(Color.White, 10f, px)
            drawCircle(Color.DarkGray, 10f, px, style = Stroke(2f))
        }

        // Green / putt label
        courseHole?.greenCenter?.let {
            val px = project(it)
            drawCircle(GolfGreen, 22f, px)
            drawCircle(Color.White, 22f, px, style = Stroke(3f))
            if (holeScore.putts > 0) {
                shotMapLabel(this, "${holeScore.putts} putts", Offset(px.x, px.y + 32f), GolfGreen)
            }
        }
    }
}

private fun shotMapCamera(map: MapLibreMap, courseHole: Hole?, shots: List<Shot>) {
    val tee = courseHole?.tee
    val green = courseHole?.greenCenter

    if (tee != null && green != null) {
        val dLon = Math.toRadians(green.lon - tee.lon)
        val lat1 = Math.toRadians(tee.lat)
        val lat2 = Math.toRadians(green.lat)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val bearing = (Math.toDegrees(atan2(y, x)) + 360) % 360
        val distMeters = tee.distanceMeters(green)
        val zoom = when {
            distMeters > 500 -> 15.5
            distMeters > 300 -> 16.0
            distMeters > 150 -> 16.5
            else -> 17.0
        }
        val center = LatLng(
            (tee.lat + green.lat) / 2 + (green.lat - tee.lat) * 0.1,
            (tee.lon + green.lon) / 2 + (green.lon - tee.lon) * 0.1,
        )
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(center).zoom(zoom).bearing(bearing).tilt(0.0).build()
            ), 600,
        )
    } else {
        // Fallback: fit all shot locations
        val allPoints = shots.map { LatLng(it.location.lat, it.location.lon) }
        val target = if (allPoints.isNotEmpty()) {
            LatLng(allPoints.map { it.latitude }.average(), allPoints.map { it.longitude }.average())
        } else {
            LatLng(0.0, 0.0)
        }
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(target).zoom(16.0).build()
            ), 600,
        )
    }
}

private fun shotMapClubColor(club: ClubType): Color = when (club) {
    ClubType.DRIVER -> Color.Red
    ClubType.WOOD_3, ClubType.WOOD_5 -> Color(0xFFFF9800)
    ClubType.HYBRID_3, ClubType.HYBRID_4, ClubType.HYBRID_5 -> Color(0xFF009688)
    ClubType.IRON_4, ClubType.IRON_5, ClubType.IRON_6, ClubType.IRON_7, ClubType.IRON_8, ClubType.IRON_9 -> Color(0xFF2196F3)
    ClubType.PITCHING_WEDGE, ClubType.GAP_WEDGE, ClubType.SAND_WEDGE, ClubType.LOB_WEDGE -> Color(0xFF9C27B0)
    ClubType.PUTTER -> GolfGreen
    ClubType.UNKNOWN -> Color.Gray
}

private fun shotMapLabel(scope: DrawScope, text: String, center: Offset, bgColor: Color) {
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 54f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    val bgPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(
            (bgColor.alpha * 255).toInt(), (bgColor.red * 255).toInt(),
            (bgColor.green * 255).toInt(), (bgColor.blue * 255).toInt(),
        )
        isAntiAlias = true
    }
    val w = paint.measureText(text)
    scope.drawContext.canvas.nativeCanvas.apply {
        drawRoundRect(center.x - w / 2 - 10f, center.y - 20f, center.x + w / 2 + 10f, center.y + 18f, 8f, 8f, bgPaint)
        drawText(text, center.x, center.y + 10f, paint)
    }
}
