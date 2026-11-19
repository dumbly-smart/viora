package app.viora.domain

import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private const val EXAM_LEAD_TIME_MILLIS = 7L * 24 * 60 * 60 * 1000

data class ExamWindow(val startsEpochMillis: Long, val endsEpochMillis: Long?, val examType: String)
data class ExamCalendarDate(val date: LocalDate, val title: String, val dayType: String)
data class ExamSuppressionWindow(
    val startDate: LocalDate,
    val resumeDate: LocalDate,
    val estimated: Boolean,
)

fun examSuppressionWindows(
    exams: List<ExamWindow>,
    calendar: List<ExamCalendarDate>,
    zone: ZoneId = ZoneId.of("Asia/Kolkata"),
): List<ExamSuppressionWindow> {
    val examSeries = exams.mapNotNull { exam -> exam.examType.examSeries()?.let { it to exam } }
        .groupBy({ it.first }, { it.second })
    val calendarSeries = calendar.mapNotNull { row -> row.description().examSeries()?.let { it to row } }
        .groupBy({ it.first }, { it.second })
    return (examSeries.keys + calendarSeries.keys).mapNotNull { seriesKey ->
        val series = examSeries[seriesKey].orEmpty()
        val relatedCalendar = calendarSeries[seriesKey].orEmpty()
        val firstCachedDate = series.minOfOrNull { Instant.ofEpochMilli(it.startsEpochMillis).atZone(zone).toLocalDate() }
        val lastCachedDate = series.maxOfOrNull { exam ->
            Instant.ofEpochMilli(exam.endsEpochMillis ?: exam.startsEpochMillis).atZone(zone).toLocalDate()
        }
        val explicitStart = relatedCalendar.filter { it.description().hasAny("begin", "start", "commence", "from") }
            .minOfOrNull(ExamCalendarDate::date)
        val explicitResume = relatedCalendar.filter {
            it.description().hasAny("instruction resume", "instructions resume", "class resume", "classes resume", "reopen")
        }.minOfOrNull(ExamCalendarDate::date)
        val explicitEnd = relatedCalendar.filter { it.description().hasAny("exam end", "exams end", "examination end", "examinations end", "conclude", "last exam") }
            .maxOfOrNull(ExamCalendarDate::date)
        val datedExams = relatedCalendar.filter { it.kind() == AcademicDayKind.EXAM }
        val start = explicitStart ?: listOfNotNull(firstCachedDate, datedExams.minOfOrNull { it.date }).minOrNull()
            ?: return@mapNotNull null
        val resume = explicitResume ?: explicitEnd?.plusDays(1)
            ?: listOfNotNull(lastCachedDate, datedExams.maxOfOrNull { it.date }).maxOrNull()
                ?.let { if (seriesKey == "fat") it.plusDays(1) else conservativeResumeDate(it, calendar) }
            ?: return@mapNotNull null
        if (!resume.isAfter(start)) null else ExamSuppressionWindow(start, resume, explicitResume == null && explicitEnd == null)
    }.sortedBy(ExamSuppressionWindow::startDate)
}

/** First and last FAT dates for one semester, excluding later administrative events. */
fun finalExamDates(exams: List<ExamWindow>, calendar: List<ExamCalendarDate>, zone: ZoneId): Pair<LocalDate, LocalDate>? {
    val dates = exams.filter { it.examType.examSeries() == "fat" }.flatMap {
        listOf(it.startsEpochMillis, it.endsEpochMillis ?: it.startsEpochMillis).map { value ->
            Instant.ofEpochMilli(value).atZone(zone).toLocalDate()
        }
    } + calendar.filter {
        it.description().examSeries() == "fat" && it.kind() == AcademicDayKind.EXAM
    }.map { it.date }
    if (dates.isEmpty()) return null
    return dates.min() to dates.max()
}

private val finalExamClosing = Regex(
    """\b(?:conclud\w*|complet\w*|ends?|end\s+of|last\s+(?:exam(?:ination)?|slot|day))\b""",
    RegexOption.IGNORE_CASE,
)
private val closingClock = Regex(
    """\b(?:conclud\w*|complet\w*|ends?)\s+at\s+(\d{1,2}):(\d{2})(?:\s*(AM|PM)\b)?""",
    RegexOption.IGNORE_CASE,
)

