package io.github.nicechester.gobirdie.ui.tournaments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
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
private val CellW: Dp = 36.dp
private val NameW: Dp = 100.dp
private val TotalW: Dp = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentDetailScreen(
    tournament: Tournament,
    viewModel: TournamentsViewModel,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf(tournament) }
    val sorted = remember(current.players) {
        current.players.sortedWith(compareBy({ it.scoreVsPar }, { it.totalStrokes }))
    }

    // Navigation state
    var editingPlayer by remember { mutableStateOf<TournamentPlayer?>(null) }
    var showAddManual by remember { mutableStateOf(false) }
    var showQrScan by remember { mutableStateOf(false) }
    var longPressPlayer by remember { mutableStateOf<TournamentPlayer?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }

    fun save(t: Tournament) {
        viewModel.save(t)
        current = t
    }

    // ── QR scan ──────────────────────────────────────────────────────
    if (showQrScan) {
        CollectScoresScreen(
            onScanned = { payload, name ->
                val player = payload.toTournamentPlayer(name)
                save(current.copy(players = current.players + player))
            },
            onDismiss = { showQrScan = false },
        )
        return
    }

    // ── Edit player scores ────────────────────────────────────────────
    editingPlayer?.let { player ->
        EditPlayerScoreScreen(
            player = player,
            courseHoles = current.players.firstOrNull { it.source == PlayerSource.SELF.name }
                ?.holes ?: emptyList(),
            onSave = { updated ->
                save(current.copy(players = current.players.map { if (it.id == updated.id) updated else it }))
                editingPlayer = null
            },
            onDismiss = { editingPlayer = null },
        )
        return
    }

    // ── Add manual player ─────────────────────────────────────────────
    if (showAddManual) {
        AddManualPlayerDialog(
            onConfirm = { name ->
                val blank = TournamentPlayer(
                    name = name,
                    holes = emptyList(),
                    source = PlayerSource.MANUAL.name,
                )
                save(current.copy(players = current.players + blank))
                showAddManual = false
                editingPlayer = blank.copy(
                    holes = current.players.firstOrNull { it.source == PlayerSource.SELF.name }
                        ?.holes?.map { it.copy(strokes = 0, putts = 0) } ?: emptyList()
                )
            },
            onDismiss = { showAddManual = false },
        )
    }

    // ── Long-press menu ───────────────────────────────────────────────
    longPressPlayer?.let { player ->
        if (showRenameDialog) {
            RenamePlayerDialog(
                current = player.name,
                onConfirm = { newName ->
                    save(current.copy(players = current.players.map {
                        if (it.id == player.id) it.copy(name = newName) else it
                    }))
                    showRenameDialog = false
                    longPressPlayer = null
                },
                onDismiss = { showRenameDialog = false; longPressPlayer = null },
            )
        } else {
            PlayerEditMenu(
                player = player,
                onEditScores = { editingPlayer = player; longPressPlayer = null },
                onRename = { showRenameDialog = true },
                onRemove = {
                    save(current.copy(players = current.players.filter { it.id != player.id }))
                    longPressPlayer = null
                },
                onDismiss = { longPressPlayer = null },
            )
        }
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
                    IconButton(onClick = { showQrScan = true }) {
                        Icon(Icons.Default.QrCodeScanner, "Scan Score")
                    }
                    IconButton(onClick = { showAddManual = true }) {
                        Icon(Icons.Default.Add, "Add Player")
                    }
                },
            )
        }
    ) { padding ->
        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No players yet. Tap + or scan a QR code.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                ScoreGrid(
                    players = sorted,
                    onLongPress = { longPressPlayer = it },
                )
            }
        }
    }
}

// ─── Score grid ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScoreGrid(
    players: List<TournamentPlayer>,
    onLongPress: (TournamentPlayer) -> Unit,
) {
    val hScroll = rememberScrollState()
    val maxHoles = players.maxOfOrNull { it.holes.maxOfOrNull { h -> h.number } ?: 0 } ?: 18
    val holeNumbers = (1..maxHoles).toList()

    Column {
        // Header row
        Row(Modifier.horizontalScroll(hScroll)) {
            GridCell(text = "Player", width = NameW, header = true, align = TextAlign.Start)
            holeNumbers.forEach { n -> GridCell(text = "$n", width = CellW, header = true) }
            GridCell(text = "Tot", width = TotalW, header = true)
            GridCell(text = "+/−", width = TotalW, header = true)
        }
        HorizontalDivider()

        LazyColumn {
            itemsIndexed(players) { _, player ->
                Row(
                    Modifier
                        .horizontalScroll(hScroll)
                        .background(
                            if (player.source == PlayerSource.SELF.name)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else Color.Transparent
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Name cell — long press triggers edit menu
                    Box(
                        Modifier
                            .width(NameW)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { onLongPress(player) },
                            )
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text(
                            player.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (player.source == PlayerSource.SELF.name) FontWeight.Bold else FontWeight.Normal,
                        )
                    }

                    holeNumbers.forEach { n ->
                        val hole = player.holes.firstOrNull { it.number == n }
                        ScoreCell(hole)
                    }

                    // Total
                    val total = player.totalStrokes
                    GridCell(text = if (total > 0) "$total" else "—", width = TotalW, bold = true)

                    // +/−
                    val diff = player.scoreVsPar
                    val diffStr = when { total == 0 -> "—"; diff > 0 -> "+$diff"; diff == 0 -> "E"; else -> "$diff" }
                    val diffColor = when { total == 0 -> MaterialTheme.colorScheme.onSurfaceVariant; diff < 0 -> GolfGreen; diff == 0 -> MaterialTheme.colorScheme.onSurface; else -> Color.Red }
                    GridCell(text = diffStr, width = TotalW, color = diffColor, bold = true)
                }
                HorizontalDivider(thickness = 0.5.dp)
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
            fontSize = 11.sp,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun GridCell(
    text: String,
    width: Dp,
    header: Boolean = false,
    bold: Boolean = false,
    color: Color = Color.Unspecified,
    align: TextAlign = TextAlign.Center,
) {
    Text(
        text,
        modifier = Modifier.width(width).padding(horizontal = 2.dp, vertical = 8.dp),
        fontSize = if (header) 10.sp else 12.sp,
        fontWeight = if (header || bold) FontWeight.Bold else FontWeight.Normal,
        color = if (color != Color.Unspecified) color else if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textAlign = align,
        maxLines = 1,
    )
}

// ─── Dialogs / menus ─────────────────────────────────────────────────

@Composable
private fun PlayerEditMenu(
    player: TournamentPlayer,
    onEditScores: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(player.name) },
        text = {
            Column {
                TextButton(onClick = onEditScores, modifier = Modifier.fillMaxWidth()) { Text("Edit Scores") }
                TextButton(onClick = onRename, modifier = Modifier.fillMaxWidth()) { Text("Rename") }
                TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) { Text("Remove", color = Color.Red) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddManualPlayerDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Player") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Player name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
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

// ─── QR payload → TournamentPlayer ───────────────────────────────────

private fun QrRoundPayload.toTournamentPlayer(name: String): TournamentPlayer {
    val holes = h.mapIndexed { idx, scores ->
        HoleScore(
            number = idx + 1,
            par = 4, // par not in payload; host can edit via EditPlayerScoreScreen
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
