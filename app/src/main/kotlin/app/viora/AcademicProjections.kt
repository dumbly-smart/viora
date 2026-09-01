package app.viora

import app.viora.domain.AttendanceMilestone
import app.viora.domain.maximumSkippableOccurrences
import app.viora.domain.sameCourseCode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MarkSectionUi(
    val courseCode: String,
    val courseTitle: String,
    val marks: List<MarkUi>,
)

enum class AcademicCalendarMarker { HOLIDAY, EXAM, ASSIGNMENT, CLASS, DAY_ORDER }

data class AcademicDayEvent(
    val id: String,
    val marker: AcademicCalendarMarker,
    val at: Long?,
    val title: String,
    val detail: String,
)

internal fun VioraUiState.calendarMarkers(month: YearMonth): Map<LocalDate, Set<AcademicCalendarMarker>> {
    val markers = mutableMapOf<LocalDate, MutableSet<AcademicCalendarMarker>>()
    fun mark(date: LocalDate, marker: AcademicCalendarMarker) {
        if (YearMonth.from(date) == month) markers.getOrPut(date) { linkedSetOf() } += marker
    }

    calendar.forEach { row -> row.calendarMarker()?.let { marker -> mark(LocalDate.ofEpochDay(row.dateEpochDay), marker) } }
    exams.forEach { exam -> mark(exam.startsEpochMillis.academicDate(), AcademicCalendarMarker.EXAM) }
    assignments.forEach { assignment -> assignment.dueEpochMillis?.let { due -> mark(due.academicDate(), AcademicCalendarMarker.ASSIGNMENT) } }
    for (day in 1..month.lengthOfMonth()) {
        val date = month.atDay(day)
        if (slotsForDate(date).isNotEmpty()) mark(date, AcademicCalendarMarker.CLASS)
    }
    return markers.toSortedMap().mapValues { (_, value) -> value.toSet() }
}

internal fun VioraUiState.eventsForDate(date: LocalDate): List<AcademicDayEvent> = (
    calendar.asSequence()
        .filter { it.dateEpochDay == date.toEpochDay() }
        .mapNotNull { row ->
            row.calendarMarker()?.let { marker ->
                AcademicDayEvent(
                    id = "calendar:${row.semesterId}:${row.id}",
                    marker = marker,
                    at = null,
                    title = row.title.ifBlank { row.dayType },
                    detail = row.dayType,
                )
            }
        }
        .toList() +
        exams.asSequence()
            .filter { it.startsEpochMillis.academicDate() == date }
            .map { exam ->
                AcademicDayEvent(
                    id = "exam:${exam.id}",
                    marker = AcademicCalendarMarker.EXAM,
                    at = exam.startsEpochMillis,
                    title = listOf(exam.examType, exam.courseCode).filter(String::isNotBlank).joinToString(" · "),
                    detail = listOfNotNull(
                        exam.startsEpochMillis.asAcademicTime(),
                        exam.courseTitle.takeIf(String::isNotBlank),
                        exam.venue.takeIf(String::isNotBlank)?.let { "Room $it" },
                        exam.seatNumber.takeIf(String::isNotBlank)?.let { "Seat $it" },
                    ).joinToString(" · "),
                )
            }
            .toList() +
        assignments.asSequence()
            .filter { it.dueEpochMillis?.academicDate() == date }
            .map { assignment ->
                AcademicDayEvent(
                    id = "assignment:${assignment.id}",
                    marker = AcademicCalendarMarker.ASSIGNMENT,
                    at = assignment.dueEpochMillis,
                    title = assignment.title,
                    detail = listOfNotNull(
                        assignment.dueEpochMillis?.asAcademicTime()?.let { "Due $it" },
                        assignment.courseCode.takeIf(String::isNotBlank),
                        assignment.courseTitle.takeIf(String::isNotBlank),
                        assignment.status.takeIf(String::isNotBlank),
                    ).joinToString(" · "),
                )
            }
            .toList() +
        slotsForDate(date).map { slot ->
            val at = date.atStartOfDay(academicCalendarZone).plusMinutes(slot.startMinute.toLong()).toInstant().toEpochMilli()
            AcademicDayEvent(
                id = "class:${date.toEpochDay()}:${slot.slotId}",
                marker = AcademicCalendarMarker.CLASS,
                at = at,
                title = listOf(slot.code, slot.title).filter(String::isNotBlank).joinToString(" · "),
                detail = listOfNotNull(
                    at.asAcademicTime(),
                    slot.venue.takeIf(String::isNotBlank)?.let { "Room $it" },
                    slot.type.takeIf(String::isNotBlank),
                ).joinToString(" · "),
            )
        }
    ).sortedWith(
    compareBy<AcademicDayEvent> { it.at == null }
        .thenBy { it.at }
        .thenBy { it.marker }
        .thenBy { it.title }
        .thenBy { it.id },
)

private fun app.viora.database.AcademicCalendarEntity.calendarMarker(): AcademicCalendarMarker? {
    val description = "$title $dayType"
    return when {
        description.hasWeekdayOrder() -> AcademicCalendarMarker.DAY_ORDER
        description.contains("holiday", ignoreCase = true) -> AcademicCalendarMarker.HOLIDAY
        else -> null
    }
}

private fun String.hasWeekdayOrder(): Boolean = java.time.DayOfWeek.entries.any { day ->
    contains("${day.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)} order", ignoreCase = true)
}

