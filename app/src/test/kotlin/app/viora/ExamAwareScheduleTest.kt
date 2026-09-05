package app.viora

import app.viora.database.SlotWithCourse
import app.viora.database.AcademicCalendarEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ExamAwareScheduleTest {
    @Test fun `schedule suppresses the complete date during a cached exam series`() {
        val date = LocalDate.of(2026, 8, 17)
        val examStart = LocalDateTime.of(2026, 8, 17, 10, 0)
            .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        val state = VioraUiState(
            slots = listOf(slot("before", 480, 540), slot("during", 630, 680), slot("after", 720, 770)),
            exams = listOf(ExamUi("exam", "CSE1001", "Course", "CAT 1", examStart, examStart + 90 * 60_000, "AB-101", "42")),
        )

        assertEquals(emptyList<String>(), state.slotsForDate(date).map { it.slotId })
    }

    @Test fun `cached later slot exam extends global suppression to next instructional Monday`() {
        val date = LocalDate.of(2026, 8, 17)
        val examStart = LocalDateTime.of(2026, 8, 17, 10, 0)
            .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        val laterExamStart = LocalDateTime.of(2026, 8, 19, 14, 0)
            .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        val state = VioraUiState(
            slots = listOf(slot("class", 480, 540)),
            exams = listOf(
                ExamUi("own", "CSE1001", "Course", "CAT 1", examStart, examStart + 90 * 60_000, "AB-101", "42"),
                ExamUi("later-slot", "CSE1002", "Other Course", "CAT-I", laterExamStart, laterExamStart + 90 * 60_000, "AB-102", "43"),
            ),
        )

        assertEquals(emptyList<String>(), state.slotsForDate(LocalDate.of(2026, 8, 20)).map { it.slotId })
        assertEquals(listOf("class"), state.copy(slots = listOf(slot("class", 480, 540, dayOfWeek = 1))).slotsForDate(LocalDate.of(2026, 8, 24)).map { it.slotId })
    }

    @Test fun `explicit VIT exam end and resumption dates take precedence`() {
        val examStart = LocalDateTime.of(2026, 8, 17, 10, 0)
            .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        val state = VioraUiState(
            slots = listOf(slot("thursday", 480, 540, dayOfWeek = 4), slot("friday", 480, 540, dayOfWeek = 5)),
            exams = listOf(ExamUi("exam", "CSE1001", "Course", "CAT 1", examStart, examStart + 90 * 60_000, "AB-101", "42")),
            calendar = listOf(
                AcademicCalendarEntity("semester", "end", LocalDate.of(2026, 8, 20).toEpochDay(), "CAT 1 examinations end", "Exam period", 0),
                AcademicCalendarEntity("semester", "resume", LocalDate.of(2026, 8, 21).toEpochDay(), "Instruction resumes after CAT 1", "Instruction", 0),
            ),
        )

        assertEquals(emptyList<String>(), state.slotsForDate(LocalDate.of(2026, 8, 20)).map { it.slotId })
        assertEquals(listOf("friday"), state.slotsForDate(LocalDate.of(2026, 8, 21)).map { it.slotId })
        assertEquals(false, state.examSuppressionWindows().single().estimated)
    }

    private fun slot(id: String, start: Int, end: Int, dayOfWeek: Int = 1) = SlotWithCourse(
        slotId = id,
        courseId = "course-$id",
        code = "CSE1001",
        title = "Course",
        faculty = "Faculty",
        dayOfWeek = dayOfWeek,
        startMinute = start,
        endMinute = end,
        venue = "Room",
        type = "Theory",
    )
}
