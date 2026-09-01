package app.viora

import app.viora.database.AcademicCalendarEntity
import app.viora.database.SlotWithCourse
import app.viora.domain.AttendanceMilestone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth

class AcademicProjectionsTest {
    @Test
    fun `groups marks by course and orders known assessment types`() {
        val sections = listOf(mark("FAT"), mark("Quiz 1"), mark("CAT 1"))
            .markSections()

        assertEquals(listOf("CAT 1", "Quiz 1", "FAT"), sections.single().marks.map(MarkUi::title))
    }

    @Test
    fun `orders unknown assessment titles after FAT alphabetically`() {
        val sections = listOf(mark("Project"), mark("FAT"), mark("Class test"), mark("Assignment 1"))
            .markSections()

        assertEquals(
            listOf("Assignment 1", "FAT", "Class test", "Project"),
            sections.single().marks.map(MarkUi::title),
        )
    }

    @Test
    fun `groups normalized course codes and falls back to title when code is blank`() {
        val sections = listOf(
            mark("CAT 1", courseCode = " cse101 ", courseTitle = "Algorithms"),
            mark("CAT 2", courseCode = "CSE101", courseTitle = "Algorithms"),
            mark("FAT", courseCode = "", courseTitle = "Operating Systems"),
        ).markSections()

        assertEquals(listOf("CSE101", "Operating Systems"), sections.map { it.courseCode })
        assertEquals(2, sections.first().marks.size)
    }

    @Test
    fun `preserves unavailable mark values`() {
        val result = listOf(mark("Quiz 1", scoredMark = null, maxMarks = null, weightageMark = null))
            .markSections()
            .single()
            .marks
            .single()

        assertEquals(null, result.scoredMark)
        assertEquals(null, result.maxMarks)
        assertEquals(null, result.weightageMark)
    }

    @Test
    fun `holiday leaves no matching course occurrence before CAT one`() {
        val now = LocalDate.of(2026, 9, 6).atStartOfDay(academicZone).toInstant().toEpochMilli()
        val holiday = LocalDate.of(2026, 9, 7)
        val state = VioraUiState(
            attendance = listOf(attendance()),
            slots = listOf(slot(dayOfWeek = 1)),
            exams = listOf(exam("CAT-I", LocalDate.of(2026, 9, 11))),
            calendar = listOf(AcademicCalendarEntity("semester", "holiday", holiday.toEpochDay(), "Holiday", "", 0)),
        )

        val milestone = state.attendanceMilestones(now)
            .single { it.milestone == AttendanceMilestone.CAT_1 }

        assertEquals(MilestoneState.NO_CLASSES, milestone.state)
        assertEquals(0, milestone.occurrenceCount)
    }

    @Test
    fun `lab allowance counts a whole session before its milestone`() {
        val now = LocalDate.of(2026, 9, 6).atStartOfDay(academicZone).toInstant().toEpochMilli()
        val state = VioraUiState(
            attendance = listOf(attendance(courseType = "Lab", blockSize = 2)),
            slots = listOf(slot(dayOfWeek = 1, type = "Lab")),
            exams = listOf(exam("CAT 1", LocalDate.of(2026, 9, 11))),
        )

        val milestone = state.attendanceMilestones(now)
            .single { it.milestone == AttendanceMilestone.CAT_1 }

        assertEquals(MilestoneState.SCHEDULED, milestone.state)
        assertEquals(1, milestone.occurrenceCount)
        assertEquals(1, milestone.skippableOccurrences)
    }

    @Test
    fun `milestone counts only slots strictly between now and exam start on the same date`() {
        val date = LocalDate.of(2026, 9, 7)
        val now = at(date, 8)
        val state = VioraUiState(
            attendance = listOf(attendance()),
            slots = listOf(
                slot(dayOfWeek = 1, id = "at-now", startMinute = 8 * 60),
                slot(dayOfWeek = 1, id = "before-exam", startMinute = 9 * 60),
                slot(dayOfWeek = 1, id = "at-exam", startMinute = 10 * 60),
                slot(dayOfWeek = 1, id = "after-exam", startMinute = 11 * 60),
            ),
            exams = listOf(exam("CAT 1", date)),
        )

        val milestone = state.attendanceMilestones(now)
            .single { it.milestone == AttendanceMilestone.CAT_1 }

        assertEquals(MilestoneState.SCHEDULED, milestone.state)
        assertEquals(1, milestone.occurrenceCount)
    }