private fun Long.academicDate(): LocalDate = Instant.ofEpochMilli(this).atZone(academicCalendarZone).toLocalDate()

private fun Long.asAcademicTime(): String =
    Instant.ofEpochMilli(this).atZone(academicCalendarZone).format(academicCalendarTime)

internal fun List<MarkUi>.markSections(): List<MarkSectionUi> =
    groupBy { mark ->
        mark.courseCode.trim().takeIf(String::isNotEmpty)?.uppercase(Locale.ROOT)
            ?: mark.courseTitle.trim()
    }
        .map { (key, rows) ->
            MarkSectionUi(
                courseCode = key,
                courseTitle = rows.first().courseTitle,
                marks = rows.sortedWith(compareBy<MarkUi> { assessmentRank(it.title) }.thenBy { it.title.lowercase(Locale.ROOT) }),
            )
        }
        .sortedBy { it.courseCode.lowercase(Locale.ROOT) }

enum class MilestoneState { SCHEDULED, PASSED, NOT_SCHEDULED, NO_CLASSES }

data class CourseAttendanceMilestoneUi(
    val attendance: AttendanceUi,
    val milestone: AttendanceMilestone,
    val state: MilestoneState,
    val exam: ExamUi? = null,
    val occurrenceCount: Int = 0,
    val skippableOccurrences: Int = 0,
)

internal fun VioraUiState.attendanceMilestones(nowEpochMillis: Long): List<CourseAttendanceMilestoneUi> =
    attendance.flatMap { attendance ->
        AttendanceMilestone.entries.map { milestone ->
            attendanceMilestone(attendance, milestone, nowEpochMillis)
        }
    }

private fun VioraUiState.attendanceMilestone(
    attendance: AttendanceUi,
    milestone: AttendanceMilestone,
    nowEpochMillis: Long,
): CourseAttendanceMilestoneUi {
    val matchingExams = exams
        .filter { exam -> exam.examType.toAttendanceMilestone() == milestone && exam.matches(attendance) }
        .sortedBy(ExamUi::startsEpochMillis)
    val upcomingExam = matchingExams.firstOrNull { it.startsEpochMillis > nowEpochMillis }
    if (upcomingExam == null) {
        return CourseAttendanceMilestoneUi(
            attendance = attendance,
            milestone = milestone,
            state = if (matchingExams.isEmpty()) MilestoneState.NOT_SCHEDULED else MilestoneState.PASSED,
            exam = matchingExams.lastOrNull(),
        )
    }

    val occurrenceUnits = occurrenceUnitsBefore(attendance, upcomingExam.startsEpochMillis, nowEpochMillis)
    if (occurrenceUnits.isEmpty()) {
        return CourseAttendanceMilestoneUi(
            attendance = attendance,
            milestone = milestone,
            state = MilestoneState.NO_CLASSES,
            exam = upcomingExam,
        )
    }

    return CourseAttendanceMilestoneUi(
        attendance = attendance,
        milestone = milestone,
        state = MilestoneState.SCHEDULED,
        exam = upcomingExam,
        occurrenceCount = occurrenceUnits.size,
        skippableOccurrences = maximumSkippableOccurrences(
            attended = attendance.attended,
            held = attendance.held,
            targetPercent = attendanceTarget,
            occurrenceUnits = occurrenceUnits,
        ),
    )
}

private fun VioraUiState.occurrenceUnitsBefore(
    attendance: AttendanceUi,
    examStartsEpochMillis: Long,
    nowEpochMillis: Long,
): List<Int> {
    var date = Instant.ofEpochMilli(nowEpochMillis).atZone(attendanceMilestoneZone).toLocalDate().plusDays(1)
    val examDate = Instant.ofEpochMilli(examStartsEpochMillis).atZone(attendanceMilestoneZone).toLocalDate()
    val occurrenceUnits = mutableListOf<Int>()
    while (date < examDate) {
        slotsForDate(date)
            .filter { slot -> attendanceFor(slot)?.id == attendance.id }
            .forEach { occurrenceUnits += attendance.blockSize }
        date = date.plusDays(1)
    }
    return occurrenceUnits
}

private fun ExamUi.matches(attendance: AttendanceUi): Boolean =
    sameCourseCode(courseCode, attendance.courseCode) ||
        ((courseCode.isBlank() || attendance.courseCode.isBlank()) &&
            courseTitle.isNotBlank() && attendance.courseTitle.isNotBlank() &&
            courseTitle.equals(attendance.courseTitle, true))

private fun String.toAttendanceMilestone(): AttendanceMilestone? = when (
    uppercase(Locale.ROOT).filter(Char::isLetterOrDigit)
) {
    "CAT1", "CATI" -> AttendanceMilestone.CAT_1
    "CAT2", "CATII" -> AttendanceMilestone.CAT_2
    "FAT" -> AttendanceMilestone.FAT
    else -> null
}

private fun assessmentRank(title: String): Int = when (title.trim().lowercase(Locale.ROOT)) {
    "cat 1" -> 0
    "cat 2" -> 1
    else -> when {
        title.trim().startsWith("quiz", ignoreCase = true) -> 2
        title.trim().startsWith("assignment", ignoreCase = true) -> 3
        title.trim().equals("fat", ignoreCase = true) -> 4
        else -> 5
    }
}

private val attendanceMilestoneZone: ZoneId = ZoneId.of("Asia/Kolkata")
private val academicCalendarZone: ZoneId = ZoneId.of("Asia/Kolkata")
private val academicCalendarTime: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
