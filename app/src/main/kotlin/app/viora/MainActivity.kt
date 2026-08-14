package app.viora

import android.os.Bundle
import android.content.Intent
import android.Manifest
import android.app.AlarmManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import app.viora.database.SlotWithCourse
import app.viora.setup.SetupAction
import app.viora.setup.SetupScreen
import app.viora.setup.SetupState
import app.viora.setup.VtopVerificationScreen
import app.viora.assignment.VtopAssignmentUploadScreen
import app.viora.sync.VioraSyncScheduler
import app.viora.ui.VioraTheme
import app.viora.ui.VioraAmber
import app.viora.ui.VioraBlue
import app.viora.ui.VioraCoral
import app.viora.ui.VioraSuccess
import app.viora.domain.ClassPhase
import app.viora.domain.AttendanceCalculator
import app.viora.domain.classCheckInKey
import app.viora.domain.classPhase
import app.viora.domain.sameCourseCode
import app.viora.domain.ExamWindow
import app.viora.domain.isExamActive
import app.viora.domain.isExamPeriodActive
import app.viora.domain.isAssignmentSubmitted
import app.viora.domain.overlapsExam
import app.viora.domain.shouldShowExamInSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
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
    ) { if (model.state.value.configured) requestPreciseReminderAccess() }

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
                    } else if (state.configured) {
                        requestPreciseReminderAccess()
                    }
                }
                LaunchedEffect(state.activeSemester?.id) {
                    state.activeSemester?.let { graph.reminders.schedule(it.id) }
                }
                if (state.interactiveVerification) {
                    VtopVerificationScreen(state.loading, state.error, model::completeInteractiveVerification, model::interactiveVerificationError, model::cancelInteractiveVerification)
                } else if (state.assignmentUploadSession != null) {
                    VtopAssignmentUploadScreen(requireNotNull(state.assignmentUploadSession), model::closeAssignmentUpload)
                } else if (state.configured) {
                    Dashboard(state, model::refresh, model::selectSemester, model::beginReauthentication, model::logout, model::setDeadlineNotifications, model::setExamNotifications, model::openMaterial, model::downloadMaterial, model::downloadMaterials, model::beginAssignmentUpload, model::setSearchQuery, model::setQuietHours, model::setSyncHours, model::refreshDiagnostics, model::clearDownloads, model::clearAcademicCache, model::shareTimetableQr, model::markClass, notificationDestination.value)
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

    private fun requestPreciseReminderAccess() {
        if (Build.VERSION.SDK_INT < 31) return
        val alarms = getSystemService(AlarmManager::class.java)
        if (alarms.canScheduleExactAlarms()) return
        val preferences = getSharedPreferences(VioraGraph.SETTINGS_NAME, MODE_PRIVATE)
        if (preferences.getBoolean("asked_precise_reminders", false)) return
        preferences.edit().putBoolean("asked_precise_reminders", true).apply()
        runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
    }
}

private data class Destination(val label: String, val icon: ImageVector)
internal data class DetailSelection(val kind: String, val id: String)

private val destinations = listOf(
    Destination("Home", Icons.Outlined.Home),
    Destination("Schedule", Icons.Outlined.CalendarMonth),
    Destination("Courses", Icons.AutoMirrored.Outlined.MenuBook),
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
    downloadMaterial: (app.viora.database.CourseMaterialEntity) -> Unit,
    downloadMaterials: (List<app.viora.database.CourseMaterialEntity>) -> Unit,
    uploadAssignment: () -> Unit,
    setSearchQuery: (String) -> Unit,
    setQuietHours: (Boolean) -> Unit,
    setSyncHours: (Int) -> Unit,
    refreshDiagnostics: () -> Unit,
    clearDownloads: () -> Unit,
    clearAcademicCache: () -> Unit,
    shareTimetableQr: () -> Unit,
    markClass: (String, ClassCheckIn?) -> Unit,
    initialDestination: String?,
) {
    var selected by remember(initialDestination) { mutableIntStateOf(when (initialDestination) { "schedule" -> 1; "courses" -> 2; "tasks" -> 3; "more" -> 4; else -> 0 }) }
    var detail by remember { mutableStateOf<DetailSelection?>(null) }
    BoxWithConstraints {
    val expanded = maxWidth >= 840.dp
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VioraTopBar(state, detail, { detail = null }, refresh, reauthenticate)
        },
        bottomBar = {
            if (!expanded) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index; detail = null },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
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
            AnimatedVisibility(visible = state.error != null, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
                state.error?.let { message ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp).semantics { liveRegion = LiveRegionMode.Assertive })
                    }
                }
            }
            AnimatedContent(targetState = detail to selected, label = "dashboard destination") { (activeDetail, destination) ->
                if (activeDetail != null) DetailScreen(state, activeDetail, openMaterial, uploadAssignment) else when (destination) {
                    0 -> HomeScreen(state, PaddingValues())
                    1 -> ScheduleScreen(state, selectSemester, shareTimetableQr, markClass) { detail = DetailSelection("exam", it.id) }
                    2 -> CoursesScreen(state, openMaterial, downloadMaterial, downloadMaterials) { kind, id -> detail = DetailSelection(kind, id) }
                    3 -> TasksScreen(state, { detail = DetailSelection("assignment", it.id) }, { detail = DetailSelection("exam", it.id) })
                    else -> MoreScreen(state, logout, setDeadlineNotifications, setExamNotifications, setSearchQuery, setQuietHours, selectSemester, setSyncHours, refreshDiagnostics, clearDownloads, clearAcademicCache)
                }
            }
        }
    }
    }
}
}

