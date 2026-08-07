package app.viora.network

import app.viora.model.ClassSlot
import app.viora.model.Course

sealed interface SessionState {
    data object Missing : SessionState
    data object Active : SessionState
    data object VerificationRequired : SessionState
}

data class TimetableSnapshot(
    val courses: List<Course>,
    val slots: List<ClassSlot>,
)

data class SemesterOption(
    val id: String,
    val name: String,
)

data class AttendanceRecord(
    val courseCode: String,
    val courseTitle: String,
    val attended: Int,
    val held: Int,
)

data class AttendanceSnapshot(val records: List<AttendanceRecord>)

interface VtopGateway {
    suspend fun sessionState(): SessionState
    suspend fun login(username: String, password: CharArray): SessionState
    suspend fun semesters(): List<SemesterOption>
    suspend fun timetable(semesterId: String): TimetableSnapshot
    suspend fun attendance(semesterId: String): AttendanceSnapshot

    /** Clears Viora's local session only. It must not invoke VTOP's logout endpoint. */
    suspend fun clearLocalSession()
}
