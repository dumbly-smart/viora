package app.viora.network

import app.viora.model.ClassSlot
import app.viora.model.Course
import java.time.LocalDateTime
import java.time.LocalDate
import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

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
    val courseTitle: String,
    val title: String,
    val dueAt: LocalDateTime?,
    val lastUpload: String,
    val status: String,
    val uploadLocator: AssignmentUploadLocator? = null,
)

data class AssignmentUploadLocator(
    val requestPath: String,
    val fields: Map<String, String>,
    val fileField: String,
    val acceptedMimeTypes: Set<String>,
    val maxBytes: Long?,
)

internal fun requireAssignmentUploadLocator(
    assignments: List<DigitalAssignmentRecord>,
    assignmentId: String,
): AssignmentUploadLocator = assignments.firstOrNull { it.id == assignmentId }?.uploadLocator
    ?: throw IOException("VTOP did not provide a current upload form for this assessment")

internal fun resolveAssignmentUploadAction(requestPath: String): HttpUrl {
    val resolved = "https://vtop.vit.ac.in/".toHttpUrl().resolve(requestPath)
        ?: throw IOException("VTOP provided an invalid assessment upload action")
    if (resolved.scheme != "https" || resolved.host != "vtop.vit.ac.in" || resolved.port != 443) {
        throw IOException("Blocked an unsafe assessment upload action")
    }
    return resolved
}

fun AssignmentUploadLocator.accepts(mimeType: String, fileName: String): Boolean {
    if (acceptedMimeTypes.isEmpty()) return true
    val normalizedMime = mimeType.lowercase()
    val normalizedName = fileName.lowercase()
    return acceptedMimeTypes.any { raw ->
        val accepted = raw.trim().lowercase()
        accepted == "*/*" || accepted == normalizedMime ||
            (accepted.endsWith("/*") && normalizedMime.startsWith(accepted.removeSuffix("*"))) ||
            (accepted.startsWith('.') && normalizedName.endsWith(accepted))
    }
}

data class ExamRecord(
    val id: String,
    val courseCode: String,
    val courseTitle: String,
    val examType: String,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime?,
    val venue: String,
    val seatNumber: String,
)

interface VtopGateway {
    suspend fun sessionState(): SessionState
    suspend fun login(username: String, password: CharArray): SessionState
    suspend fun semesters(): List<SemesterOption>
    suspend fun timetable(semesterId: String): TimetableSnapshot
    suspend fun attendance(semesterId: String): AttendanceSnapshot
    suspend fun digitalAssignments(semesterId: String): List<DigitalAssignmentRecord>
    suspend fun uploadDigitalAssignment(semesterId: String, assignmentId: String, fileName: String, mimeType: String, bytes: ByteArray)
    suspend fun exams(semesterId: String): List<ExamRecord>
    suspend fun marks(semesterId: String): List<MarkRecord>
    suspend fun grades(semesterId: String): GradeSnapshot
    suspend fun cgpa(): CgpaSnapshot
    suspend fun academicCalendar(semesterId: String): List<AcademicCalendarRecord>
    suspend fun classMessages(): List<ClassMessageRecord>
    suspend fun courseMaterials(semesterId: String, courseCode: String, courseTitle: String, faculty: String): List<CourseMaterialRecord>
    suspend fun importInteractiveSession(cookieHeader: String): SessionState
    suspend fun downloadCourseMaterial(downloadPath: String): ByteArray

    /** Clears Viora's local session only. It must not invoke VTOP's logout endpoint. */
    suspend fun clearLocalSession()
}
