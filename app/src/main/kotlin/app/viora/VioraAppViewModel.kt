package app.viora

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.viora.auth.SessionResolution
import app.viora.database.SlotWithCourse
import app.viora.domain.AttendanceCalculator
import app.viora.network.SemesterOption
import app.viora.network.SessionState
import app.viora.sync.SyncOutcome
import app.viora.sync.VioraSyncScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VioraUiState(
    val configured: Boolean = false,
    val reauthRequired: Boolean = false,
    val username: String = "",
    val password: String = "",
    val rememberLogin: Boolean = true,
    val loading: Boolean = false,
    val syncMessage: String? = null,
    val error: String? = null,
    val semesters: List<SemesterOption> = emptyList(),
    val activeSemester: SemesterOption? = null,
    val slots: List<SlotWithCourse> = emptyList(),
    val attendance: List<AttendanceUi> = emptyList(),
)

data class AttendanceUi(
    val courseCode: String,
    val courseTitle: String,
    val attended: Int,
    val held: Int,
    val percentage: Double,
    val skippable: Int,
    val recovery: Int,
)

class VioraAppViewModel(
    private val graph: VioraGraph,
    private val scheduler: VioraSyncScheduler,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        VioraUiState(configured = graph.settings.getBoolean(VioraGraph.KEY_CONFIGURED, false)),
    )
    val state: StateFlow<VioraUiState> = mutableState.asStateFlow()
    private var timetableObservation: Job? = null
    private var attendanceObservation: Job? = null

    init {
        if (state.value.configured) restoreSessionAndLoad()
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
                    SessionState.VerificationRequired -> mutableState.update {
                        it.copy(loading = false, error = "VTOP requires interactive verification")
                    }
                    SessionState.Missing -> mutableState.update {
                        it.copy(loading = false, error = "VTOP rejected the sign-in details")
                    }
                }
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, error = "Could not connect to VTOP") }
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun refresh() {
        val semester = state.value.activeSemester ?: return
        mutableState.update { it.copy(loading = true, syncMessage = "Syncing with VTOP", error = null) }
        viewModelScope.launch { refreshSemester(semester) }
    }

    fun beginReauthentication() = mutableState.update {
        it.copy(configured = false, loading = false, password = "", error = null)
    }

    fun selectSemester(semester: SemesterOption) {
        saveSemester(semester)
        mutableState.update { it.copy(activeSemester = semester) }
        observeTimetable(semester.id)
        observeAttendance(semester.id)
        refresh()
    }

    private fun restoreSessionAndLoad() {
        mutableState.update { it.copy(loading = true, syncMessage = "Restoring your local session") }
        viewModelScope.launch {
            when (graph.sessions.ensureActive()) {
                SessionResolution.Ready -> loadSemestersAndRefresh()
                SessionResolution.SignInRequired -> requireSignIn("Sign in again to resume synchronization")
                SessionResolution.VerificationRequired -> requireSignIn("VTOP requires interactive verification")
            }
        }
    }

    private suspend fun loadSemestersAndRefresh() {
        runCatching { graph.gateway.semesters() }
            .onSuccess { options ->
                val savedId = graph.settings.getString(VioraGraph.KEY_SEMESTER_ID, null)
                val selected = options.firstOrNull { it.id == savedId } ?: options.firstOrNull()
                if (selected == null) {
                    mutableState.update { it.copy(loading = false, error = "No semester is available on VTOP") }
                    return@onSuccess
                }
                saveSemester(selected)
                mutableState.update {
                    it.copy(semesters = options, activeSemester = selected, loading = false, syncMessage = null)
                }
                observeTimetable(selected.id)
                observeAttendance(selected.id)
                scheduler.schedule()
                refreshSemester(selected)
            }
            .onFailure { requireSignIn("Your cached data is available; sign in to refresh it") }
    }

    private suspend fun refreshSemester(semester: SemesterOption) {
        when (graph.timetableSync.refresh(semester.id, semester.name)) {
            SyncOutcome.Updated -> {
                val attendanceResult = graph.attendance.refresh(semester.id)
                mutableState.update {
                    it.copy(
                        loading = false,
                        syncMessage = if (attendanceResult.isSuccess) "Academics updated" else "Timetable updated",
                        error = if (attendanceResult.isFailure) "Attendance refresh failed; showing cached data" else null,
                    )
                }
            }
            SyncOutcome.SignInRequired -> requireSignIn("Sign in again to refresh VTOP")
            SyncOutcome.VerificationRequired -> requireSignIn("VTOP requires interactive verification")
            is SyncOutcome.Failed -> mutableState.update {
                it.copy(loading = false, syncMessage = null, error = "Could not refresh; showing cached timetable")
            }
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
                        val projection = AttendanceCalculator.calculate(record.attended, record.held, 75)
                        AttendanceUi(
                            record.courseCode,
                            record.courseTitle,
                            record.attended,
                            record.held,
                            projection.percentage,
                            projection.skippableClasses,
                            projection.classesToRecover,
                        )
                    }
                    mutableState.update { it.copy(attendance = projections) }
                }
        }
    }

    private fun requireSignIn(message: String) {
        val savedId = graph.settings.getString(VioraGraph.KEY_SEMESTER_ID, null)
        val savedName = graph.settings.getString(VioraGraph.KEY_SEMESTER_NAME, null)
        if (savedId != null) {
            val semester = SemesterOption(savedId, savedName ?: savedId)
            observeTimetable(savedId)
            observeAttendance(savedId)
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

    class Factory(
        private val graph: VioraGraph,
        private val scheduler: VioraSyncScheduler,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VioraAppViewModel(graph, scheduler) as T
    }
}
