package app.viora

import app.viora.database.SlotWithCourse
import app.viora.database.AcademicCalendarEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ExamAwareScheduleTest {
    @Test fun `schedule removes only classes that overlap an exam`() {
        val date = LocalDate.of(2026, 8, 17)
        val examStart = LocalDateTime.of(2026, 8, 17, 10, 0)
            .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        val state = VioraUiState(
            slots = listOf(slot("before", 480, 540), slot("during", 630, 680), slot("after", 720, 770)),
            exams = listOf(ExamUi("exam", "CSE1001", "Course", "CAT 1", examStart, examStart + 90 * 60_000, "AB-101", "42")),
        )

        assertEquals(listOf("before", "after"), state.slotsForDate(date).map { it.slotId })
    }

    @Test fun `exam day calendar label does not hide non overlapping classes`() {
        val date = LocalDate.of(2026, 8, 17)
        val examStart = LocalDateTime.of(2026, 8, 17, 10, 0)
            .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        val state = VioraUiState(
            slots = listOf(slot("before", 480, 540), slot("during", 630, 680), slot("after", 720, 770)),
            exams = listOf(ExamUi("exam", "CSE1001", "Course", "CAT 1", examStart, examStart + 90 * 60_000, "AB-101", "42")),
            calendar = listOf(AcademicCalendarEntity("semester", "event", date.toEpochDay(), "CAT exam day", "Exam day", 0)),
        )

        assertEquals(listOf("before", "after"), state.slotsForDate(date).map { it.slotId })
    }

    private fun slot(id: String, start: Int, end: Int) = SlotWithCourse(
        slotId = id,
        courseId = "course-$id",
        code = "CSE1001",
        title = "Course",
        faculty = "Faculty",
        dayOfWeek = 1,
        startMinute = start,
        endMinute = end,
        venue = "Room",
        type = "Theory",
    )
}
