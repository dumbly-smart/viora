package app.viora.data

import app.viora.database.AcademicDao
import app.viora.database.ExamEntity
import app.viora.database.SyncResourceEntity
import app.viora.database.AcademicChangeEntity
import app.viora.network.VtopGateway
import kotlinx.coroutines.flow.Flow

class ExamRepository(
    private val dao: AcademicDao,
    private val gateway: VtopGateway,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(semesterId: String): Flow<List<ExamEntity>> = dao.observeExams(semesterId)

    suspend fun refresh(semesterId: String): Result<Unit> = refreshResource(
        dao = dao,
        resource = RESOURCE,
        clock = clock,
    ) { attempt ->
        val previous = dao.examSnapshot(semesterId).associateBy { it.id }
        val records = gateway.exams(semesterId).map {
            ExamEntity(
                semesterId = semesterId,
                id = it.id,
                courseCode = it.courseCode,
                courseTitle = it.courseTitle,
                examType = it.examType,
                startsEpochMillis = it.startsAt.atZone(DigitalAssignmentRepository.VTOP_ZONE).toInstant().toEpochMilli(),
                endsEpochMillis = it.endsAt?.atZone(DigitalAssignmentRepository.VTOP_ZONE)?.toInstant()?.toEpochMilli(),
                venue = it.venue,
                seatNumber = it.seatNumber,
                sourceEpochMillis = attempt,
            )
        }
        val changes = records.mapNotNull { current -> previous[current.id]?.takeIf { it.startsEpochMillis != current.startsEpochMillis || it.endsEpochMillis != current.endsEpochMillis || it.venue != current.venue || it.seatNumber != current.seatNumber }?.let { AcademicChangeEntity("exam:${current.id}:${current.startsEpochMillis}:${current.venue}", "exams", "Exam schedule changed", "${current.examType} · ${current.courseCode} · ${current.venue}", attempt) } }
        dao.insertChanges(changes)
        dao.replaceExams(
            semesterId,
            records,
            SyncResourceEntity(RESOURCE, "FRESH", attempt, attempt, null),
        )
    }

    companion object { const val RESOURCE = "exams" }
}
