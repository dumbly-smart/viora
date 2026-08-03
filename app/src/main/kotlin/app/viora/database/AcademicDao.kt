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
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSemester(semester: SemesterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourses(courses: List<CourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSlots(slots: List<ClassSlotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncResource(resource: SyncResourceEntity)

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

    @Query("SELECT * FROM sync_resources WHERE resource = :resource")
    fun observeSyncResource(resource: String): Flow<SyncResourceEntity?>

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
}
