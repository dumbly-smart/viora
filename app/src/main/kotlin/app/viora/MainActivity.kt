package app.viora

import android.os.Bundle
import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.viora.database.SlotWithCourse
import app.viora.setup.SetupAction
import app.viora.setup.SetupScreen
import app.viora.setup.SetupState
import app.viora.sync.VioraSyncScheduler
import app.viora.ui.VioraTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val graph by lazy { VioraGraph(applicationContext) }
    private val model by viewModels<VioraAppViewModel> {
        VioraAppViewModel.Factory(graph, VioraSyncScheduler(applicationContext))
    }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VioraTheme {
                val state by model.state.collectAsState()
                LaunchedEffect(state.configured) {
                    if (state.configured && Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (state.configured) {
                    Dashboard(state, model::refresh, model::selectSemester, model::beginReauthentication)
                } else {
                    SetupScreen(
                        state = SetupState(
                            username = state.username,
                            password = state.password,
                            rememberLogin = state.rememberLogin,
                            loading = state.loading,
                            error = state.error,
                        ),
                        onAction = { action ->
                            when (action) {
                                is SetupAction.UsernameChanged -> model.updateUsername(action.value)
                                is SetupAction.PasswordChanged -> model.updatePassword(action.value)
                                is SetupAction.RememberLoginChanged -> model.updateRememberLogin(action.value)
                                SetupAction.Submit -> model.signIn()
                            }
                        },
                    )
                }
            }
        }
    }
}

private data class Destination(val label: String, val icon: ImageVector)

private val destinations = listOf(
    Destination("Home", Icons.Outlined.Home),
    Destination("Schedule", Icons.Outlined.CalendarMonth),
    Destination("Courses", Icons.Outlined.MenuBook),
    Destination("Tasks", Icons.Outlined.Checklist),
    Destination("More", Icons.Outlined.MoreHoriz),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    state: VioraUiState,
    refresh: () -> Unit,
    selectSemester: (app.viora.network.SemesterOption) -> Unit,
    reauthenticate: () -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Viora") },
                actions = {
                    if (state.reauthRequired) {
                        TextButton(onClick = reauthenticate) { Text("Sign in to sync") }
                    } else {
                        TextButton(onClick = refresh, enabled = !state.loading) { Text("Sync") }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
            when (selected) {
                0 -> HomeScreen(state, PaddingValues())
                1 -> ScheduleScreen(state, selectSemester)
                2 -> AttendanceScreen(state)
                3 -> TasksScreen(state)
                else -> Placeholder(destinations[selected].label)
            }
        }
    }
}

@Composable
private fun HomeScreen(state: VioraUiState, padding: PaddingValues) {
    val today = LocalDate.now().dayOfWeek.value
    val todaySlots = state.slots.filter { it.dayOfWeek == today }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Today", style = MaterialTheme.typography.headlineMedium)
            Text(
                state.activeSemester?.name ?: "No semester selected",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (todaySlots.isEmpty()) {
            item { SummaryCard("Schedule", "No cached classes", "Sync with VTOP to load your timetable") }
        } else {
            items(todaySlots, key = SlotWithCourse::slotId) { slot -> ClassCard(slot) }
        }
        val risks = state.attendance.filter { it.recovery > 0 || it.skippable == 0 }.take(3)
        if (risks.isNotEmpty()) {
            item { Text("Attendance watch", style = MaterialTheme.typography.titleLarge) }
            items(risks, key = AttendanceUi::courseCode) { AttendanceCard(it) }
        }
        state.assignments.firstOrNull { it.dueEpochMillis == null || it.dueEpochMillis > System.currentTimeMillis() }?.let {
            item { SummaryCard("Next assignment", "${it.courseCode} · ${it.title}", it.dueEpochMillis.asAcademicTime()) }
        }
        state.exams.firstOrNull { it.startsEpochMillis > System.currentTimeMillis() }?.let {
            item { SummaryCard("Next exam", "${it.examType} · ${it.courseCode}", it.startsEpochMillis.asAcademicTime()) }
        }
        state.syncMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
    }
}

