package app.viora.data

import app.viora.database.AcademicDao
import app.viora.database.ClassSlotEntity
import app.viora.database.CourseEntity
import app.viora.database.SemesterEntity
import app.viora.database.SlotWithCourse
import app.viora.database.SyncResourceEntity
import app.viora.database.AcademicChangeEntity
import app.viora.network.TimetableSnapshot
import app.viora.network.VtopGateway
import kotlinx.coroutines.flow.Flow

class TimetableRepository(
    private val dao: AcademicDao,
    private val gateway: VtopGateway,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(semesterId: String): Flow<List<SlotWithCourse>> = dao.observeTimetable(semesterId)

    suspend fun refresh(semesterId: String, semesterName: String): Result<Unit> {
        val attempt = clock()
        dao.upsertSyncResource(
            SyncResourceEntity(RESOURCE, "SYNCING", attempt, null, null),
        )
        return runCatching {
            val snapshot = gateway.timetable(semesterId)
            require(snapshot.courses.isNotEmpty() && snapshot.slots.isNotEmpty()) {
                "VTOP returned an empty timetable"
            }
            persist(semesterId, semesterName, snapshot, attempt)
        }.onFailure {
            dao.upsertSyncResource(
                SyncResourceEntity(
                    resource = RESOURCE,
                    status = "ERROR",
                    lastAttemptEpochMillis = attempt,
                    lastSuccessEpochMillis = null,
                    safeError = "Timetable could not be refreshed",
                ),
            )
        }
    }

    private suspend fun persist(
        semesterId: String,
        semesterName: String,
        snapshot: TimetableSnapshot,
        timestamp: Long,
    ) {
        val previous = dao.slotSnapshot(semesterId).associateBy { it.id }
        val scopedIds = snapshot.courses.associate { it.id to "$semesterId:${it.id}" }
        val courses = snapshot.courses.map {
            CourseEntity(scopedIds.getValue(it.id), semesterId, it.code, it.title, it.faculty)
        }
        val slots = snapshot.slots.map {
            ClassSlotEntity(
                id = "$semesterId:${it.id}",
                courseId = scopedIds.getValue(it.courseId),
                dayOfWeek = it.day.value,
                startMinute = it.start.hour * 60 + it.start.minute,
                endMinute = it.end.hour * 60 + it.end.minute,
                venue = it.venue,
                type = it.type.name,
            )
        }
        dao.insertChanges(slots.mapNotNull { current -> previous[current.id]?.takeIf { it.dayOfWeek != current.dayOfWeek || it.startMinute != current.startMinute || it.endMinute != current.endMinute || it.venue != current.venue }?.let { AcademicChangeEntity("timetable:${current.id}:${current.dayOfWeek}:${current.startMinute}:${current.venue}", "timetable", "Timetable changed", "A class slot moved to ${current.venue}", timestamp) } })
        dao.replaceTimetable(
            semester = SemesterEntity(semesterId, semesterName, active = true),
            courses = courses,
            slots = slots,
            sync = SyncResourceEntity(RESOURCE, "FRESH", timestamp, timestamp, null),
        )
    }

    companion object { const val RESOURCE = "timetable" }
}
