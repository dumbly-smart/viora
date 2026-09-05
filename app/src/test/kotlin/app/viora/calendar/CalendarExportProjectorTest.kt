package app.viora.calendar

import app.viora.AssignmentUi
import app.viora.ExamUi
import app.viora.database.AcademicCalendarEntity
import app.viora.database.SlotWithCourse
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarExportProjectorTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun `classes honor holidays day orders and the 180 day range`() {
        val from = LocalDate.of(2026, 9, 7)
        val monday = slot("monday", 1, "CSE1001")
        val tuesday = slot("tuesday", 2, "MAT1001")
        val rows = listOf(
            AcademicCalendarEntity("semester", "holiday", from.toEpochDay(), "Holiday", "Holiday", 0),
            AcademicCalendarEntity("semester", "order", from.plusDays(2).toEpochDay(), "Tuesday order", "Instruction", 0),
        )

        val result = CalendarExportProjector.project("semester", from, listOf(monday, tuesday), rows, emptyList(), emptyList())

        assertFalse(result.any { it.uid.contains(":class:${from.toEpochDay()}:monday") })
        assertTrue(result.any { it.uid.contains(":class:${from.plusDays(2).toEpochDay()}:tuesday") })
        assertTrue(result.filter { ":class:" in it.uid }.all {
            val date = it.startsAt.atZone(zone).toLocalDate()
            date >= from && date <= from.plusDays(179)
        })
    }

    @Test
    fun `export contains full exam assignment and class detail`() {
        val from = LocalDate.of(2026, 9, 7)
        val examStart = from.plusDays(1).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val due = from.plusDays(2).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()

        val result = CalendarExportProjector.project(
            semesterId = "fall/2026",
            fromDate = from,
            slots = listOf(slot("slot", 1, "CSE1001")),
            calendar = emptyList(),
            exams = listOf(ExamUi("exam", "CSE1001", "Algorithms", "CAT 1", examStart, null, "AB1-201", "42")),
            assignments = listOf(AssignmentUi("assignment", "CSE1001", "DA 1", due, "Open", courseTitle = "Algorithms")),
        )

        val classEvent = result.first { ":class:" in it.uid }
        assertEquals("CSE1001 · Synthetic course", classEvent.title)
        assertTrue(classEvent.details.contains("Faculty"))
        assertEquals("AB1", classEvent.location)
        val exam = result.single { ":exam:" in it.uid }
        assertEquals(120, java.time.Duration.between(exam.startsAt, exam.endsAt).toMinutes())
        assertTrue(exam.details.contains("Seat 42"))
        val assignment = result.single { ":assignment:" in it.uid }
        assertEquals("DA 1 · Algorithms", assignment.title)
        assertEquals(30, java.time.Duration.between(assignment.startsAt, assignment.endsAt).toMinutes())
    }

    private fun slot(id: String, day: Int, code: String) = SlotWithCourse(
        slotId = id,
        courseId = "course-$id",
        code = code,
        title = "Synthetic course",
        faculty = "Faculty",
        dayOfWeek = day,
        startMinute = 9 * 60,
        endMinute = 10 * 60,
        venue = "AB1",
        type = "Theory",
    )
}
