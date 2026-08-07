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
            val previousMessages = dao.messageSnapshot().map { it.id }.toSet()
            val messages = gateway.classMessages().map { ClassMessageEntity(it.id, it.courseCode, it.courseTitle, it.faculty, it.subject, it.body, it.postedAt?.atZone(ZONE)?.toInstant()?.toEpochMilli(), now) }
            dao.insertChanges(messages.filterNot { it.id in previousMessages }.map { AcademicChangeEntity("message:${it.id}", "messages", "New class message", it.subject.ifBlank { it.body.take(100) }, now) })
            dao.replaceMessages(messages)
            courses.distinct().forEach { (code, faculty) ->
                runCatching { gateway.courseMaterials(semesterId, code, faculty) }.getOrNull()?.let { materials ->
                    val old = dao.materialSnapshot(semesterId, code).map { it.id }.toSet()
                    val rows = materials.map { CourseMaterialEntity(semesterId, it.id, it.courseCode, it.title, it.fileName, it.downloadPath, it.postedAt?.atZone(ZONE)?.toInstant()?.toEpochMilli(), now) }
                    dao.insertChanges(rows.filterNot { it.id in old }.map { AcademicChangeEntity("material:${it.id}", "materials", "New course material", "${it.courseCode} · ${it.title}", now) })
                    dao.replaceMaterials(semesterId, code, rows)
                }
            }
            dao.upsertSyncResource(SyncResourceEntity(RESOURCE, "FRESH", now, now, null))
        }.onFailure { dao.upsertSyncResource(SyncResourceEntity(RESOURCE, "ERROR", now, null, "Calendar and course updates could not be refreshed")) }
    }
    companion object { const val RESOURCE = "academic-extras"; private val ZONE = ZoneId.of("Asia/Kolkata") }
}
