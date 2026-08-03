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

interface VtopGateway {
    suspend fun sessionState(): SessionState
    suspend fun login(username: String, password: CharArray): SessionState
    suspend fun semesters(): List<SemesterOption>
    suspend fun timetable(semesterId: String): TimetableSnapshot

    /** Clears Viora's local session only. It must not invoke VTOP's logout endpoint. */
    suspend fun clearLocalSession()
}
