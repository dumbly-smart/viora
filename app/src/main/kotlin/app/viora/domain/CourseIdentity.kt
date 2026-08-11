package app.viora.domain

private val courseCodePattern = Regex("[A-Z]{2,8}\\s*[-_]?\\s*\\d{3,5}[A-Z]?")

/** Matches the same VTOP course even when pages format its code differently. */
fun sameCourseCode(first: String, second: String): Boolean {
    val firstKey = first.courseCodeKey()
    val secondKey = second.courseCodeKey()
    return firstKey.isNotEmpty() && firstKey == secondKey
}

private fun String.courseCodeKey(): String {
    val upper = uppercase()
    return (courseCodePattern.find(upper)?.value ?: upper)
        .filter(Char::isLetterOrDigit)
}
