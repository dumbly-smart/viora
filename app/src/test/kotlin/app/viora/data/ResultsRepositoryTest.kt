package app.viora.data

import app.viora.database.AcademicDao
import app.viora.database.GradeEntity
import app.viora.database.MarkEntity
import app.viora.database.SyncResourceEntity
import app.viora.network.*
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsRepositoryTest {
    @Test fun `new marks persist when grades fail while cached grades remain`() = runTest {
        val store = RecordingDao()
        val result = ResultsRepository(store.dao, MarksOnlyGateway(), clock = { 2_000 }).refresh("semester")

        assertTrue(result.isFailure)
        assertEquals(listOf("new-mark"), store.marks.map(MarkEntity::id))
        assertEquals(listOf("cached-grade"), store.grades.map(GradeEntity::grade))
        assertEquals("ERROR", store.sync?.status)
    }

    @Suppress("UNCHECKED_CAST")
    private class RecordingDao {
        var marks = emptyList<MarkEntity>()
        var grades = listOf(GradeEntity("semester", "CSE1001", "Synthetic Course", "Theory", 3.0, 75.0, "Relative", "cached-grade", 1_000))
        var sync: SyncResourceEntity? = null

        val dao: AcademicDao = Proxy.newProxyInstance(
            AcademicDao::class.java.classLoader,
            arrayOf(AcademicDao::class.java),
        ) { _, method, args ->
            when (method.name) {
                "markSnapshot" -> marks
                "gradeSnapshot" -> grades
                "replaceMarks" -> { marks = args!![1] as List<MarkEntity>; sync = args[2] as SyncResourceEntity; Unit }
                "replaceGrades" -> { grades = args!![1] as List<GradeEntity>; sync = args[3] as SyncResourceEntity; Unit }
                "upsertSyncResource" -> { sync = args!![0] as SyncResourceEntity; Unit }
                "insertChanges" -> Unit
                "observeMarks", "observeGrades", "observeAcademicSummary" -> emptyFlow<Any?>()
                "toString" -> "RecordingAcademicDao"
                else -> error("Unexpected DAO call: ${method.name}")
            }
        } as AcademicDao
    }

    private class MarksOnlyGateway : VtopGateway {
        override suspend fun marks(semesterId: String) = listOf(
            MarkRecord("new-mark", "CSE1001", "Synthetic Course", "Theory", "Quiz", 10.0, null, "Published", 9.0, null),
        )
        override suspend fun grades(semesterId: String): GradeSnapshot = throw IOException("synthetic grade failure")
        override suspend fun sessionState() = SessionState.Active
        override suspend fun login(username: String, password: CharArray) = SessionState.Active
        override suspend fun semesters() = emptyList<SemesterOption>()
        override suspend fun timetable(semesterId: String) = TimetableSnapshot(emptyList(), emptyList())
        override suspend fun attendance(semesterId: String) = AttendanceSnapshot(emptyList())
        override suspend fun digitalAssignments(semesterId: String) = emptyList<DigitalAssignmentRecord>()
        override suspend fun uploadDigitalAssignment(semesterId: String, assignmentId: String, fileName: String, mimeType: String, bytes: ByteArray) = Unit
        override suspend fun exams(semesterId: String) = emptyList<ExamRecord>()
        override suspend fun cgpa() = CgpaSnapshot(null, null, null, emptyMap())
        override suspend fun academicCalendar(semesterId: String) = emptyList<AcademicCalendarRecord>()
        override suspend fun classMessages() = emptyList<ClassMessageRecord>()
        override suspend fun courseMaterials(semesterId: String, courseCode: String, courseTitle: String, faculty: String) = emptyList<CourseMaterialRecord>()
        override suspend fun importInteractiveSession(cookieHeader: String) = SessionState.Active
        override suspend fun downloadCourseMaterial(downloadPath: String) = ByteArray(0)
        override suspend fun clearLocalSession() = Unit
    }
}
