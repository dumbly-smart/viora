package app.viora.notifications

import app.viora.database.AcademicChangeEntity
import app.viora.database.AttendanceEntity

data class AttendanceNotificationPlan(
    val title: String,
    val summary: String,
    val expandedLines: List<String>,
    val ledgerKeys: List<String>,
)

object AttendanceNotificationPolicy {
    private const val NINE_POINT_CGPA = 9.0
    private const val MAX_DETAIL_LINES = 5

    fun hasNinePointRule(cgpa: Double?): Boolean = cgpa != null && cgpa >= NINE_POINT_CGPA

    fun plan(
        semesterId: String,
        cgpa: Double?,
        attendance: List<AttendanceEntity>,
        attendanceChanges: List<AcademicChangeEntity>,
        publishedKeys: Set<String>,
        target: Int = 75,
    ): AttendanceNotificationPlan? {
        val changes = attendanceChanges
            .filter { it.category == "attendance" && "change:${it.id}" !in publishedKeys }
            .sortedWith(compareByDescending<AcademicChangeEntity> { it.occurredEpochMillis }.thenBy { it.id })
        val warnings = if (hasNinePointRule(cgpa)) emptyList() else attendance
            .filter { it.held > 0 && it.attended * 100 < target * it.held }
            .map { row -> row to warningKey(semesterId, row, target) }
            .filterNot { (_, key) -> key in publishedKeys }
            .sortedBy { (row, _) -> row.courseTitle.ifBlank { row.courseCode } }
        if (changes.isEmpty() && warnings.isEmpty()) return null

        val changeCount = changes.size
        val warningCount = warnings.size
        val summary = listOfNotNull(
            changeCount.takeIf { it > 0 }?.let { "$it ${if (it == 1) "course" else "courses"} updated" },
            warningCount.takeIf { it > 0 }?.let { "$it below $target%" },
        ).joinToString(" · ")
        val detailCandidates = buildList {
            changes.forEach { add(it.detail.ifBlank { it.title }) }
            warnings.forEach { (row, _) -> add("${row.courseTitle.ifBlank { row.courseCode }} below $target%") }
        }.distinct()
        val shown = detailCandidates.take(MAX_DETAIL_LINES).toMutableList()
        val remaining = detailCandidates.size - shown.size
        if (remaining > 0) shown += "+$remaining more"

        return AttendanceNotificationPlan(
            title = if (changes.isNotEmpty()) "Attendance updated" else "Attendance warning",
            summary = summary,
            expandedLines = shown,
            ledgerKeys = (changes.map { "change:${it.id}" } + warnings.map { it.second }).distinct(),
        )
    }

    private fun warningKey(semesterId: String, row: AttendanceEntity, target: Int): String =
        "attendance-warning:$semesterId:${row.id}:${row.attended}:${row.held}:$target"
}
