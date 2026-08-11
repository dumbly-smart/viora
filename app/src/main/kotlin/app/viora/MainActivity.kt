package app.viora

import android.os.Bundle
import android.content.Intent
import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import app.viora.database.SlotWithCourse
import app.viora.setup.SetupAction
import app.viora.setup.SetupScreen
import app.viora.setup.SetupState
import app.viora.setup.VtopVerificationScreen
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
    private val notificationDestination = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val graph by lazy { VioraGraph(applicationContext) }
    private val model by viewModels<VioraAppViewModel> {
        VioraAppViewModel.Factory(graph, VioraSyncScheduler(applicationContext))
    }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Viora)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationDestination.value = intent.getStringExtra("viora_destination")
        setContent {
            VioraTheme {
                val state by model.state.collectAsState()
                LaunchedEffect(state.configured) {
                    if (state.configured && Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (state.interactiveVerification) {
                    VtopVerificationScreen(state.loading, state.error, model::completeInteractiveVerification, model::interactiveVerificationError, model::cancelInteractiveVerification)
                } else if (state.configured) {
                    Dashboard(state, model::refresh, model::selectSemester, model::beginReauthentication, model::logout, model::setDeadlineNotifications, model::setExamNotifications, model::openMaterial, model::setAttendanceTarget, model::setPlannedMissedBlocks, model::setSearchQuery, model::setQuietHours, model::setSyncHours, model::refreshDiagnostics, model::clearDownloads, model::clearAcademicCache, model::shareTimetableQr, notificationDestination.value)
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
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); notificationDestination.value = intent.getStringExtra("viora_destination") }
}

private data class Destination(val label: String, val icon: ImageVector)
private data class DetailSelection(val kind: String, val id: String)

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
    logout: () -> Unit,
    setDeadlineNotifications: (Boolean) -> Unit,
    setExamNotifications: (Boolean) -> Unit,
    openMaterial: (app.viora.database.CourseMaterialEntity, Boolean) -> Unit,
    setAttendanceTarget: (Int) -> Unit,
    setPlannedMissedBlocks: (Int) -> Unit,
    setSearchQuery: (String) -> Unit,
    setQuietHours: (Boolean) -> Unit,
    setSyncHours: (Int) -> Unit,
    refreshDiagnostics: () -> Unit,
    clearDownloads: () -> Unit,
    clearAcademicCache: () -> Unit,
    shareTimetableQr: () -> Unit,
    initialDestination: String?,
) {
    var selected by remember(initialDestination) { mutableIntStateOf(when (initialDestination) { "schedule" -> 1; "courses" -> 2; "tasks" -> 3; "more" -> 4; else -> 0 }) }
    var detail by remember { mutableStateOf<DetailSelection?>(null) }
    BoxWithConstraints {
    val expanded = maxWidth >= 840.dp
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.kind?.replaceFirstChar(Char::uppercase) ?: "Viora") },
                navigationIcon = { if (detail != null) TextButton(onClick = { detail = null }) { Text("Back") } },
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
            if (!expanded) NavigationBar {
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
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (expanded) NavigationRail {
                destinations.forEachIndexed { index, destination ->
                    NavigationRailItem(selected = selected == index, onClick = { selected = index; detail = null }, icon = { Icon(destination.icon, contentDescription = destination.label) }, label = { Text(destination.label) })
                }
            }
        Column(Modifier.weight(1f).fillMaxSize()) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Assertive }) }
            if (detail != null) DetailScreen(state, detail!!, openMaterial) else when (selected) {
                0 -> HomeScreen(state, PaddingValues())
                1 -> ScheduleScreen(state, selectSemester, shareTimetableQr) { detail = DetailSelection("exam", it.id) }
                2 -> CoursesScreen(state, openMaterial, setAttendanceTarget, setPlannedMissedBlocks) { kind, id -> detail = DetailSelection(kind, id) }
                3 -> TasksScreen(state, { detail = DetailSelection("assignment", it.id) }, { detail = DetailSelection("exam", it.id) })
                else -> MoreScreen(state, logout, setDeadlineNotifications, setExamNotifications, setSearchQuery, setQuietHours, selectSemester, setSyncHours, refreshDiagnostics, clearDownloads, clearAcademicCache)
            }
        }
    }
    }
}
}

