package app.viora.data

import app.viora.database.*
import app.viora.network.VtopGateway
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

class AcademicExtrasRepository(private val dao: AcademicDao, private val gateway: VtopGateway, private val clock: () -> Long = System::currentTimeMillis) {
    fun observeCalendar(semesterId: String): Flow<List<AcademicCalendarEntity>> = dao.observeCalendar(semesterId)
    fun observeMessages(): Flow<List<ClassMessageEntity>> = dao.observeMessages()
    fun observeMaterials(semesterId: String): Flow<List<CourseMaterialEntity>> = dao.observeMaterials(semesterId)
    suspend fun refresh(semesterId: String, courses: List<Pair<String, String>>): Result<Unit> {
        val now = clock(); dao.upsertSyncResource(SyncResourceEntity(RESOURCE, "SYNCING", now, null, null))
        return runCatching {
            dao.replaceCalendar(semesterId, gateway.academicCalendar(semesterId).map { AcademicCalendarEntity(semesterId, it.id, it.date.toEpochDay(), it.title, it.dayType, now) })
            dao.replaceMessages(gateway.classMessages().map { ClassMessageEntity(it.id, it.courseCode, it.courseTitle, it.faculty, it.subject, it.body, it.postedAt?.atZone(ZONE)?.toInstant()?.toEpochMilli(), now) })
            courses.distinct().forEach { (code, faculty) ->
                runCatching { gateway.courseMaterials(semesterId, code, faculty) }.getOrNull()?.let { materials -> dao.replaceMaterials(semesterId, code, materials.map { CourseMaterialEntity(semesterId, it.id, it.courseCode, it.title, it.fileName, it.downloadPath, it.postedAt?.atZone(ZONE)?.toInstant()?.toEpochMilli(), now) }) }
            }
            dao.upsertSyncResource(SyncResourceEntity(RESOURCE, "FRESH", now, now, null))
        }.onFailure { dao.upsertSyncResource(SyncResourceEntity(RESOURCE, "ERROR", now, null, "Calendar and course updates could not be refreshed")) }
    }
    companion object { const val RESOURCE = "academic-extras"; private val ZONE = ZoneId.of("Asia/Kolkata") }
}
