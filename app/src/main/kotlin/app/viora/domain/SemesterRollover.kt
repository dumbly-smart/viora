package app.viora.domain

data class SemesterSelection(val selectedId: String?, val rolloverDetected: Boolean)

object SemesterRollover {
    fun select(remoteIds: List<String>, cachedIds: Set<String>, savedId: String?): SemesterSelection {
        val newest = remoteIds.firstOrNull()
        val rollover = newest != null && cachedIds.isNotEmpty() && newest !in cachedIds
        val selected = if (rollover) newest else savedId?.takeIf(remoteIds::contains) ?: newest
        return SemesterSelection(selected, rollover)
    }
}
