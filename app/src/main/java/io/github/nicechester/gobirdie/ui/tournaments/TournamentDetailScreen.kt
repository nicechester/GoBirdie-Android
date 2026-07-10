package io.github.nicechester.gobirdie.ui.tournaments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nicechester.gobirdie.core.model.HoleScore
import io.github.nicechester.gobirdie.core.model.PlayerSource
import io.github.nicechester.gobirdie.core.model.Tournament
import io.github.nicechester.gobirdie.core.model.TournamentPlayer
import io.github.nicechester.gobirdie.ui.scorecards.CollectScoresScreen
import io.github.nicechester.gobirdie.ui.scorecards.QrRoundPayload
import java.util.UUID

private val GolfGreen = Color(0xFF2E7D32)
private val CellW: Dp = 60.dp
private val LabelW: Dp = 44.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentDetailScreen(
    tournament: Tournament,
    viewModel: TournamentsViewModel,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf(tournament) }

    var showAddPlayer by remember { mutableStateOf(false) }
    var longPressPlayer by remember { mutableStateOf<TournamentPlayer?>(null) }
    var editingCell by remember { mutableStateOf<Pair<String, Int>?>(null) }

    fun save(t: Tournament) {
        viewModel.save(t)
        current = t
    }

    // ── Add player sheet ──────────────────────────────────────────────
    if (showAddPlayer) {
        AddPlayerSheet(
            onAdd = { name ->
                val blank = TournamentPlayer(name = name, holes = emptyList(), source = PlayerSource.MANUAL.name)
                save(current.copy(players = current.players + blank))
                showAddPlayer = false
            },
            onScanned = { payload, name ->
                val player = payload.toTournamentPlayer(name)
                save(current.copy(players = current.players + player))
                showAddPlayer = false
            },
            onDismiss = { showAddPlayer = false },
        )
        return
    }

    // ── Rename dialog (long-press player name) ────────────────────────
    longPressPlayer?.let { player ->
        RenamePlayerDialog(
            current = player.name,
            onConfirm = { newName ->
                save(current.copy(players = current.players.map {
                    if (it.id == player.id) it.copy(name = newName) else it
                }))
                longPressPlayer = null
            },
            onDismiss = { longPressPlayer = null },
        )
    }

    // ── Inline stroke editor (tap cell) ──────────────────────────────
    editingCell?.let { (playerId, holeNumber) ->
        val currentStrokes = current.players.firstOrNull { it.id == playerId }
            ?.holes?.firstOrNull { it.number == holeNumber }?.strokes ?: 0
        StrokePickerDialog(
            current = currentStrokes,
            onSelect = { val_ ->
                val pIdx = current.players.indexOfFirst { it.id == playerId }
                if (pIdx >= 0) {
                    val players = current.players.toMutableList()
                    val holes = players[pIdx].holes.toMutableList()
                    val hIdx = holes.indexOfFirst { it.number == holeNumber }
                    if (hIdx >= 0) {
                        holes[hIdx] = holes[hIdx].copy(strokes = val_)
                    } else {
                        val par = current.players.firstOrNull { it.source == PlayerSource.SELF.name }
                            ?.holes?.firstOrNull { it.number == holeNumber }?.par ?: 4
                        holes.add(HoleScore(number = holeNumber, par = par, strokes = val_))
                    }
                    players[pIdx] = players[pIdx].copy(holes = holes)
                    save(current.copy(players = players))
                }
                editingCell = null
            },
            onDismiss = { editingCell = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(current.title ?: current.courseName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(current.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                },
                actions = {
                    IconButton(onClick = { showAddPlayer = true }) {
                        Icon(Icons.Default.Add, "Add Player")
                    }
                },
            )
        }
    ) { padding ->
        if (current.players.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No players yet. Tap + to add.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                ScoreGrid(
                    players = current.players,
                    onLongPress = { longPressPlayer = it },
                    onTapCell = { playerId, hole ->
                        val p = current.players.firstOrNull { it.id == playerId }
                        val h = p?.holes?.firstOrNull { it.number == hole }
                        editingCell = playerId to hole
                    },
                )
            }
        }
    }
}

