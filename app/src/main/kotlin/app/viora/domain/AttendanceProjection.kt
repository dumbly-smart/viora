package app.viora.domain

data class AttendanceProjection(
    val attended: Int,
    val held: Int,
    val targetPercent: Int,
    val skippableClasses: Int,
    val classesToRecover: Int,
) {
    val percentage: Double = if (held == 0) 0.0 else attended * 100.0 / held
}

object AttendanceCalculator {
    fun calculate(attended: Int, held: Int, targetPercent: Int = 75): AttendanceProjection {
        require(attended >= 0) { "attended must not be negative" }
        require(held >= attended) { "held must be at least attended" }
        require(targetPercent in 1..100) { "targetPercent must be between 1 and 100" }

        var skippable = 0
        while (meetsTarget(attended, held + skippable + 1, targetPercent)) skippable++

        var recovery = 0
        while (!meetsTarget(attended + recovery, held + recovery, targetPercent)) recovery++

        return AttendanceProjection(attended, held, targetPercent, skippable, recovery)
    }

    private fun meetsTarget(attended: Int, held: Int, targetPercent: Int): Boolean =
        held > 0 && attended.toLong() * 100 >= targetPercent.toLong() * held
}
