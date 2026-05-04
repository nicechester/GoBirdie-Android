package io.github.nicechester.gobirdie.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nicechester.gobirdie.core.model.ClubType
import kotlinx.coroutines.launch

private val GolfGreen = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubPickerSheet(
    defaultClub: ClubType,
    enabledClubs: List<ClubType> = ClubType.entries.filter { it != ClubType.UNKNOWN },
    onSelect: (ClubType) -> Unit,
    onCancel: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val itemHeightDp = 48.dp
    val visibleItems = 5
    val initialIndex = enabledClubs.indexOf(defaultClub).coerceAtLeast(0)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        listState.scrollToItem(maxOf(0, initialIndex - visibleItems / 2))
    }

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
                            style = if (isSelected) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
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
