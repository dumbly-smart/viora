package app.viora.data

import app.viora.database.*
import app.viora.network.VtopGateway
import kotlinx.coroutines.flow.Flow
import java.io.IOException

class ResultsRepository(private val dao: AcademicDao, private val gateway: VtopGateway, private val clock: () -> Long = System::currentTimeMillis) {
    fun observeMarks(semesterId: String): Flow<List<MarkEntity>> = dao.observeMarks(semesterId)
    fun observeGrades(semesterId: String): Flow<List<GradeEntity>> = dao.observeGrades(semesterId)
    fun observeSummary(): Flow<AcademicSummaryEntity?> = dao.observeAcademicSummary()
    suspend fun refresh(semesterId: String): Result<Unit> {
        val now = clock()
        dao.upsertSyncResource(SyncResourceEntity(RESOURCE, "SYNCING", now, null, null))
        val marksResult = runCatching {
            val previousMarks = dao.markSnapshot(semesterId).associateBy { it.id }
            val marks = gateway.marks(semesterId)
            val markRows = marks.map {
                MarkEntity(semesterId, it.id, it.courseCode, it.courseTitle, it.courseType, it.title, it.maxMarks, it.weightagePercent, it.status, it.scoredMark, it.weightageMark, now)
            }
            dao.insertChanges(markRows.mapNotNull { row ->
                previousMarks[row.id]?.takeIf { it.scoredMark != row.scoredMark || it.status != row.status }?.let {
                    AcademicChangeEntity("mark:${row.id}:${row.scoredMark}:${row.status}", "marks", "Assessment mark updated", "${row.courseTitle} · ${row.title}", now)
                }
            })
            dao.replaceMarks(semesterId, markRows, SyncResourceEntity(RESOURCE, "SYNCING", now, null, null))
        }
        val gradesResult = runCatching {
            val previousGrades = dao.gradeSnapshot(semesterId).associateBy { it.courseCode }
            val grades = gateway.grades(semesterId)
            val cgpa = gateway.cgpa()
            val gradeRows = grades.records.map {
                GradeEntity(semesterId, it.courseCode, it.courseTitle, it.courseType, it.credits, it.total, it.grading, it.grade, now)
            }
            dao.insertChanges(gradeRows.mapNotNull { row ->
                previousGrades[row.courseCode]?.takeIf { it.grade != row.grade || it.total != row.total }?.let {
                    AcademicChangeEntity("grade:${row.courseCode}:${row.grade}:${row.total}", "grades", "Grade updated", "${row.courseCode} · ${row.grade}", now)
                }
            })
            dao.replaceGrades(
                semesterId,
                gradeRows,
                AcademicSummaryEntity("current", grades.gpa, cgpa.cgpa, cgpa.registeredCredits, cgpa.earnedCredits, cgpa.gradeCounts.entries.joinToString(",") { "${it.key}:${it.value}" }, now),
                SyncResourceEntity(RESOURCE, "SYNCING", now, null, null),
            )
        }
        if (marksResult.isSuccess && gradesResult.isSuccess) {
            dao.upsertSyncResource(SyncResourceEntity(RESOURCE, "FRESH", now, now, null))
            return Result.success(Unit)
        }
        val message = when {
            marksResult.isSuccess -> "Marks updated; grades and summary could not be refreshed"
            gradesResult.isSuccess -> "Grades updated; marks could not be refreshed"
            else -> "Results could not be refreshed"
        }
        dao.upsertSyncResource(SyncResourceEntity(RESOURCE, "ERROR", now, null, message))
        val failure = listOfNotNull(marksResult.exceptionOrNull(), gradesResult.exceptionOrNull())
            .firstOrNull { it is app.viora.network.AuthenticationException }
            ?: IOException(message)
        return Result.failure(failure)
    }
    companion object { const val RESOURCE = "results" }
}
