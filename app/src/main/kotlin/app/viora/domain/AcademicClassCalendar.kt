package app.viora.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

enum class AcademicDayKind { INSTRUCTIONAL, HOLIDAY, EXAM, OTHER }

private val nonInstructional = Regex("""\b(?:non[\s‐‑–—-]*instructional|no\s+instructional|no\s+classes)\b""", RegexOption.IGNORE_CASE)
private val instructional = Regex("""\binstructional\s+days?\b""", RegexOption.IGNORE_CASE)
private val examDay = Regex("""\bexam(?:ination)?s?\s+(?:day|period)s?\b""", RegexOption.IGNORE_CASE)
private val administrativeExam = Regex("""\b(?:result|registration|revaluation|resume|reopen|timetable|schedule release)\w*\b""", RegexOption.IGNORE_CASE)
private val weekdayOrders = DayOfWeek.entries.associateWith { Regex("""\b${it.name}[\s-]+order\b""", RegexOption.IGNORE_CASE) }
private val termStart = Regex("""\b(?:commencement|start|beginning)\s+of\s+(?:the\s+)?(?:classes|instruction|semester)\b|\b(?:classes|instruction|semester)\s+(?:commence|begin|start)s?\b""", RegexOption.IGNORE_CASE)

fun ExamCalendarDate.weekdayOrder(): DayOfWeek? =
    weekdayOrders.entries.firstOrNull { it.value.containsMatchIn("$title $dayType") }?.key

fun ExamCalendarDate.kind(): AcademicDayKind {
    val description = "$title $dayType"
    return when {
        description.contains("holiday", true) || nonInstructional.containsMatchIn(description) -> AcademicDayKind.HOLIDAY
        !administrativeExam.containsMatchIn(description) && (examDay.containsMatchIn(description) ||
            dayType.trim().equals("exam", true) || dayType.trim().equals("examination", true) ||
            description.examSeries() != null) -> AcademicDayKind.EXAM
        weekdayOrder() != null || instructional.containsMatchIn(description) ||
            dayType.trim().equals("instructional", true) || termStart.containsMatchIn(description) -> AcademicDayKind.INSTRUCTIONAL
        else -> AcademicDayKind.OTHER
    }
}

fun semesterInstructionStart(calendar: List<ExamCalendarDate>): LocalDate? =
    calendar.filter { it.kind() == AcademicDayKind.INSTRUCTIONAL && termStart.containsMatchIn("${it.title} ${it.dayType}") }
        .minOfOrNull { it.date }

/** Confirmed first day for the upcoming Winter 2026–27 term. */
internal fun confirmedInstructionStart(semesterName: String?): LocalDate? {
    val normalized = semesterName.orEmpty().lowercase().replace(Regex("[^a-z0-9]"), "")
    return LocalDate.of(2026, 12, 2).takeIf {
        normalized in setOf("wintersemester202627", "wintersemester20262027", "winter202627", "winter20262027")
    }
}

class AcademicClassCalendar(
    calendar: List<ExamCalendarDate>,
    exams: List<ExamWindow>,
    zone: ZoneId,
    semesterName: String? = null,
) {
    private val rowsByDate = calendar.groupBy { it.date }
    private val firstInstruction = listOfNotNull(
        semesterInstructionStart(calendar),
        confirmedInstructionStart(semesterName),
    ).maxOrNull()
    private val finalExamStart = finalExamDates(exams, calendar, zone)?.first
    val suppression = examSuppressionWindows(exams, calendar, zone)

    fun timetableDay(date: LocalDate): DayOfWeek? {
        if (firstInstruction?.let { date < it } == true || finalExamStart?.let { date >= it } == true ||
            suppression.any { it.suppresses(date) }) return null
        val rows = rowsByDate[date].orEmpty()
        if (rows.any {
            val kind = it.kind()
            kind == AcademicDayKind.HOLIDAY || kind == AcademicDayKind.EXAM
        }) return null
        return rows.firstNotNullOfOrNull { it.weekdayOrder() } ?: date.dayOfWeek
    }
}
