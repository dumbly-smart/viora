package app.viora.domain

data class SemesterSelection(val selectedId: String?, val rolloverDetected: Boolean)

object SemesterRollover {
    fun select(remoteIds: List<String>, cachedIds: Set<String>, savedId: String?, confirmedCurrentId: String? = null): SemesterSelection {
        confirmedCurrentId?.takeIf(remoteIds::contains)?.let {
            return SemesterSelection(it, false)
        }
        val newest = remoteIds.firstOrNull()
        val rollover = newest != null && cachedIds.isNotEmpty() && newest !in cachedIds
        val selected = if (rollover) newest else savedId?.takeIf(remoteIds::contains) ?: newest
        return SemesterSelection(selected, rollover)
    }
}
