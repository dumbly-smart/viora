package app.viora.domain

enum class AttendanceMilestone { CAT_1, CAT_2, FAT }

fun maximumSkippableOccurrences(
    attended: Int,
    held: Int,
    targetPercent: Int,
    occurrenceUnits: List<Int>,
): Int {
    val futureUnits = occurrenceUnits.filter { it > 0 }.sumOf(Int::toLong)
    val finalHeld = held.toLong() + futureUnits
    var skippedUnits = 0L
    return occurrenceUnits
        .filter { it > 0 }
        .sorted()
        .takeWhile { units ->
            val candidateSkipped = skippedUnits + units
            val allowed = (attended.toLong() + futureUnits - candidateSkipped) * 100 >=
                targetPercent.toLong() * finalHeld
            if (allowed) skippedUnits += units
            allowed
        }
        .size
}
