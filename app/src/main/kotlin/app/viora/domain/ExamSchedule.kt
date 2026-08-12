package app.viora.domain

private const val EXAM_LEAD_TIME_MILLIS = 7L * 24 * 60 * 60 * 1000

data class ExamWindow(val startsEpochMillis: Long, val endsEpochMillis: Long?, val examType: String)

fun isExamActive(startsEpochMillis: Long, endsEpochMillis: Long?, nowEpochMillis: Long): Boolean =
    endsEpochMillis?.let { nowEpochMillis in startsEpochMillis until it } ?: false

fun shouldShowExamInSchedule(startsEpochMillis: Long, endsEpochMillis: Long?, nowEpochMillis: Long): Boolean =
    (endsEpochMillis ?: startsEpochMillis) > nowEpochMillis &&
        startsEpochMillis - nowEpochMillis <= EXAM_LEAD_TIME_MILLIS

fun isExamPeriodActive(exams: List<ExamWindow>, nowEpochMillis: Long): Boolean {
    return exams.groupBy { normalizeExamType(it.examType) }.values.any { series ->
        val completed = series.filter { it.endsEpochMillis != null }
        if (completed.isEmpty()) return@any false
        val firstStart = completed.minOf { it.startsEpochMillis }
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
