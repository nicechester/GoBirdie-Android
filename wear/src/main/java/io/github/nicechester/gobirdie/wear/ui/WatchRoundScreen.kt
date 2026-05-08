package io.github.nicechester.gobirdie.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import io.github.nicechester.gobirdie.wear.WatchRoundSession
import kotlin.math.abs

private val GolfGreen = Color(0xFF4CAF50)
private val DarkBg = Color(0xFF000000)

@Composable
fun WatchRoundScreen(session: WatchRoundSession, isAmbient: Boolean = false) {
    val hasHoleData by session.hasHoleData.collectAsState()
    val isRoundEnded by session.isRoundEnded.collectAsState()
    val showClubPicker by session.showClubPicker.collectAsState()

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(DarkBg)) {
            when {
                isAmbient && hasHoleData && !isRoundEnded -> AmbientScoringView(session)
                isRoundEnded -> RoundEndedView(session)
                hasHoleData -> ActiveRoundPager(session)
                else -> WaitingView(session)
            }

            if (showClubPicker && !isAmbient) {
                ClubPickerOverlay(session)
            }
        }
    }
}

// ── Ambient Mode (Always-On Display) ──

@Composable
private fun AmbientScoringView(session: WatchRoundSession) {
    val holeNum by session.holeNumber.collectAsState()
    val parVal by session.par.collectAsState()
    val pin by session.pinYards.collectAsState()
    val strokesVal by session.strokes.collectAsState()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "H$holeNum  P$parVal",
                fontSize = 14.sp, color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${pin ?: "--"}",
                fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White,
            )
            Text("yds", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            Text(
                "$strokesVal strokes",
                fontSize = 14.sp, color = Color.Gray,
            )
        }
    }
}

// ── Waiting View ──

@Composable
private fun WaitingView(session: WatchRoundSession) {
    val name by session.courseName.collectAsState()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⛳", fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                if (name.isEmpty()) "Waiting for\nphone..." else name,
                color = Color.Gray,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Active Round (two pages: scoring + end round) ──

@Composable
private fun ActiveRoundPager(session: WatchRoundSession) {
    var page by remember { mutableIntStateOf(0) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(page) {
                var accumulated = 0f
                detectVerticalDragGestures(
                    onDragStart = { accumulated = 0f },
                    onDragEnd = {
                        if (accumulated < -60 && page == 0) page = 1
                        else if (accumulated > 60 && page == 1) page = 0
                    },
                    onVerticalDrag = { _, dragAmount -> accumulated += dragAmount },
                )
            }
    ) {
        if (page == 0) {
            ScoringPage(session)
        } else {
            EndRoundPage(session, onBack = { page = 0 })
        }

        // Page indicator dots at bottom — tap to switch
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
                .clickable { page = if (page == 0) 1 else 0 },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PageDot(active = page == 0)
            PageDot(active = page == 1)
        }
    }
}

@Composable
private fun PageDot(active: Boolean) {
    Box(
        Modifier
            .size(6.dp)
            .background(
                if (active) GolfGreen else Color.DarkGray,
                shape = RoundedCornerShape(3.dp)
            )
    )
}

// ── Scoring Page ──

