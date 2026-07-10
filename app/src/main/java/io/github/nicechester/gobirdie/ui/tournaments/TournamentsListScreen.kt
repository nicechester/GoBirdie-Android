package io.github.nicechester.gobirdie.ui.tournaments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.nicechester.gobirdie.core.model.Tournament

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentsListScreen(
    viewModel: TournamentsViewModel = hiltViewModel(),
    tabRow: @Composable () -> Unit = {},
) {
    val tournaments by viewModel.tournaments.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var renamingTournament by remember { mutableStateOf<Tournament?>(null) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.load() }

    selectedId?.let { id ->
        val t = remember(id, tournaments) { viewModel.load(id) }
        if (t != null) {
            TournamentDetailScreen(
                tournament = t,
                viewModel = viewModel,
                onDismiss = {
                    selectedId = null
                    viewModel.load()
                },
            )
            return
        }
    }

    // ── Rename dialog ─────────────────────────────────────────────────
    renamingTournament?.let { t ->
        RenameTournamentDialog(
            current = t.title ?: t.courseName,
            onConfirm = { newName ->
                viewModel.save(t.copy(title = newName))
                viewModel.load()
                renamingTournament = null
            },
            onDismiss = { renamingTournament = null },
        )
    }

    if (showCreate) {
        CreateTournamentScreen(
            viewModel = viewModel,
            onCreated = { tournament ->
                viewModel.save(tournament)
                showCreate = false
                selectedId = tournament.id
            },
            onDismiss = { showCreate = false },
        )
        return
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Scorecards") })
                tabRow()
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, "New Tournament")
            }
        },
    ) { padding ->
        if (tournaments.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🏆", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text("No Tournaments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Tap + to create one", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(tournaments, key = { it.id }) { tournament ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.delete(tournament.id)
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
                            ) { Icon(Icons.Default.Delete, "Delete", tint = Color.White) }
                        },
                        enableDismissFromStartToEnd = false,
                    ) {
                        TournamentRow(
                            tournament = tournament,
                            onClick = { selectedId = tournament.id },
                            onLongClick = { renamingTournament = tournament; renameText = tournament.title ?: tournament.courseName },
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TournamentRow(tournament: Tournament, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFFD600), modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tournament.title ?: tournament.courseName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${tournament.date}  ·  ${tournament.players.size} player${if (tournament.players.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RenameTournamentDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Tournament") },
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
