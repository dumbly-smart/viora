package app.viora.data

import app.viora.database.*
import app.viora.network.VtopGateway
import app.viora.domain.sameCourseCode
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

class AcademicExtrasRepository(private val dao: AcademicDao, private val gateway: VtopGateway, private val clock: () -> Long = System::currentTimeMillis) {
    fun observeCalendar(semesterId: String): Flow<List<AcademicCalendarEntity>> = dao.observeCalendar(semesterId)
    fun observeMessages(): Flow<List<ClassMessageEntity>> = dao.observeMessages()
    fun observeMaterials(semesterId: String): Flow<List<CourseMaterialEntity>> = dao.observeMaterials(semesterId)
    suspend fun refresh(semesterId: String, courses: List<Pair<String, String>>): Result<Unit> {
        val now = clock(); dao.upsertSyncResource(SyncResourceEntity(RESOURCE, "SYNCING", now, null, null))
        return runCatching {
            val resourceFailures = mutableListOf<String>()
            runCatching { gateway.academicCalendar(semesterId) }
                .onSuccess { records -> dao.replaceCalendar(semesterId, records.map { AcademicCalendarEntity(semesterId, it.id, it.date.toEpochDay(), it.title, it.dayType, now) }) }
                .onFailure { resourceFailures += "calendar: ${it.message}" }
            runCatching { gateway.classMessages() }
                .onSuccess { records ->
                    val previousMessages = dao.messageSnapshot().map { it.id }.toSet()
                    val messages = records.map { ClassMessageEntity(it.id, it.courseCode, it.courseTitle, it.faculty, it.subject, it.body, it.postedAt?.atZone(ZONE)?.toInstant()?.toEpochMilli(), now) }
                    dao.insertChanges(messages.filterNot { it.id in previousMessages }.map { AcademicChangeEntity("message:${it.id}", "messages", "New class message", it.subject.ifBlank { it.body.take(100) }, now) })
                    dao.replaceMessages(messages)
                }
                .onFailure { resourceFailures += "messages: ${it.message}" }
            val materialFailures = mutableListOf<Throwable>()
            var materialSuccesses = 0
            val attendance = dao.attendanceSnapshot(semesterId)
            courses.distinctBy { it.first }.forEach { (code, _) ->
                val title = attendance.firstOrNull { sameCourseCode(it.courseCode, code) }?.courseTitle.orEmpty()
                val attempt = runCatching { gateway.courseMaterials(semesterId, code, title, "") }
                if (attempt.isSuccess) materialSuccesses++ else attempt.exceptionOrNull()?.let(materialFailures::add)
                attempt.getOrNull()?.let { fetched ->
                    val materials = fetched.distinctBy { it.id }
                    val rows = materials.map { CourseMaterialEntity(semesterId, it.id, code, it.title, it.fileName, it.downloadPath, it.postedAt?.atZone(ZONE)?.toInstant()?.toEpochMilli(), now) }
                    dao.replaceMaterials(semesterId, code, rows)
                }
            }
            if (courses.isNotEmpty() && materialSuccesses == 0 && materialFailures.isNotEmpty()) {
                resourceFailures += "materials: ${materialFailures.first().message}"
            }
            check(resourceFailures.isEmpty()) { resourceFailures.joinToString("; ") }
            dao.upsertSyncResource(SyncResourceEntity(RESOURCE, "FRESH", now, now, null))
        }.onFailure { error ->
            dao.upsertSyncResource(SyncResourceEntity(RESOURCE, "ERROR", now, null, error.message?.take(300) ?: "Calendar and course updates could not be refreshed"))
        }
    }
    companion object { const val RESOURCE = "academic-extras"; private val ZONE = ZoneId.of("Asia/Kolkata") }
}
