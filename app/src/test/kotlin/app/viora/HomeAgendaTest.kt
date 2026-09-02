package app.viora

import app.viora.database.SlotWithCourse
import app.viora.network.SemesterOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeAgendaTest {
    @Test fun `orders assignments by due date with unknown deadlines last`() {
        val later = AssignmentUi("later", "CSE1002", "DA 2", 2_000, "Open", courseTitle = "Course Two")
        val unknown = AssignmentUi("unknown", "CSE1003", "DA 3", null, "Open", courseTitle = "Course Three")
        val sooner = AssignmentUi("sooner", "CSE1001", "DA 1", 1_000, "Open", courseTitle = "Course One")

        assertEquals(listOf("sooner", "later", "unknown"), listOf(later, unknown, sooner).orderedByDueDate().map(AssignmentUi::id))
    }

    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun `normal home merges upcoming classes and exams chronologically`() {
        val now = time(2026, 8, 12, 8, 0)
        val examStart = time(2026, 8, 13, 9, 0)
        val state = VioraUiState(
            slots = listOf(slot("class", 3, 10 * 60, 10 * 60 + 50)),
            exams = listOf(exam("exam", examStart, examStart + 90 * 60_000)),
        )

        val agenda = state.homeAgenda(now)

        assertFalse(agenda.examDates)
        assertEquals(listOf("class", "exam"), agenda.items.take(2).map { if (it.slot != null) "class" else "exam" })
    }

    @Test fun `between first exam start and final exam end home contains only exams`() {
        val now = time(2026, 8, 12, 10, 0)
        val currentStart = time(2026, 8, 12, 9, 0)
        val nextStart = time(2026, 8, 13, 9, 0)
        val state = VioraUiState(
            slots = listOf(slot("later-class", 3, 15 * 60, 15 * 60 + 50)),
            exams = listOf(
                exam("current", currentStart, currentStart + 120 * 60_000),
                exam("next", nextStart, nextStart + 120 * 60_000),
            ),
        )

        val agenda = state.homeAgenda(now)

        assertTrue(agenda.examDates)
        assertTrue(agenda.items.first().isActiveExam)
        assertEquals(listOf("current", "next"), agenda.items.map { it.exam?.id })
        assertTrue(agenda.items.none { it.slot != null })
    }

    @Test fun `exam only home starts at midnight on the first exam date`() {
        val now = time(2026, 8, 12, 7, 0)
        val firstStart = time(2026, 8, 12, 9, 0)
        val state = VioraUiState(
            slots = listOf(slot("morning-class", 3, 8 * 60, 8 * 60 + 50)),
            exams = listOf(exam("first", firstStart, firstStart + 120 * 60_000)),
        )

        val agenda = state.homeAgenda(now)

        assertTrue(agenda.examDates)
        assertEquals(listOf("first"), agenda.items.map { it.exam?.id })
    }

    @Test fun `empty home gets weekend copy`() {
        val saturday = ZonedDateTime.of(2026, 8, 15, 10, 0, 0, 0, zone)

        assertEquals("Weekend detected", emptyHomeCopy(saturday).first)
    }

    @Test fun `home moves to next class day after todays classes finish`() {
        val now = time(2026, 8, 12, 18, 0)
        val state = VioraUiState(slots = listOf(
            slot("finished", 3, 10 * 60, 11 * 60),
            slot("tomorrow", 4, 9 * 60, 10 * 60),
        ))

        val agenda = state.homeAgenda(now)

        assertEquals(listOf("tomorrow"), agenda.items.map { it.slot?.slotId })
    }

    @Test fun `home due assignments excludes uploaded work`() {
        val now = time(2026, 8, 12, 8, 0)
        val due = now + 24 * 60 * 60 * 1000
        val state = VioraUiState(assignments = listOf(
            AssignmentUi("open", "CSE1001", "DA 1", due, "Not uploaded"),
            AssignmentUi("done", "CSE1002", "DA 2", due, "Open", "12-Aug-2026 10:00 PM"),
            AssignmentUi("stale", "CSE1003", "DA 3", due, "Submitted", "File Not Uploaded"),
        ))

        assertEquals(listOf("open"), state.homeDueAssignments(now).map(AssignmentUi::id))
    }

    @Test fun `cached agenda remains available while a refresh is loading`() {
        val now = time(2026, 8, 12, 8, 0)
        val state = VioraUiState(
            loading = true,
            slots = listOf(slot("cached", 3, 10 * 60, 11 * 60)),
        )

        assertEquals(listOf("cached"), state.homeAgenda(now).items.map { it.slot?.slotId })
    }

    @Test fun `saved semester is ready before network discovery`() {
        assertEquals(SemesterOption("semester-id", "Fall 2026"), savedSemesterOption("semester-id", "Fall 2026"))
        assertEquals(SemesterOption("semester-id", "semester-id"), savedSemesterOption("semester-id", null))
        assertEquals(null, savedSemesterOption(null, null))
    }

    private fun time(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun exam(id: String, start: Long, end: Long) = ExamUi(
        id, "CODE", "Course", "CAT 1", start, end, "Room", "Seat",
    )

    private fun slot(id: String, day: Int, start: Int, end: Int) = SlotWithCourse(
        id, "course-$id", "CODE", "Course", "Faculty", day, start, end, "Room", "Theory",
    )
}
