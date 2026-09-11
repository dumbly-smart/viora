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
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import app.viora.database.SlotWithCourse
import app.viora.setup.SetupAction
import app.viora.setup.SetupScreen
import app.viora.setup.SetupState
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
import app.viora.domain.ExamCalendarDate
import app.viora.domain.ExamSuppressionWindow
import app.viora.domain.examSuppressionWindows as buildExamSuppressionWindows
import app.viora.domain.suppresses
import app.viora.domain.isExamActive
import app.viora.domain.isAssignmentSubmitted
import app.viora.domain.overlapsExam
import app.viora.domain.shouldShowExamInSchedule
import app.viora.notifications.AttendanceNotificationPolicy
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
    private var notificationSequence = 0
    private val graph by lazy { VioraGraph(applicationContext) }
    private val model by viewModels<VioraAppViewModel> {
        VioraAppViewModel.Factory(graph, VioraSyncScheduler(applicationContext))
    }
    private val runtimePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { if (model.state.value.configured) requestPreciseReminderAccess() }
    private val calendarPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.READ_CALENDAR] == true && grants[Manifest.permission.WRITE_CALENDAR] == true) {
            model.exportToDeviceCalendar()
        } else {
            model.calendarPermissionDenied()
        }
    }
    private val createIcsDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri -> uri?.let(model::exportCalendarIcs) }
    private val openIcsDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(model::importCalendarIcs) }
    private var pendingAssignmentUploadId: String? = null
    private val assignmentDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val assignmentId = pendingAssignmentUploadId
        pendingAssignmentUploadId = null
        if (uri != null && assignmentId != null) model.uploadAssignment(assignmentId, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Viora)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationDestination.value = intent.getStringExtra("viora_destination")?.let { "$it#${notificationSequence++}" }
        setContent {
            VioraTheme {
                val state by model.state.collectAsState()
                LaunchedEffect(state.configured) {
                    if (state.configured) {
                        val permissions = buildList {
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                            if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                        if (permissions.isNotEmpty()) runtimePermissions.launch(permissions.toTypedArray())
                        else requestPreciseReminderAccess()
                    }
                }
                LaunchedEffect(state.activeSemester?.id) {
                    state.activeSemester?.let { graph.reminders.schedule(it.id) }
                }
                if (state.configured) {
                    Dashboard(state, model::refresh, model::selectSemester, model::beginReauthentication, model::logout, model::setDeadlineNotifications, model::setExamNotifications, model::openMaterial, model::downloadMaterial, model::downloadMaterials, { assignment -> pendingAssignmentUploadId = assignment.id; assignmentDocument.launch(arrayOf("*/*")) }, model::setSearchQuery, model::setQuietHours, model::setSyncHours, model::refreshDiagnostics, model::clearDownloads, model::clearAcademicCache, model::shareTimetableQr, model::markClass, ::requestDeviceCalendarExport, { createIcsDocument.launch("viora-timetable.ics") }, { openIcsDocument.launch(arrayOf("text/calendar", "text/*", "application/octet-stream")) }, model::shareCalendarIcs, notificationDestination.value)
                } else {
                    SetupScreen(
                        state = SetupState(
                            username = state.username,
                            password = state.password,
                            rememberLogin = state.rememberLogin,
                            loading = state.loading,
                            error = state.error,
                            captchaImageDataUri = state.captchaImageDataUri,
                            captchaAnswer = state.captchaAnswer,
                        ),
                        onAction = { action ->
                            when (action) {
                                is SetupAction.UsernameChanged -> model.updateUsername(action.value)
                                is SetupAction.PasswordChanged -> model.updatePassword(action.value)
                                is SetupAction.RememberLoginChanged -> model.updateRememberLogin(action.value)
                                is SetupAction.CaptchaAnswerChanged -> model.updateCaptchaAnswer(action.value)
                                SetupAction.Submit -> model.signIn()
                                SetupAction.SubmitCaptcha -> model.submitCaptcha()
                            }
                        },
                    )
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); notificationDestination.value = intent.getStringExtra("viora_destination")?.let { "$it#${notificationSequence++}" } }

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

    private fun requestDeviceCalendarExport() {
        val permissions = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            model.exportToDeviceCalendar()
        } else {
            calendarPermissions.launch(permissions)
        }
    }
}

private data class Destination(val label: String, val icon: ImageVector)
internal data class DetailSelection(val kind: String, val id: String)