@Composable
private fun VioraTopBar(
    state: VioraUiState,
    detail: DetailSelection?,
    closeDetail: () -> Unit,
    refresh: () -> Unit,
    reauthenticate: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (detail != null) {
                TextButton(onClick = closeDetail) { Text("← Back") }
            } else {
                Column(Modifier.weight(1f)) {
                    Text("viora", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
                    Text(state.activeSemester?.name ?: "your VTOP, distilled", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    state.syncSummary()?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (detail != null) Spacer(Modifier.weight(1f))
            if (state.reauthRequired) {
                AssistChip(onClick = reauthenticate, label = { Text("Sign in") })
            } else {
                Surface(
                    onClick = refresh,
                    enabled = !state.loading,
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                        Text("Sync", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

internal fun VioraUiState.syncSummary(): String? {
    val latest = syncResources.filter { it.status != "SYNCING" }.maxByOrNull { it.lastAttemptEpochMillis } ?: return null
    val label = if (latest.status == "ERROR") "Sync failed" else "Synced"
    val time = Instant.ofEpochMilli(latest.lastAttemptEpochMillis).atZone(academicZone)
        .format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
    return "$label · $time"
}

@Composable
private fun HomeScreen(state: VioraUiState, padding: PaddingValues) {
    val nowEpochMillis = System.currentTimeMillis()
    val now = Instant.ofEpochMilli(nowEpochMillis).atZone(academicZone)
    val agenda = state.homeAgenda(nowEpochMillis)
    val dueAssignments = state.homeDueAssignments(nowEpochMillis)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(now.toLocalDate().format(DateTimeFormatter.ofPattern("EEEE, d MMM")), style = MaterialTheme.typography.labelMedium, color = VioraBlue)
                Text(greeting(now.hour), style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                Text(
                    if (agenda.examDates) "Exams are here. Lock in—you've gooned enough already."
                    else "Today's classes, deadlines and what comes next.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!agenda.examDates && dueAssignments.isNotEmpty()) {
            item { SectionLabel("DUE SOON") }
            items(dueAssignments, key = AssignmentUi::id) { assignment -> CompactAssignmentCard(assignment) }
        }
        if (agenda.items.isEmpty() && dueAssignments.isEmpty()) {
            val emptyCopy = emptyHomeCopy(now)
            item { EmptyStateCard(emptyCopy.first, emptyCopy.second) }
        } else {
            item { SectionLabel(if (agenda.items.first().isActiveExam) "HAPPENING NOW" else "UPCOMING EVENTS") }
            items(agenda.items, key = HomeAgendaItem::id) { event ->
                event.exam?.let { ExamCard(it) }
                event.slot?.let { slot ->
                    val starts = Instant.ofEpochMilli(event.at).atZone(academicZone)
                    CompactHomeClassCard(slot, state.attendanceFor(slot), starts.toLocalDate())
                }
            }
        }
    }
}

@Composable
private fun CompactHomeClassCard(slot: SlotWithCourse, attendance: AttendanceUi?, date: LocalDate) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(slot.title.ifBlank { slot.code }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(slot.startMinute.asTime(), style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
            }
            Text("${date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))} · ${slot.venue.ifBlank { "Room unavailable" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            attendance?.let { AttendanceGuidance(it) }
        }
    }
}

@Composable
private fun CompactAssignmentCard(assignment: AssignmentUi) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = VioraAmber.copy(alpha = 0.08f)) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${assignment.courseCode} · ${assignment.title}", style = MaterialTheme.typography.titleMedium)
            Text(assignment.dueEpochMillis.asAcademicTime("Due time unavailable"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun emptyHomeCopy(now: ZonedDateTime): Pair<String, String> = when {
    now.dayOfWeek == DayOfWeek.FRIDAY && now.hour >= 18 ->
        "Friday night survived" to "Go to Tarama. Academic comeback resumes later."
    now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY ->
        "Weekend detected" to "Go outside. VTOP cannot hurt you here."
    else -> "Nothing coming up" to "Your fetched schedule is clear for now."
}

private fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun CoursesScreen(
    state: VioraUiState,
    openMaterial: (app.viora.database.CourseMaterialEntity, Boolean) -> Unit,
    downloadMaterial: (app.viora.database.CourseMaterialEntity) -> Unit,
    downloadMaterials: (List<app.viora.database.CourseMaterialEntity>) -> Unit,
    showDetail: (String, String) -> Unit,
) {
    val courses = state.consolidatedCourses()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Consolidated courses", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text("Every course and material fetched from VTOP, in one place.")
        }
        if (state.materials.isNotEmpty()) item {
            Button(onClick = { downloadMaterials(state.materials) }, enabled = state.downloads.values.none { it.status == "DOWNLOADING" }) {
                Text("Download all materials")
            }
        }
        items(courses, key = ConsolidatedCourseUi::code) { course ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(course.code, style = MaterialTheme.typography.titleLarge)
                            if (course.title.isNotBlank() && course.title != course.code) Text(course.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { showDetail("course", course.code) }) { Text("View course") }
                    }
                    course.attendance?.let { Text("${"%.1f".format(it.percentage)}% attendance", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (course.faculty.isNotBlank()) Text(course.faculty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    Text("Materials (${course.materials.size})", style = MaterialTheme.typography.titleMedium)
                    if (course.materials.isEmpty()) Text("No materials fetched for this course.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        TextButton(onClick = { downloadMaterials(course.materials) }) { Text("Download all for ${course.code}") }
                        course.materials.forEach { material ->
                            CourseMaterialActions(material, state, openMaterial, downloadMaterial)
                        }
                    }
                }
            }
        }
        if (courses.isEmpty()) item { Text("No courses have been cached yet.") }
    }
}

internal data class ConsolidatedCourseUi(
    val code: String,
    val title: String,
    val faculty: String,
    val attendance: AttendanceUi?,
    val materials: List<app.viora.database.CourseMaterialEntity>,
)

internal fun VioraUiState.consolidatedCourses(): List<ConsolidatedCourseUi> {
    val codes = linkedSetOf<String>()
    slots.mapTo(codes) { it.code }
    attendance.mapTo(codes) { it.courseCode }
    assignments.mapTo(codes) { it.courseCode }
    grades.mapTo(codes) { it.courseCode }
    materials.mapTo(codes) { it.courseCode }
    return codes.filter(String::isNotBlank).map { code ->
        val attendanceItem = attendance.firstOrNull { sameCourseCode(it.courseCode, code) }
        val slot = slots.firstOrNull { sameCourseCode(it.code, code) }
        val grade = grades.firstOrNull { sameCourseCode(it.courseCode, code) }
        ConsolidatedCourseUi(
            code = code,
            title = attendanceItem?.courseTitle?.takeIf(String::isNotBlank) ?: slot?.title?.takeIf(String::isNotBlank) ?: grade?.courseTitle.orEmpty(),
            faculty = attendanceItem?.faculty?.takeIf(String::isNotBlank) ?: slot?.faculty.orEmpty(),
            attendance = attendanceItem,
            materials = materials.filter { sameCourseCode(it.courseCode, code) },
        )
    }.sortedBy { it.code }
}

@Composable
private fun CourseMaterialActions(
    material: app.viora.database.CourseMaterialEntity,
    state: VioraUiState,
    openMaterial: (app.viora.database.CourseMaterialEntity, Boolean) -> Unit,
    downloadMaterial: (app.viora.database.CourseMaterialEntity) -> Unit,
) {
    val download = state.downloads[material.id]
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(material.title.ifBlank { material.fileName }, style = MaterialTheme.typography.labelLarge)
        download?.let { Text(if (it.status == "READY") "Downloaded · ${it.localBytes.readableBytes()}" else it.status.lowercase().replaceFirstChar(Char::uppercase), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { downloadMaterial(material) }, enabled = download?.status != "DOWNLOADING") { Text(if (download?.status == "READY") "Downloaded" else "Download") }
            TextButton(onClick = { openMaterial(material, false) }, enabled = download?.status != "DOWNLOADING") { Text("Open") }
            TextButton(onClick = { openMaterial(material, true) }, enabled = download?.status != "DOWNLOADING") { Text("Share") }
        }
    }
}

@Composable
private fun AttendanceCard(item: AttendanceUi) {
    val healthy = item.recovery == 0 && item.skippable > 0
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (healthy) VioraSuccess.copy(alpha = 0.22f) else VioraCoral.copy(alpha = 0.28f)),
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (item.courseTitle.isBlank()) item.courseCode else "${item.courseCode} · ${item.courseTitle}",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${item.attended}/${item.held} classes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${"%.1f".format(item.percentage)}%", color = if (healthy) VioraSuccess else VioraCoral, fontWeight = FontWeight.Bold)
            }
            listOf(item.courseType, item.faculty).filter(String::isNotBlank).joinToString(" · ").takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            when {
                item.recovery > 0 -> Text(
                    "Attend next ${if (item.blockSize > 1) item.recoveryBlocks else item.recovery} ${if (item.blockSize > 1) "lab blocks" else "classes"} to recover",
                    color = VioraCoral,
                    style = MaterialTheme.typography.labelLarge,
                )
                item.skippable == 0 -> Text("At the target · don’t skip the next class", color = VioraAmber, style = MaterialTheme.typography.labelLarge)
                else -> Text(
                    if (item.blockSize > 1) "Safe to skip ${item.skippableBlocks} lab ${if (item.skippableBlocks == 1) "block" else "blocks"}" else "Safe to skip ${item.skippable} ${if (item.skippable == 1) "class" else "classes"}",
                    color = VioraSuccess,
                    style = MaterialTheme.typography.labelLarge,
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
    markClass: (String, ClassCheckIn?) -> Unit,
    showExam: (ExamUi) -> Unit,
) {
    val today = LocalDate.now()
    val now = LocalTime.now()
    val nowMinute = now.hour * 60 + now.minute
    var selectedDay by remember { mutableIntStateOf(today.dayOfWeek.value) }
    val selectedDate = today.plusDays(((selectedDay - today.dayOfWeek.value + 7) % 7).toLong())
    val daySlots = state.slots.filter { it.dayOfWeek == selectedDay }.sortedBy(SlotWithCourse::startMinute)
    val visibleExams = state.exams.filter { shouldShowExamInSchedule(it.startsEpochMillis, it.endsEpochMillis, System.currentTimeMillis()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Timetable", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                    Text("Your complete weekly timetable", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = shareTimetableQr, enabled = state.slots.isNotEmpty() && !state.loading) { Icon(Icons.Outlined.Share, "Share timetable") }
            }
        }
        if (state.semesters.size > 1) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.semesters.take(3).forEach { semester ->
                        FilterChip(selected = semester == state.activeSemester, onClick = { selectSemester(semester) }, label = { Text(semester.name) })
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..7).forEach { day ->
                    val hasClasses = state.slots.any { it.dayOfWeek == day }
                    FilterChip(
                        selected = selectedDay == day,
                        onClick = { selectedDay = day },
                        label = { Text(DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()) },
                        enabled = hasClasses || selectedDay == day,
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    SectionLabel(DayOfWeek.of(selectedDay).getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase())
                    Text("${daySlots.size} ${if (daySlots.size == 1) "class" else "classes"}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (daySlots.isEmpty()) {
            item {
                EmptyStateCard("No classes", "This day has no cached timetable entries.")
            }
        } else {
            items(daySlots, key = SlotWithCourse::slotId) { slot ->
                val isToday = selectedDay == today.dayOfWeek.value
                val phase = if (isToday) classPhase(slot.startMinute, slot.endMinute, nowMinute) else ClassPhase.UPCOMING
                val key = classCheckInKey(selectedDate, slot.slotId)
                ClassCard(slot, state.attendanceFor(slot), phase, state.classCheckIns[key], if (selectedDate <= today && phase != ClassPhase.UPCOMING) key else null, markClass)
            }
        }
        if (state.calendar.isNotEmpty()) {
            item { SectionLabel("ACADEMIC CALENDAR") }
            items(state.calendar, key = { it.id }) { day -> SummaryCard(day.dayType.ifBlank { "Calendar" }, day.title, LocalDate.ofEpochDay(day.dateEpochDay).format(DateTimeFormatter.ofPattern("EEE, dd MMM"))) }
        }
        if (visibleExams.isNotEmpty()) {
            item { SectionLabel("EXAMINATIONS") }
            items(visibleExams, key = ExamUi::id) { exam -> Column(Modifier.clickable { showExam(exam) }) { ExamCard(exam) } }
        }
    }
}

@Composable
private fun TasksScreen(state: VioraUiState, showAssignment: (AssignmentUi) -> Unit, showExam: (ExamUi) -> Unit) {
    val visibleExams = state.exams.filter {
        shouldShowExamInSchedule(it.startsEpochMillis, it.endsEpochMillis, System.currentTimeMillis())
    }
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
        items(visibleExams, key = ExamUi::id) { exam -> Column(Modifier.clickable { showExam(exam) }) { ExamCard(exam) } }
        if (visibleExams.isEmpty()) item { Text("No upcoming examinations.") }
    }
}

@Composable
private fun ExamCard(exam: ExamUi) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${exam.examType} · ${exam.courseCode}", style = MaterialTheme.typography.titleMedium)
            if (exam.courseTitle.isNotBlank()) Text(exam.courseTitle)
            Text(exam.startsEpochMillis.asAcademicTime())
            val details = listOfNotNull(
                exam.venue.takeIf(String::isNotBlank)?.let { "Room $it" },
                exam.seatNumber.takeIf(String::isNotBlank)?.let { "Seat $it" },
            ).joinToString(" · ")
            if (details.isNotBlank()) Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ClassCard(
    slot: SlotWithCourse,
    attendance: AttendanceUi? = null,
    phase: ClassPhase = ClassPhase.UPCOMING,
    checkIn: ClassCheckIn? = null,
    checkInKey: String? = null,
    markClass: (String, ClassCheckIn?) -> Unit = { _, _ -> },
    whenText: String? = null,
) {
    val accent = when (checkIn) {
        ClassCheckIn.ATTENDED -> VioraSuccess
        ClassCheckIn.MISSED -> VioraCoral
        null -> if (phase == ClassPhase.LIVE) VioraBlue else MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        color = if (phase == ClassPhase.LIVE) VioraBlue.copy(alpha = 0.09f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(if (phase == ClassPhase.LIVE || checkIn != null) 1.5.dp else 1.dp, accent),
    ) {
        Row {
            Spacer(Modifier.width(4.dp).height(148.dp).background(accent, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(whenText ?: "${slot.startMinute.asTime()} — ${slot.endMinute.asTime()}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelLarge)
                    ClassStatusBadge(phase, checkIn)
                }
                Text(slot.code, style = MaterialTheme.typography.titleLarge)
                if (slot.title.isNotBlank() && slot.title != slot.code) Text(slot.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val metadata = listOf(slot.venue, slot.faculty).filter(String::isNotBlank).joinToString("  ·  ")
                if (metadata.isNotBlank()) Text(metadata, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                attendance?.let { AttendanceGuidance(it) }
                if (checkInKey != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = checkIn == ClassCheckIn.ATTENDED,
                            onClick = { markClass(checkInKey, if (checkIn == ClassCheckIn.ATTENDED) null else ClassCheckIn.ATTENDED) },
                            leadingIcon = { Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) },
                            label = { Text("Attended") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VioraSuccess.copy(alpha = 0.2f), selectedLabelColor = VioraSuccess, selectedLeadingIconColor = VioraSuccess),
                        )
                        FilterChip(
                            selected = checkIn == ClassCheckIn.MISSED,
                            onClick = { markClass(checkInKey, if (checkIn == ClassCheckIn.MISSED) null else ClassCheckIn.MISSED) },
                            leadingIcon = { Icon(Icons.Outlined.Close, null, Modifier.size(16.dp)) },
                            label = { Text("Missed") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VioraCoral.copy(alpha = 0.2f), selectedLabelColor = VioraCoral, selectedLeadingIconColor = VioraCoral),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassStatusBadge(phase: ClassPhase, checkIn: ClassCheckIn?) {
    val (label, color) = when (checkIn) {
        ClassCheckIn.ATTENDED -> "ATTENDED" to VioraSuccess
        ClassCheckIn.MISSED -> "MISSED" to VioraCoral
        null -> when (phase) {
            ClassPhase.LIVE -> "LIVE" to VioraBlue
            ClassPhase.ENDED -> "ENDED" to MaterialTheme.colorScheme.onSurfaceVariant
            ClassPhase.UPCOMING -> "UPCOMING" to VioraAmber
        }
    }
    Text(label, color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.background(color.copy(alpha = 0.11f), CircleShape).padding(horizontal = 9.dp, vertical = 5.dp))
}

@Composable
private fun AttendanceGuidance(attendance: AttendanceUi) {
    val projection = AttendanceCalculator.calculate(attendance.attended, attendance.held, 75, attendance.blockSize)
    val (text, color) = when {
        projection.classesToRecover > 0 -> "Attend next ${if (attendance.blockSize > 1) projection.blocksToRecover else projection.classesToRecover} to reach 75%" to VioraCoral
        projection.skippableClasses > 0 -> "Can skip ${if (attendance.blockSize > 1) projection.skippableBlocks else projection.skippableClasses} ${if (attendance.blockSize > 1) "lab blocks" else "classes"} safely" to VioraSuccess
        else -> "At 75% limit · attend this one" to VioraAmber
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Spacer(Modifier.size(7.dp).background(color, CircleShape))
        Text("${"%.0f".format(attendance.percentage)}% · $text", color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun DetailScreen(state: VioraUiState, selection: DetailSelection, openMaterial: (app.viora.database.CourseMaterialEntity, Boolean) -> Unit, uploadAssignment: () -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (selection.kind) {
            "course" -> {
                val attendance = state.attendance.firstOrNull { it.id == selection.id || sameCourseCode(it.courseCode, selection.id) }
                val code = attendance?.courseCode ?: selection.id
                val title = attendance?.courseTitle?.takeIf(String::isNotBlank)
                    ?: state.slots.firstOrNull { sameCourseCode(it.code, code) }?.title
                    ?: code
                item { Text(title, style = MaterialTheme.typography.headlineMedium) }
                attendance?.let { item { AttendanceCard(it) } }
                state.slots.filter { sameCourseCode(it.code, code) }.forEach { slot -> item("slot:${slot.slotId}") { ClassCard(slot) } }
                state.marks.filter { it.courseTitle.equals(title, true) }.forEach { mark -> item("mark:${mark.id}") { SummaryCard(mark.title, mark.scoredMark?.cleanNumber() ?: mark.status, mark.weightageMark?.let { "Weighted ${it.cleanNumber()}" } ?: "") } }
                state.grades.filter { sameCourseCode(it.courseCode, code) }.forEach { grade -> item("grade:${grade.courseCode}") { SummaryCard("Grade", grade.grade, grade.total?.let { "${it.cleanNumber()}/100" } ?: "") } }
                val assignments = state.assignments.filter { sameCourseCode(it.courseCode, code) }
                val materials = state.materials.filter { sameCourseCode(it.courseCode, code) }
                item { Text("Digital assignments", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
                if (assignments.isEmpty()) item { Text("No digital assignments are cached for this course.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                assignments.forEach { assignment -> item("assignment:${assignment.id}") { AssignmentCard(assignment) } }
                item { Text("Course materials", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
                if (materials.isEmpty()) item { Text("No materials are cached for this course yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                materials.forEach { material -> item("material:${material.id}") { MaterialDetailCard(material, state, openMaterial) } }
            }
            "assignment" -> state.assignments.firstOrNull { it.id == selection.id }?.let { assignment ->
                item { Text(assignment.title, style = MaterialTheme.typography.headlineMedium) }
                item { SummaryCard(assignment.courseCode, assignment.status.ifBlank { "Status unavailable" }, assignment.dueEpochMillis.asAcademicTime("Due time unavailable")) }
                item { Button(onClick = uploadAssignment, enabled = !state.loading) { Text("Upload on VTOP") } }
            }
            "exam" -> state.exams.firstOrNull { it.id == selection.id }?.let { exam -> item { ExamCard(exam) } }
            "material" -> state.materials.firstOrNull { it.id == selection.id }?.let { material -> item { MaterialDetailCard(material, state, openMaterial) } }
        }
    }
}

@Composable private fun AssignmentCard(assignment: AssignmentUi) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(assignment.title, style = MaterialTheme.typography.titleMedium)
        Text("Due ${assignment.dueEpochMillis.asAcademicTime("time unavailable")}")
        if (assignment.status.isNotBlank()) Text(assignment.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
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

private val classTime = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private fun Int.asTime(): String = LocalTime.of(this / 60, this % 60).format(classTime)

private val academicDateTime = DateTimeFormatter.ofPattern("EEE, dd MMM · h:mm a")
private val academicZone = ZoneId.of("Asia/Kolkata")

private fun Long?.asAcademicTime(fallback: String = "Time unavailable"): String =
    this?.let { academicDateTime.format(Instant.ofEpochMilli(it).atZone(academicZone)) } ?: fallback

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.2.sp)
}

@Composable
private fun EmptyStateCard(title: String, body: String) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SyncStatusCard(message: String, loading: Boolean) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = VioraBlue.copy(alpha = 0.09f), border = androidx.compose.foundation.BorderStroke(1.dp, VioraBlue.copy(alpha = 0.22f))) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = VioraBlue) else Spacer(Modifier.size(9.dp).background(VioraSuccess, CircleShape))
            Column {
                Text(if (loading) "Syncing with VTOP" else "Sync complete", style = MaterialTheme.typography.labelLarge)
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, supporting: String) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (supporting.isNotBlank()) Text(supporting, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
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
        item { GpaPlanner(state) }
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
        item { Text("Viora is a student-made, unofficial project and is not connected to or endorsed by VIT or VTOP.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text("Viora stores academic data, credentials and its isolated VTOP cookies only on this device. Logging out here does not call VTOP logout or affect browser sessions.") }
        item { Button(onClick = logout) { Text("Erase local Viora account") } }
    }
}

internal data class ManualSubjectInput(val name: String = "", val grade: String = "", val credits: String = "")
internal data class ManualSemesterInput(val sgpa: String = "", val credits: String = "")

private val gradePoints = mapOf("S" to 10.0, "A" to 9.0, "B" to 8.0, "C" to 7.0, "D" to 6.0, "E" to 5.0, "F" to 0.0, "N" to 0.0)

internal fun manualSgpa(rows: List<ManualSubjectInput>): Double? {
    val values = rows.mapNotNull { row ->
        val points = gradePoints[row.grade.trim().uppercase(Locale.ENGLISH)] ?: return@mapNotNull null
        val credits = row.credits.toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        points to credits
    }
    return values.takeIf { it.isNotEmpty() }?.let { it.sumOf { (points, credits) -> points * credits } / it.sumOf { (_, credits) -> credits } }
}

internal fun manualCgpa(rows: List<ManualSemesterInput>): Double? {
    val values = rows.mapNotNull { row ->
        val sgpa = row.sgpa.toDoubleOrNull()?.takeIf { it in 0.0..10.0 } ?: return@mapNotNull null
        val credits = row.credits.toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        sgpa to credits
    }
    return values.takeIf { it.isNotEmpty() }?.let { it.sumOf { (sgpa, credits) -> sgpa * credits } / it.sumOf { (_, credits) -> credits } }
}

internal fun requiredFutureSgpa(currentCgpa: Double, completedCredits: Double, futureCreditsPerSemester: Double, target: Double, semesters: Int): Double? {
    if (completedCredits <= 0 || futureCreditsPerSemester <= 0 || semesters <= 0) return null
    return (target * (completedCredits + futureCreditsPerSemester * semesters) - currentCgpa * completedCredits) /
        (futureCreditsPerSemester * semesters)
}

@Composable
private fun GpaPlanner(state: VioraUiState) {
    var manualMode by remember { mutableIntStateOf(0) }
    var subjects by remember { mutableStateOf(listOf(ManualSubjectInput())) }
    var semesters by remember { mutableStateOf(listOf(ManualSemesterInput())) }
    var showScale by remember { mutableStateOf(false) }
    val completedCredits = state.registeredCredits ?: state.earnedCredits
    val futureCredits = state.grades.mapNotNull(GradeUi::credits).sum().takeIf { it > 0 } ?: 20.0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("GPA planner", style = MaterialTheme.typography.titleLarge)
        Text("Uses VTOP-fetched grades, CGPA and credits. Future semesters assume ${futureCredits.cleanNumber()} credits each.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.grades.isNotEmpty()) {
            state.grades.forEach { grade ->
                Text("${grade.courseCode} · ${grade.grade.ifBlank { "—" }} · ${grade.credits?.let { "${it.cleanNumber()} credits" } ?: "credits unavailable"}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.cgpa != null && completedCredits != null) {
            listOf(9.0, 9.5, 10.0).forEach { target ->
                val required = (1..4).associateWith { count ->
                    requiredFutureSgpa(state.cgpa, completedCredits, futureCredits, target, count)
                }
                SummaryCard(
                    "Target ${target.cleanNumber()} CGPA",
                    "1 sem: ${required.getValue(1).asRequiredGpa()}",
                    (2..4).joinToString(" · ") { count -> "$count sems: ${required.getValue(count).asRequiredGpa()} avg" },
                )
            }
        } else Text("Sync VTOP results once to unlock target projections.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Manual calculator", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = manualMode == 0, onClick = { manualMode = 0 }, label = { Text("SGPA") })
            FilterChip(selected = manualMode == 1, onClick = { manualMode = 1 }, label = { Text("CGPA") })
        }
        if (manualMode == 0) {
            subjects.forEachIndexed { index, row ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    OutlinedTextField(value = row.name, onValueChange = { value -> subjects = subjects.toMutableList().also { it[index] = row.copy(name = value.take(60)) } }, label = { Text("Course") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = row.grade, onValueChange = { value -> subjects = subjects.toMutableList().also { it[index] = row.copy(grade = value.uppercase(Locale.ENGLISH).filter(Char::isLetter).take(1)) } }, label = { Text("Grade") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = row.credits, onValueChange = { value -> subjects = subjects.toMutableList().also { it[index] = row.copy(credits = value.filter { char -> char.isDigit() || char == '.' }.take(5)) } }, label = { Text("Credits") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                }
            }
            manualSgpa(subjects)?.let { SummaryCard("Calculated SGPA", it.cleanNumber(), "${(it * 10).cleanNumber()}%") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { subjects = subjects + ManualSubjectInput() }) { Text("Add course") }
                TextButton(onClick = { subjects = listOf(ManualSubjectInput()) }) { Text("Reset") }
            }
        } else {
            semesters.forEachIndexed { index, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = row.sgpa, onValueChange = { value -> semesters = semesters.toMutableList().also { it[index] = row.copy(sgpa = value.filter { char -> char.isDigit() || char == '.' }.take(5)) } }, label = { Text("SGPA") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = row.credits, onValueChange = { value -> semesters = semesters.toMutableList().also { it[index] = row.copy(credits = value.filter { char -> char.isDigit() || char == '.' }.take(5)) } }, label = { Text("Credits") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
            manualCgpa(semesters)?.let { SummaryCard("Calculated CGPA", it.cleanNumber(), "${(it * 10).cleanNumber()}%") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { semesters = semesters + ManualSemesterInput() }) { Text("Add semester") }
                TextButton(onClick = { semesters = listOf(ManualSemesterInput()) }) { Text("Reset") }
            }
        }
        TextButton(onClick = { showScale = !showScale }) { Text(if (showScale) "Hide grade scale" else "Show grade scale") }
        if (showScale) Text("S 10 · A 9 · B 8 · C 7 · D 6 · E 5 · F/N 0", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Raw marks are not converted to grades because VIT relative grading can vary by course.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Double?.asRequiredGpa(): String = when {
    this == null -> "Unavailable"
    this <= 0 -> "Already reached"
    this > 10.0 -> "Not possible"
    else -> cleanNumber()
}

@Composable private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) { Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Column(Modifier.padding(17.dp)) { SectionLabel(label.uppercase()); Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary) } } }
private fun Double.cleanNumber(): String = if (this % 1.0 == 0.0) toInt().toString() else "%.2f".format(this).trimEnd('0')
private fun Long.readableBytes(): String = when { this >= 1024 * 1024 -> "%.1f MB".format(this / 1024.0 / 1024.0); this >= 1024 -> "%.1f KB".format(this / 1024.0); else -> "$this B" }

internal data class HomeAgendaItem(
    val id: String,
    val at: Long,
    val slot: SlotWithCourse? = null,
    val exam: ExamUi? = null,
    val isActiveExam: Boolean = false,
)

internal data class HomeAgenda(val examDates: Boolean, val items: List<HomeAgendaItem>)

private fun VioraUiState.attendanceFor(slot: SlotWithCourse): AttendanceUi? = attendance.firstOrNull {
    it.courseCode.equals(slot.code, true) || (it.courseTitle.isNotBlank() && it.courseTitle.equals(slot.title, true))
}

internal fun VioraUiState.slotsForDate(date: LocalDate): List<SlotWithCourse> {
    val exception = calendar.firstOrNull { it.dateEpochDay == date.toEpochDay() }
    val description = listOfNotNull(exception?.title, exception?.dayType).joinToString(" ")
    if (description.contains("holiday", true)) return emptyList()
    val order = DayOfWeek.entries.firstOrNull { description.contains("${it.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} order", true) }
    val examsToday = examsForDate(date)
    return slots.filter { slot ->
        slot.dayOfWeek == (order ?: date.dayOfWeek).value && examsToday.none { exam ->
            overlapsExam(slot.startMinute, slot.endMinute, exam.startMinute(), exam.endMinute())
        }
    }
}

private fun VioraUiState.examsForDate(date: LocalDate): List<ExamUi> = exams
    .filter { Instant.ofEpochMilli(it.startsEpochMillis).atZone(academicZone).toLocalDate() == date }
    .sortedBy(ExamUi::startsEpochMillis)

private fun ExamUi.startMinute(): Int {
    val time = Instant.ofEpochMilli(startsEpochMillis).atZone(academicZone).toLocalTime()
    return time.hour * 60 + time.minute
}

private fun ExamUi.endMinute(): Int? = endsEpochMillis?.let {
    startMinute() + ((it - startsEpochMillis) / 60_000L).toInt()
}

internal fun VioraUiState.homeAgenda(nowEpochMillis: Long, classLookAheadDays: Long = 7): HomeAgenda {
    val now = Instant.ofEpochMilli(nowEpochMillis).atZone(academicZone)
    val examDates = isExamPeriodActive(exams.map { ExamWindow(it.startsEpochMillis, it.endsEpochMillis, it.examType) }, nowEpochMillis)
    val examItems = exams
        .filter { shouldShowExamInSchedule(it.startsEpochMillis, it.endsEpochMillis, nowEpochMillis) }
        .map { exam ->
            HomeAgendaItem(
                id = "exam:${exam.id}:${exam.startsEpochMillis}",
                at = exam.startsEpochMillis,
                exam = exam,
                isActiveExam = isExamActive(exam.startsEpochMillis, exam.endsEpochMillis, nowEpochMillis),
            )
        }
    if (examDates) return HomeAgenda(true, examItems.sortedBy { if (it.isActiveExam) Long.MIN_VALUE else it.at })

    val classItems = (0..classLookAheadDays).firstNotNullOfOrNull { offset ->
        val date = now.toLocalDate().plusDays(offset)
        slotsForDate(date).mapNotNull { slot ->
            val at = date.atStartOfDay(academicZone).plusMinutes(slot.startMinute.toLong()).toInstant().toEpochMilli()
            val ends = date.atStartOfDay(academicZone).plusMinutes(slot.endMinute.toLong()).toInstant().toEpochMilli()
            if (ends <= nowEpochMillis) null else HomeAgendaItem("class:${date.toEpochDay()}:${slot.slotId}", at, slot = slot)
        }.takeIf { it.isNotEmpty() }
    }.orEmpty()
    return HomeAgenda(false, (classItems + examItems).sortedBy(HomeAgendaItem::at))
}

internal fun VioraUiState.homeDueAssignments(nowEpochMillis: Long, lookAheadDays: Long = 7): List<AssignmentUi> {
    val horizon = nowEpochMillis + lookAheadDays * 24 * 60 * 60 * 1000
    return assignments.filter { assignment ->
        val due = assignment.dueEpochMillis ?: return@filter false
        due in (nowEpochMillis + 1)..horizon && !isAssignmentSubmitted(assignment.status, assignment.lastUpload)
    }.sortedBy { it.dueEpochMillis }
}
