package app.viora.domain

fun examDurationMinutes(examType: String): Int = when {
    examType.contains("quiz", true) -> 60
    examType.contains("cat", true) -> 90
    else -> 180
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