@Composable
private fun ScoringPage(session: WatchRoundSession) {
    val holeNum by session.holeNumber.collectAsState()
    val parVal by session.par.collectAsState()
    val strokesVal by session.strokes.collectAsState()
    val puttsVal by session.putts.collectAsState()
    val front by session.frontYards.collectAsState()
    val pin by session.pinYards.collectAsState()
    val back by session.backYards.collectAsState()
    val totalHolesVal by session.totalHoles.collectAsState()

    // Compute total from accumulated + current strokes
    val totalDisplay = remember(strokesVal) { session.totalStrokes }

    // Rotary input for hole navigation
    var rotaryHole by remember { mutableIntStateOf(holeNum) }
    LaunchedEffect(holeNum) { rotaryHole = holeNum }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 0.dp)
            .padding(top = 24.dp)
            .onRotaryScrollEvent { event ->
                val delta = if (event.verticalScrollPixels > 0) 1 else -1
                val target = (rotaryHole + delta).coerceIn(1, totalHolesVal)
                if (target != rotaryHole) {
                    rotaryHole = target
                    session.navigateToHole(target)
                }
                true
            }
            .focusable()
            .pointerInput(holeNum) {
                var accumulated = 0f
                detectHorizontalDragGestures(
                    onDragStart = { accumulated = 0f },
                    onDragEnd = {
                        if (accumulated > 60) session.previousHole()
                        else if (accumulated < -60) session.nextHole()
                    },
                    onHorizontalDrag = { _, dragAmount -> accumulated += dragAmount },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header: Hole # / Par
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("Hole $holeNum", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("  ·  Par $parVal", fontSize = 13.sp, color = Color.Gray)
        }

        Spacer(Modifier.height(2.dp))

        // Distances: F / PIN / B
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text("F", fontSize = 11.sp, color = Color.Gray)
                Text("${front ?: 0}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1.5f)) {
                Text("PIN", fontSize = 13.sp, color = Color.Gray)
                Text("${pin ?: 0}", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = GolfGreen)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text("B", fontSize = 11.sp, color = Color.Gray)
                Text("${back ?: 0}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(Modifier.height(2.dp))

        // Strokes / Putts
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$strokesVal", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Strokes", fontSize = 10.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$puttsVal", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GolfGreen)
                Text("Putts", fontSize = 10.sp, color = Color.Gray)
            }
        }

        Spacer(Modifier.height(4.dp))

        // Buttons: Shot + Putt
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactChip(
                onClick = { session.markShot() },
                label = { Text("Shot", fontSize = 12.sp) },
                icon = { Text("📍", fontSize = 14.sp) },
                colors = ChipDefaults.chipColors(backgroundColor = GolfGreen),
                modifier = Modifier.weight(1f),
            )
            CompactChip(
                onClick = { session.addPutt() },
                label = { Text("Putt", fontSize = 12.sp) },
                icon = { Text("+1", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF333333)),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ── End Round Page ──

@Composable
private fun EndRoundPage(session: WatchRoundSession, onBack: () -> Unit) {
    val holeNum by session.holeNumber.collectAsState()
    val totalHolesVal by session.totalHoles.collectAsState()
    val strokesVal by session.strokes.collectAsState()
    val totalDisplay = remember(strokesVal) { session.totalStrokes }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$totalDisplay", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Hole $holeNum of $totalHolesVal", fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(12.dp))
            Chip(
                onClick = { session.finishRound() },
                label = { Text("End Round") },
                icon = { Text("🏁", fontSize = 14.sp) },
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFFB71C1C)),
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Cancel Round",
                fontSize = 12.sp, color = Color.Gray,
                modifier = Modifier.clickable { session.cancelRound() },
            )
        }
    }
}

// ── Round Ended View ──

@Composable
private fun RoundEndedView(session: WatchRoundSession) {
    val name by session.courseName.collectAsState()
    val strokesVal by session.strokes.collectAsState()
    val totalDisplay = remember(strokesVal) { session.totalStrokes }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(5_000L)
        session.resetToWaiting()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⛳", fontSize = 28.sp)
            Spacer(Modifier.height(4.dp))
            Text(name, fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center, maxLines = 2)
            Text("$totalDisplay", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Round Saved", fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            CompactChip(
                onClick = { session.resetToWaiting() },
                label = { Text("Done") },
                colors = ChipDefaults.chipColors(backgroundColor = GolfGreen),
            )
        }
    }
}

// ── Club Picker (full-screen ScalingLazyColumn) ──

@Composable
private fun ClubPickerOverlay(session: WatchRoundSession) {
    val clubs by session.clubBag.collectAsState()
    val selected by session.selectedClub.collectAsState()
    val countdown by session.clubPickerCountdown.collectAsState()
    val listState = rememberScalingLazyListState(
        initialCenterItemIndex = clubs.indexOf(selected).coerceAtLeast(0)
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(listState.centerItemIndex) {
        val club = clubs.getOrNull(listState.centerItemIndex)
        if (club != null && club != selected) {
            session.selectedClub.value = club
            session.resetClubPickerTimer()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent { event ->
                val delta = if (event.verticalScrollPixels > 0) 1 else -1
                val idx = (listState.centerItemIndex + delta).coerceIn(0, clubs.lastIndex)
                scope.launch { listState.animateScrollToItem(idx) }
                true
            }
            .focusable()
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            autoCentering = androidx.wear.compose.foundation.lazy.AutoCenteringParams(itemIndex = 0),
        ) {
            items(clubs) { club ->
                val isSelected = club == selected
                Text(
                    clubDisplayName(club),
                    fontSize = if (isSelected) 36.sp else 20.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) GolfGreen else Color.Gray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            session.selectedClub.value = club
                            session.confirmClub()
                        }
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Countdown + cancel button
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${countdown}s", fontSize = 11.sp, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            CompactChip(
                onClick = { session.cancelClubPicker() },
                label = { Text("✕ Cancel", fontSize = 11.sp) },
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF8B0000)),
            )
        }
    }
}

private fun clubDisplayName(raw: String): String = when (raw) {
    "driver" -> "DR"
    "3w" -> "3W"; "5w" -> "5W"
    "3h" -> "3H"; "4h" -> "4H"; "5h" -> "5H"
    "4i" -> "4i"; "5i" -> "5i"; "6i" -> "6i"
    "7i" -> "7i"; "8i" -> "8i"; "9i" -> "9i"
    "pw" -> "PW"; "gw" -> "GW"; "sw" -> "SW"; "lw" -> "LW"
    "putter" -> "PT"
    else -> raw.uppercase()
}
