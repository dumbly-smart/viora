package app.viora.data

import app.viora.database.AcademicDao
import app.viora.database.AttendanceEntity
import app.viora.database.SyncResourceEntity
import app.viora.network.VtopGateway
import kotlinx.coroutines.flow.Flow

class AttendanceRepository(
    private val dao: AcademicDao,
    private val gateway: VtopGateway,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(semesterId: String): Flow<List<AttendanceEntity>> = dao.observeAttendance(semesterId)

    suspend fun refresh(semesterId: String): Result<Unit> {
        val attempt = clock()
        dao.upsertSyncResource(SyncResourceEntity(RESOURCE, "SYNCING", attempt, null, null))
        return runCatching {
            val snapshot = gateway.attendance(semesterId)
            require(snapshot.records.isNotEmpty()) { "VTOP returned empty attendance" }
            val records = snapshot.records.map {
                AttendanceEntity(
                    semesterId = semesterId,
                    courseCode = it.courseCode,
                    courseTitle = it.courseTitle,
                    attended = it.attended,
                    held = it.held,
                    sourceEpochMillis = attempt,
                )
            }
            dao.replaceAttendance(
                semesterId,
                records,
                SyncResourceEntity(RESOURCE, "FRESH", attempt, attempt, null),
            )
        }.onFailure {
            dao.upsertSyncResource(
                SyncResourceEntity(
                    RESOURCE,
                    "ERROR",
                    attempt,
                    null,
                    "Attendance could not be refreshed",
                ),
            )
        }
    }

    companion object { const val RESOURCE = "attendance" }
}
