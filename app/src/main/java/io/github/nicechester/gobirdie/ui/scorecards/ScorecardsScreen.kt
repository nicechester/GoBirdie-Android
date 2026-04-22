package io.github.nicechester.gobirdie.ui.scorecards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.nicechester.gobirdie.core.model.HoleScore
import io.github.nicechester.gobirdie.core.model.Round
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
private fun ScorecardDetail(round: Round, onDismiss: () -> Unit) {
    val playedHoles = round.holes.filter { it.strokes > 0 }
    val parTotal = playedHoles.sumOf { it.par }
    val frontNine = round.holes.take(9).let { if (it.any { h -> h.strokes > 0 }) it else emptyList() }
    val backNine = round.holes.drop(9).take(9).let { if (it.any { h -> h.strokes > 0 }) it else emptyList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(round.courseName) },
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
                Text("📅 ${formatDate(round.startedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            TotalsRow(round, playedHoles, parTotal)

            // Stats
            HorizontalDivider(Modifier.padding(top = 4.dp))
            StatsSection(playedHoles)
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
