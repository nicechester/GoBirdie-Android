package io.github.nicechester.gobirdie.ui.tournaments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nicechester.gobirdie.core.model.HoleScore
import io.github.nicechester.gobirdie.core.model.TournamentPlayer

private val GolfGreen = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlayerScoreScreen(
    player: TournamentPlayer,
    courseHoles: List<HoleScore>,   // par reference (strokes=0 if not played)
    onSave: (TournamentPlayer) -> Unit,
    onDismiss: () -> Unit,
) {
    // Ensure we have 18 holes; fill missing with par from courseHoles or par=4
    var holes by remember {
        mutableStateOf(
            (1..18).map { n ->
                player.holes.firstOrNull { it.number == n }
                    ?: courseHoles.firstOrNull { it.number == n }?.copy(strokes = 0, putts = 0)
                    ?: HoleScore(number = n, par = 4)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(player.name) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                },
                actions = {
                    TextButton(onClick = {
                        onSave(player.copy(holes = holes.filter { it.strokes > 0 }))
                    }) {
                        Text("Save", fontWeight = FontWeight.Bold, color = GolfGreen)
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            // Header
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Hole", Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Par", Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("Strokes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }

            itemsIndexed(holes) { idx, hole ->
                HoleStepperRow(
                    hole = hole,
                    onStrokesChange = { delta ->
                        val newStrokes = (hole.strokes + delta).coerceAtLeast(0)
                        holes = holes.toMutableList().also { it[idx] = hole.copy(strokes = newStrokes) }
                    },
                )
                HorizontalDivider(thickness = 0.5.dp)
            }

            // Total
            item {
                val total = holes.sumOf { it.strokes }
                val par = holes.sumOf { it.par }
                val diff = total - par
                val diffStr = when { diff > 0 -> "+$diff"; diff == 0 -> "E"; else -> "$diff" }
                val diffColor = when { diff < 0 -> GolfGreen; diff == 0 -> MaterialTheme.colorScheme.onSurface; else -> Color.Red }
                Surface(tonalElevation = 1.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Total", Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("$par", Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("$total", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = diffColor)
                        Spacer(Modifier.width(8.dp))
                        Text("($diffStr)", style = MaterialTheme.typography.labelSmall, color = diffColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun HoleStepperRow(hole: HoleScore, onStrokesChange: (Int) -> Unit) {
    val diff = if (hole.strokes > 0) hole.strokes - hole.par else Int.MIN_VALUE
    val scoreColor = when {
        hole.strokes == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        diff <= -2 -> Color(0xFFFFD600)
        diff == -1 -> GolfGreen
        diff == 0 -> MaterialTheme.colorScheme.onSurface
        diff == 1 -> Color(0xFFE65100)
        else -> Color.Red
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${hole.number}", Modifier.width(40.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text("${hole.par}", Modifier.width(36.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { onStrokesChange(-1) }, modifier = Modifier.size(36.dp)) {
            Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            if (hole.strokes > 0) "${hole.strokes}" else "—",
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = scoreColor,
        )
        IconButton(onClick = { onStrokesChange(1) }, modifier = Modifier.size(36.dp)) {
            Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
