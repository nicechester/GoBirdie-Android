package io.github.nicechester.gobirdie.ui.tournaments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nicechester.gobirdie.core.model.Course
import io.github.nicechester.gobirdie.core.model.Round
import io.github.nicechester.gobirdie.core.model.Tournament

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTournamentScreen(
    viewModel: TournamentsViewModel,
    onCreated: (Tournament) -> Unit,
    onDismiss: () -> Unit,
) {
    val courses = remember { viewModel.loadCourses() }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var date by remember { mutableStateOf(viewModel.todayDate()) }
    var title by remember { mutableStateOf("") }
    var showCoursePicker by remember { mutableStateOf(false) }

    if (showCoursePicker) {
        CoursePickerSheet(
            courses = courses,
            onSelect = { selectedCourse = it; showCoursePicker = false },
            onDismiss = { showCoursePicker = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Tournament") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val c = selectedCourse ?: return@TextButton
                            onCreated(viewModel.createTournament(c.id, c.name, date, title))
                        },
                        enabled = selectedCourse != null && date.isNotBlank(),
                    ) {
                        Text("Create", fontWeight = FontWeight.Bold)
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // Course picker
            OutlinedCard(
                onClick = { showCoursePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Course", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        selectedCourse?.name ?: "Select a course…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selectedCourse != null) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Date
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("Date (yyyy-MM-dd)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Optional title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title (optional)") },
                placeholder = { Text("e.g. Saturday Scramble") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoursePickerSheet(
    courses: List<Course>,
    onSelect: (Course) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            "Select Course",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (courses.isEmpty()) {
            Text(
                "No saved courses. Download a course first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(courses) { course ->
                    ListItem(
                        headlineContent = { Text(course.name) },
                        modifier = Modifier.clickable { onSelect(course) },
                    )
                    HorizontalDivider()
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