    @Test
    fun `milestone allowance uses cached held count instead of what-if projection`() {
        val now = LocalDate.of(2026, 9, 6).atStartOfDay(academicZone).toInstant().toEpochMilli()
        val state = VioraUiState(
            attendance = listOf(attendance(sourceHeld = 20, held = 24)),
            slots = listOf(slot(dayOfWeek = 1)),
            exams = listOf(exam("CAT 1", LocalDate.of(2026, 9, 15))),
        )

        val milestone = state.attendanceMilestones(now)
            .single { it.milestone == AttendanceMilestone.CAT_1 }

        assertEquals(2, milestone.occurrenceCount)
        assertEquals(2, milestone.skippableOccurrences)
    }

    @Test
    fun `same title does not match an exam for a different course code`() {
        val now = LocalDate.of(2026, 9, 6).atStartOfDay(academicZone).toInstant().toEpochMilli()
        val state = VioraUiState(
            attendance = listOf(attendance()),
            slots = listOf(slot(dayOfWeek = 1)),
            exams = listOf(exam("CAT 1", LocalDate.of(2026, 9, 11), courseCode = "CSE102")),
        )

        val milestone = state.attendanceMilestones(now)
            .single { it.milestone == AttendanceMilestone.CAT_1 }

        assertEquals(MilestoneState.NOT_SCHEDULED, milestone.state)
    }

    @Test
    fun `calendar marks holiday exam assignment class and weekday order on their dates`() {
        val holidayDate = LocalDate.of(2026, 8, 10)
        val examDate = LocalDate.of(2026, 8, 11)
        val dueDate = LocalDate.of(2026, 8, 12)
        val weekdayOrderDate = LocalDate.of(2026, 8, 13)
        val state = VioraUiState(
            slots = listOf(slot(dayOfWeek = 1)),
            calendar = listOf(
                AcademicCalendarEntity("semester", "holiday", holidayDate.toEpochDay(), "Independence holiday", "Holiday", 0),
                AcademicCalendarEntity("semester", "order", weekdayOrderDate.toEpochDay(), "Monday order", "Instruction", 0),
            ),
            exams = listOf(exam("CAT 1", examDate)),
            assignments = listOf(AssignmentUi("assignment", "CSE101", "DA 1", at(dueDate, 20), "Open")),
        )

        val markers = state.calendarMarkers(YearMonth.of(2026, 8))

        assertTrue(AcademicCalendarMarker.HOLIDAY in markers[holidayDate].orEmpty())
        assertTrue(AcademicCalendarMarker.EXAM in markers[examDate].orEmpty())
        assertTrue(AcademicCalendarMarker.ASSIGNMENT in markers[dueDate].orEmpty())
        assertTrue(AcademicCalendarMarker.CLASS in markers[weekdayOrderDate].orEmpty())
        assertTrue(AcademicCalendarMarker.DAY_ORDER in markers[weekdayOrderDate].orEmpty())
    }

    @Test
    fun `selected day returns ordered calendar exam assignment and class events`() {
        val date = LocalDate.of(2026, 8, 13)
        val state = VioraUiState(
            slots = listOf(slot(dayOfWeek = 1)),
            calendar = listOf(AcademicCalendarEntity("semester", "order", date.toEpochDay(), "Monday order", "Instruction", 0)),
            exams = listOf(ExamUi("exam", "CSE101", "Algorithms", "CAT 1", at(date, 10), at(date, 11, 30), "AB-101", "42")),
            assignments = listOf(AssignmentUi("assignment", "CSE101", "DA 1", at(date, 20), "Open", courseTitle = "Algorithms")),
        )

        val events = state.eventsForDate(date)

        assertEquals(listOf("class:20678:slot", "exam:exam", "assignment:assignment", "calendar:semester:order"), events.map(AcademicDayEvent::id))
        assertEquals("DA 1", events.single { it.marker == AcademicCalendarMarker.ASSIGNMENT }.title)
        assertEquals("10:00 AM · Algorithms · Room AB-101 · Seat 42", events.single { it.marker == AcademicCalendarMarker.EXAM }.detail)
        assertEquals("Monday order", events.single { it.marker == AcademicCalendarMarker.DAY_ORDER }.title)
    }

    @Test
    fun `holiday selected day excludes class events through slots projection`() {
        val holidayDate = LocalDate.of(2026, 8, 10)
        val state = VioraUiState(
            slots = listOf(slot(dayOfWeek = 1)),
            calendar = listOf(AcademicCalendarEntity("semester", "holiday", holidayDate.toEpochDay(), "Holiday", "Holiday", 0)),
        )

        val events = state.eventsForDate(holidayDate)

        assertEquals(listOf(AcademicCalendarMarker.HOLIDAY), events.map(AcademicDayEvent::marker))
    }

