package io.github.nicechester.gobirdie.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
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
import androidx.wear.compose.foundation.SwipeToDismissBoxState
import androidx.wear.compose.foundation.edgeSwipeToDismiss
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.*
import io.github.nicechester.gobirdie.wear.WatchRoundSession

private val GolfGreen = Color(0xFF4CAF50)
private val DarkBg = Color(0xFF000000)

@Composable
fun WatchRoundScreen(session: WatchRoundSession) {
    val hasHoleData by session.hasHoleData.collectAsState()
    val isRoundEnded by session.isRoundEnded.collectAsState()
    val showClubPicker by session.showClubPicker.collectAsState()

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(DarkBg)) {
            when {
                isRoundEnded -> RoundEndedView(session)
                hasHoleData -> ActiveRoundPager(session)
                else -> WaitingView(session)
            }

            if (showClubPicker) {
                ClubPickerOverlay(session)
            }
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
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectHorizontalDragGestures { _, dragAmount ->
                // Vertical page simulation via horizontal swipe isn't standard,
                // but we use a simple two-page toggle
            }
        }
    ) {
        if (page == 0) {
            ScoringPage(session, onSwipeUp = { page = 1 })
        } else {
            EndRoundPage(session, onBack = { page = 0 })
        }

        // Page indicator dots at bottom
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
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
private fun ScoringPage(session: WatchRoundSession, onSwipeUp: () -> Unit) {
    val holeNum by session.holeNumber.collectAsState()
    val parVal by session.par.collectAsState()
    val strokesVal by session.strokes.collectAsState()
    val puttsVal by session.putts.collectAsState()
    val front by session.frontYards.collectAsState()
    val pin by session.pinYards.collectAsState()
    val back by session.backYards.collectAsState()
    val total by remember { derivedStateOf { session.totalStrokes } }
    val totalHolesVal by session.totalHoles.collectAsState()

    // Rotary input for hole navigation
    var rotaryHole by remember { mutableIntStateOf(holeNum) }
    LaunchedEffect(holeNum) { rotaryHole = holeNum }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
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
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 30) session.previousHole()
                    else if (dragAmount < -30) session.nextHole()
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header: Hole # / Par
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Hole $holeNum",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
            )
            Text(
                "Par $parVal",
                fontSize = 13.sp, color = Color.Gray,
            )
        }

        Spacer(Modifier.height(2.dp))

        // Distances: F / PIN / B
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            // Front
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text("F", fontSize = 11.sp, color = Color.Gray)
                Text(
                    "${front ?: 0}",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White,
                )
            }
            // Pin
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1.5f)) {
                Text("PIN", fontSize = 13.sp, color = Color.Gray)
                Text(
                    "${pin ?: 0}",
                    fontSize = 36.sp, fontWeight = FontWeight.Bold, color = GolfGreen,
                )
            }
            // Back
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text("B", fontSize = 11.sp, color = Color.Gray)
                Text(
                    "${back ?: 0}",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White,
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        // Strokes / Putts
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
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
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
    val total by remember { derivedStateOf { session.totalStrokes } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$total",
                fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White,
            )
            Text(
                "Hole $holeNum of $totalHolesVal",
                fontSize = 13.sp, color = Color.Gray,
            )
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
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.clickable { session.cancelRound() },
            )
        }
    }
}

// ── Round Ended View ──

@Composable
private fun RoundEndedView(session: WatchRoundSession) {
    val name by session.courseName.collectAsState()
    val total by remember { derivedStateOf { session.totalStrokes } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⛳", fontSize = 28.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                name,
                fontSize = 11.sp, color = Color.Gray,
                textAlign = TextAlign.Center, maxLines = 2,
            )
            Text(
                "$total",
                fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White,
            )
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

// ── Club Picker Overlay ──

@Composable
private fun ClubPickerOverlay(session: WatchRoundSession) {
    val clubs by session.clubBag.collectAsState()
    val selected by session.selectedClub.collectAsState()
    var currentIndex by remember(clubs, selected) {
        mutableIntStateOf(clubs.indexOf(selected).coerceAtLeast(0))
    }

    // Sync selection back to session
    LaunchedEffect(currentIndex) {
        if (clubs.indices.contains(currentIndex)) {
            session.selectedClub.value = clubs[currentIndex]
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .onRotaryScrollEvent { event ->
                val delta = if (event.verticalScrollPixels > 0) 1 else -1
                currentIndex = (currentIndex + delta).coerceIn(0, clubs.size - 1)
                true
            }
            .focusable()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 20 && currentIndex > 0) currentIndex--
                    else if (dragAmount < -20 && currentIndex < clubs.size - 1) currentIndex++
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Carousel: prev / current / next
            Row(
                Modifier.fillMaxWidth().height(50.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Previous
                Text(
                    if (currentIndex > 0) clubDisplayName(clubs[currentIndex - 1]) else "",
                    fontSize = 13.sp, color = Color.Gray.copy(alpha = 0.5f),
                )
                // Current
                Text(
                    clubDisplayName(clubs.getOrElse(currentIndex) { "?" }),
                    fontSize = 36.sp, fontWeight = FontWeight.Bold, color = GolfGreen,
                )
                // Next
                Text(
                    if (currentIndex < clubs.size - 1) clubDisplayName(clubs[currentIndex + 1]) else "",
                    fontSize = 13.sp, color = Color.Gray.copy(alpha = 0.5f),
                )
            }

            Spacer(Modifier.height(12.dp))

            CompactChip(
                onClick = { session.confirmClub() },
                label = { Text("✓", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                colors = ChipDefaults.chipColors(backgroundColor = GolfGreen),
            )
        }
    }
}

private fun clubDisplayName(raw: String): String = when (raw) {
    "driver" -> "Driver"
    "3w" -> "3W"; "5w" -> "5W"
    "3h" -> "3H"; "4h" -> "4H"; "5h" -> "5H"
    "4i" -> "4i"; "5i" -> "5i"; "6i" -> "6i"
    "7i" -> "7i"; "8i" -> "8i"; "9i" -> "9i"
    "pw" -> "PW"; "gw" -> "GW"; "sw" -> "SW"; "lw" -> "LW"
    "putter" -> "Putter"
    else -> raw
}
