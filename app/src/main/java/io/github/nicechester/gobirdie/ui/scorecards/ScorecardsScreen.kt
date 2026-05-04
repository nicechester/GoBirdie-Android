package io.github.nicechester.gobirdie.ui.scorecards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.nicechester.gobirdie.core.model.*
import io.github.nicechester.gobirdie.ui.components.ClubPickerSheet
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
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
                        val index = rounds.indexOf(round)
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
                            RoundRow(round, Modifier.semantics { testTag = "scorecardItem_$index" }) { selectedRound = round }
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
private fun RoundRow(round: Round, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val playedPar = round.holes.filter { it.strokes > 0 }.sumOf { it.par }
    val diff = round.totalStrokes - playedPar
    val diffStr = when { diff > 0 -> "+$diff"; diff == 0 -> "E"; else -> "$diff" }
    val diffColor = when { diff < 0 -> GolfGreen; diff == 0 -> MaterialTheme.colorScheme.onSurface; else -> Color.Red }

    Surface(modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
    val course = remember(currentRound.courseId) { viewModel.loadCourse(currentRound.courseId) }

    if (showShotMap) {
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
    var pendingTapPoint by remember { mutableStateOf<GpsPoint?>(null) }
    var showReorderSheet by remember { mutableStateOf(false) }

    val holeScore = editableHoles.getOrNull(holeIdx) ?: return
    val courseHole = courseHoles.firstOrNull { it.number == holeScore.number }
    val shots = holeScore.shots.sortedBy { it.sequence }

    val context = LocalContext.current
    val coordinator = remember { ShotMapCoordinator(context) }
    var styleLoaded by remember { mutableStateOf(false) }

    coordinator.onTapShot = { shotId ->
        if (selectedShotId == shotId) {
            clubPickerShotId = shotId
            clubPickerInitialClub = holeScore.shots.firstOrNull { it.id == shotId }?.club ?: ClubType.UNKNOWN
            showClubPicker = true
        } else {
            selectedShotId = shotId
        }
    }
    coordinator.onTapMap = { gps -> if (editMode) pendingTapPoint = gps }
    coordinator.onMoveShot = { shotId, gps -> run {
        val hi = editableHoles.indexOfFirst { it.id == holeScore.id }.takeIf { it >= 0 } ?: return@run
        val si = editableHoles[hi].shots.indexOfFirst { it.id == shotId }.takeIf { it >= 0 } ?: return@run
        editableHoles = editableHoles.toMutableList().also {
            val shots2 = it[hi].shots.toMutableList()
            shots2[si] = shots2[si].copy(location = gps)
            it[hi] = it[hi].copy(shots = shots2)
        }
        dirty = true
    } }

    fun holeIndex() = editableHoles.indexOfFirst { it.id == holeScore.id }

    fun confirmPendingTap() {
        val gps = pendingTapPoint ?: return
        pendingTapPoint = null
        val hi = holeIndex().takeIf { it >= 0 } ?: return
        val existing = editableHoles[hi].shots.sortedBy { it.sequence }
        val insertAt = coordinator.insertionIndex(gps, existing, courseHole?.greenCenter)
        val resequenced = existing.toMutableList().also { it.add(insertAt, Shot(
            sequence = 0,
            location = gps,
            timestamp = java.time.Instant.now().toString(),
            club = ClubType.UNKNOWN,
        )) }
        val finalShots = resequenced.mapIndexed { i, s -> s.copy(sequence = i + 1) }
        val newShot = finalShots[insertAt]
        editableHoles = editableHoles.toMutableList().also {
            it[hi] = it[hi].copy(shots = finalShots, strokes = finalShots.size + it[hi].putts)
        }
        selectedShotId = newShot.id
        clubPickerShotId = newShot.id
        clubPickerInitialClub = ClubType.UNKNOWN
        showClubPicker = true
        dirty = true
    }

    fun saveEdits() {
        val updated = round.copy(
            holes = editableHoles,
            totalStrokes = editableHoles.sumOf { it.strokes },
            totalPutts = editableHoles.sumOf { it.putts },
        )
        viewModel.saveRound(updated)
    }

    LaunchedEffect(holeIdx, styleLoaded) {
        if (!styleLoaded) return@LaunchedEffect
        coordinator.update(shots, courseHole, holeScore, selectedShotId, moveCamera = true)
    }
    LaunchedEffect(shots, selectedShotId) {
        if (styleLoaded) coordinator.update(shots, courseHole, holeScore, selectedShotId)
    }

    if (pendingTapPoint != null) {
        AlertDialog(
            onDismissRequest = { pendingTapPoint = null },
            title = { Text("Add Shot Here?") },
            confirmButton = {
                TextButton(onClick = { confirmPendingTap() }) {
                    Text("Add", color = GolfGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTapPoint = null }) { Text("Cancel") }
            },
        )
    }

    if (showReorderSheet) {
        ReorderShotsSheet(
            shots = editableHoles.getOrNull(holeIdx)?.shots?.sortedBy { it.sequence } ?: emptyList(),
            onConfirm = { reordered ->
                showReorderSheet = false
                val hi = holeIndex().takeIf { it >= 0 } ?: return@ReorderShotsSheet
                editableHoles = editableHoles.toMutableList().also {
                    it[hi] = it[hi].copy(shots = reordered.mapIndexed { i, s -> s.copy(sequence = i + 1) })
                }
                dirty = true
            },
            onDismiss = { showReorderSheet = false },
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
        ClubPickerSheet(
            defaultClub = clubPickerInitialClub,
            onSelect = { club ->
                showClubPicker = false
                val sid = clubPickerShotId ?: return@ClubPickerSheet
                val hi = holeIndex().takeIf { it >= 0 } ?: return@ClubPickerSheet
                val si = editableHoles[hi].shots.indexOfFirst { it.id == sid }.takeIf { it >= 0 } ?: return@ClubPickerSheet
                editableHoles = editableHoles.toMutableList().also {
                    val shots2 = it[hi].shots.toMutableList()
                    shots2[si] = shots2[si].copy(club = club)
                    it[hi] = it[hi].copy(shots = shots2)
                }
                dirty = true
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
                    mv.getMapAsync { mlMap ->
                        coordinator.attach(mlMap)
                        val json = """{"version":8,"sources":{"osm":{"type":"raster","tiles":["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256}},"layers":[{"id":"osm","type":"raster","source":"osm"}]}"""
                        val file = java.io.File(ctx.cacheDir, "shotmap-style.json")
                        file.writeText(json)
                        mlMap.setStyle(Style.Builder().fromUri("file://${file.absolutePath}")) {
                            styleLoaded = true
                        }
                        mlMap.uiSettings.isRotateGesturesEnabled = false
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.onDestroy() },
        )

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
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { showReorderSheet = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                            ) {
                                Icon(Icons.Default.SwapVert, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reorder", fontSize = 12.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            if (selectedShotId != null) {
                                TextButton(
                                    onClick = { showDeleteConfirm = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                                ) {
                                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Delete", fontSize = 12.sp)
                                }
                            } else {
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

// ─── Reorder Shots Sheet ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReorderShotsSheet(
    shots: List<Shot>,
    onConfirm: (List<Shot>) -> Unit,
    onDismiss: () -> Unit,
) {
    var ordered by remember { mutableStateOf(shots) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Text("Reorder Shots", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onConfirm(ordered) }) {
                Text("Done", color = GolfGreen, fontWeight = FontWeight.Bold)
            }
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp).heightIn(max = 400.dp)) {
            itemsIndexed(ordered, key = { _, s -> s.id }) { idx, shot ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${idx + 1}.", Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(shot.club.displayName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(
                        onClick = { if (idx > 0) ordered = ordered.toMutableList().also { it.add(idx - 1, it.removeAt(idx)) } },
                        enabled = idx > 0,
                        modifier = Modifier.size(36.dp),
                    ) { Icon(Icons.Default.KeyboardArrowUp, "Move up", Modifier.size(20.dp)) }
                    IconButton(
                        onClick = { if (idx < ordered.size - 1) ordered = ordered.toMutableList().also { it.add(idx + 1, it.removeAt(idx)) } },
                        enabled = idx < ordered.size - 1,
                        modifier = Modifier.size(36.dp),
                    ) { Icon(Icons.Default.KeyboardArrowDown, "Move down", Modifier.size(20.dp)) }
                }
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}