    @Test
    fun `later holiday row suppresses classes for the date`() {
        val date = LocalDate.of(2026, 8, 10)
        val state = VioraUiState(
            slots = listOf(slot(dayOfWeek = 1)),
            calendar = listOf(
                AcademicCalendarEntity("semester", "order", date.toEpochDay(), "Monday order", "Instruction", 0),
                AcademicCalendarEntity("semester", "holiday", date.toEpochDay(), "Holiday", "Holiday", 0),
            ),
        )

        assertEquals(emptyList<SlotWithCourse>(), state.slotsForDate(date))
    }

    @Test
    fun `later day order row determines classes for the date`() {
        val date = LocalDate.of(2026, 8, 13)
        val mondaySlot = slot(dayOfWeek = 1)
        val state = VioraUiState(
            slots = listOf(mondaySlot),
            calendar = listOf(
                AcademicCalendarEntity("semester", "event", date.toEpochDay(), "Academic event", "Event", 0),
                AcademicCalendarEntity("semester", "order", date.toEpochDay(), "Monday order", "Instruction", 0),
            ),
        )

        assertEquals(listOf(mondaySlot), state.slotsForDate(date))
    }

    @Test
    fun `calendar retains normal instructional and exam day rows`() {
        val normalDate = LocalDate.of(2026, 8, 14)
        val instructionalDate = LocalDate.of(2026, 8, 15)
        val examDayDate = LocalDate.of(2026, 8, 16)
        val state = VioraUiState(
            calendar = listOf(
                AcademicCalendarEntity("semester", "normal", normalDate.toEpochDay(), "Faculty meeting", "Event", 0),
                AcademicCalendarEntity("semester", "instruction", instructionalDate.toEpochDay(), "Instructional day", "Instruction", 0),
                AcademicCalendarEntity("semester", "exam-day", examDayDate.toEpochDay(), "CAT exam day", "Exam day", 0),
            ),
        )

        val markers = state.calendarMarkers(YearMonth.of(2026, 8))
        val events = state.eventsForDate(examDayDate)

        assertEquals(setOf(AcademicCalendarMarker.CALENDAR), markers[normalDate])
        assertEquals(setOf(AcademicCalendarMarker.CALENDAR), markers[instructionalDate])
        assertEquals(setOf(AcademicCalendarMarker.CALENDAR), markers[examDayDate])
        assertEquals(
            AcademicDayEvent("calendar:semester:exam-day", AcademicCalendarMarker.CALENDAR, null, "CAT exam day", "Exam day"),
            events.single(),
        )
    }

    private fun mark(
        title: String,
        courseCode: String = "CSE101",
        courseTitle: String = "Algorithms",
        scoredMark: Double? = 8.0,
        maxMarks: Double? = 10.0,
        weightageMark: Double? = 8.0,
    ) = MarkUi(
        id = title,
        courseCode = courseCode,
        courseTitle = courseTitle,
        courseType = "Theory",
        title = title,
        maxMarks = maxMarks,
        weightagePercent = 10.0,
        status = "",
        scoredMark = scoredMark,
        weightageMark = weightageMark,
    )

    private fun attendance(
        courseType: String = "Theory",
        blockSize: Int = 1,
        sourceHeld: Int = 20,
        held: Int = sourceHeld,
    ) = AttendanceUi(
        id = "attendance",
        courseCode = "CSE101",
        courseTitle = "Algorithms",
        courseType = courseType,
        faculty = "Faculty",
        attended = 18,
        sourceHeld = sourceHeld,
        held = held,
        percentage = 90.0,
        skippable = 0,
        recovery = 0,
        blockSize = blockSize,
        skippableBlocks = 0,
        recoveryBlocks = 0,
    )

    private fun slot(
        dayOfWeek: Int,
        type: String = "Theory",
        id: String = "slot",
        startMinute: Int = 8 * 60,
    ) = SlotWithCourse(
        slotId = id,
        courseId = "course",
        code = "CSE101",
        title = "Algorithms",
        faculty = "Faculty",
        dayOfWeek = dayOfWeek,
        startMinute = startMinute,
        endMinute = startMinute + 60,
        venue = "Room",
        type = type,
    )

    private fun exam(examType: String, date: LocalDate, courseCode: String = "CSE101"): ExamUi {
        val starts = date.atStartOfDay(academicZone).plusHours(10).toInstant().toEpochMilli()
        return ExamUi("exam-$examType", courseCode, "Algorithms", examType, starts, null, "Room", "1")
    }

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atStartOfDay(academicZone).plusHours(hour.toLong()).plusMinutes(minute.toLong()).toInstant().toEpochMilli()

    private companion object {
        val academicZone: ZoneId = ZoneId.of("Asia/Kolkata")
    }
}