@Composable
private fun AttendanceScreen(state: VioraUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Courses", style = MaterialTheme.typography.headlineMedium)
            Text("Attendance projections use a 75% target and the latest VTOP snapshot.")
        }
        items(state.attendance, key = AttendanceUi::courseCode) { AttendanceCard(it) }
        if (state.attendance.isEmpty()) item { Text("No attendance has been cached yet.") }
    }
}

@Composable
private fun AttendanceCard(item: AttendanceUi) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (item.courseTitle.isBlank()) item.courseCode else "${item.courseCode} · ${item.courseTitle}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text("${item.attended}/${item.held} · ${"%.1f".format(item.percentage)}%")
            when {
                item.recovery > 0 -> Text(
                    "Attend ${item.recovery} consecutive classes to reach 75%",
                    color = MaterialTheme.colorScheme.error,
                )
                item.skippable == 0 -> Text("No projected buffer at 75%", color = MaterialTheme.colorScheme.error)
                else -> Text(
                    "Projected buffer: ${item.skippable} ${if (item.skippable == 1) "class" else "classes"}",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ScheduleScreen(
    state: VioraUiState,
    selectSemester: (app.viora.network.SemesterOption) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Schedule", style = MaterialTheme.typography.headlineMedium) }
        if (state.semesters.size > 1) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.semesters.take(3).forEach { semester ->
                        Button(onClick = { selectSemester(semester) }, enabled = semester != state.activeSemester) {
                            Text(semester.name)
                        }
                    }
                }
            }
        }
        val grouped = state.slots.groupBy(SlotWithCourse::dayOfWeek).toSortedMap()
        grouped.forEach { (day, slots) ->
            item {
                Text(
                    DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(slots, key = SlotWithCourse::slotId) { ClassCard(it) }
        }
        if (grouped.isEmpty()) item { Text("No timetable has been cached for this semester.") }
        if (state.exams.isNotEmpty()) {
            item { Text("Examinations", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
            items(state.exams, key = ExamUi::id) { ExamCard(it) }
        }
    }
}

@Composable
private fun TasksScreen(state: VioraUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Tasks", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Digital assignments", style = MaterialTheme.typography.titleLarge) }
        items(state.assignments, key = AssignmentUi::id) { assignment ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${assignment.courseCode} · ${assignment.title}", style = MaterialTheme.typography.titleMedium)
                    Text(assignment.dueEpochMillis.asAcademicTime("Due time unavailable"))
                    if (assignment.status.isNotBlank()) Text(assignment.status)
                }
            }
        }
        if (state.assignments.isEmpty()) item { Text("No digital assignments are cached.") }
        item { Text("Examinations", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
        items(state.exams, key = ExamUi::id) { ExamCard(it) }
        if (state.exams.isEmpty()) item { Text("No examination schedule is cached.") }
    }
}

@Composable
private fun ExamCard(exam: ExamUi) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${exam.examType} · ${exam.courseCode}", style = MaterialTheme.typography.titleMedium)
            if (exam.courseTitle.isNotBlank()) Text(exam.courseTitle)
            Text(exam.startsEpochMillis.asAcademicTime())
            val details = listOf(exam.venue, exam.seatNumber).filter(String::isNotBlank).joinToString(" · ")
            if (details.isNotBlank()) Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ClassCard(slot: SlotWithCourse) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${slot.code} · ${slot.title}", style = MaterialTheme.typography.titleMedium)
            Text("${slot.startMinute.asTime()}–${slot.endMinute.asTime()} · ${slot.venue}")
            if (slot.faculty.isNotBlank()) {
                Text(slot.faculty, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun Int.asTime(): String = "%02d:%02d".format(this / 60, this % 60)

private val academicDateTime = DateTimeFormatter.ofPattern("EEE, dd MMM · hh:mm a")
private val academicZone = ZoneId.of("Asia/Kolkata")

private fun Long?.asAcademicTime(fallback: String = "Time unavailable"): String =
    this?.let { academicDateTime.format(Instant.ofEpochMilli(it).atZone(academicZone)) } ?: fallback

@Composable
private fun SummaryCard(title: String, value: String, supporting: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, color = MaterialTheme.colorScheme.primary)
            Text(supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Placeholder(label: String) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(label, style = MaterialTheme.typography.headlineMedium)
        Text("This feature is queued after the timetable vertical slice.")
    }
}
