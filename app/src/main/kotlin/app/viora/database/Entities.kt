package app.viora.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "semesters")
data class SemesterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val active: Boolean,
)

@Entity(
    tableName = "courses",
    indices = [Index("semesterId")],
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CourseEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val code: String,
    val title: String,
    val faculty: String,
)

@Entity(
    tableName = "class_slots",
    indices = [Index("courseId")],
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ClassSlotEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val venue: String,
    val type: String,
)

@Entity(tableName = "sync_resources")
data class SyncResourceEntity(
    @PrimaryKey val resource: String,
    val status: String,
    val lastAttemptEpochMillis: Long,
    val lastSuccessEpochMillis: Long?,
    val safeError: String?,
)

@Entity(
    tableName = "attendance",
    indices = [Index("semesterId"), Index("courseCode")],
    primaryKeys = ["semesterId", "id"],
)
data class AttendanceEntity(
    val semesterId: String,
    val id: String,
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val faculty: String,
    val attended: Int,
    val held: Int,
    val sourceEpochMillis: Long,
)

@Entity(
    tableName = "digital_assignments",
    indices = [Index("semesterId"), Index("dueEpochMillis")],
    primaryKeys = ["semesterId", "id"],
)
data class DigitalAssignmentEntity(
    val semesterId: String,
    val id: String,
    val courseCode: String,
    val title: String,
    val dueEpochMillis: Long?,
    val lastUpload: String,
    val status: String,
    val sourceEpochMillis: Long,
)

@Entity(
    tableName = "exams",
    indices = [Index("semesterId"), Index("startsEpochMillis")],
    primaryKeys = ["semesterId", "id"],
)
data class ExamEntity(
    val semesterId: String,
    val id: String,
    val courseCode: String,
    val courseTitle: String,
    val examType: String,
    val startsEpochMillis: Long,
    val endsEpochMillis: Long?,
    val venue: String,
    val seatNumber: String,
    val sourceEpochMillis: Long,
)

@Entity(tableName = "notification_ledger")
data class NotificationLedgerEntity(
    @PrimaryKey val key: String,
    val notifiedEpochMillis: Long,
)

@Entity(tableName = "marks", indices = [Index("semesterId"), Index("courseCode")], primaryKeys = ["semesterId", "id"])
data class MarkEntity(val semesterId: String, val id: String, val courseCode: String, val courseTitle: String, val courseType: String, val title: String, val maxMarks: Double?, val weightagePercent: Double?, val status: String, val scoredMark: Double?, val weightageMark: Double?, val sourceEpochMillis: Long)

@Entity(tableName = "grades", indices = [Index("semesterId")], primaryKeys = ["semesterId", "courseCode"])
data class GradeEntity(val semesterId: String, val courseCode: String, val courseTitle: String, val courseType: String, val credits: Double?, val total: Double?, val grading: String, val grade: String, val sourceEpochMillis: Long)

@Entity(tableName = "academic_summaries")
data class AcademicSummaryEntity(@PrimaryKey val id: String, val gpa: Double?, val cgpa: Double?, val registeredCredits: Double?, val earnedCredits: Double?, val gradeCounts: String, val sourceEpochMillis: Long)

@Entity(tableName = "academic_calendar", indices = [Index("semesterId"), Index("dateEpochDay")], primaryKeys = ["semesterId", "id"])
data class AcademicCalendarEntity(val semesterId: String, val id: String, val dateEpochDay: Long, val title: String, val dayType: String, val sourceEpochMillis: Long)
@Entity(tableName = "class_messages", indices = [Index("postedEpochMillis")])
data class ClassMessageEntity(@PrimaryKey val id: String, val courseCode: String, val courseTitle: String, val faculty: String, val subject: String, val body: String, val postedEpochMillis: Long?, val sourceEpochMillis: Long)
@Entity(tableName = "course_materials", indices = [Index("semesterId"), Index("courseCode")], primaryKeys = ["semesterId", "id"])
data class CourseMaterialEntity(val semesterId: String, val id: String, val courseCode: String, val title: String, val fileName: String, val downloadPath: String, val postedEpochMillis: Long?, val sourceEpochMillis: Long)

@Entity(tableName = "academic_changes", indices = [Index("occurredEpochMillis")])
data class AcademicChangeEntity(@PrimaryKey val id: String, val category: String, val title: String, val detail: String, val occurredEpochMillis: Long)
