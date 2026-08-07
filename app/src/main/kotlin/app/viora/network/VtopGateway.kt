package app.viora.network

import app.viora.model.ClassSlot
import app.viora.model.Course
import java.time.LocalDateTime
import java.time.LocalDate

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
    val id: String,
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val faculty: String,
    val attended: Int,
    val held: Int,
)

data class MarkRecord(val id: String, val courseCode: String, val courseTitle: String, val courseType: String, val title: String, val maxMarks: Double?, val weightagePercent: Double?, val status: String, val scoredMark: Double?, val weightageMark: Double?)
data class GradeRecord(val courseCode: String, val courseTitle: String, val courseType: String, val credits: Double?, val total: Double?, val grading: String, val grade: String)
data class GradeSnapshot(val records: List<GradeRecord>, val gpa: Double?)
data class CgpaSnapshot(val registeredCredits: Double?, val earnedCredits: Double?, val cgpa: Double?, val gradeCounts: Map<String, Int>)
data class AcademicCalendarRecord(val id: String, val date: LocalDate, val title: String, val dayType: String)
data class ClassMessageRecord(val id: String, val courseCode: String, val courseTitle: String, val faculty: String, val subject: String, val body: String, val postedAt: LocalDateTime?)
data class CourseMaterialRecord(val id: String, val courseCode: String, val title: String, val fileName: String, val downloadPath: String, val postedAt: LocalDateTime?)

data class AttendanceSnapshot(val records: List<AttendanceRecord>)

data class DigitalAssignmentRecord(
    val id: String,
    val courseCode: String,
    val title: String,
    val dueAt: LocalDateTime?,
    val lastUpload: String,
    val status: String,
)

data class ExamRecord(
    val id: String,
    val courseCode: String,
    val courseTitle: String,
    val examType: String,
    val startsAt: LocalDateTime,
    val venue: String,
    val seatNumber: String,
)

interface VtopGateway {
    suspend fun sessionState(): SessionState
    suspend fun login(username: String, password: CharArray): SessionState
    suspend fun semesters(): List<SemesterOption>
    suspend fun timetable(semesterId: String): TimetableSnapshot
    suspend fun attendance(semesterId: String): AttendanceSnapshot
    suspend fun digitalAssignments(): List<DigitalAssignmentRecord>
    suspend fun exams(semesterId: String): List<ExamRecord>
    suspend fun marks(semesterId: String): List<MarkRecord>
    suspend fun grades(semesterId: String): GradeSnapshot
    suspend fun cgpa(): CgpaSnapshot
    suspend fun academicCalendar(semesterId: String): List<AcademicCalendarRecord>
    suspend fun classMessages(): List<ClassMessageRecord>
    suspend fun courseMaterials(semesterId: String, courseCode: String, faculty: String): List<CourseMaterialRecord>

    /** Clears Viora's local session only. It must not invoke VTOP's logout endpoint. */
    suspend fun clearLocalSession()
}
