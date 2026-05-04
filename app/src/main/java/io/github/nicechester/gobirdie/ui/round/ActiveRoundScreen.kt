package io.github.nicechester.gobirdie.ui.round

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nicechester.gobirdie.core.data.session.RoundSession
import io.github.nicechester.gobirdie.core.model.ClubType
import io.github.nicechester.gobirdie.core.model.Course
import io.github.nicechester.gobirdie.core.model.GpsPoint
import io.github.nicechester.gobirdie.core.model.Hole
import io.github.nicechester.gobirdie.core.model.HoleScore

private val GolfGreen = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveRoundScreen(
    session: RoundSession,
    course: Course,
    playerLocation: GpsPoint?,
    onEndRound: () -> Unit,
    onCancelRound: () -> Unit,
    onUserInteraction: () -> Unit = {},
) {
    val round by session.round.collectAsState()
    val holeIndex by session.currentHoleIndex.collectAsState()
    val hole = round.holes.getOrNull(holeIndex)
    val courseHole = course.holes.firstOrNull { it.number == (hole?.number ?: 0) }
    val isLast = holeIndex == round.holes.size - 1

    var showClubPicker by remember { mutableStateOf(false) }
    var defaultClub by remember { mutableStateOf(ClubType.UNKNOWN) }
    var showEndConfirm by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showMoveShots by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val enabledClubs = remember {
        val prefs = context.getSharedPreferences("gobirdie_clubs", android.content.Context.MODE_PRIVATE)
        val defaultSet = ClubType.defaultBag.map { it.name }.toSet()
        val enabled = prefs.getStringSet("enabled", defaultSet) ?: defaultSet
        ClubType.allSelectable.filter { it.name in enabled }
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Hole ${hole?.number ?: ""}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { testTag = "holeLabel" },
            )
            Spacer(Modifier.width(8.dp))
            val info = buildString {
                append("Par ${hole?.par ?: ""}")
                courseHole?.yardage?.let { append("  ·  $it yds") }
            }
            Text(info, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Menu")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("End Round") },
                        onClick = { showMenu = false; showEndConfirm = true },
                        leadingIcon = { Icon(Icons.Default.Flag, null) },
                    )
                    if ((hole?.shots?.size ?: 0) > 0) {
                        DropdownMenuItem(
                            text = { Text("Move Shots to Hole...") },
                            onClick = { showMenu = false; showMoveShots = true },
                            leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Cancel Round", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onCancelRound() },
                        leadingIcon = { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error) },
                    )
                }
            }
        }

        // Distance display — Front | Flag | Back
        DistanceDisplay(
            playerLocation = playerLocation,
            courseHole = courseHole,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // Hole controls
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            // Row 1: Mark Shot + Penalty + Undo
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onUserInteraction()
                        val distToPin = courseHole?.greenCenter?.let { playerLocation?.distanceYards(it) }
                        defaultClub = defaultClubForDistance(distToPin, enabledClubs)
                        showClubPicker = true
                    },
                    modifier = Modifier.weight(1f).semantics { testTag = "markShotButton" },
                    colors = ButtonDefaults.buttonColors(containerColor = GolfGreen),
                ) {
                    Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Mark Shot")
                }
                FilledTonalButton(onClick = { onUserInteraction(); session.addPenalty() }, Modifier.width(64.dp).height(48.dp)) {
                    Text("⚠", fontSize = 20.sp)
                }
                FilledTonalButton(
                    onClick = { onUserInteraction(); session.undoLastAction() },
                    modifier = Modifier.width(64.dp).height(48.dp),
                    enabled = (hole?.strokes ?: 0) > 0,
                ) {
                    Icon(Icons.Default.Refresh, "Undo", Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Row 2: Putts stepper
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Putts", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { onUserInteraction(); session.setPutts((hole?.putts ?: 0) - 1) },
                        enabled = (hole?.putts ?: 0) > 0,
                    ) {
                        Icon(Icons.Default.RemoveCircle, "Minus", tint = if ((hole?.putts ?: 0) > 0) GolfGreen else Color.Gray)
                    }
                    Text("${hole?.putts ?: 0}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { testTag = "puttCount" })
                    IconButton(
                        onClick = { onUserInteraction(); session.setPutts((hole?.putts ?: 0) + 1) },
                        modifier = Modifier.semantics { testTag = "puttPlus" },
                    ) {
                        Icon(Icons.Default.AddCircle, "Plus", tint = GolfGreen)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Row 3: Prev / Next
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { onUserInteraction(); session.navigateTo(session.currentHoleNumber - 1) },
                    modifier = Modifier.weight(1f),
                    enabled = holeIndex > 0,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Prev")
                }
                Button(
                    onClick = { onUserInteraction(); if (isLast) onEndRound() else session.navigateTo(session.currentHoleNumber + 1) },
                    modifier = Modifier.weight(1f).semantics { testTag = "nextHoleButton" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLast) Color(0xFFE65100) else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isLast) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Text(if (isLast) "Finish" else "Next")
                    Spacer(Modifier.width(4.dp))
                    Icon(if (isLast) Icons.Default.Flag else Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }

        // Mini scorecard
        MiniScorecard(
            holes = round.holes,
            currentHoleNumber = hole?.number ?: 1,
            totalStrokes = round.totalStrokes,
            modifier = Modifier.weight(1f).padding(top = 4.dp),
        )
    }

    // Club picker
    if (showClubPicker) {
        ClubPickerSheet(
            defaultClub = defaultClub,
            enabledClubs = enabledClubs,
            onSelect = { club ->
                val loc = playerLocation ?: GpsPoint(0.0, 0.0)
                val distToPin = courseHole?.greenCenter?.let { playerLocation?.distanceYards(it) }
                session.markShot(loc, club, distanceToPinYards = distToPin)
                showClubPicker = false
            },
            onCancel = { showClubPicker = false },
        )
    }

    // Move shots dialog
    if (showMoveShots && hole != null) {
        MoveShotsDialog(
            currentHoleNumber = hole.number,
            allHoles = round.holes,
            onConfirm = { targetNumber ->
                session.moveShotsToHole(hole.number, targetNumber)
                showMoveShots = false
            },
            onDismiss = { showMoveShots = false },
        )
    }

    // End round confirmation
    if (showEndConfirm) {
        AlertDialog(
            onDismissRequest = { showEndConfirm = false },
            title = { Text("End Round?") },
            text = { Text("Save and finish this round?") },
            confirmButton = {
                TextButton(onClick = { showEndConfirm = false; onEndRound() }) {
                    Text("End", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

// ─── Distance Display ───────────────────────────────────────────────

@Composable
private fun DistanceDisplay(
    playerLocation: GpsPoint?,
    courseHole: Hole?,
    modifier: Modifier = Modifier,
) {
    val hasGps = playerLocation != null && courseHole?.greenCenter != null
    val front = if (hasGps) courseHole?.greenFront?.let { playerLocation!!.distanceYards(it) } else null
    val flag = if (hasGps) courseHole?.greenCenter?.let { playerLocation!!.distanceYards(it) } else null
    val back = if (hasGps) courseHole?.greenBack?.let { playerLocation!!.distanceYards(it) } else null

    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            // Front
            DistanceCol(label = "Front", value = front?.toString() ?: "—", isPrimary = false)
            // Flag
            DistanceCol(
                label = "Flag",
                value = flag?.toString() ?: courseHole?.yardage ?: "—",
                isPrimary = true,
                modifier = Modifier.semantics { testTag = "flagDistance" },
            )
            // Back
            DistanceCol(label = "Back", value = back?.toString() ?: "—", isPrimary = false)
        }
    }
}

@Composable
private fun DistanceCol(label: String, value: String, isPrimary: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = if (isPrimary) 48.sp else 32.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPrimary) GolfGreen else MaterialTheme.colorScheme.onSurface,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─── Mini Scorecard ─────────────────────────────────────────────────

@Composable
private fun MiniScorecard(
    holes: List<HoleScore>,
    currentHoleNumber: Int,
    totalStrokes: Int,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentHoleNumber) {
        val idx = (currentHoleNumber - 1).coerceAtLeast(0)
        listState.animateScrollToItem(idx)
    }

    Column(modifier) {
        HorizontalDivider()

        // Header
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "SCORECARD",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.weight(1f))
            if (totalStrokes > 0) {
                val playedPar = holes.filter { it.strokes > 0 }.sumOf { it.par }
                val diff = totalStrokes - playedPar
                val parStr = when { diff > 0 -> "+$diff"; diff == 0 -> "E"; else -> "$diff" }
                Text(
                    "$totalStrokes ($parStr)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when { diff < 0 -> GolfGreen; diff == 0 -> MaterialTheme.colorScheme.onSurface; else -> Color.Red },
                )
            }
        }

        // Hole rows
        LazyColumn(state = listState) {
            items(holes, key = { it.number }) { h ->
                ScorecardRow(
                    hole = h,
                    isCurrent = h.number == currentHoleNumber,
                )
                HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun ScorecardRow(hole: HoleScore, isCurrent: Boolean) {
    val overPar = hole.strokes - hole.par
    val scoreLabel = when {
        hole.strokes == 0 -> "—"
        overPar == 0 -> "E"
        overPar > 0 -> "+$overPar"
        else -> "$overPar"
    }
    val scoreLabelColor = when {
        hole.strokes == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        overPar < 0 -> GolfGreen
        overPar == 0 -> MaterialTheme.colorScheme.onSurface
        else -> Color.Red
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isCurrent) GolfGreen.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // H#
        Text(
            "H${hole.number}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isCurrent) GolfGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        // P#
        Text(
            "P${hole.par}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        // Stroke circles
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val totalSlots = maxOf(hole.par, hole.strokes)
            for (n in 1..totalSlots) {
                if (n <= hole.strokes) {
                    // Played stroke circle
                    val bg = if (n > hole.par) Color.Red else GolfGreen
                    Box(
                        Modifier.size(18.dp).clip(CircleShape).background(bg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$n", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else {
                    // Empty slot
                    Box(
                        Modifier.size(18.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
        // Score vs par
        Text(
            scoreLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = scoreLabelColor,
            textAlign = TextAlign.End,
            modifier = Modifier.width(28.dp),
        )
    }
}

// ─── Club Picker ────────────────────────────────────────────────────

private fun defaultClubForDistance(yards: Int?, enabledClubs: List<ClubType>): ClubType {
    if (yards == null || enabledClubs.isEmpty()) return enabledClubs.firstOrNull() ?: ClubType.UNKNOWN
    val table = listOf(
        ClubType.DRIVER to 230, ClubType.WOOD_3 to 210, ClubType.WOOD_5 to 195,
        ClubType.HYBRID_3 to 190, ClubType.HYBRID_4 to 180, ClubType.HYBRID_5 to 170,
        ClubType.IRON_4 to 170, ClubType.IRON_5 to 160, ClubType.IRON_6 to 150,
        ClubType.IRON_7 to 140, ClubType.IRON_8 to 130, ClubType.IRON_9 to 120,
        ClubType.PITCHING_WEDGE to 110, ClubType.GAP_WEDGE to 95,
        ClubType.SAND_WEDGE to 80, ClubType.LOB_WEDGE to 60,
    )
    for ((club, minDist) in table) {
        if (club in enabledClubs && yards >= minDist) return club
    }
    return enabledClubs.last()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClubPickerSheet(
    defaultClub: ClubType,
    enabledClubs: List<ClubType>,
    onSelect: (ClubType) -> Unit,
    onCancel: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val itemHeightDp = 48.dp
    val visibleItems = 5
    val initialIndex = enabledClubs.indexOf(defaultClub).coerceAtLeast(0)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Scroll so defaultClub is centered on first composition
    LaunchedEffect(Unit) {
        listState.scrollToItem(maxOf(0, initialIndex - visibleItems / 2))
    }

    // selectedIndex = item snapped to center of the visible window
    val selectedIndex by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex.coerceIn(0, enabledClubs.lastIndex)
        }
    }

    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheetState) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Text("Select Club", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = {
                onSelect(enabledClubs.getOrElse(selectedIndex) { defaultClub })
            }) {
                Text("Confirm", color = GolfGreen, fontWeight = FontWeight.Bold)
            }
        }

        Box(
            Modifier.fillMaxWidth().height(itemHeightDp * visibleItems),
            contentAlignment = Alignment.Center,
        ) {
            // Selection highlight behind the center row
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(itemHeightDp)
                    .background(GolfGreen.copy(alpha = 0.12f), MaterialTheme.shapes.small)
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().semantics { testTag = "clubPicker" },
                flingBehavior = rememberSnapFlingBehavior(listState),
                // top/bottom padding = 2 items so first/last item can reach center
                contentPadding = PaddingValues(vertical = itemHeightDp * (visibleItems / 2)),
            ) {
                itemsIndexed(enabledClubs) { idx, club ->
                    val isSelected = idx == selectedIndex
                    Box(
                        Modifier.fillMaxWidth().height(itemHeightDp)
                            .clickable {
                                if (isSelected) onSelect(club)
                                else scope.launch { listState.animateScrollToItem(idx) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            club.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) GolfGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ─── Move Shots Dialog ───────────────────────────────────────────────

@Composable
private fun MoveShotsDialog(
    currentHoleNumber: Int,
    allHoles: List<HoleScore>,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val targets = allHoles.filter { it.number != currentHoleNumber }
    var selected by remember { mutableStateOf(targets.firstOrNull()?.number) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move Shots to Hole") },
        text = {
            LazyColumn {
                items(targets) { hole ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = hole.number }.padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = hole.number == selected, onClick = { selected = hole.number })
                        Spacer(Modifier.width(8.dp))
                        Text("Hole ${hole.number}  (Par ${hole.par})", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let { onConfirm(it) } },
                enabled = selected != null,
            ) { Text("Move", color = GolfGreen, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
