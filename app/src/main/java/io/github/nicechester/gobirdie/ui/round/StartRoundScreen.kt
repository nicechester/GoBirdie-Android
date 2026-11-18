package io.github.nicechester.gobirdie.ui.round

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.nicechester.gobirdie.R
import io.github.nicechester.gobirdie.core.model.Course
import io.github.nicechester.gobirdie.core.model.CourseLayouts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartRoundScreen(
    viewModel: StartRoundViewModel,
    onStartRound: (Course, Int) -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
) {
    val state by viewModel.state.collectAsState()
    val searchText by viewModel.searchText.collectAsState()
    val selectedCourse by viewModel.selectedCourse.collectAsState()

    LaunchedEffect(selectedCourse) {
        selectedCourse?.let { (course, hole) -> onStartRound(course, hole) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title ?: stringResource(R.string.round_start)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, stringResource(R.string.common_close))
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Flag, null, Modifier.size(48.dp), tint = Color(0xFF2E7D32))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.start_discover), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            when (val s = state) {
                is StartRoundUiState.Loading -> {
                    Spacer(Modifier.height(32.dp))
                    CircularProgressIndicator(color = Color(0xFF2E7D32))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.start_finding_location))
                }

                is StartRoundUiState.CourseList -> {
                    // Search bar
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { viewModel.onSearchTextChanged(it) },
                        modifier = Modifier.fillMaxWidth().semantics { testTag = "searchField" },
                        placeholder = { Text(stringResource(R.string.start_search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.searchByName() }),
                    )
                    Spacer(Modifier.height(8.dp))

                    if (s.isSearchingOnline) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFF2E7D32))
                        Spacer(Modifier.height(4.dp))
                    }

                    if (s.searchFailed) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.start_search_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { viewModel.retry() }) { Text(stringResource(R.string.common_retry)) }
                        }
                    }

                    if (s.courses.isEmpty() && !s.isSearchingOnline) {
                        Spacer(Modifier.height(32.dp))
                        Text(stringResource(R.string.start_no_courses))
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.retry() }) { Text(stringResource(R.string.common_retry)) }
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(s.courses, key = { it.id }) { course ->
                                CourseRow(course) { viewModel.selectCourse(course) }
                            }
                        }
                    }
                }

                is StartRoundUiState.Downloading -> {
                    Spacer(Modifier.height(32.dp))
                    CircularProgressIndicator(color = Color(0xFF2E7D32))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.start_downloading, s.name))
                }

                is StartRoundUiState.SelectLayouts -> {
                    LayoutPicker(s.layouts) { picked -> viewModel.pickLayouts(picked) }
                }

                is StartRoundUiState.SelectHole -> {
                    HolePicker(s.course) { hole ->
                        viewModel.confirmStart(s.course, hole)
                    }
                }

                is StartRoundUiState.Error -> {
                    Spacer(Modifier.height(32.dp))
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.retry() }) { Text(stringResource(R.string.common_retry)) }
                }
            }
        }
    }
}

@Composable
private fun CourseRow(course: CourseItem, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(course.name, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    course.distanceText?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (course.city.isNotEmpty()) {
                        Text(course.city, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (course.isSaved) {
                Icon(Icons.Default.CheckCircle, stringResource(R.string.common_saved), tint = Color(0xFF2E7D32))
            }
        }
    }
}

/**
 * Picks the layouts of a multi-course complex: one for a 9-hole round, two for an 18-hole round.
 * The first layout picked is holes 1–9. Used by both start round and course explore.
 */
@Composable
private fun LayoutPicker(layouts: List<Course>, onPick: (List<Course>) -> Unit) {
    var pickedIds by remember { mutableStateOf(listOf<String>()) }
    val picked = pickedIds.mapNotNull { id -> layouts.firstOrNull { it.id == id } }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.layout_pick_prompt),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(layouts, key = { it.id }) { layout ->
                val order = pickedIds.indexOf(layout.id)
                Surface(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable {
                        pickedIds = when {
                            order >= 0 -> pickedIds - layout.id
                            pickedIds.size < 2 -> pickedIds + layout.id
                            else -> pickedIds
                        }
                    },
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = if (order >= 0) 4.dp else 0.dp,
                    color = if (order >= 0) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(CourseLayouts.layoutName(layout.name), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.layout_holes, layout.holes.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (order >= 0) {
                            Surface(shape = CircleShape, color = Color(0xFF2E7D32)) {
                                Text(
                                    "${order + 1}",
                                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onPick(picked) },
            enabled = picked.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(48.dp).semantics { testTag = "startLayoutsButton" },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
        ) {
            Text(
                stringResource(
                    when (picked.size) {
                        2 -> R.string.layout_start_18
                        1 -> R.string.layout_start_9
                        else -> R.string.layout_choose
                    }
                )
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HolePicker(course: Course, onStart: (Int) -> Unit) {
    var selected by remember { mutableIntStateOf(1) }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.round_which_hole), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(course.holes, key = { it.number }) { hole ->
                Surface(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        .clickable { selected = hole.number },
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = if (selected == hole.number) 4.dp else 0.dp,
                    color = if (selected == hole.number) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                ) {
                    Row(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.round_hole, hole.number), Modifier.weight(1f))
                        Text(stringResource(R.string.round_par, hole.par))
                        hole.yardage?.let { Text("  $it ${stringResource(R.string.distance_yds)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onStart(selected) },
            modifier = Modifier.fillMaxWidth().height(48.dp).semantics { testTag = "startOnHoleButton" },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
        ) {
            Text(stringResource(R.string.round_start_on_hole, selected))
        }
        Spacer(Modifier.height(16.dp))
    }
}
