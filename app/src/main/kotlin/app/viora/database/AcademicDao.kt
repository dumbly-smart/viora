package app.viora.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class SlotWithCourse(
    val slotId: String,
    val courseId: String,
    val code: String,
    val title: String,
    val faculty: String,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val venue: String,
    val type: String,
)

@Dao
interface AcademicDao {
    @Query("SELECT * FROM sync_resources ORDER BY resource")
    fun observeSyncResources(): Flow<List<SyncResourceEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSemester(semester: SemesterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourses(courses: List<CourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSlots(slots: List<ClassSlotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncResource(resource: SyncResourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttendance(records: List<AttendanceEntity>)

    @Query("DELETE FROM attendance WHERE semesterId = :semesterId")
    suspend fun deleteAttendanceForSemester(semesterId: String)

    @Query("SELECT * FROM attendance WHERE semesterId = :semesterId ORDER BY courseCode")
    fun observeAttendance(semesterId: String): Flow<List<AttendanceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignments(records: List<DigitalAssignmentEntity>)

    @Query("DELETE FROM digital_assignments WHERE semesterId = :semesterId")
    suspend fun deleteAssignmentsForSemester(semesterId: String)

    @Query("SELECT * FROM digital_assignments WHERE semesterId = :semesterId ORDER BY dueEpochMillis IS NULL, dueEpochMillis")
    fun observeAssignments(semesterId: String): Flow<List<DigitalAssignmentEntity>>

    @Query("SELECT * FROM digital_assignments WHERE semesterId = :semesterId AND dueEpochMillis BETWEEN :from AND :to ORDER BY dueEpochMillis")
    suspend fun assignmentsDueBetween(semesterId: String, from: Long, to: Long): List<DigitalAssignmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExams(records: List<ExamEntity>)

    @Query("DELETE FROM exams WHERE semesterId = :semesterId")
    suspend fun deleteExamsForSemester(semesterId: String)

    @Query("SELECT * FROM exams WHERE semesterId = :semesterId ORDER BY startsEpochMillis")
    fun observeExams(semesterId: String): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams WHERE semesterId = :semesterId AND startsEpochMillis BETWEEN :from AND :to ORDER BY startsEpochMillis")
    suspend fun examsBetween(semesterId: String, from: Long, to: Long): List<ExamEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotificationLedger(record: NotificationLedgerEntity): Long

    @Query("DELETE FROM class_slots WHERE courseId IN (SELECT id FROM courses WHERE semesterId = :semesterId)")
    suspend fun deleteSlotsForSemester(semesterId: String)

    @Query("DELETE FROM courses WHERE semesterId = :semesterId")
    suspend fun deleteCoursesForSemester(semesterId: String)

    @Query(
        """
        SELECT class_slots.id AS slotId, courses.id AS courseId, courses.code, courses.title,
               courses.faculty, class_slots.dayOfWeek, class_slots.startMinute,
               class_slots.endMinute, class_slots.venue, class_slots.type
        FROM class_slots
        JOIN courses ON courses.id = class_slots.courseId
        WHERE courses.semesterId = :semesterId
        ORDER BY class_slots.dayOfWeek, class_slots.startMinute
        """,
    )
    fun observeTimetable(semesterId: String): Flow<List<SlotWithCourse>>
    @Query("SELECT * FROM courses WHERE semesterId = :semesterId ORDER BY code") suspend fun courses(semesterId: String): List<CourseEntity>

    @Query("SELECT * FROM sync_resources WHERE resource = :resource")
    fun observeSyncResource(resource: String): Flow<SyncResourceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMarks(records: List<MarkEntity>)
    @Query("DELETE FROM marks WHERE semesterId = :semesterId") suspend fun deleteMarks(semesterId: String)
    @Query("SELECT * FROM marks WHERE semesterId = :semesterId ORDER BY courseTitle, title") fun observeMarks(semesterId: String): Flow<List<MarkEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertGrades(records: List<GradeEntity>)
    @Query("DELETE FROM grades WHERE semesterId = :semesterId") suspend fun deleteGrades(semesterId: String)
    @Query("SELECT * FROM grades WHERE semesterId = :semesterId ORDER BY courseCode") fun observeGrades(semesterId: String): Flow<List<GradeEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAcademicSummary(summary: AcademicSummaryEntity)
    @Query("SELECT * FROM academic_summaries WHERE id = 'current'") fun observeAcademicSummary(): Flow<AcademicSummaryEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCalendar(rows: List<AcademicCalendarEntity>)
    @Query("DELETE FROM academic_calendar WHERE semesterId = :semesterId") suspend fun deleteCalendar(semesterId: String)
    @Query("SELECT * FROM academic_calendar WHERE semesterId = :semesterId ORDER BY dateEpochDay") fun observeCalendar(semesterId: String): Flow<List<AcademicCalendarEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMessages(rows: List<ClassMessageEntity>)
    @Query("DELETE FROM class_messages") suspend fun deleteMessages()
    @Query("SELECT * FROM class_messages ORDER BY postedEpochMillis DESC") fun observeMessages(): Flow<List<ClassMessageEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMaterials(rows: List<CourseMaterialEntity>)
    @Query("DELETE FROM course_materials WHERE semesterId = :semesterId AND courseCode = :courseCode") suspend fun deleteMaterials(semesterId: String, courseCode: String)
    @Query("SELECT * FROM course_materials WHERE semesterId = :semesterId ORDER BY courseCode, title") fun observeMaterials(semesterId: String): Flow<List<CourseMaterialEntity>>
    @Transaction suspend fun replaceCalendar(semesterId: String, rows: List<AcademicCalendarEntity>) { deleteCalendar(semesterId); upsertCalendar(rows) }
    @Transaction suspend fun replaceMessages(rows: List<ClassMessageEntity>) { deleteMessages(); upsertMessages(rows) }
    @Transaction suspend fun replaceMaterials(semesterId: String, courseCode: String, rows: List<CourseMaterialEntity>) { deleteMaterials(semesterId, courseCode); upsertMaterials(rows) }
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertChanges(rows: List<AcademicChangeEntity>)
    @Query("SELECT * FROM academic_changes WHERE occurredEpochMillis >= :since ORDER BY occurredEpochMillis DESC") suspend fun changesSince(since: Long): List<AcademicChangeEntity>
    @Query("SELECT * FROM academic_changes ORDER BY occurredEpochMillis DESC LIMIT 50") fun observeChanges(): Flow<List<AcademicChangeEntity>>
    @Query("SELECT * FROM exams WHERE semesterId = :semesterId") suspend fun examSnapshot(semesterId: String): List<ExamEntity>
    @Query("SELECT * FROM attendance WHERE semesterId = :semesterId") suspend fun attendanceSnapshot(semesterId: String): List<AttendanceEntity>
    @Query("SELECT * FROM class_messages") suspend fun messageSnapshot(): List<ClassMessageEntity>
    @Query("SELECT * FROM course_materials WHERE semesterId = :semesterId AND courseCode = :courseCode") suspend fun materialSnapshot(semesterId: String, courseCode: String): List<CourseMaterialEntity>
    @Query("SELECT * FROM class_slots WHERE courseId IN (SELECT id FROM courses WHERE semesterId = :semesterId)") suspend fun slotSnapshot(semesterId: String): List<ClassSlotEntity>
    @Query("SELECT * FROM marks WHERE semesterId = :semesterId") suspend fun markSnapshot(semesterId: String): List<MarkEntity>
    @Query("SELECT * FROM grades WHERE semesterId = :semesterId") suspend fun gradeSnapshot(semesterId: String): List<GradeEntity>
    @Query("SELECT * FROM digital_assignments WHERE semesterId = :semesterId") suspend fun assignmentSnapshot(semesterId: String): List<DigitalAssignmentEntity>

    @Transaction suspend fun replaceMarks(semesterId: String, records: List<MarkEntity>, sync: SyncResourceEntity) { deleteMarks(semesterId); upsertMarks(records); upsertSyncResource(sync) }
    @Transaction suspend fun replaceGrades(semesterId: String, records: List<GradeEntity>, summary: AcademicSummaryEntity, sync: SyncResourceEntity) { deleteGrades(semesterId); upsertGrades(records); upsertAcademicSummary(summary); upsertSyncResource(sync) }

    @Transaction
    suspend fun replaceTimetable(
        semester: SemesterEntity,
        courses: List<CourseEntity>,
        slots: List<ClassSlotEntity>,
        sync: SyncResourceEntity,
    ) {
        upsertSemester(semester)
        deleteSlotsForSemester(semester.id)
        deleteCoursesForSemester(semester.id)
        upsertCourses(courses)
        upsertSlots(slots)
        upsertSyncResource(sync)
    }

    @Transaction
    suspend fun replaceAttendance(
        semesterId: String,
        records: List<AttendanceEntity>,
        sync: SyncResourceEntity,
    ) {
        deleteAttendanceForSemester(semesterId)
        upsertAttendance(records)
        upsertSyncResource(sync)
    }

    @Transaction
    suspend fun replaceAssignments(
        semesterId: String,
        records: List<DigitalAssignmentEntity>,
        sync: SyncResourceEntity,
    ) {
        deleteAssignmentsForSemester(semesterId)
        upsertAssignments(records)
        upsertSyncResource(sync)
    }

    @Transaction
    suspend fun replaceExams(
        semesterId: String,
        records: List<ExamEntity>,
        sync: SyncResourceEntity,
    ) {
        deleteExamsForSemester(semesterId)
        upsertExams(records)
        upsertSyncResource(sync)
    }
}
