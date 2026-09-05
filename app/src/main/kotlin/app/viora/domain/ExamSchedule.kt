package app.viora.domain

import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

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
): List<ExamSuppressionWindow> = exams.mapNotNull { exam -> exam.examType.examSeries() to exam }
    .filter { it.first != null }
    .groupBy({ requireNotNull(it.first) }, { it.second })
    .mapNotNull { (seriesKey, series) ->
        val relatedCalendar = calendar.filter { row -> row.description().examSeries() == seriesKey }
        val firstCachedDate = series.minOf { Instant.ofEpochMilli(it.startsEpochMillis).atZone(zone).toLocalDate() }
        val lastCachedDate = series.maxOf { exam ->
            Instant.ofEpochMilli(exam.endsEpochMillis ?: exam.startsEpochMillis).atZone(zone).toLocalDate()
        }
        val explicitStart = relatedCalendar.filter { it.description().hasAny("begin", "start", "commence", "from") }
            .minOfOrNull(ExamCalendarDate::date)
        val explicitResume = relatedCalendar.filter {
            it.description().hasAny("instruction resume", "instructions resume", "class resume", "classes resume", "reopen")
        }.minOfOrNull(ExamCalendarDate::date)
        val explicitEnd = relatedCalendar.filter { it.description().hasAny("exam end", "exams end", "examination end", "examinations end", "conclude", "last exam") }
            .maxOfOrNull(ExamCalendarDate::date)
        val start = explicitStart ?: firstCachedDate
        val resume = explicitResume ?: explicitEnd?.plusDays(1) ?: conservativeResumeDate(lastCachedDate, calendar)
        if (!resume.isAfter(start)) null else ExamSuppressionWindow(start, resume, explicitResume == null && explicitEnd == null)
    }.sortedBy(ExamSuppressionWindow::startDate)

fun ExamSuppressionWindow.suppresses(date: LocalDate): Boolean = !date.isBefore(startDate) && date.isBefore(resumeDate)

private fun conservativeResumeDate(lastExamDate: LocalDate, calendar: List<ExamCalendarDate>): LocalDate {
    val explicitOrder = calendar.asSequence()
        .filter { it.date.isAfter(lastExamDate) && it.description().hasWeekdayOrder() && !it.description().contains("holiday", true) }
        .minByOrNull(ExamCalendarDate::date)
        ?.date
    var monday = lastExamDate.plusDays(1)
    while (monday.dayOfWeek != DayOfWeek.MONDAY || calendar.any { it.date == monday && it.description().contains("holiday", true) }) {
        monday = monday.plusDays(1)
    }
    return listOfNotNull(explicitOrder, monday).minOrNull() ?: monday
}

private fun ExamCalendarDate.description(): String = "$title $dayType"
private fun String.hasAny(vararg signals: String): Boolean = signals.any { contains(it, ignoreCase = true) }
private fun String.hasWeekdayOrder(): Boolean = DayOfWeek.entries.any { day ->
    contains("${day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} order", ignoreCase = true)
}

private fun String.examSeries(): String? = when {
    Regex("\\bcat\\s*[- ]*(?:2|ii)\\b", RegexOption.IGNORE_CASE).containsMatchIn(this) -> "cat2"
    Regex("\\bcat\\s*[- ]*(?:1|i)\\b", RegexOption.IGNORE_CASE).containsMatchIn(this) -> "cat1"
    Regex("\\bfat\\b", RegexOption.IGNORE_CASE).containsMatchIn(this) -> "fat"
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