@Composable
private fun HomeScreen(state: VioraUiState, padding: PaddingValues) {
    val todayDate = LocalDate.now()
    val today = todayDate.dayOfWeek.value
    val nowMinute = java.time.LocalTime.now().hour * 60 + java.time.LocalTime.now().minute
    val todaySlots = state.slotsForDate(todayDate)
    val timeline = state.academicTimeline(todayDate)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Today", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(
                state.activeSemester?.name ?: "No semester selected",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        todaySlots.firstOrNull { it.endMinute >= nowMinute }?.let { slot -> item { SummaryCard(if (slot.startMinute <= nowMinute) "Happening now" else "Up next", "${slot.code} · ${slot.title}", "${slot.startMinute.asTime()}–${slot.endMinute.asTime()} · ${slot.venue}") } }
        if (todaySlots.isEmpty()) {
            item { SummaryCard("Schedule", "No cached classes", "Sync with VTOP to load your timetable") }
        } else {
            items(todaySlots, key = SlotWithCourse::slotId) { slot -> ClassCard(slot) }
        }
        val risks = state.attendance.filter { it.recovery > 0 || it.skippable == 0 }.take(3)
        if (risks.isNotEmpty()) {
            item { Text("Attendance watch", style = MaterialTheme.typography.titleLarge) }
            items(risks, key = AttendanceUi::id) { AttendanceCard(it) }
        }
        state.assignments.firstOrNull { it.dueEpochMillis == null || it.dueEpochMillis > System.currentTimeMillis() }?.let {
            item { SummaryCard("Next assignment", "${it.courseCode} · ${it.title}", it.dueEpochMillis.asAcademicTime()) }
        }
        state.exams.firstOrNull { it.startsEpochMillis > System.currentTimeMillis() }?.let {
            item { SummaryCard("Next exam", "${it.examType} · ${it.courseCode}", it.startsEpochMillis.asAcademicTime()) }
        }
        if (timeline.isNotEmpty()) {
            item { Text("Coming up", style = MaterialTheme.typography.titleLarge) }
            items(timeline.take(12), key = TimelineItem::id) { entry -> SummaryCard(entry.kind, entry.title, entry.whenText) }
        }
        state.syncMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        state.syncResources.maxByOrNull { it.lastAttemptEpochMillis }?.let { sync -> item { SummaryCard("Local sync", sync.status.lowercase().replaceFirstChar(Char::uppercase), sync.lastSuccessEpochMillis.asAcademicTime("Not synced yet")) } }
    }
}

