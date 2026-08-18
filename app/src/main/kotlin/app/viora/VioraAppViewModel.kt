package app.viora

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.viora.auth.SessionResolution
import app.viora.database.SlotWithCourse
import app.viora.database.SyncResourceEntity
import app.viora.database.AcademicCalendarEntity
import app.viora.database.ClassMessageEntity
import app.viora.database.CourseMaterialEntity
import app.viora.database.AcademicChangeEntity
import app.viora.database.SemesterEntity
import app.viora.data.MaterialDownloadState
import app.viora.domain.AttendanceCalculator
import app.viora.domain.SemesterRollover
import app.viora.network.SemesterOption
import app.viora.network.SessionState
import app.viora.network.VtopWebSession
import app.viora.network.AuthenticationException
import app.viora.sync.SyncOutcome
import app.viora.sync.VioraSyncScheduler
import app.viora.sync.SyncDiagnosticsSnapshot
import app.viora.widget.NextClassWidgetProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.net.UnknownHostException
import java.net.SocketTimeoutException

data class VioraUiState(
    val configured: Boolean = false,
    val reauthRequired: Boolean = false,
    val username: String = "",
    val password: String = "",
    val rememberLogin: Boolean = true,
    val loading: Boolean = false,
    val syncMessage: String? = null,
    val error: String? = null,
    val assignmentUploadSession: VtopWebSession? = null,
    val semesters: List<SemesterOption> = emptyList(),
    val activeSemester: SemesterOption? = null,
    val slots: List<SlotWithCourse> = emptyList(),
    val attendance: List<AttendanceUi> = emptyList(),
    val assignments: List<AssignmentUi> = emptyList(),
    val exams: List<ExamUi> = emptyList(),
    val marks: List<MarkUi> = emptyList(),
    val grades: List<GradeUi> = emptyList(),
    val gpa: Double? = null,
    val cgpa: Double? = null,
    val registeredCredits: Double? = null,
    val earnedCredits: Double? = null,
    val syncResources: List<SyncResourceEntity> = emptyList(),
    val calendar: List<AcademicCalendarEntity> = emptyList(),
    val messages: List<ClassMessageEntity> = emptyList(),
    val materials: List<CourseMaterialEntity> = emptyList(),
    val deadlineNotifications: Boolean = true,
    val examNotifications: Boolean = true,
    val interactiveVerification: Boolean = false,
    val attendanceTarget: Int = 75,
    val plannedMissedBlocks: Int = 0,
    val searchQuery: String = "",
    val quietHours: Boolean = true,
    val recentChanges: List<AcademicChangeEntity> = emptyList(),
    val downloads: Map<String, MaterialDownloadState> = emptyMap(),
    val downloadStorageBytes: Long = 0,
    val syncHours: Int = 6,
    val cachedSemesters: List<SemesterEntity> = emptyList(),
    val rolloverDetected: Boolean = false,
    val syncDiagnostics: SyncDiagnosticsSnapshot? = null,
    val classCheckIns: Map<String, ClassCheckIn> = emptyMap(),
)
enum class ClassCheckIn { ATTENDED, MISSED }
data class MarkUi(val id: String, val courseTitle: String, val title: String, val scoredMark: Double?, val maxMarks: Double?, val weightageMark: Double?, val status: String)
data class GradeUi(val courseCode: String, val courseTitle: String, val credits: Double?, val total: Double?, val grade: String)

data class AttendanceUi(
    val id: String,
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val faculty: String,
    val attended: Int,
    val sourceHeld: Int,
    val held: Int,
    val percentage: Double,
    val skippable: Int,
    val recovery: Int,
    val blockSize: Int,
    val skippableBlocks: Int,
    val recoveryBlocks: Int,
)

data class AssignmentUi(
    val id: String,
    val courseCode: String,
    val title: String,
    val dueEpochMillis: Long?,
    val status: String,
    val lastUpload: String = "",
    val courseTitle: String = "",
)

data class ExamUi(
    val id: String,
    val courseCode: String,
    val courseTitle: String,
    val examType: String,
    val startsEpochMillis: Long,
    val endsEpochMillis: Long?,
    val venue: String,
    val seatNumber: String,
)