private val destinations = listOf(
    Destination("Today", Icons.Outlined.Home),
    Destination("Plan", Icons.Outlined.CalendarMonth),
    Destination("Library", Icons.AutoMirrored.Outlined.MenuBook),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Dashboard(
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
    uploadAssignment: (AssignmentUi) -> Unit,
    setSearchQuery: (String) -> Unit,
    setQuietHours: (Boolean) -> Unit,
    setSyncHours: (Int) -> Unit,
    refreshDiagnostics: () -> Unit,
    clearDownloads: () -> Unit,
    clearAcademicCache: () -> Unit,
    shareTimetableQr: () -> Unit,
    markClass: (String, ClassCheckIn?) -> Unit,
    exportToDeviceCalendar: () -> Unit,
    exportIcs: () -> Unit,
    importIcs: () -> Unit,
    shareCalendarIcs: () -> Unit,
    initialDestination: String?,
) {
    val initialRoute = initialDestination?.substringBefore('#')
    var selected by remember(initialDestination) {
        mutableIntStateOf(when (initialRoute) {
            "schedule" -> 1
            "courses", "attendance", "tasks" -> 2
            else -> 0
        })
    }
    var librarySection by remember(initialDestination) { mutableIntStateOf(if (initialRoute == "tasks") 1 else 0) }
    var academicsTab by remember(initialDestination) { mutableIntStateOf(if (initialRoute == "attendance") 2 else 0) }
    var showProfileSheet by remember(initialDestination) { mutableStateOf(initialRoute == "more") }
    var detail by remember { mutableStateOf<DetailSelection?>(null) }
    if (showProfileSheet) {
        ModalBottomSheet(onDismissRequest = { showProfileSheet = false }) {
            MoreScreen(
                state,
                logout,
                setDeadlineNotifications,
                setExamNotifications,
                setSearchQuery,
                setQuietHours,
                selectSemester,
                setSyncHours,
                refreshDiagnostics,
                clearDownloads,
                clearAcademicCache,
            )
        }
    }
    BoxWithConstraints {
    val expanded = maxWidth >= 840.dp
    val showingHome = detail == null && selected == 0
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VioraTopBar(state, detail, { detail = null }, refresh, reauthenticate) { showProfileSheet = true }
        },
        bottomBar = {
            if (!expanded) VioraFloatingNavigationBar(selected, showingHome) { index ->
                selected = index
                if (index == 2) {
                    librarySection = 0
                    academicsTab = 0
                }
                detail = null
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (expanded) NavigationRail {
                destinations.forEachIndexed { index, destination ->
                    NavigationRailItem(
                        selected = selected == index,
                        onClick = {
                            selected = index
                            if (index == 2) {
                                librarySection = 0
                                academicsTab = 0
                            }
                            detail = null
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
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
                if (activeDetail != null) DetailScreen(state, activeDetail, openMaterial, downloadMaterial, downloadMaterials, uploadAssignment) else when (destination) {
                    0 -> HomeScreen(state = state, refresh = refresh, reauthenticate = reauthenticate, openDetail = { detail = it })
                    1 -> ScheduleScreen(
                        state = state,
                        shareTimetableQr = shareTimetableQr,
                        markClass = markClass,
                        showExam = { detail = DetailSelection("exam", it.id) },
                        exportToDeviceCalendar = exportToDeviceCalendar,
                        exportIcs = exportIcs,
                        importIcs = importIcs,
                        shareCalendarIcs = shareCalendarIcs,
                    )
                    else -> LibraryDestination(
                        state = state,
                        initialSection = librarySection,
                        initialAcademicsTab = academicsTab,
                        uploadAssignment = uploadAssignment,
                        showDetail = { kind, id -> detail = DetailSelection(kind, id) },
                    )
                }
            }
        }
    }
    }
}
}

@Composable
internal fun VioraFloatingNavigationBar(selected: Int, lightBackground: Boolean, onSelect: (Int) -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 16.dp) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 5.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                destinations.forEachIndexed { index, destination ->
                    val active = selected == index
                    Column(
                        Modifier.width(80.dp).height(56.dp).clip(RoundedCornerShape(24.dp))
                            .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .selectable(selected = active, onClick = { onSelect(index) }, role = Role.Tab)
                            .semantics { contentDescription = destination.label },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            destination.icon,
                            contentDescription = null,
                            tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                        Text(
                            destination.label,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
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
    openProfile: () -> Unit,
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
            IconButton(onClick = openProfile) {
                Icon(Icons.Outlined.AccountCircle, contentDescription = "Open profile and settings")
            }
        }
    }
}

@Composable
private fun LibraryDestination(
    state: VioraUiState,
    initialSection: Int,
    initialAcademicsTab: Int,
    uploadAssignment: (AssignmentUi) -> Unit,
    showDetail: (String, String) -> Unit,
) {
    var selectedSection by remember(initialSection) { mutableIntStateOf(initialSection.coerceIn(0, 1)) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedSection) {
            Tab(selected = selectedSection == 0, onClick = { selectedSection = 0 }, text = { Text("Academics") })
            Tab(selected = selectedSection == 1, onClick = { selectedSection = 1 }, text = { Text("Assessments") })
        }
        when (selectedSection) {
            0 -> AcademicsScreen(state, initialTab = initialAcademicsTab, showCourseDetail = showDetail)
            else -> AssessmentsScreen(
                state,
                uploadAssignment,
                { showDetail("assignment", it.id) },
                { showDetail("assessments-course", it) },
            )
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

internal fun emptyHomeCopy(now: ZonedDateTime): Pair<String, String> = when {
    now.dayOfWeek == DayOfWeek.FRIDAY && now.hour >= 18 ->
        "Friday night survived" to "Go to Tarama. Academic comeback resumes later."
    now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY ->
        "Weekend detected" to "Go outside. VTOP cannot hurt you here."
    else -> "Nothing coming up" to "Your fetched schedule is clear for now."
}

@Composable
internal fun CoursesScreen(
    state: VioraUiState,
    showDetail: (String, String) -> Unit,
) {
    val courses = state.consolidatedCourses()
    var query by remember { mutableStateOf("") }
    val visibleCourses = courses.filter { course ->
        query.isBlank() || listOf(
            course.code,
            course.title,
            course.type,
            course.faculty,
            course.materials.joinToString(" ") { "${it.title} ${it.fileName}" },
            course.messages.joinToString(" ") { "${it.subject} ${it.body}" },
        )
            .any { it.contains(query.trim(), ignoreCase = true) }
    }
    val groupedCourses = visibleCourses.groupBy { it.type.ifBlank { "Other" } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Library", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text("Consolidated courses, materials, marks and class messages.")
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                label = { Text("Search courses") },
                placeholder = { Text("Course code, name or faculty") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        groupedCourses.forEach { (type, rows) ->
            item("course-group:$type") {
                Text("${type} courses", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            }
            items(rows, key = ConsolidatedCourseUi::id) { course ->
                CourseCard(course) { showDetail("course", course.id) }
            }
        }
        if (courses.isEmpty()) item { Text("No courses have been cached yet.") }
        else if (visibleCourses.isEmpty()) item { Text("No courses match your search.") }
    }
}

@Composable
private fun CourseCard(course: ConsolidatedCourseUi, onClick: () -> Unit) {
    val palette = courseCardPalette(course.code)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Open ${course.code} ${course.type} course" },
        shape = RoundedCornerShape(28.dp),
        color = palette.accent,
        contentColor = palette.onAccent,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = palette.onAccent.copy(alpha = 0.10f),
                    contentColor = palette.onAccent,
                ) {
                    Text(
                        course.code,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("OPEN  ↗", style = MaterialTheme.typography.labelMedium)
            }
            if (course.type.isNotBlank()) {
                Text(course.type, style = MaterialTheme.typography.labelMedium, color = palette.onAccent.copy(alpha = 0.76f))
            }
            Text(
                course.title.takeIf { it.isNotBlank() && it != course.code } ?: course.code,
                style = MaterialTheme.typography.titleLarge,
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                course.attendance?.let { Text("${"%.1f".format(it.percentage)}% attendance", style = MaterialTheme.typography.labelLarge) }
                if (course.faculty.isNotBlank()) Text(course.faculty, style = MaterialTheme.typography.bodyMedium)
                Text(
                    listOf(
                        "${course.materials.size} ${if (course.materials.size == 1) "material" else "materials"}",
                        "${course.marks.size} ${if (course.marks.size == 1) "mark" else "marks"}",
                        "${course.messages.size} ${if (course.messages.size == 1) "message" else "messages"}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onAccent.copy(alpha = 0.76f),
                )
            }
        }
    }
}

internal data class ConsolidatedCourseUi(
    val id: String,
    val code: String,
    val title: String,
    val type: String,
    val faculty: String,
    val attendance: AttendanceUi?,
    val materials: List<app.viora.database.CourseMaterialEntity>,
    val marks: List<MarkUi>,
    val messages: List<app.viora.database.ClassMessageEntity>,
)

internal fun VioraUiState.consolidatedCourses(): List<ConsolidatedCourseUi> {
    val anchors = linkedMapOf<String, CourseAnchor>()
    slots.distinctBy { it.courseId }.forEach { slot ->
        anchors[slot.courseId] = CourseAnchor(slot.courseId, slot.code, slot.title, slot.type, slot.faculty, strictKind = true)
    }
    attendance.forEach { item ->
        val alreadyRepresented = anchors.values.any { anchor ->
            sameCourseCode(anchor.code, item.courseCode) &&
                compatibleCourseKind(item.courseType, anchor.kind, strict = true) &&
                anchor.faculty.facultyKey().let { it.isEmpty() || it == item.faculty.facultyKey() }
        }
        if (!alreadyRepresented) {
            anchors[item.id] = CourseAnchor(item.id, item.courseCode, item.courseTitle, item.courseType, item.faculty, strictKind = true)
        }
    }
    (assignments.map { it.courseCode } + grades.map { it.courseCode } + marks.map { it.courseCode } + materials.map { it.courseCode } + messages.map { it.courseCode })
        .filter(String::isNotBlank)
        .forEach { code ->
            if (anchors.values.none { sameCourseCode(it.code, code) }) {
                val grade = grades.firstOrNull { sameCourseCode(it.courseCode, code) }
                anchors[code] = CourseAnchor(code, code, grade?.courseTitle.orEmpty(), "", "", strictKind = false)
            }
        }
    return anchors.values.map { anchor ->
        val attendanceItem = attendance.firstOrNull { it.id == anchor.id }
            ?: attendance.firstOrNull { it.matchesCourse(anchor) }
        val title = attendanceItem?.courseTitle?.takeIf(String::isNotBlank)
            ?: anchor.title.takeIf(String::isNotBlank)
            ?: grades.firstOrNull { sameCourseCode(it.courseCode, anchor.code) }?.courseTitle.orEmpty()
        ConsolidatedCourseUi(
            id = anchor.id,
            code = anchor.code,
            title = title,
            type = anchor.type.takeIf(String::isNotBlank) ?: attendanceItem?.courseType.orEmpty(),
            faculty = attendanceItem?.faculty?.takeIf(String::isNotBlank) ?: anchor.faculty,
            attendance = attendanceItem,
            materials = materials.filter { it.matchesCourse(anchor) },
            marks = marks.filter { it.matchesCourse(anchor) },
            messages = messages.filter { it.matchesCourse(anchor) },
        )
    }.sortedWith(compareBy<ConsolidatedCourseUi> { it.code }.thenBy { courseKindOrder(it.type) }.thenBy { it.title })
}

private data class CourseAnchor(
    val id: String,
    val code: String,
    val title: String,
    val type: String,
    val faculty: String,
    val strictKind: Boolean,
) {
    val kind: AttendanceKind = attendanceKind(type)
}

private fun AttendanceUi.matchesCourse(anchor: CourseAnchor): Boolean =
    sameCourseCode(courseCode, anchor.code) &&
        compatibleCourseKind(courseType, anchor.kind, anchor.strictKind) &&
        compatibleFaculty(faculty, anchor.faculty)

private fun MarkUi.matchesCourse(anchor: CourseAnchor): Boolean =
    sameCourseCode(courseCode, anchor.code) && compatibleCourseKind("$courseCode $courseTitle $courseType", anchor.kind, anchor.strictKind)

private fun AssignmentUi.matchesCourse(anchor: CourseAnchor): Boolean =
    sameCourseCode(courseCode, anchor.code) && compatibleCourseKind("$courseCode $courseTitle", anchor.kind, anchor.strictKind)

private fun app.viora.database.CourseMaterialEntity.matchesCourse(anchor: CourseAnchor): Boolean =
    sameCourseCode(courseCode, anchor.code) && compatibleCourseKind("$courseCode $title $fileName", anchor.kind, anchor.strictKind)

private fun app.viora.database.ClassMessageEntity.matchesCourse(anchor: CourseAnchor): Boolean =
    sameCourseCode(courseCode, anchor.code) && compatibleFaculty(faculty, anchor.faculty)

private fun compatibleFaculty(candidate: String, selected: String): Boolean {
    val selectedKey = selected.facultyKey()
    return selectedKey.isEmpty() || candidate.facultyKey().isEmpty() || candidate.facultyKey() == selectedKey
}

private fun compatibleCourseKind(value: String, selected: AttendanceKind, strict: Boolean): Boolean {
    if (!strict || selected == AttendanceKind.UNKNOWN) return true
    val candidate = attendanceKind(value)
    return candidate == AttendanceKind.UNKNOWN || candidate == selected
}

private fun courseKindOrder(value: String): Int = when (attendanceKind(value)) {
    AttendanceKind.THEORY -> 0
    AttendanceKind.LAB -> 1
    AttendanceKind.PROJECT -> 2
    AttendanceKind.UNKNOWN -> 3
}

private fun VioraUiState.courseAnchorForSelection(id: String): CourseAnchor {
    slots.firstOrNull { it.courseId == id }?.let { slot ->
        return CourseAnchor(slot.courseId, slot.code, slot.title, slot.type, slot.faculty, strictKind = true)
    }
    attendance.firstOrNull { it.id == id }?.let { item ->
        return CourseAnchor(item.id, item.courseCode, item.courseTitle, item.courseType, item.faculty, strictKind = false)
    }
    consolidatedCourses().firstOrNull { it.id == id }?.let { course ->
        return CourseAnchor(course.id, course.code, course.title, course.type, course.faculty, strictKind = true)
    }
    val slot = slots.firstOrNull { sameCourseCode(it.code, id) }
    return CourseAnchor(
        id = id,
        code = slot?.code ?: id,
        title = slot?.title.orEmpty(),
        type = "",
        faculty = "",
        strictKind = false,
    )
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
internal fun AttendanceCard(item: AttendanceUi, ninePointRule: Boolean = false) {
    val skippableMeetings = if (item.blockSize > 1) item.skippableBlocks else item.skippable
    val healthy = ninePointRule || (item.recovery == 0 && skippableMeetings > 0)
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
                ninePointRule -> Text("9-point attendance rule applies", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                item.recovery > 0 -> Text(
                    "Attend next ${if (item.blockSize > 1) item.recoveryBlocks else item.recovery} ${if (item.blockSize > 1) "lab blocks" else "classes"} to recover",
                    color = VioraCoral,
                    style = MaterialTheme.typography.labelLarge,
                )
                skippableMeetings == 0 -> Text("At the target · don’t skip the next class", color = VioraAmber, style = MaterialTheme.typography.labelLarge)
                else -> Text(
                    if (item.blockSize > 1) "Safe to skip $skippableMeetings lab ${if (skippableMeetings == 1) "class" else "classes"}" else "Safe to skip $skippableMeetings ${if (skippableMeetings == 1) "class" else "classes"}",
                    color = VioraSuccess,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
internal fun ScheduleScreen(
    state: VioraUiState,
    shareTimetableQr: () -> Unit,
    markClass: (String, ClassCheckIn?) -> Unit,
    showExam: (ExamUi) -> Unit,
    exportToDeviceCalendar: () -> Unit = {},
    exportIcs: () -> Unit = {},
    importIcs: () -> Unit = {},
    shareCalendarIcs: () -> Unit = {},
    initialDate: LocalDate = LocalDate.now(academicZone),
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var confirmImport by remember { mutableStateOf(false) }
    val canExport = !state.loading && (state.slots.isNotEmpty() || state.exams.isNotEmpty() || state.assignments.any { it.dueEpochMillis != null })
    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Replace imported timetable?") },
            text = { Text("A valid ICS file will replace the timetable events previously imported into Viora. VTOP data is not changed.") },
            confirmButton = {
                TextButton(onClick = { confirmImport = false; importIcs() }) { Text("Choose ICS") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } },
        )
    }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Timeline") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Calendar") })
        }
        if (selectedTab == 1) {
            Column(Modifier.weight(1f)) {
                CalendarInterchangeActions(
                    canExport = canExport,
                    loading = state.loading,
                    message = state.calendarInterchangeMessage,
                    exportToDeviceCalendar = exportToDeviceCalendar,
                    exportIcs = exportIcs,
                    importIcs = { confirmImport = true },
                    shareCalendarIcs = shareCalendarIcs,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
                Box(Modifier.weight(1f)) { CalendarScreen(state, initialDate = initialDate) }
            }
            return@Column
        }
    val today = initialDate
    val now = LocalTime.now()
    val nowMinute = now.hour * 60 + now.minute
    var selectedDay by remember { mutableIntStateOf(today.dayOfWeek.value) }
    val selectedDate = today.plusDays(((selectedDay - today.dayOfWeek.value + 7) % 7).toLong())
    val daySlots = state.slots.filter { it.dayOfWeek == selectedDay }.sortedBy(SlotWithCourse::startMinute)
    val importedForDay = state.importedCalendarEvents
        .filter { Instant.ofEpochMilli(it.startsEpochMillis).atZone(academicZone).dayOfWeek.value == selectedDay }
        .distinctBy { event ->
            val start = Instant.ofEpochMilli(event.startsEpochMillis).atZone(academicZone).toLocalTime()
            val end = Instant.ofEpochMilli(event.endsEpochMillis).atZone(academicZone).toLocalTime()
            listOf(event.title, event.location, start.toString(), end.toString())
        }
        .sortedBy { it.startsEpochMillis }
    val visibleExams = state.exams.filter { shouldShowExamInSchedule(it.startsEpochMillis, it.endsEpochMillis, System.currentTimeMillis()) }
    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Timeline", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                    Text("Your complete weekly timetable and imported events", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = shareTimetableQr, enabled = state.slots.isNotEmpty() && !state.loading) { Icon(Icons.Outlined.Share, "Share timetable QR") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..7).forEach { day ->
                    val hasClasses = state.slots.any { it.dayOfWeek == day } || state.importedCalendarEvents.any {
                        Instant.ofEpochMilli(it.startsEpochMillis).atZone(academicZone).dayOfWeek.value == day
                    }
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
                    val entryCount = daySlots.size + importedForDay.size
                    Text("$entryCount ${if (entryCount == 1) "entry" else "entries"}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (daySlots.isEmpty() && importedForDay.isEmpty()) {
            item {
                EmptyStateCard("No classes", "This day has no cached timetable entries.")
            }
        } else {
            items(daySlots, key = SlotWithCourse::slotId) { slot ->
                val isToday = selectedDay == today.dayOfWeek.value
                val phase = if (isToday) classPhase(slot.startMinute, slot.endMinute, nowMinute) else ClassPhase.UPCOMING
                val key = classCheckInKey(selectedDate, slot.slotId)
                ClassCard(slot, state.attendanceFor(slot), phase, state.classCheckIns[key], if (selectedDate <= today && phase != ClassPhase.UPCOMING) key else null, markClass, ninePointRule = AttendanceNotificationPolicy.hasNinePointRule(state.cgpa))
            }
        }
        if (importedForDay.isNotEmpty()) {
            item { SectionLabel("IMPORTED TIMETABLE") }
            items(importedForDay, key = { "imported:${it.id}" }) { event ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Imported", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Text(event.title, style = MaterialTheme.typography.titleMedium)
                        val time = Instant.ofEpochMilli(event.startsEpochMillis).atZone(academicZone).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
                        Text(listOf(time, event.location).filter(String::isNotBlank).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (event.details.isNotBlank()) Text(event.details)
                    }
                }
            }
        }
        item {
            CalendarInterchangeActions(
                canExport = canExport,
                loading = state.loading,
                message = state.calendarInterchangeMessage,
                exportToDeviceCalendar = exportToDeviceCalendar,
                exportIcs = exportIcs,
                importIcs = { confirmImport = true },
                shareCalendarIcs = shareCalendarIcs,
            )
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
}

@Composable
private fun CalendarInterchangeActions(
    canExport: Boolean,
    loading: Boolean,
    message: String?,
    exportToDeviceCalendar: () -> Unit,
    exportIcs: () -> Unit,
    importIcs: () -> Unit,
    shareCalendarIcs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = exportToDeviceCalendar, enabled = canExport, modifier = Modifier.fillMaxWidth()) { Text("Export to Viora calendar") }
        OutlinedButton(onClick = exportIcs, enabled = canExport, modifier = Modifier.fillMaxWidth()) { Text("Export ICS") }
        OutlinedButton(onClick = importIcs, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Import ICS") }
        OutlinedButton(onClick = shareCalendarIcs, enabled = canExport, modifier = Modifier.fillMaxWidth()) { Text("Share timetable") }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
        }
    }
}

@Composable
internal fun AssessmentsScreen(
    state: VioraUiState,
    uploadAssignment: (AssignmentUi) -> Unit,
    showAssignment: (AssignmentUi) -> Unit,
    showCourse: (String) -> Unit,
    nowEpochMillis: Long = System.currentTimeMillis(),
) {
    val dueThisWeek = state.assessmentsDueThisWeek(nowEpochMillis)
    val courseGroups = state.assessmentCourseGroups()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Assessments", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
        item { Text("Due this week", style = MaterialTheme.typography.titleLarge) }
        items(dueThisWeek, key = { "due:${it.id}" }) { assignment ->
            AssessmentCard(assignment, Modifier.clickable { showAssignment(assignment) })
        }
        if (dueThisWeek.isEmpty()) item { Text("No assessments are due in the next seven days.") }
        item { Text("By course", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
        items(courseGroups, key = AssessmentCourseGroup::courseCode) { group ->
            AssessmentCourseCard(group) { showCourse(group.courseCode) }
        }
        if (courseGroups.isEmpty()) item { Text("No digital assignments are cached.") }
    }
}

@Composable
private fun AssessmentCourseCard(group: AssessmentCourseGroup, onClick: () -> Unit) {
    val palette = courseCardPalette(group.courseCode)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = palette.accent,
        contentColor = palette.onAccent,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(group.courseCode, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("VIEW  ↗", style = MaterialTheme.typography.labelMedium)
            }
            Text(group.courseTitle.ifBlank { group.courseCode }, style = MaterialTheme.typography.titleLarge)
            Text(
                "${group.assignments.size} ${if (group.assignments.size == 1) "assessment" else "assessments"}",
                color = palette.onAccent.copy(alpha = 0.76f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AssessmentCard(assignment: AssignmentUi, modifier: Modifier = Modifier) {
    val submitted = isAssignmentSubmitted(assignment.status, assignment.lastUpload)
    val palette = courseCardPalette(assignment.courseCode)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = palette.container,
        contentColor = Color(0xFFF8F7FA),
        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.42f)),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(assignment.title, style = MaterialTheme.typography.titleMedium)
            Text(assignment.courseLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Due ${assignment.dueEpochMillis.asAcademicTime("date unavailable")}")
            Text(if (submitted) "Submitted" else "Pending", color = if (submitted) Color(0xFF75D9B2) else Color(0xFFFF9B8E), fontWeight = FontWeight.Bold)
            if (assignment.lastUpload.isNotBlank() && !assignment.lastUpload.equals("N/A", true)) Text("Last upload · ${assignment.lastUpload}")
        }
    }
}

@Composable
private fun ExpandableAssessmentCard(
    assignment: AssignmentUi,
    number: Int,
    expanded: Boolean,
    state: VioraUiState,
    uploadAssignment: (AssignmentUi) -> Unit,
    onToggle: () -> Unit,
) {
    val palette = courseCardPalette(assignment.courseCode)
    val submitted = isAssignmentSubmitted(assignment.status, assignment.lastUpload)
    Surface(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
        shape = RoundedCornerShape(26.dp),
        color = palette.container,
        contentColor = Color(0xFFF8F7FA),
        border = BorderStroke(1.dp, palette.accent.copy(alpha = if (expanded) 0.72f else 0.34f)),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = palette.accent, contentColor = palette.onAccent) {
                    Text(
                        number.toString().padStart(2, '0'),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(assignment.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Due ${assignment.dueEpochMillis.asAcademicTime("date unavailable")}",
                        color = Color(0xFFBDB9C6),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Icon(
                    if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse assessment" else "Expand assessment",
                    tint = palette.accent,
                )
            }
            Text(
                if (submitted) "SUBMITTED" else "PENDING",
                color = if (submitted) Color(0xFF75D9B2) else Color(0xFFFF9B8E),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = palette.accent.copy(alpha = 0.24f))
                    Text(assignment.courseLabel(), color = Color(0xFFD8D4DF), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        assignment.status.takeIf(String::isNotBlank)?.let { "VTOP status · $it" } ?: "VTOP status unavailable",
                        color = Color(0xFFAAA5B3),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (assignment.lastUpload.isNotBlank() && !assignment.lastUpload.equals("N/A", true)) {
                        Text("Last upload · ${assignment.lastUpload}", color = Color(0xFFAAA5B3), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (assignment.dueEpochMillis?.let { it > System.currentTimeMillis() } == true) {
                        AssignmentUploadAction(assignment, submitted, state, uploadAssignment)
                    }
                }
            }
        }
    }
}

private data class CourseCardPalette(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
)

private fun courseCardPalette(courseCode: String): CourseCardPalette {
    val palettes = listOf(
        CourseCardPalette(Color(0xFF61D5BD), Color(0xFF082D28), Color(0xFF153430)),
        CourseCardPalette(Color(0xFF66C7F0), Color(0xFF092C3B), Color(0xFF142F3A)),
        CourseCardPalette(Color(0xFFFFC45B), Color(0xFF352300), Color(0xFF3A2D17)),
        CourseCardPalette(Color(0xFFFF8E7D), Color(0xFF3C120D), Color(0xFF3B2421)),
        CourseCardPalette(Color(0xFFB9A7FF), Color(0xFF24174D), Color(0xFF2D2942)),
    )
    return palettes[Math.floorMod(courseCode.filter(Char::isLetterOrDigit).uppercase(Locale.ENGLISH).hashCode(), palettes.size)]
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
    ninePointRule: Boolean = false,
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
                attendance?.let { AttendanceGuidance(it, ninePointRule) }
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
private fun AttendanceGuidance(attendance: AttendanceUi, ninePointRule: Boolean = false) {
    if (ninePointRule) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Spacer(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Text("${"%.0f".format(attendance.percentage)}% · 9-point attendance rule applies", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        return
    }
    val projection = AttendanceCalculator.calculate(attendance.attended, attendance.held, 75, attendance.blockSize)
    val skippableMeetings = if (attendance.blockSize > 1) projection.skippableBlocks else projection.skippableClasses
    val (text, color) = when {
        projection.classesToRecover > 0 -> "Attend next ${if (attendance.blockSize > 1) projection.blocksToRecover else projection.classesToRecover} to reach 75%" to VioraCoral
        skippableMeetings > 0 -> {
            val unit = when {
                attendance.blockSize > 1 && skippableMeetings == 1 -> "lab class"
                attendance.blockSize > 1 -> "lab classes"
                skippableMeetings == 1 -> "class"
                else -> "classes"
            }
            "Can skip $skippableMeetings $unit safely" to VioraSuccess
        }
        else -> "At 75% limit · attend this one" to VioraAmber
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Spacer(Modifier.size(7.dp).background(color, CircleShape))
        Text("${"%.0f".format(attendance.percentage)}% · $text", color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun DetailScreen(
    state: VioraUiState,
    selection: DetailSelection,
    openMaterial: (app.viora.database.CourseMaterialEntity, Boolean) -> Unit,
    downloadMaterial: (app.viora.database.CourseMaterialEntity) -> Unit = {},
    downloadMaterials: (List<app.viora.database.CourseMaterialEntity>) -> Unit = {},
    uploadAssignment: (AssignmentUi) -> Unit = {},
) {
    var materialQuery by remember(selection.kind, selection.id) { mutableStateOf("") }
    var expandedAssessmentId by remember(selection.kind, selection.id) { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (selection.kind) {
            "course" -> {
                val anchor = state.courseAnchorForSelection(selection.id)
                val attendance = state.attendance.firstOrNull { it.id == selection.id } ?: state.attendance.firstOrNull { it.matchesCourse(anchor) }
                val code = anchor.code
                val title = attendance?.courseTitle?.takeIf(String::isNotBlank)
                    ?: anchor.title.takeIf(String::isNotBlank)
                    ?: state.slots.firstOrNull { sameCourseCode(it.code, code) }?.title
                    ?: code
                item { Text(title, style = MaterialTheme.typography.headlineMedium) }
                attendance?.let { item { AttendanceCard(it, AttendanceNotificationPolicy.hasNinePointRule(state.cgpa)) } }
                val assignments = state.assignments.filter { it.matchesCourse(anchor) }
                val materials = state.materials.filter { it.matchesCourse(anchor) }
                val visibleMaterials = materials.filter {
                    materialQuery.isBlank() || "${it.title} ${it.fileName}".contains(materialQuery.trim(), true)
                }
                item { Text("Course materials", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
                if (materials.isEmpty()) item { Text("No materials are cached for this course yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else {
                    item {
                        OutlinedTextField(
                            value = materialQuery,
                            onValueChange = { materialQuery = it.take(100) },
                            label = { Text("Search materials") },
                            placeholder = { Text("Module, topic or filename") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Button(
                            onClick = { downloadMaterials(visibleMaterials) },
                            enabled = visibleMaterials.isNotEmpty() && state.downloads.values.none { it.status == "DOWNLOADING" },
                        ) { Text(if (materialQuery.isBlank()) "Download all materials" else "Download search results") }
                    }
                    visibleMaterials.forEach { material ->
                        item("material:${material.id}") { CourseMaterialActions(material, state, openMaterial, downloadMaterial) }
                    }
                    if (visibleMaterials.isEmpty()) item { Text("No materials match your search.") }
                }
                item { Text("Class schedule", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
                state.slots.filter { slot ->
                    sameCourseCode(slot.code, code) &&
                        compatibleCourseKind(slot.type, anchor.kind, anchor.strictKind) &&
                        compatibleFaculty(slot.faculty, anchor.faculty)
                }.forEach { slot -> item("slot:${slot.slotId}") { ClassCard(slot) } }
                val marks = state.marks.filter { it.matchesCourse(anchor) }
                item { Text("Marks", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
                if (marks.isEmpty()) item { Text("No marks are cached for this course yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                marks.forEach { mark ->
                    item("mark:${mark.id}") {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) { MarkDetails(mark) }
                        }
                    }
                }
                state.grades.filter { sameCourseCode(it.courseCode, code) }.forEach { grade -> item("grade:${grade.courseCode}") { SummaryCard("Grade", grade.grade, grade.total?.let { "${it.cleanNumber()}/100" } ?: "") } }
                val messages = state.messages.filter { it.matchesCourse(anchor) }
                item { Text("Class messages", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
                if (messages.isEmpty()) item { Text("No class messages are cached for this course.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                messages.forEach { message ->
                    item("message:${message.id}") {
                        ClassMessageCard(message)
                    }
                }
                item { Text("Digital assignments", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
                if (assignments.isEmpty()) item { Text("No digital assignments are cached for this course.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                assignments.forEachIndexed { index, assignment ->
                    item("assignment:${assignment.id}") {
                        ExpandableAssessmentCard(
                            assignment = assignment,
                            number = index + 1,
                            expanded = expandedAssessmentId == assignment.id,
                            state = state,
                            uploadAssignment = uploadAssignment,
                            onToggle = {
                                expandedAssessmentId = assignment.id.takeUnless { expandedAssessmentId == assignment.id }
                            },
                        )
                    }
                }
            }
            "assignment" -> state.assignments.firstOrNull { it.id == selection.id }?.let { assignment ->
                item { Text(assignment.title, style = MaterialTheme.typography.headlineMedium) }
                item { SummaryCard(assignment.courseLabel(), assignment.status.ifBlank { "Status unavailable" }, assignment.dueEpochMillis.asAcademicTime("Due time unavailable")) }
                val submitted = isAssignmentSubmitted(assignment.status, assignment.lastUpload)
                val beforeDeadline = assignment.dueEpochMillis?.let { it > System.currentTimeMillis() } == true
                if (beforeDeadline) item { AssignmentUploadAction(assignment, submitted, state, uploadAssignment) }
            }
            "assessments-course" -> {
                val assignments = state.assignments.filter { sameCourseCode(it.courseCode, selection.id) }.orderedByDueDate()
                val title = assignments.firstOrNull()?.courseTitle?.takeIf(String::isNotBlank) ?: selection.id
                item { Text(title, style = MaterialTheme.typography.headlineMedium) }
                item { Text("Assessments", style = MaterialTheme.typography.titleLarge) }
                assignments.forEachIndexed { index, assignment ->
                    item("assessment-course:${assignment.id}") {
                        ExpandableAssessmentCard(
                            assignment = assignment,
                            number = index + 1,
                            expanded = expandedAssessmentId == assignment.id,
                            state = state,
                            uploadAssignment = uploadAssignment,
                            onToggle = {
                                expandedAssessmentId = assignment.id.takeUnless { expandedAssessmentId == assignment.id }
                            },
                        )
                    }
                }
            }
            "exam" -> state.exams.firstOrNull { it.id == selection.id }?.let { exam -> item { ExamCard(exam) } }
            "material" -> state.materials.firstOrNull { it.id == selection.id }?.let { material -> item { MaterialDetailCard(material, state, openMaterial) } }
        }
    }
}

@Composable
private fun ClassMessageCard(message: app.viora.database.ClassMessageEntity) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(message.subject.ifBlank { "Class message" }, style = MaterialTheme.typography.titleMedium)
            Text(message.body)
            listOf(message.faculty, message.postedEpochMillis?.asAcademicTime()).filterNotNull()
                .filter(String::isNotBlank)
                .joinToString(" · ")
                .takeIf(String::isNotBlank)
                ?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun AssignmentUploadAction(
    assignment: AssignmentUi,
    submitted: Boolean,
    state: VioraUiState,
    uploadAssignment: (AssignmentUi) -> Unit,
) {
    Button(onClick = { uploadAssignment(assignment) }, enabled = state.uploadingAssignmentId == null) {
        Text(if (state.uploadingAssignmentId == assignment.id) "Uploading…" else if (submitted) "Replace submission" else "Submit file")
    }
}

@Composable private fun AssignmentCard(assignment: AssignmentUi) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(assignment.title, style = MaterialTheme.typography.titleMedium)
        Text(assignment.courseLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
internal val academicZone = ZoneId.of("Asia/Kolkata")

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
                state.assignments.filter { "${it.courseCode} ${it.courseTitle} ${it.title} ${it.status}".contains(q, true) }.forEach { add("Assignment" to "${it.courseLabel()} · ${it.title}") }
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
        item { SummaryCard("Downloaded materials", state.downloadStorageBytes.readableBytes(), "Stored privately in Viora/materials/<course name>") }
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

internal fun VioraUiState.attendanceFor(slot: SlotWithCourse): AttendanceUi? {
    val codeMatches = attendance.filter { sameCourseCode(it.courseCode, slot.code) }
    val candidates = codeMatches.ifEmpty {
        attendance.filter {
            it.courseTitle.isNotBlank() && slot.title.isNotBlank() &&
                it.courseTitle.equals(slot.title, ignoreCase = true)
        }
    }
    if (candidates.isEmpty()) return null

    val slotKind = attendanceKind(slot.type)
    if (slotKind == AttendanceKind.UNKNOWN) return candidates.first()
    val compatible = candidates.filter { attendanceKind(it.courseType) == slotKind }
        .ifEmpty { candidates.filter { attendanceKind(it.courseType) == AttendanceKind.UNKNOWN } }
    if (compatible.isEmpty()) return null

    val slotFaculty = slot.faculty.facultyKey()
    return compatible.firstOrNull { slotFaculty.isNotEmpty() && it.faculty.facultyKey() == slotFaculty }
        ?: compatible.first()
}

internal enum class AttendanceKind { THEORY, LAB, PROJECT, UNKNOWN }

internal fun attendanceKind(value: String): AttendanceKind {
    val tokens = Regex("[A-Z]+").findAll(value.uppercase(Locale.ENGLISH)).map { it.value }.toSet()
    return when {
        tokens.any { it in setOf("LAB", "ELA", "ELP", "LO") } -> AttendanceKind.LAB
        tokens.any { it in setOf("PROJECT", "EPR", "PJT") } -> AttendanceKind.PROJECT
        tokens.any { it in setOf("THEORY", "LECTURE", "ETH", "ETL") } -> AttendanceKind.THEORY
        else -> AttendanceKind.UNKNOWN
    }
}

private fun String.facultyKey(): String = lowercase().filter(Char::isLetterOrDigit)

private fun VioraUiState.courseNameFor(slot: SlotWithCourse): String {
    val candidates = listOfNotNull(
        attendanceFor(slot)?.courseTitle,
        grades.firstOrNull { sameCourseCode(it.courseCode, slot.code) }?.courseTitle,
        slot.title,
    )
    return candidates.firstOrNull { name ->
        name.isNotBlank() && !name.filter(Char::isLetterOrDigit)
            .equals(slot.code.filter(Char::isLetterOrDigit), true)
    } ?: slot.title.takeIf(String::isNotBlank) ?: slot.code
}

internal fun VioraUiState.slotsForDate(date: LocalDate): List<SlotWithCourse> {
    if (examSuppressionWindows().any { it.suppresses(date) }) return emptyList()
    val descriptions = calendar
        .filter { it.dateEpochDay == date.toEpochDay() }
        .map { "${it.title} ${it.dayType}" }
    if (descriptions.any { it.contains("holiday", true) }) return emptyList()
    val order = DayOfWeek.entries.firstOrNull { day ->
        descriptions.any { it.contains("${day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} order", true) }
    }
    val examsToday = examsForDate(date)
    return slots.filter { slot ->
        slot.dayOfWeek == (order ?: date.dayOfWeek).value && examsToday.none { exam ->
            overlapsExam(slot.startMinute, slot.endMinute, exam.startMinute(), exam.endMinute())
        }
    }
}

internal fun VioraUiState.examSuppressionWindows(): List<ExamSuppressionWindow> = buildExamSuppressionWindows(
    exams = exams.map { ExamWindow(it.startsEpochMillis, it.endsEpochMillis, it.examType) },
    calendar = calendar.map { ExamCalendarDate(LocalDate.ofEpochDay(it.dateEpochDay), it.title, it.dayType) },
    zone = academicZone,
)

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
    val examDates = examSuppressionWindows().any { it.suppresses(now.toLocalDate()) }
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
    }.orderedByDueDate()
}

internal fun VioraUiState.assessmentsDueThisWeek(nowEpochMillis: Long, lookAheadDays: Long = 7): List<AssignmentUi> {
    val horizon = nowEpochMillis + lookAheadDays * 24 * 60 * 60 * 1000
    return assignments.filter { assignment ->
        assignment.dueEpochMillis?.let { it in (nowEpochMillis + 1)..horizon } == true
    }.orderedByDueDate()
}

internal data class AssessmentCourseGroup(
    val courseCode: String,
    val courseTitle: String,
    val assignments: List<AssignmentUi>,
)

internal fun VioraUiState.assessmentCourseGroups(): List<AssessmentCourseGroup> {
    val groups = mutableListOf<MutableList<AssignmentUi>>()
    assignments.orderedByDueDate().forEach { assignment ->
        groups.firstOrNull { sameCourseCode(it.first().courseCode, assignment.courseCode) }?.add(assignment)
            ?: groups.add(mutableListOf(assignment))
    }
    return groups.map { rows ->
        val first = rows.first()
        AssessmentCourseGroup(first.courseCode.filter(Char::isLetterOrDigit), first.courseTitle, rows.toList())
    }.sortedBy { it.courseTitle.ifBlank { it.courseCode }.lowercase(Locale.ENGLISH) }
}

internal fun List<AssignmentUi>.orderedByDueDate(): List<AssignmentUi> =
    sortedWith(
        compareBy<AssignmentUi> { it.dueEpochMillis == null }
            .thenBy { it.dueEpochMillis ?: Long.MAX_VALUE }
            .thenBy { it.courseTitle.ifBlank { it.courseCode }.lowercase(Locale.ENGLISH) }
            .thenBy { it.title.lowercase(Locale.ENGLISH) },
    )

private fun AssignmentUi.courseLabel(): String =
    courseTitle.trim().takeIf(String::isNotBlank)?.let { "$courseCode · $it" } ?: courseCode