@Composable
private fun CoursesScreen(state: VioraUiState, openMaterial: (app.viora.database.CourseMaterialEntity, Boolean) -> Unit, setAttendanceTarget: (Int) -> Unit, setPlannedMissedBlocks: (Int) -> Unit, showDetail: (String, String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Consolidated courses", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text("Attendance, assessments, grades, faculty and materials from the local cache.")
        }
        item { Text("Target: ${state.attendanceTarget}%"); Slider(value = state.attendanceTarget.toFloat(), onValueChange = { setAttendanceTarget(it.toInt()) }, valueRange = 50f..95f, steps = 8) }
        item { Column { Text("Plan missing ${state.plannedMissedBlocks} future ${if (state.plannedMissedBlocks == 1) "block" else "blocks"}"); Row { TextButton(onClick = { setPlannedMissedBlocks(state.plannedMissedBlocks - 1) }) { Text("Fewer") }; TextButton(onClick = { setPlannedMissedBlocks(state.plannedMissedBlocks + 1) }) { Text("More") } } } }
        items(state.attendance, key = AttendanceUi::id) { item -> Column(Modifier.clickable { showDetail("course", item.id) }) { AttendanceCard(item) } }
        if (state.attendance.isEmpty()) item { Text("No attendance has been cached yet.") }
        if (state.grades.isNotEmpty()) item { Text("Grades", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp)) }
        items(state.grades, key = GradeUi::courseCode) { grade -> SummaryCard("${grade.courseCode} · ${grade.courseTitle}", "Grade ${grade.grade}", listOfNotNull(grade.total?.let { "${it.cleanNumber()}/100" }, grade.credits?.let { "${it.cleanNumber()} credits" }).joinToString(" · ")) }
        if (state.materials.isNotEmpty()) item { Text("Course materials", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp)) }
        items(state.materials, key = { it.id }) { material -> Card(Modifier.fillMaxWidth().clickable { showDetail("material", material.id) }) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(material.courseCode, style = MaterialTheme.typography.titleMedium); Text(material.title.ifBlank { material.fileName })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { openMaterial(material, false) }) { Text("Open") }; TextButton(onClick = { openMaterial(material, true) }) { Text("Share") } }
        } } }
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
            listOf(item.courseType, item.faculty).filter(String::isNotBlank).joinToString(" · ").takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            when {
                item.recovery > 0 -> Text(
                    "Attend ${if (item.blockSize > 1) item.recoveryBlocks else item.recovery} consecutive ${if (item.blockSize > 1) "blocks" else "classes"} to reach the target",
                    color = MaterialTheme.colorScheme.error,
                )
                item.skippable == 0 -> Text("No projected buffer at the selected target", color = MaterialTheme.colorScheme.error)
                else -> Text(
                    if (item.blockSize > 1) "Projected buffer: ${item.skippableBlocks} lab ${if (item.skippableBlocks == 1) "block" else "blocks"}" else "Projected buffer: ${item.skippable} ${if (item.skippable == 1) "class" else "classes"}",
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
    shareTimetableQr: () -> Unit,
    showExam: (ExamUi) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Schedule", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }); Button(onClick = shareTimetableQr, enabled = state.slots.isNotEmpty() && !state.loading) { Text("Share timetable QR") } } }
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
        if (state.calendar.isNotEmpty()) {
            item { Text("Academic calendar", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
            items(state.calendar, key = { it.id }) { day -> SummaryCard(day.dayType.ifBlank { "Calendar" }, day.title, LocalDate.ofEpochDay(day.dateEpochDay).format(DateTimeFormatter.ofPattern("EEE, dd MMM"))) }
        }
        if (state.exams.isNotEmpty()) {
            item { Text("Examinations", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
            items(state.exams, key = ExamUi::id) { exam -> Column(Modifier.clickable { showExam(exam) }) { ExamCard(exam) } }
        }
    }
}

@Composable
private fun TasksScreen(state: VioraUiState, showAssignment: (AssignmentUi) -> Unit, showExam: (ExamUi) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Tasks", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
        item { Text("Digital assignments", style = MaterialTheme.typography.titleLarge) }
        items(state.assignments, key = AssignmentUi::id) { assignment ->
            Card(Modifier.fillMaxWidth().clickable { showAssignment(assignment) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${assignment.courseCode} · ${assignment.title}", style = MaterialTheme.typography.titleMedium)
                    Text(assignment.dueEpochMillis.asAcademicTime("Due time unavailable"))
                    if (assignment.status.isNotBlank()) Text(assignment.status)
                }
            }
        }
        if (state.assignments.isEmpty()) item { Text("No digital assignments are cached.") }
        item { Text("Assessment marks", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
        items(state.marks, key = MarkUi::id) { mark ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(mark.title, style = MaterialTheme.typography.titleMedium)
                Text(mark.courseTitle)
                Text(listOfNotNull(mark.scoredMark?.let { "${it.cleanNumber()}/${mark.maxMarks?.cleanNumber() ?: "—"}" }, mark.weightageMark?.let { "Weighted ${it.cleanNumber()}" }, mark.status.takeIf(String::isNotBlank)).joinToString(" · "))
            } }
        }
        item { Text("Examinations", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
        items(state.exams, key = ExamUi::id) { exam -> Column(Modifier.clickable { showExam(exam) }) { ExamCard(exam) } }
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

@Composable
private fun DetailScreen(state: VioraUiState, selection: DetailSelection, openMaterial: (app.viora.database.CourseMaterialEntity, Boolean) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (selection.kind) {
            "course" -> state.attendance.firstOrNull { it.id == selection.id }?.let { attendance ->
                item { Text(attendance.courseTitle.ifBlank { attendance.courseCode }, style = MaterialTheme.typography.headlineMedium) }
                item { AttendanceCard(attendance) }
                state.slots.filter { it.code == attendance.courseCode || it.title == attendance.courseTitle }.forEach { slot -> item("slot:${slot.slotId}") { ClassCard(slot) } }
                state.marks.filter { it.courseTitle == attendance.courseTitle }.forEach { mark -> item("mark:${mark.id}") { SummaryCard(mark.title, mark.scoredMark?.cleanNumber() ?: mark.status, mark.weightageMark?.let { "Weighted ${it.cleanNumber()}" } ?: "") } }
                state.grades.filter { it.courseCode == attendance.courseCode || it.courseTitle == attendance.courseTitle }.forEach { grade -> item("grade:${grade.courseCode}") { SummaryCard("Grade", grade.grade, grade.total?.let { "${it.cleanNumber()}/100" } ?: "") } }
                state.materials.filter { it.courseCode == attendance.courseCode }.forEach { material -> item("material:${material.id}") { MaterialDetailCard(material, state, openMaterial) } }
            }
            "assignment" -> state.assignments.firstOrNull { it.id == selection.id }?.let { assignment ->
                item { Text(assignment.title, style = MaterialTheme.typography.headlineMedium) }
                item { SummaryCard(assignment.courseCode, assignment.status.ifBlank { "Status unavailable" }, assignment.dueEpochMillis.asAcademicTime("Due time unavailable")) }
            }
            "exam" -> state.exams.firstOrNull { it.id == selection.id }?.let { exam -> item { ExamCard(exam) } }
            "material" -> state.materials.firstOrNull { it.id == selection.id }?.let { material -> item { MaterialDetailCard(material, state, openMaterial) } }
        }
    }
}

@Composable private fun MaterialDetailCard(material: app.viora.database.CourseMaterialEntity, state: VioraUiState, openMaterial: (app.viora.database.CourseMaterialEntity, Boolean) -> Unit) {
    val download = state.downloads[material.id]
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(material.title.ifBlank { material.fileName }, style = MaterialTheme.typography.titleMedium)
        Text(material.courseCode)
        download?.let { Text(when (it.status) { "DOWNLOADING" -> "Downloading · attempt ${it.attempt}/3"; "READY" -> "Downloaded · ${it.localBytes.readableBytes()}"; "ERROR" -> it.error ?: "Download failed"; else -> it.status }) }
        if (download?.status == "DOWNLOADING") LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { openMaterial(material, false) }, enabled = download?.status != "DOWNLOADING") { Text(if (download?.status == "ERROR") "Retry" else "Open") }; TextButton(onClick = { openMaterial(material, true) }, enabled = download?.status != "DOWNLOADING") { Text("Share") } }
    } }
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
        Text("Grades and academic summary")
    }
}

@Composable
private fun ResultsScreen(state: VioraUiState) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Academic results", style = MaterialTheme.typography.headlineMedium) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryMetric("GPA", state.gpa?.cleanNumber() ?: "—")
            SummaryMetric("CGPA", state.cgpa?.cleanNumber() ?: "—")
        } }
        items(state.grades, key = GradeUi::courseCode) { grade -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("${grade.courseCode} · ${grade.courseTitle}", style = MaterialTheme.typography.titleMedium)
            Text(listOfNotNull("Grade ${grade.grade.ifBlank { "—" }}", grade.total?.let { "${it.cleanNumber()}/100" }, grade.credits?.let { "${it.cleanNumber()} credits" }).joinToString(" · "))
        } } }
        if (state.grades.isEmpty()) item { Text("No grade history is cached yet.") }
    }
}

