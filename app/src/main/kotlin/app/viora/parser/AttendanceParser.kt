package app.viora.parser

import app.viora.network.AttendanceRecord
import app.viora.network.AttendanceSnapshot
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class AttendanceParser {
    fun parse(html: String): ParseResult<AttendanceSnapshot> {
        val document = Jsoup.parse(html)
        if (VtopDocument.isAuthenticationPage(document)) return ParseResult.AuthenticationRequired
        val table = document.selectFirst("#getStudentDetails, #AttendanceDetailDataTable, table.customTable, table.table")
            ?: return ParseResult.InvalidDocument("Attendance table was not found")
        val rows = table.select("tr")
        val headerRow = rows.firstOrNull { it.select("th").isNotEmpty() } ?: rows.firstOrNull()
            ?: return ParseResult.InvalidDocument("Attendance rows were not found")
        val headers = headerRow.select("th, td").map { normalize(it.text()) }
        if (headers.isEmpty()) return ParseResult.InvalidDocument("Attendance headers were not found")

        val records = rows.filterNot { it == headerRow }.mapNotNull { row ->
            val cells = row.select("td")
            if (cells.isEmpty()) return@mapNotNull null
            val values = headers.zip(cells.map(Element::text)).toMap()
            val courseDetail = values.find("course detail").orEmpty().trim()
            val code = values.find("course code", "code")?.trim().orEmpty()
                .ifBlank { COURSE_CODE.find(courseDetail)?.value.orEmpty() }
            val title = values.find("subject", "subject name", "course title", "course name", "title")?.trim().orEmpty()
                .ifBlank { courseDetail.removeCourseCode(code) }
            if (code.isBlank() && title.isBlank()) return@mapNotNull null
            val combined = values.find("classes attended")?.let { Regex("(\\d+)\\s*/\\s*(\\d+)").find(it) }
            fun cellWhere(predicate: (String) -> Boolean): String? {
                val index = headers.indexOfFirst(predicate)
                return cells.getOrNull(index)?.text()
            }
            val attended = combined?.groupValues?.get(1)?.toIntOrNull()
                ?: values.find("classes attended", "attended classes", "attended")?.firstInt()
                ?: cellWhere { "attend" in it && "percent" !in it }?.firstInt()
            val held = combined?.groupValues?.get(2)?.toIntOrNull()
                ?: values.find("total classes", "classes conducted", "conducted", "held")?.firstInt()
                ?: cellWhere { "total" in it && ("class" in it || "hour" in it) }?.firstInt()
            if (attended == null || held == null || attended < 0 || held < attended) return@mapNotNull null
            val type = values.find("type", "course type", "class detail").orEmpty().trim()
            val faculty = values.find("faculty name", "faculty", "faculty detail").orEmpty().trim()
            AttendanceRecord(
                id = stableId("$code-$title-$type-$faculty"),
                courseCode = code,
                courseTitle = title,
                courseType = type,
                faculty = faculty,
                attended = attended,
                held = held,
            )
        }.distinctBy(AttendanceRecord::id)

        return if (records.isEmpty()) ParseResult.InvalidDocument(
            "Attendance shape: headers=${headers.joinToString("|")}; cells=${rows.filterNot { it == headerRow }.take(4).joinToString(",") { it.select("td").size.toString() }}",
        )
        else ParseResult.Success(AttendanceSnapshot(records))
    }

    private fun Map<String, String>.find(vararg names: String): String? =
        names.firstNotNullOfOrNull { this[normalize(it)] }

    private fun normalize(value: String) = value.trim().lowercase().replace(Regex("\\s+"), " ")
    private fun String.removeCourseCode(code: String): String {
        val match = COURSE_CODE.find(this)
        return (if (match != null && sameCode(match.value, code)) removeRange(match.range) else this)
            .trim().trimStart('-', ':').trim()
    }
    private fun sameCode(first: String, second: String) =
        first.filter(Char::isLetterOrDigit).equals(second.filter(Char::isLetterOrDigit), true)
    private fun String.firstInt(): Int? = Regex("\\d+").find(this)?.value?.toIntOrNull()
    private fun stableId(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private companion object {
        val COURSE_CODE = Regex("[A-Z]{2,8}\\s*[-_]?\\s*\\d{3,5}[A-Z]?", RegexOption.IGNORE_CASE)
    }
}