// ─── Score grid ───────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScoreGrid(
    players: List<TournamentPlayer>,
    onLongPress: (TournamentPlayer) -> Unit,
    onTapCell: (playerId: String, hole: Int) -> Unit,
) {
    val holeNumbers = players
        .flatMap { it.holes }
        .filter { it.strokes > 0 }
        .map { it.number }
        .toSortedSet()
        .toList()

    LazyColumn {
        // Header: player names
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(LabelW))
                players.forEach { player ->
                    Box(
                        Modifier
                            .width(CellW)
                            .combinedClickable(onClick = {}, onLongClick = { onLongPress(player) })
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            player.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (player.source == PlayerSource.SELF.name) FontWeight.Bold else FontWeight.Normal,
                            color = if (player.source == PlayerSource.SELF.name) GolfGreen else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            HorizontalDivider()
        }

        // One row per hole
        itemsIndexed(holeNumbers) { _, n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "H$n",
                    modifier = Modifier.width(LabelW).padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                players.forEach { player ->
                    val hole = player.holes.firstOrNull { it.number == n }
                    Box(
                        Modifier.width(CellW).combinedClickable(
                            onClick = { onTapCell(player.id, n) },
                            onLongClick = {},
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        ScoreCell(hole)
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp)
        }

        // Total row
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tot",
                    modifier = Modifier.width(LabelW).padding(horizontal = 4.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                players.forEach { player ->
                    val total = player.totalStrokes
                    Text(
                        if (total > 0) "$total" else "—",
                        modifier = Modifier.width(CellW).padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp)
        }

        // +/− row
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "+/−",
                    modifier = Modifier.width(LabelW).padding(horizontal = 4.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                players.forEach { player ->
                    val total = player.totalStrokes
                    val diff = player.scoreVsPar
                    val diffStr = when { total == 0 -> "—"; diff > 0 -> "+$diff"; diff == 0 -> "E"; else -> "$diff" }
                    val diffColor = when { total == 0 -> MaterialTheme.colorScheme.onSurfaceVariant; diff < 0 -> GolfGreen; diff == 0 -> MaterialTheme.colorScheme.onSurface; else -> Color.Red }
                    Text(
                        diffStr,
                        modifier = Modifier.width(CellW).padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = diffColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreCell(hole: HoleScore?) {
    val strokes = hole?.strokes ?: 0
    val par = hole?.par ?: 4
    val diff = strokes - par
    val bg = when {
        strokes == 0 -> Color.Transparent
        diff <= -2 -> Color(0xFFFFD600).copy(alpha = 0.8f)
        diff == -1 -> GolfGreen.copy(alpha = 0.7f)
        diff == 0 -> Color.Transparent
        diff == 1 -> Color(0xFFE65100).copy(alpha = 0.6f)
        else -> Color.Red.copy(alpha = 0.7f)
    }
    val textColor = when {
        strokes == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        diff in -2..-1 -> Color.White
        diff >= 1 -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        Modifier.width(CellW).padding(2.dp).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (strokes > 0) "$strokes" else "—",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

// ─── Dialogs ──────────────────────────────────────────────────────────

@Composable
private fun AddPlayerSheet(
    onAdd: (String) -> Unit,
    onScanned: (QrRoundPayload, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var showQrScan by remember { mutableStateOf(false) }

    if (showQrScan) {
        CollectScoresScreen(
            playerName = name.trim(),
            onScanned = { payload, playerName ->
                onScanned(payload, playerName)
            },
            onDismiss = { showQrScan = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Player") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Player name") },
                    singleLine = true,
                )
                TextButton(
                    onClick = { showQrScan = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Scan QR Code")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onAdd(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Add", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RenamePlayerDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Player") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StrokePickerDialog(current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Strokes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Two rows of 5
                listOf(1..5, 6..10).forEach { range ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        range.forEach { n ->
                            val selected = n == current
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape,
                                    )
                                    .clickable { onSelect(n); onDismiss() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$n",
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onSelect(0); onDismiss() }) { Text("Clear") }
        },
    )
}

// ─── QR payload → TournamentPlayer ───────────────────────────────────

private fun QrRoundPayload.toTournamentPlayer(name: String): TournamentPlayer {
    val holes = h.mapIndexed { idx, scores ->
        HoleScore(
            number = idx + 1,
            par = 4,
            strokes = scores.getOrElse(0) { 0 },
            putts = scores.getOrElse(1) { 0 },
        )
    }
    return TournamentPlayer(
        id = UUID.randomUUID().toString(),
        name = name,
        holes = holes,
        source = PlayerSource.RECEIVED.name,
    )
}