@Composable private fun MoreScreen(state: VioraUiState, logout: () -> Unit, setDeadlineNotifications: (Boolean) -> Unit, setExamNotifications: (Boolean) -> Unit, setSearchQuery: (String) -> Unit, setQuietHours: (Boolean) -> Unit, selectSemester: (app.viora.network.SemesterOption) -> Unit, setSyncHours: (Int) -> Unit, refreshDiagnostics: () -> Unit, clearDownloads: () -> Unit, clearAcademicCache: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("More", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
        item { OutlinedTextField(value = state.searchQuery, onValueChange = setSearchQuery, label = { Text("Search cached academics") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.trim()
            val results = buildList {
                state.slots.distinctBy { it.courseId }.filter { listOf(it.code, it.title, it.faculty).any { value -> value.contains(q, true) } }.forEach { add("Course" to "${it.code} · ${it.title}") }
                state.assignments.filter { "${it.courseCode} ${it.title} ${it.status}".contains(q, true) }.forEach { add("Assignment" to "${it.courseCode} · ${it.title}") }
                state.exams.filter { "${it.courseCode} ${it.courseTitle} ${it.examType} ${it.venue}".contains(q, true) }.forEach { add("Exam" to "${it.examType} · ${it.courseCode}") }
                state.messages.filter { "${it.courseCode} ${it.subject} ${it.body}".contains(q, true) }.forEach { add("Message" to it.subject.ifBlank { it.body.take(80) }) }
                state.materials.filter { "${it.courseCode} ${it.title} ${it.fileName}".contains(q, true) }.forEach { add("Material" to "${it.courseCode} · ${it.title}") }
                state.marks.filter { "${it.courseTitle} ${it.title} ${it.status}".contains(q, true) }.forEach { add("Mark" to "${it.courseTitle} · ${it.title}") }
            }.take(30)
            items(results, key = { "${it.first}:${it.second}" }) { result -> SummaryCard(result.first, result.second, "Local result") }
            if (results.isEmpty()) item { Text("No cached results found.") }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { SummaryMetric("GPA", state.gpa?.cleanNumber() ?: "—"); SummaryMetric("CGPA", state.cgpa?.cleanNumber() ?: "—") } }
        item { Text("Class messages", style = MaterialTheme.typography.titleLarge) }
        items(state.messages, key = { it.id }) { message -> SummaryCard(message.subject.ifBlank { "Class message" }, message.body, listOf(message.courseCode, message.faculty).filter(String::isNotBlank).joinToString(" · ")) }
        if (state.messages.isEmpty()) item { Text("No class messages are cached.") }
        if (state.recentChanges.isNotEmpty()) {
            item { Text("What changed", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp)) }
            items(state.recentChanges.take(10), key = { it.id }) { change -> SummaryCard(change.title, change.detail, change.occurredEpochMillis.asAcademicTime()) }
        }
        item { Text("Notifications", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp)) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Assignment reminders"); Switch(checked = state.deadlineNotifications, onCheckedChange = setDeadlineNotifications) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Exam reminders"); Switch(checked = state.examNotifications, onCheckedChange = setExamNotifications) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("Quiet hours"); Text("10 PM–7 AM", color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked = state.quietHours, onCheckedChange = setQuietHours) } }
        item { Text("Sync and storage", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp)) }
        item { Column { Text("Active semester"); state.semesters.take(3).forEach { semester -> TextButton(onClick = { selectSemester(semester) }, enabled = semester != state.activeSemester, modifier = Modifier.fillMaxWidth()) { Text(semester.name) } } } }
        if (state.rolloverDetected) item { Text("A new semester was detected. Older cached semesters remain archived below.", color = MaterialTheme.colorScheme.primary) }
        if (state.cachedSemesters.any { !it.active }) item { Text("Archived: ${state.cachedSemesters.filterNot { it.active }.joinToString { it.name }}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Column { Text("Background sync: every ${state.syncHours} hours"); listOf(listOf(1, 3, 6), listOf(12, 24)).forEach { group -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { group.forEach { hours -> TextButton(onClick = { setSyncHours(hours) }, enabled = hours != state.syncHours) { Text("${hours}h") } } } } } }
        item { Text("Background diagnostics", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp)) }
        state.syncDiagnostics?.let { diagnostics ->
            item { SummaryCard("Worker", diagnostics.workState, if (diagnostics.runAttemptCount > 0) "Retry attempt ${diagnostics.runAttemptCount}" else "Periodic work is constrained to a connected network") }
            item { SummaryCard("Battery", diagnostics.batteryPercent?.let { "$it%${if (diagnostics.charging) " · Charging" else ""}" } ?: "Unavailable", listOfNotNull(if (diagnostics.powerSaveMode) "Power saver on" else null, if (diagnostics.batteryOptimizationActive) "Battery optimization active" else "Unrestricted by battery optimization", if (diagnostics.backgroundRestricted) "Background activity restricted" else null).joinToString(" · ")) }
            item { SummaryCard("Last profiled sync", diagnostics.lastOutcome?.replaceFirstChar(Char::uppercase) ?: "No run recorded", listOfNotNull(diagnostics.lastSource, diagnostics.lastDurationMillis?.let { "${it} ms" }, diagnostics.lastRunEpochMillis?.asAcademicTime()).joinToString(" · ")) }
        }
        item { TextButton(onClick = refreshDiagnostics) { Text("Refresh diagnostics") } }
        item { SummaryCard("Downloaded materials", state.downloadStorageBytes.readableBytes(), "Stored in Viora's private app folder") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton(onClick = clearDownloads) { Text("Clear downloads") }; TextButton(onClick = clearAcademicCache) { Text("Clear academic cache") } } }
        item { Text("Privacy and account", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp)) }
        item { Text("Viora stores academic data, credentials and its isolated VTOP cookies only on this device. Logging out here does not call VTOP logout or affect browser sessions.") }
        item { Button(onClick = logout) { Text("Erase local Viora account") } }
    }
}

