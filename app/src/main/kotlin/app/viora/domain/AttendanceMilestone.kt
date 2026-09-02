package app.viora.domain

enum class AttendanceMilestone { CAT_1, CAT_2, FAT }

fun maximumSkippableOccurrences(
    attended: Int,
    held: Int,
    targetPercent: Int,
    occurrenceUnits: List<Int>,
): Int {
    if (attended.toLong() * 100 < targetPercent.toLong() * held) return 0

    var skippedUnits = 0L
    return occurrenceUnits
        .filter { it > 0 }
        .sorted()
        .takeWhile { units ->
            val allowed = attended.toLong() * 100 >= targetPercent.toLong() * (held.toLong() + skippedUnits + units)
            if (allowed) skippedUnits += units
            allowed
        }
        .size
}
