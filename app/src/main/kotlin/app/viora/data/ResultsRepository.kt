package app.viora.data

import app.viora.database.*
import app.viora.network.VtopGateway
import kotlinx.coroutines.flow.Flow

class ResultsRepository(private val dao: AcademicDao, private val gateway: VtopGateway, private val clock: () -> Long = System::currentTimeMillis) {
    fun observeMarks(semesterId: String): Flow<List<MarkEntity>> = dao.observeMarks(semesterId)
    fun observeGrades(semesterId: String): Flow<List<GradeEntity>> = dao.observeGrades(semesterId)
    fun observeSummary(): Flow<AcademicSummaryEntity?> = dao.observeAcademicSummary()
    suspend fun refresh(semesterId: String): Result<Unit> {
        val now = clock(); dao.upsertSyncResource(SyncResourceEntity(RESOURCE, "SYNCING", now, null, null))
        return runCatching {
            val previousMarks = dao.markSnapshot(semesterId).associateBy { it.id }
            val previousGrades = dao.gradeSnapshot(semesterId).associateBy { it.courseCode }
            val marks = gateway.marks(semesterId)
            val grades = gateway.grades(semesterId)
            val cgpa = gateway.cgpa()
            val markRows = marks.map { MarkEntity(semesterId, it.id, it.courseCode, it.courseTitle, it.courseType, it.title, it.maxMarks, it.weightagePercent, it.status, it.scoredMark, it.weightageMark, now) }
            val gradeRows = grades.records.map { GradeEntity(semesterId, it.courseCode, it.courseTitle, it.courseType, it.credits, it.total, it.grading, it.grade, now) }
            dao.insertChanges(markRows.mapNotNull { row -> previousMarks[row.id]?.takeIf { it.scoredMark != row.scoredMark || it.status != row.status }?.let { AcademicChangeEntity("mark:${row.id}:${row.scoredMark}:${row.status}", "marks", "Assessment mark updated", "${row.courseTitle} · ${row.title}", now) } } + gradeRows.mapNotNull { row -> previousGrades[row.courseCode]?.takeIf { it.grade != row.grade || it.total != row.total }?.let { AcademicChangeEntity("grade:${row.courseCode}:${row.grade}:${row.total}", "grades", "Grade updated", "${row.courseCode} · ${row.grade}", now) } })
            dao.replaceMarks(semesterId, markRows, SyncResourceEntity(RESOURCE, "SYNCING", now, null, null))
            dao.replaceGrades(semesterId, gradeRows, AcademicSummaryEntity("current", grades.gpa, cgpa.cgpa, cgpa.registeredCredits, cgpa.earnedCredits, cgpa.gradeCounts.entries.joinToString(",") { "${it.key}:${it.value}" }, now), SyncResourceEntity(RESOURCE, "FRESH", now, now, null))
        }.onFailure { dao.upsertSyncResource(SyncResourceEntity(RESOURCE, "ERROR", now, null, "Results could not be refreshed")) }
    }
    companion object { const val RESOURCE = "results" }
}