class VioraAppViewModel(
    private val graph: VioraGraph,
    private val scheduler: VioraSyncScheduler,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        VioraUiState(configured = graph.settings.getBoolean(VioraGraph.KEY_CONFIGURED, false), deadlineNotifications = graph.settings.getBoolean("notify_deadlines", true), examNotifications = graph.settings.getBoolean("notify_exams", true), attendanceTarget = ATTENDANCE_TARGET, quietHours = graph.settings.getBoolean("quiet_hours", true), downloadStorageBytes = graph.materialManager.storageBytes(), syncHours = graph.settings.getInt("sync_hours", 6), classCheckIns = loadClassCheckIns()),
    )
    val state: StateFlow<VioraUiState> = mutableState.asStateFlow()
    private var timetableObservation: Job? = null
    private var attendanceObservation: Job? = null
    private var assignmentObservation: Job? = null
    private var examObservation: Job? = null
    private var resultsObservation: Job? = null
    private var extrasObservation: Job? = null

    init {
        graph.settings.edit().putInt("attendance_target", ATTENDANCE_TARGET).apply()
        viewModelScope.launch { graph.database.academicDao().observeSyncResources().collect { resources -> mutableState.update { it.copy(syncResources = resources) } } }
        viewModelScope.launch { graph.database.academicDao().observeChanges().collect { changes -> mutableState.update { it.copy(recentChanges = changes) } } }
        viewModelScope.launch { graph.materialManager.states.collect { downloads -> mutableState.update { it.copy(downloads = downloads, downloadStorageBytes = graph.materialManager.storageBytes()) } } }
        viewModelScope.launch { graph.database.academicDao().observeSemesters().collect { semesters -> mutableState.update { it.copy(cachedSemesters = semesters) } } }
        refreshDiagnostics()
        if (state.value.configured) {
            observeSavedSemester()
            restoreSessionAndLoad()
        }
    }

    fun updateUsername(value: String) = mutableState.update { it.copy(username = value, error = null) }
    fun updatePassword(value: String) = mutableState.update { it.copy(password = value, error = null) }
    fun updateRememberLogin(value: Boolean) = mutableState.update { it.copy(rememberLogin = value) }

    fun signIn() {
        val snapshot = state.value
        if (snapshot.username.isBlank() || snapshot.password.isBlank()) {
            mutableState.update { it.copy(error = "Enter your VTOP username and password") }
            return
        }
        mutableState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val password = snapshot.password.toCharArray()
            try {
                when (graph.gateway.login(snapshot.username.trim(), password)) {
                    SessionState.Active -> {
                        if (snapshot.rememberLogin) graph.credentials.save(snapshot.username.trim(), password)
                        else graph.credentials.clear()
                        graph.settings.edit().putBoolean(VioraGraph.KEY_CONFIGURED, true).commit()
                        mutableState.update {
                            it.copy(configured = true, reauthRequired = false, password = "", loading = false)
                        }
                        loadSemestersAndRefresh()
                    }
                    SessionState.VerificationRequired -> {
                        if (snapshot.rememberLogin) graph.credentials.save(snapshot.username.trim(), password)
                        mutableState.update { it.copy(loading = false, interactiveVerification = true, error = null) }
                    }
                    SessionState.Missing -> mutableState.update {
                        it.copy(loading = false, error = "VTOP rejected the sign-in details")
                    }
                }
            } catch (error: Exception) {
                val message = when (error) { is UnknownHostException -> "VTOP could not be reached. Check your connection."; is SocketTimeoutException -> "VTOP took too long to respond. Try again."; else -> "Could not connect to VTOP (${error.message?.take(80) ?: "unknown error"})" }
                mutableState.update { it.copy(loading = false, error = message) }
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun refresh() {
        val semester = state.value.activeSemester
        mutableState.update { it.copy(loading = true, syncMessage = "Syncing with VTOP", error = null) }
        viewModelScope.launch {
            if (semester == null) loadSemestersAndRefresh() else refreshSemester(semester)
        }
    }

    fun beginReauthentication() = mutableState.update {
        it.copy(configured = false, loading = false, password = "", error = null)
    }
    fun logout() { viewModelScope.launch { graph.account.eraseVioraAccount(); mutableState.value = VioraUiState() } }
    fun setDeadlineNotifications(enabled: Boolean) {
        graph.settings.edit().putBoolean("notify_deadlines", enabled).apply()
        mutableState.update { it.copy(deadlineNotifications = enabled) }
        state.value.activeSemester?.let { semester -> viewModelScope.launch { graph.reminders.schedule(semester.id) } }
    }
    fun setExamNotifications(enabled: Boolean) {
        graph.settings.edit().putBoolean("notify_exams", enabled).apply()
        mutableState.update { it.copy(examNotifications = enabled) }
        state.value.activeSemester?.let { semester -> viewModelScope.launch { graph.reminders.schedule(semester.id) } }
    }
    fun setAttendanceTarget(target: Int) { mutableState.update { state -> state.copy(attendanceTarget = ATTENDANCE_TARGET, attendance = state.attendance.reproject(ATTENDANCE_TARGET, state.plannedMissedBlocks)) } }
    fun setPlannedMissedBlocks(blocks: Int) = mutableState.update { state -> state.copy(plannedMissedBlocks = blocks.coerceIn(0, 10), attendance = state.attendance.reproject(state.attendanceTarget, blocks.coerceIn(0, 10))) }
    fun setSearchQuery(query: String) = mutableState.update { it.copy(searchQuery = query.take(80)) }
    fun setQuietHours(enabled: Boolean) { graph.settings.edit().putBoolean("quiet_hours", enabled).apply(); mutableState.update { it.copy(quietHours = enabled) } }
    fun beginAssignmentUpload() {
        mutableState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                check(graph.sessions.ensureActive() == SessionResolution.Ready) { "Sign in again before uploading" }
                val semester = requireNotNull(state.value.activeSemester) { "Select a semester before uploading" }
                graph.gateway.digitalAssignmentUploadSession(semester.id)
            }.onSuccess { session -> mutableState.update { it.copy(loading = false, assignmentUploadSession = session) } }
                .onFailure { failure -> mutableState.update { it.copy(loading = false, error = failure.message ?: "Could not open VTOP assignment upload") } }
        }
    }
    fun closeAssignmentUpload(cookieHeader: String?) {
        mutableState.update { it.copy(assignmentUploadSession = null) }
        viewModelScope.launch {
            if (!cookieHeader.isNullOrBlank()) graph.gateway.importInteractiveSession(cookieHeader)
            state.value.activeSemester?.let { semester ->
                graph.assignments.refresh(semester.id)
                graph.reminders.schedule(semester.id)
            }
        }
    }
    fun setSyncHours(hours: Int) { val safe = hours.coerceIn(1, 24); graph.settings.edit().putInt("sync_hours", safe).apply(); scheduler.schedule(safe.toLong()); mutableState.update { it.copy(syncHours = safe) }; refreshDiagnostics() }
    fun refreshDiagnostics() { viewModelScope.launch { mutableState.update { it.copy(syncDiagnostics = graph.syncDiagnostics.snapshot()) } } }
    fun markClass(key: String, mark: ClassCheckIn?) {
        require(key.startsWith(CLASS_CHECK_IN_PREFIX)) { "Invalid class check-in key" }
        val updated = state.value.classCheckIns.toMutableMap()
        if (mark == null) updated.remove(key) else updated[key] = mark
        graph.settings.edit().apply {
            if (mark == null) remove(key) else putString(key, mark.name)
        }.apply()
        mutableState.update { it.copy(classCheckIns = updated) }
    }
    fun clearDownloads() { viewModelScope.launch { graph.materialManager.clearDownloads(); mutableState.update { it.copy(downloadStorageBytes = 0, downloads = emptyMap(), syncMessage = "Downloaded materials cleared") } } }
    fun clearAcademicCache() { viewModelScope.launch { withContext(Dispatchers.IO) { graph.database.clearAllTables() }; mutableState.update { it.copy(slots = emptyList(), attendance = emptyList(), assignments = emptyList(), exams = emptyList(), marks = emptyList(), grades = emptyList(), calendar = emptyList(), messages = emptyList(), materials = emptyList(), recentChanges = emptyList(), syncMessage = "Academic cache cleared") } } }
    fun completeInteractiveVerification(cookieHeader: String) {
        mutableState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (runCatching { graph.gateway.importInteractiveSession(cookieHeader) }.getOrNull()) {
                SessionState.Active -> {
                    graph.settings.edit().putBoolean(VioraGraph.KEY_CONFIGURED, true).commit()
                    mutableState.update { it.copy(configured = true, interactiveVerification = false, loading = false, password = "") }
                    loadSemestersAndRefresh()
                }
                else -> mutableState.update { it.copy(loading = false, error = "Verification did not create a VTOP session yet") }
            }
        }
    }
    fun cancelInteractiveVerification() = mutableState.update { it.copy(interactiveVerification = false, loading = false) }
    fun interactiveVerificationError(message: String) = mutableState.update { it.copy(loading = false, error = message) }
    fun openMaterial(material: CourseMaterialEntity, share: Boolean = false) {
        mutableState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch { graph.materialManager.open(material, courseName(material), share).onFailure { mutableState.update { state -> state.copy(error = "Could not download or open that material") } }; mutableState.update { it.copy(loading = false) } }
    }
    fun downloadMaterial(material: CourseMaterialEntity) {
        viewModelScope.launch { graph.materialManager.download(material, courseName(material)).onFailure { mutableState.update { state -> state.copy(error = "Could not download that material") } } }
    }
    fun downloadMaterials(materials: List<CourseMaterialEntity>) {
        viewModelScope.launch {
            val failures = materials.count { graph.materialManager.download(it, courseName(it)).isFailure }
            if (failures > 0) mutableState.update { it.copy(error = "$failures material download(s) failed") }
        }
    }
    private fun courseName(material: CourseMaterialEntity): String {
        val snapshot = state.value
        return listOfNotNull(
            snapshot.attendance.firstOrNull { app.viora.domain.sameCourseCode(it.courseCode, material.courseCode) }?.courseTitle,
            snapshot.slots.firstOrNull { app.viora.domain.sameCourseCode(it.code, material.courseCode) }?.title,
            snapshot.grades.firstOrNull { app.viora.domain.sameCourseCode(it.courseCode, material.courseCode) }?.courseTitle,
        ).firstOrNull { title ->
            title.isNotBlank() && !title.filter(Char::isLetterOrDigit)
                .equals(material.courseCode.filter(Char::isLetterOrDigit), true)
        }?.substringBefore(" - ")?.trim().orEmpty().ifBlank { material.courseCode.ifBlank { "Course" } }
    }
    fun shareTimetableQr() {
        val snapshot = state.value
        mutableState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            graph.timetableQr.share(snapshot.activeSemester?.name ?: "Viora", snapshot.slots)
                .onFailure { error -> mutableState.update { it.copy(error = error.message ?: "Could not create timetable QR") } }
            mutableState.update { it.copy(loading = false) }
        }
    }

    fun selectSemester(semester: SemesterOption) {
        saveSemester(semester)
        mutableState.update { it.copy(activeSemester = semester) }
        observeTimetable(semester.id)
        observeAttendance(semester.id)
        observeAssignments(semester.id)
        observeExams(semester.id)
        observeResults(semester.id)
        observeExtras(semester.id)
        refresh()
    }

    private fun restoreSessionAndLoad() {
        mutableState.update { it.copy(loading = true, syncMessage = "Restoring your local session") }
        viewModelScope.launch {
            when (graph.sessions.ensureActive()) {
                SessionResolution.Ready -> loadSemestersAndRefresh()
                SessionResolution.SignInRequired -> requireSignIn("Sign in again to resume synchronization")
                SessionResolution.VerificationRequired -> requireSignIn("VTOP requires interactive verification")
                SessionResolution.Unavailable -> useCachedDataAfterConnectionFailure()
            }
        }
    }

    /**
     * Connect the UI to Room before making any VTOP request. This keeps the last
     * successful snapshot visible while session restoration and refresh run.
     */
    private fun observeSavedSemester(): SemesterOption? {
        val semester = savedSemesterOption(
            graph.settings.getString(VioraGraph.KEY_SEMESTER_ID, null),
            graph.settings.getString(VioraGraph.KEY_SEMESTER_NAME, null),
        ) ?: return null
        mutableState.update { current ->
            current.copy(
                activeSemester = semester,
                semesters = current.semesters.ifEmpty { listOf(semester) },
            )
        }
        observeTimetable(semester.id)
        observeAttendance(semester.id)
        observeAssignments(semester.id)
        observeExams(semester.id)
        observeResults(semester.id)
        observeExtras(semester.id)
        return semester
    }

    private fun useCachedDataAfterConnectionFailure() {
        val savedId = graph.settings.getString(VioraGraph.KEY_SEMESTER_ID, null)
        val savedName = graph.settings.getString(VioraGraph.KEY_SEMESTER_NAME, null)
        if (savedId != null) {
            val semester = SemesterOption(savedId, savedName ?: savedId)
            observeTimetable(savedId)
            observeAttendance(savedId)
            observeAssignments(savedId)
            observeExams(savedId)
            observeResults(savedId)
            observeExtras(savedId)
            mutableState.update { it.copy(activeSemester = semester, loading = false, syncMessage = "Showing cached data", error = "VTOP could not be reached. Tap Sync to retry.") }
        } else {
            mutableState.update { it.copy(loading = false, syncMessage = null, error = "VTOP could not be reached. Check your connection and try again.") }
        }
    }

    private suspend fun loadSemestersAndRefresh() {
        runCatching { graph.gateway.semesters() }
            .onSuccess { options ->
                val savedId = graph.settings.getString(VioraGraph.KEY_SEMESTER_ID, null)
                val cachedIds = graph.database.academicDao().semesterSnapshot().map { it.id }.toSet()
                val decision = SemesterRollover.select(options.map { it.id }, cachedIds, savedId)
                val rollover = decision.rolloverDetected
                val selected = options.firstOrNull { it.id == decision.selectedId }
                if (selected == null) {
                    mutableState.update { it.copy(loading = false, error = "No semester is available on VTOP") }
                    return@onSuccess
                }
                saveSemester(selected)
                mutableState.update {
                    it.copy(semesters = options, activeSemester = selected, loading = false, rolloverDetected = rollover, syncMessage = if (rollover) "New semester detected; previous data was archived" else null)
                }
                observeTimetable(selected.id)
                observeAttendance(selected.id)
                observeAssignments(selected.id)
                observeExams(selected.id)
                observeResults(selected.id)
                observeExtras(selected.id)
                scheduler.schedule()
                refreshSemester(selected)
            }
            .onFailure { useCachedSemesterAfterDiscoveryFailure() }
    }

    private fun useCachedSemesterAfterDiscoveryFailure() {
        val savedId = graph.settings.getString(VioraGraph.KEY_SEMESTER_ID, null)
        val savedName = graph.settings.getString(VioraGraph.KEY_SEMESTER_NAME, null)
        if (savedId == null) {
            mutableState.update {
                it.copy(
                    loading = false,
                    password = "",
                    error = "Signed in, but VTOP did not return a semester list. Try again in a moment.",
                )
            }
            return
        }

        val semester = SemesterOption(savedId, savedName ?: savedId)
        observeTimetable(savedId)
        observeAttendance(savedId)
        observeAssignments(savedId)
        observeExams(savedId)
        observeResults(savedId)
        observeExtras(savedId)
        mutableState.update {
            it.copy(
                configured = true,
                reauthRequired = false,
                activeSemester = semester,
                loading = false,
                password = "",
                syncMessage = "Using your cached semester",
                error = "Could not refresh the semester list. Tap Sync to retry.",
            )
        }
    }

    private suspend fun refreshSemester(semester: SemesterOption) {
        val profile = graph.syncDiagnostics.start("foreground")
        var diagnosticOutcome = "failure"
        try {
        when (graph.sessions.ensureActive()) {
            SessionResolution.Ready -> {
                val examResult = graph.exams.refresh(semester.id)
                val timetableResult = graph.timetable.refresh(semester.id, semester.name)
                val attendanceResult = graph.attendance.refresh(semester.id)
                val assignmentResult = graph.assignments.refresh(semester.id)
                val resultsResult = graph.results.refresh(semester.id)
                val extrasResult = graph.extras.refresh(semester.id, graph.database.academicDao().courses(semester.id).map { it.code to it.faculty })
                graph.notifications.publishUpcoming(semester.id)
                graph.reminders.schedule(semester.id)
                val refreshResults = listOf(examResult, timetableResult, attendanceResult, assignmentResult, resultsResult, extrasResult)
                if (refreshResults.any { it.exceptionOrNull() is AuthenticationException }) {
                    diagnosticOutcome = "sign-in required"
                    requireSignIn("VTOP session expired. Sign in again to refresh and upload.")
                    return
                }
                val failed = refreshResults.count(Result<*>::isFailure)
                diagnosticOutcome = if (failed == 0) "success" else "partial ($failed)"
                mutableState.update {
                    it.copy(
                        loading = false,
                        syncMessage = if (failed == 0) "Academics updated" else "Updated with $failed partial failure(s)",
                        error = null,
                    )
                }
            }
            SessionResolution.SignInRequired -> { diagnosticOutcome = "sign-in required"; requireSignIn("Sign in again to refresh VTOP") }
            SessionResolution.VerificationRequired -> { diagnosticOutcome = "verification required"; requireSignIn("VTOP requires interactive verification") }
            SessionResolution.Unavailable -> useCachedDataAfterConnectionFailure()
        }
        } finally {
            graph.syncDiagnostics.finish(profile, diagnosticOutcome)
            NextClassWidgetProvider.updateAll(graph.context)
            refreshDiagnostics()
        }
    }

    private fun observeTimetable(semesterId: String) {
        timetableObservation?.cancel()
        timetableObservation = viewModelScope.launch {
            graph.timetable.observe(semesterId)
                .catch { mutableState.update { state -> state.copy(error = "Could not read the local timetable") } }
                .collect { slots -> mutableState.update { it.copy(slots = slots) } }
        }
    }

    private fun observeAttendance(semesterId: String) {
        attendanceObservation?.cancel()
        attendanceObservation = viewModelScope.launch {
            graph.attendance.observe(semesterId)
                .catch { mutableState.update { state -> state.copy(error = "Could not read cached attendance") } }
                .collect { records ->
                    val projections = records.map { record ->
                        val blockSize = if (record.courseType.contains("lab", true)) 2 else 1
                        val projectedHeld = record.held + state.value.plannedMissedBlocks * blockSize
                        val projection = AttendanceCalculator.calculate(record.attended, projectedHeld, ATTENDANCE_TARGET, blockSize)
                        AttendanceUi(
                            record.id,
                            record.courseCode,
                            record.courseTitle,
                            record.courseType,
                            record.faculty,
                            record.attended,
                            record.held,
                            projectedHeld,
                            projection.percentage,
                            projection.skippableClasses,
                            projection.classesToRecover,
                            blockSize,
                            projection.skippableBlocks,
                            projection.blocksToRecover,
                        )
                    }
                    mutableState.update { it.copy(attendance = projections) }
                }
        }
    }

    private fun observeAssignments(semesterId: String) {
        assignmentObservation?.cancel()
        assignmentObservation = viewModelScope.launch {
            graph.assignments.observe(semesterId)
                .catch { mutableState.update { state -> state.copy(error = "Could not read cached assignments") } }
                .collect { records ->
                    mutableState.update { state ->
                        state.copy(
                            assignments = records.map {
                                AssignmentUi(it.id, it.courseCode, it.title, it.dueEpochMillis, it.status, it.lastUpload, it.courseTitle)
                            },
                        )
                    }
                }
        }
    }

    private fun observeExams(semesterId: String) {
        examObservation?.cancel()
        examObservation = viewModelScope.launch {
            graph.exams.observe(semesterId)
                .catch { mutableState.update { state -> state.copy(error = "Could not read cached exams") } }
                .collect { records ->
                    mutableState.update { state ->
                        state.copy(
                            exams = records.map {
                                ExamUi(
                                    it.id, it.courseCode, it.courseTitle, it.examType,
                                    it.startsEpochMillis, it.endsEpochMillis, it.venue, it.seatNumber,
                                )
                            },
                        )
                    }
                }
        }
    }

    private fun observeResults(semesterId: String) {
        resultsObservation?.cancel()
        resultsObservation = viewModelScope.launch {
            launch { graph.results.observeMarks(semesterId).collect { rows -> mutableState.update { state -> state.copy(marks = rows.map { MarkUi(it.id, it.courseTitle, it.title, it.scoredMark, it.maxMarks, it.weightageMark, it.status) }) } } }
            launch { graph.results.observeGrades(semesterId).collect { rows -> mutableState.update { state -> state.copy(grades = rows.map { GradeUi(it.courseCode, it.courseTitle, it.credits, it.total, it.grade) }) } } }
            launch { graph.results.observeSummary().collect { summary -> mutableState.update { it.copy(gpa = summary?.gpa, cgpa = summary?.cgpa, registeredCredits = summary?.registeredCredits, earnedCredits = summary?.earnedCredits) } } }
        }
    }
    private fun observeExtras(semesterId: String) {
        extrasObservation?.cancel(); extrasObservation = viewModelScope.launch {
            launch { graph.extras.observeCalendar(semesterId).collect { rows -> mutableState.update { it.copy(calendar = rows) } } }
            launch { graph.extras.observeMessages().collect { rows -> mutableState.update { it.copy(messages = rows) } } }
            launch { graph.extras.observeMaterials(semesterId).collect { rows -> mutableState.update { it.copy(materials = rows) } } }
        }
    }

    private fun requireSignIn(message: String) {
        val savedId = graph.settings.getString(VioraGraph.KEY_SEMESTER_ID, null)
        val savedName = graph.settings.getString(VioraGraph.KEY_SEMESTER_NAME, null)
        if (savedId != null) {
            val semester = SemesterOption(savedId, savedName ?: savedId)
            observeTimetable(savedId)
            observeAttendance(savedId)
            observeAssignments(savedId)
            observeExams(savedId)
            observeResults(savedId)
            observeExtras(savedId)
            mutableState.update {
                it.copy(
                    configured = true,
                    reauthRequired = true,
                    activeSemester = semester,
                    loading = false,
                    password = "",
                    syncMessage = null,
                    error = message,
                )
            }
        } else {
            graph.settings.edit().putBoolean(VioraGraph.KEY_CONFIGURED, false).commit()
            mutableState.update {
                it.copy(configured = false, loading = false, password = "", syncMessage = null, error = message)
            }
        }
    }

    private fun saveSemester(semester: SemesterOption) {
        graph.settings.edit()
            .putString(VioraGraph.KEY_SEMESTER_ID, semester.id)
            .putString(VioraGraph.KEY_SEMESTER_NAME, semester.name)
            .commit()
    }

    private fun loadClassCheckIns(): Map<String, ClassCheckIn> = graph.settings.all.mapNotNull { (key, value) ->
        if (!key.startsWith(CLASS_CHECK_IN_PREFIX)) return@mapNotNull null
        val mark = runCatching { ClassCheckIn.valueOf(value as String) }.getOrNull() ?: return@mapNotNull null
        key to mark
    }.toMap()

    class Factory(
        private val graph: VioraGraph,
        private val scheduler: VioraSyncScheduler,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VioraAppViewModel(graph, scheduler) as T
    }

    companion object {
        const val CLASS_CHECK_IN_PREFIX = "class_check_in:"
        const val ATTENDANCE_TARGET = 75
    }
}

internal fun savedSemesterOption(id: String?, name: String?): SemesterOption? =
    id?.takeIf(String::isNotBlank)?.let { SemesterOption(it, name?.takeIf(String::isNotBlank) ?: it) }

private fun List<AttendanceUi>.reproject(target: Int, missedBlocks: Int): List<AttendanceUi> = map { item ->
    val held = item.sourceHeld + missedBlocks * item.blockSize
    val projection = AttendanceCalculator.calculate(item.attended, held, target, item.blockSize)
    item.copy(held = held, percentage = projection.percentage, skippable = projection.skippableClasses, recovery = projection.classesToRecover, skippableBlocks = projection.skippableBlocks, recoveryBlocks = projection.blocksToRecover)
}
