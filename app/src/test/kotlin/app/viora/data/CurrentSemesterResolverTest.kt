package app.viora.data

import app.viora.network.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.ZoneId

class CurrentSemesterResolverTest {
    @Test fun `explicit historical selection remains selected without automatic discovery`() = runTest {
        val gateway = Proxy.newProxyInstance(VtopGateway::class.java.classLoader, arrayOf(VtopGateway::class.java)) { _, _, _ ->
            error("Explicit selection must not probe other semesters")
        } as VtopGateway
        val result = CurrentSemesterResolver(gateway).resolve(
            listOf(SemesterOption("current", "Current term"), SemesterOption("past", "Past term")), setOf("past"), "past", "past")
        assertEquals("past", result.selectedId)
        assertFalse(result.rolloverDetected)
    }

    @Test fun `failed exam discovery preserves saved term instead of rolling into future registration`() = runTest {
        val unused = Proxy.newProxyInstance(VtopGateway::class.java.classLoader, arrayOf(VtopGateway::class.java)) { _, _, _ ->
            error("Unexpected gateway call")
        } as VtopGateway
        val gateway = object : VtopGateway by unused {
            override suspend fun exams(semesterId: String): List<ExamRecord> = throw IOException("Synthetic network failure")
            override suspend fun academicCalendar(semesterId: String): List<AcademicCalendarRecord> = throw IOException("Synthetic network failure")
        }
        val result = CurrentSemesterResolver(gateway).resolve(
            listOf(SemesterOption("future", "Future term"), SemesterOption("current", "Current term")), setOf("current"), "current")
        assertEquals("current", result.selectedId)
        assertFalse(result.rolloverDetected)
    }

    @Test fun `keeps the FAT term until next term instruction starts even if both terms are cached`() = runTest {
        val unused = Proxy.newProxyInstance(VtopGateway::class.java.classLoader, arrayOf(VtopGateway::class.java)) { _, _, _ ->
            error("Unexpected gateway call")
        } as VtopGateway
        val finalDay = LocalDate.of(2026, 11, 20)
        val nextStart = finalDay.plusDays(10)
        val gateway = object : VtopGateway by unused {
            override suspend fun exams(semesterId: String): List<ExamRecord> = emptyList()
            override suspend fun academicCalendar(semesterId: String): List<AcademicCalendarRecord> =
                if (semesterId == "current") listOf(AcademicCalendarRecord("fat", finalDay, "FAT examinations conclude", "Exam day"))
                else listOf(AcademicCalendarRecord("start", nextStart, "Commencement of classes", "Instructional Day"))
        }
        val options = listOf(SemesterOption("next", "Next term"), SemesterOption("current", "Current term"))
        suspend fun resolve(date: LocalDate) = CurrentSemesterResolver(gateway) {
            date.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        }.resolve(options, setOf("current", "next"), "current")

        assertEquals("current", resolve(finalDay).selectedId)
        assertEquals("current", resolve(nextStart.minusDays(1)).selectedId)
        assertEquals("next", resolve(nextStart).selectedId)
        assertTrue(resolve(nextStart).rolloverDetected)
    }
}
