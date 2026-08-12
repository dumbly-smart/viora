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
    if (endsEpochMillis <= nowEpochMillis) return false
    val normalized = examType.lowercase().replace(Regex("[^a-z0-9]+"), "")
    val isCat2OrFat = normalized.contains("cat2") || normalized.contains("catii") || normalized.contains("fat")
    return !isCat2OrFat || startsEpochMillis - nowEpochMillis <= EXAM_LEAD_TIME_MILLIS
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