@Composable private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) { Card(modifier) { Column(Modifier.padding(16.dp)) { Text(label); Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary) } } }
private fun Double.cleanNumber(): String = if (this % 1.0 == 0.0) toInt().toString() else "%.2f".format(this).trimEnd('0')
private fun Long.readableBytes(): String = when { this >= 1024 * 1024 -> "%.1f MB".format(this / 1024.0 / 1024.0); this >= 1024 -> "%.1f KB".format(this / 1024.0); else -> "$this B" }

private data class TimelineItem(val id: String, val at: Long, val kind: String, val title: String, val whenText: String)

private fun VioraUiState.slotsForDate(date: LocalDate): List<SlotWithCourse> {
    val exception = calendar.firstOrNull { it.dateEpochDay == date.toEpochDay() }
    val description = listOfNotNull(exception?.title, exception?.dayType).joinToString(" ")
    if (description.contains("holiday", true) || description.contains("exam day", true)) return emptyList()
    val order = DayOfWeek.entries.firstOrNull { description.contains("${it.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} order", true) }
    return slots.filter { it.dayOfWeek == (order ?: date.dayOfWeek).value }
}

private fun VioraUiState.academicTimeline(from: LocalDate): List<TimelineItem> {
    val zone = academicZone
    val end = from.plusDays(7)
    val items = mutableListOf<TimelineItem>()
    var day = from
    while (!day.isAfter(end)) {
        slotsForDate(day).forEach { slot ->
            val at = day.atStartOfDay(zone).plusMinutes(slot.startMinute.toLong()).toInstant().toEpochMilli()
            items += TimelineItem("class:${day}:${slot.slotId}", at, "Class", "${slot.code} · ${slot.title}", at.asAcademicTime())
        }
        calendar.filter { it.dateEpochDay == day.toEpochDay() }.forEach { event ->
            val at = day.atStartOfDay(zone).toInstant().toEpochMilli()
            items += TimelineItem("calendar:${event.id}", at, event.dayType.ifBlank { "Calendar" }, event.title, at.asAcademicTime())
        }
        day = day.plusDays(1)
    }
    assignments.filter { it.dueEpochMillis != null }.forEach { assignment -> items += TimelineItem("assignment:${assignment.id}", assignment.dueEpochMillis!!, "Assignment", "${assignment.courseCode} · ${assignment.title}", assignment.dueEpochMillis.asAcademicTime()) }
    exams.forEach { exam -> items += TimelineItem("exam:${exam.id}", exam.startsEpochMillis, exam.examType, "${exam.courseCode} · ${exam.courseTitle}", exam.startsEpochMillis.asAcademicTime()) }
    messages.filter { it.postedEpochMillis != null }.take(5).forEach { message -> items += TimelineItem("message:${message.id}", message.postedEpochMillis!!, "Message", message.subject.ifBlank { message.body.take(80) }, message.postedEpochMillis.asAcademicTime()) }
    val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
    return items.filter { it.at >= start }.sortedBy(TimelineItem::at)
}
