package app.viora

import app.viora.database.SlotWithCourse
import app.viora.network.SemesterOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeAgendaTest {
    @Test fun `home timeline merges classes assignments and exams across fourteen days`() {
        val now = time(2026, 8, 12, 8, 0)
        val assignmentDue = time(2026, 8, 12, 9, 0)
        val examStart = time(2026, 8, 13, 8, 30)
        val state = VioraUiState(
            slots = listOf(slot("class", 3, 10 * 60, 10 * 60 + 50)),
            assignments = listOf(AssignmentUi("assignment", "CSE1002", "DA 1", assignmentDue, "Pending", courseTitle = "Course Two")),
            exams = listOf(exam("exam", examStart, examStart + 90 * 60_000)),
        )

        val timeline = state.homeTimeline(now)

        assertEquals(
            listOf(HomeTimelineKind.ASSIGNMENT, HomeTimelineKind.CLASS, HomeTimelineKind.EXAM, HomeTimelineKind.CLASS),
            timeline.map(HomeTimelineItem::kind),
        )
        assertEquals(listOf("DA 1", "Course", "Course", "Course"), timeline.map(HomeTimelineItem::title))
    }

    @Test fun `home timeline excludes submitted assignments and events outside fourteen days`() {
        val now = time(2026, 8, 12, 8, 0)
        val state = VioraUiState(
            assignments = listOf(
                AssignmentUi("submitted", "CSE1001", "Submitted", time(2026, 8, 13, 9, 0), "Open", lastUpload = "answer.pdf"),
                AssignmentUi("boundary", "CSE1002", "Too late", time(2026, 8, 26, 8, 1), "Pending"),
            ),
        )

        assertTrue(state.homeTimeline(now).isEmpty())
    }

    @Test fun `home timeline dates report distinct academic days with events`() {
        val timeline = listOf(
            HomeTimelineItem("one", time(2026, 8, 12, 9, 0), HomeTimelineKind.CLASS, "One", "", ""),
            HomeTimelineItem("two", time(2026, 8, 12, 10, 0), HomeTimelineKind.ASSIGNMENT, "Two", "", ""),
            HomeTimelineItem("three", time(2026, 8, 14, 9, 0), HomeTimelineKind.EXAM, "Three", "", ""),
        )

        assertEquals(setOf(LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 14)), timeline.academicDates())
    }

    @Test fun `home timeline covers exactly fourteen academic calendar dates`() {
        val now = time(2026, 8, 12, 8, 0)
        val fifteenthDate = LocalDateTime.of(2026, 8, 26, 0, 0).atZone(zone).toInstant().toEpochMilli()
        val state = VioraUiState(
            slots = listOf(slot("early", 3, 7 * 60, 8 * 60)),
            assignments = listOf(AssignmentUi("boundary", "CSE1002", "Boundary assignment", fifteenthDate, "Pending")),
            exams = listOf(exam("boundary-exam", fifteenthDate + 7 * 60 * 60_000, fifteenthDate + 8 * 60 * 60_000)),
        )

        val timeline = state.homeTimeline(now)

        assertEquals(listOf(LocalDate.of(2026, 8, 19)), timeline.academicDates().toList())
    }

    @Test fun `home calendar day description announces date selection and events`() {
        val today = LocalDate.of(2026, 8, 12)

        assertEquals(
            "Wednesday, August 12, today, events scheduled",
            homeCalendarDayDescription(today, today, hasEvent = true),
        )
        assertEquals(
            "Thursday, August 13, no events scheduled",
            homeCalendarDayDescription(today.plusDays(1), today, hasEvent = false),
        )
    }

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

    @Test fun `home suppresses classes after own exam until later cached slot exam series finishes`() {
        val now = time(2026, 8, 18, 8, 0)
        val ownStart = time(2026, 8, 17, 9, 0)
        val laterStart = time(2026, 8, 19, 9, 0)
        val state = VioraUiState(
            slots = listOf(slot("class", 2, 10 * 60, 11 * 60)),
            exams = listOf(
                exam("own", ownStart, ownStart + 120 * 60_000),
                exam("later", laterStart, laterStart + 120 * 60_000),
            ),
        )

        val agenda = state.homeAgenda(now)

        assertTrue(agenda.examDates)
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

    @Test fun `assessments due this week includes pending and submitted work`() {
        val now = time(2026, 8, 12, 8, 0)
        val state = VioraUiState(assignments = listOf(
            AssignmentUi("pending", "CSE1001", "DA 1", now + 86_400_000, "Pending"),
            AssignmentUi("submitted", "CSE1002", "DA 2", now + 2 * 86_400_000, "Open", "answer.pdf"),
            AssignmentUi("later", "CSE1003", "DA 3", now + 8 * 86_400_000, "Pending"),
        ))

        assertEquals(listOf("pending", "submitted"), state.assessmentsDueThisWeek(now).map(AssignmentUi::id))
    }

    @Test fun `assessment course groups preserve every assignment`() {
        val state = VioraUiState(assignments = listOf(
            AssignmentUi("one", "CSE1001", "DA 1", null, "Pending", courseTitle = "Synthetic Course"),
            AssignmentUi("two", "CSE 1001 (Theory)", "DA 2", null, "Submitted", courseTitle = "Synthetic Course"),
            AssignmentUi("three", "MAT1001", "Quiz", null, "Pending", courseTitle = "Mathematics"),
        ))

        val groups = state.assessmentCourseGroups()

        assertEquals(2, groups.size)
        assertEquals(listOf("one", "two"), groups.first { it.courseCode == "CSE1001" }.assignments.map(AssignmentUi::id))
        assertEquals(listOf("three"), groups.first { it.courseCode == "MAT1001" }.assignments.map(AssignmentUi::id))
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
