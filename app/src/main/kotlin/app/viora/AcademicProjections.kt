package app.viora

import app.viora.domain.AttendanceMilestone
import app.viora.domain.maximumSkippableOccurrences
import app.viora.domain.sameCourseCode
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

data class MarkSectionUi(
    val courseCode: String,
    val courseTitle: String,
    val marks: List<MarkUi>,
)

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
