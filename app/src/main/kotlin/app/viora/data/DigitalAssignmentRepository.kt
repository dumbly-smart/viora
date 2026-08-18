package app.viora.data

import app.viora.database.AcademicDao
import app.viora.database.DigitalAssignmentEntity
import app.viora.database.SyncResourceEntity
import app.viora.database.AcademicChangeEntity
import app.viora.network.VtopGateway
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

class DigitalAssignmentRepository(
    private val dao: AcademicDao,
    private val gateway: VtopGateway,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(semesterId: String): Flow<List<DigitalAssignmentEntity>> = dao.observeAssignments(semesterId)

    suspend fun refresh(semesterId: String): Result<Unit> = refreshResource(
        dao = dao,
        resource = RESOURCE,
        clock = clock,
    ) { attempt ->
        val previous = dao.assignmentSnapshot(semesterId).associateBy { it.id }
        val records = gateway.digitalAssignments(semesterId).map {
            DigitalAssignmentEntity(
                semesterId = semesterId,
                id = it.id,
                courseCode = it.courseCode,
                courseTitle = it.courseTitle,
                title = it.title,
                dueEpochMillis = it.dueAt?.atZone(VTOP_ZONE)?.toInstant()?.toEpochMilli(),
                lastUpload = it.lastUpload,
                status = it.status,
                sourceEpochMillis = attempt,
            )
        }
        dao.insertChanges(records.mapNotNull { row -> previous[row.id]?.takeIf { it.dueEpochMillis != row.dueEpochMillis || it.status != row.status }?.let { AcademicChangeEntity("assignment:${row.id}:${row.dueEpochMillis}:${row.status}", "assignments", "Assignment updated", "${row.courseCode} · ${row.title}", attempt) } })
        dao.replaceAssignments(
            semesterId,
            records,
            SyncResourceEntity(RESOURCE, "FRESH", attempt, attempt, null),
        )
    }

    companion object {
        const val RESOURCE = "digital_assignments"
        val VTOP_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    }
}
