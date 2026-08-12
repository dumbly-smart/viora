package app.viora.domain

fun examDurationMinutes(examType: String): Int = when {
    examType.contains("quiz", true) -> 60
    examType.contains("cat", true) -> 90
    else -> 180
}

private const val EXAM_LEAD_TIME_MILLIS = 7L * 24 * 60 * 60 * 1000

fun isExamActive(startsEpochMillis: Long, examType: String, nowEpochMillis: Long): Boolean {
    val endsEpochMillis = startsEpochMillis + examDurationMinutes(examType) * 60_000L
    return nowEpochMillis in startsEpochMillis until endsEpochMillis
}

fun shouldShowExamInSchedule(startsEpochMillis: Long, examType: String, nowEpochMillis: Long): Boolean {
    val endsEpochMillis = startsEpochMillis + examDurationMinutes(examType) * 60_000L
    return endsEpochMillis > nowEpochMillis && startsEpochMillis - nowEpochMillis <= EXAM_LEAD_TIME_MILLIS
}

fun isExamPeriodActive(exams: List<Pair<Long, String>>, nowEpochMillis: Long): Boolean {
    return exams.groupBy { (_, type) -> normalizeExamType(type) }.values.any { series ->
        val firstStart = series.minOf { it.first }
        val lastEnd = series.maxOf { (start, type) -> start + examDurationMinutes(type) * 60_000L }
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
    examType: String,
): Boolean {
    val examEndMinute = examStartMinute + examDurationMinutes(examType)
    return classStartMinute < examEndMinute && classEndMinute > examStartMinute
}
