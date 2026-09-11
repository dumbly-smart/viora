package app.viora.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.viora.data.ResultsRepository
import app.viora.network.*
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultsRepositoryInstrumentedTest {
    @Test fun marksPersistAndCachedGradesRemainWhenGradeRefreshFails() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, VioraDatabase::class.java).allowMainThreadQueries().build()
        val dao = database.academicDao()
        dao.upsertGrades(listOf(GradeEntity("semester", "CSE1001", "Synthetic Course", "Theory", 3.0, 75.0, "Relative", "cached-grade", 1_000)))

        val result = ResultsRepository(dao, MarksOnlyGateway(), clock = { 2_000 }).refresh("semester")

        assertTrue(result.isFailure)
        assertEquals(listOf("new-mark"), dao.markSnapshot("semester").map(MarkEntity::id))
        assertEquals(listOf("cached-grade"), dao.gradeSnapshot("semester").map(GradeEntity::grade))
        database.close()
    }

    private class MarksOnlyGateway : VtopGateway {
        override suspend fun marks(semesterId: String) = listOf(MarkRecord("new-mark", "CSE1001", "Synthetic Course", "Theory", "Quiz", 10.0, null, "Published", 9.0, null))
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
