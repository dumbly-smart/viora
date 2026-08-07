package app.viora.domain

data class AttendanceProjection(
    val attended: Int,
    val held: Int,
    val targetPercent: Int,
    val skippableClasses: Int,
    val classesToRecover: Int,
    val blockSize: Int = 1,
) {
    val percentage: Double = if (held == 0) 0.0 else attended * 100.0 / held
    val skippableBlocks: Int get() = skippableClasses / blockSize
    val blocksToRecover: Int get() = (classesToRecover + blockSize - 1) / blockSize
}

object AttendanceCalculator {
    fun calculate(attended: Int, held: Int, targetPercent: Int = 75, blockSize: Int = 1): AttendanceProjection {
        require(attended >= 0) { "attended must not be negative" }
        require(held >= attended) { "held must be at least attended" }
        require(targetPercent in 1..99) { "targetPercent must be between 1 and 99" }
        require(blockSize > 0) { "blockSize must be positive" }

        var skippable = 0
        while (meetsTarget(attended, held + skippable + 1, targetPercent)) skippable++

        var recovery = 0
        while (!meetsTarget(attended + recovery, held + recovery, targetPercent)) recovery++

        return AttendanceProjection(attended, held, targetPercent, skippable, recovery, blockSize)
    }

    private fun meetsTarget(attended: Int, held: Int, targetPercent: Int): Boolean =
        held > 0 && attended.toLong() * 100 >= targetPercent.toLong() * held
}