/** Personal slots may extend a known program boundary, but cannot establish it. */
fun programFatCompletionMillis(
    exams: List<ExamWindow>,
    calendar: List<ExamCalendarDate>,
    zone: ZoneId,
): Long? {
    val finalRows = calendar.filter {
        it.kind() == AcademicDayKind.EXAM && it.description().examSeries() == "fat"
    }
    val lastDay = finalRows.maxOfOrNull { it.date } ?: return null
    val closingRows = finalRows.filter { it.date == lastDay && finalExamClosing.containsMatchIn(it.description()) }
    if (closingRows.isEmpty()) return null
    val closingTimes = closingRows.map { row -> closingTime(row.description()) }
    val programEnd = if (closingTimes.any { it == null }) lastDay.plusDays(1).atStartOfDay(zone)
        else lastDay.atTime(closingTimes.filterNotNull().max()).atZone(zone)
    val personalEnd = exams.filter { it.examType.examSeries() == "fat" }.maxOfOrNull {
        it.endsEpochMillis ?: Instant.ofEpochMilli(it.startsEpochMillis).atZone(zone).toLocalDate()
            .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val programEndMillis = programEnd.toInstant().toEpochMilli()
    return maxOf(programEndMillis, personalEnd ?: programEndMillis)
}

private fun closingTime(description: String): LocalTime? {
    val match = closingClock.find(description) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    val period = match.groupValues[3]
    val hourOfDay = when {
        period.isEmpty() && hour in 0..23 -> hour
        period.equals("AM", true) && hour in 1..12 -> hour % 12
        period.equals("PM", true) && hour in 1..12 -> hour % 12 + 12
        else -> return null
    }
    return if (minute in 0..59) LocalTime.of(hourOfDay, minute) else null
}

fun ExamSuppressionWindow.suppresses(date: LocalDate): Boolean = !date.isBefore(startDate) && date.isBefore(resumeDate)

private fun conservativeResumeDate(lastExamDate: LocalDate, calendar: List<ExamCalendarDate>): LocalDate {
    val explicitOrder = calendar.asSequence()
        .filter { it.date.isAfter(lastExamDate) && it.weekdayOrder() != null && it.kind() == AcademicDayKind.INSTRUCTIONAL }
        .minByOrNull(ExamCalendarDate::date)
        ?.date
    var monday = lastExamDate.plusDays(1)
    while (monday.dayOfWeek != DayOfWeek.MONDAY || calendar.any { it.date == monday && it.kind() == AcademicDayKind.HOLIDAY }) {
        monday = monday.plusDays(1)
    }
    return listOfNotNull(explicitOrder, monday).minOrNull() ?: monday
}

private fun ExamCalendarDate.description(): String = "$title $dayType"
private fun String.hasAny(vararg signals: String): Boolean = signals.any { contains(it, ignoreCase = true) }
internal fun String.examSeries(): String? = when {
    Regex("\\bcat\\s*[- ]*(?:2|ii)\\b", RegexOption.IGNORE_CASE).containsMatchIn(this) -> "cat2"
    Regex("\\bcat\\s*[- ]*(?:1|i)\\b", RegexOption.IGNORE_CASE).containsMatchIn(this) -> "cat1"
    Regex("\\b(?:fat|final assessment (?:test|exam(?:ination)?)|final exam(?:ination)?s?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(this) -> "fat"
    else -> null
}

fun isExamActive(startsEpochMillis: Long, endsEpochMillis: Long?, nowEpochMillis: Long): Boolean =
    endsEpochMillis?.let { nowEpochMillis in startsEpochMillis until it } ?: false

fun shouldShowExamInSchedule(startsEpochMillis: Long, endsEpochMillis: Long?, nowEpochMillis: Long): Boolean =
    (endsEpochMillis ?: startsEpochMillis) > nowEpochMillis &&
        startsEpochMillis - nowEpochMillis <= EXAM_LEAD_TIME_MILLIS

fun isExamPeriodActive(
    exams: List<ExamWindow>,
    nowEpochMillis: Long,
    zone: ZoneId = ZoneId.of("Asia/Kolkata"),
): Boolean {
    return exams.groupBy { normalizeExamType(it.examType) }.values.any { series ->
        val completed = series.filter { it.endsEpochMillis != null }
        if (completed.isEmpty()) return@any false
        val firstStart = completed.minOf { it.startsEpochMillis }.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val lastEnd = completed.maxOf { requireNotNull(it.endsEpochMillis) }
        nowEpochMillis in firstStart until lastEnd
    }
}

private fun normalizeExamType(value: String): String = when (
    val normalized = value.lowercase().replace(Regex("[^a-z0-9]+"), "")
) {
    "cati" -> "cat1"
    "catii" -> "cat2"
    else -> normalized
}

fun overlapsExam(
    classStartMinute: Int,
    classEndMinute: Int,
    examStartMinute: Int,
    examEndMinute: Int?,
): Boolean = examEndMinute?.let {
    classStartMinute < it && classEndMinute > examStartMinute
} ?: false
