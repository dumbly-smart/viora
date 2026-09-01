package app.viora

import app.viora.database.AcademicCalendarEntity
import app.viora.database.SlotWithCourse
import app.viora.domain.AttendanceMilestone
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

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
    ) = AttendanceUi(
        id = "attendance",
        courseCode = "CSE101",
        courseTitle = "Algorithms",
        courseType = courseType,
        faculty = "Faculty",
        attended = 18,
        sourceHeld = 20,
        held = 20,
        percentage = 90.0,
        skippable = 0,
        recovery = 0,
        blockSize = blockSize,
        skippableBlocks = 0,
        recoveryBlocks = 0,
    )

    private fun slot(dayOfWeek: Int, type: String = "Theory") = SlotWithCourse(
        slotId = "slot",
        courseId = "course",
        code = "CSE101",
        title = "Algorithms",
        faculty = "Faculty",
        dayOfWeek = dayOfWeek,
        startMinute = 8 * 60,
        endMinute = 9 * 60,
        venue = "Room",
        type = type,
    )

    private fun exam(examType: String, date: LocalDate, courseCode: String = "CSE101"): ExamUi {
        val starts = date.atStartOfDay(academicZone).plusHours(10).toInstant().toEpochMilli()
        return ExamUi("exam-$examType", courseCode, "Algorithms", examType, starts, null, "Room", "1")
    }

    private companion object {
        val academicZone: ZoneId = ZoneId.of("Asia/Kolkata")
    }
}
