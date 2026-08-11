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
            val code = values.find("course code", "code")?.trim().orEmpty()
            val title = values.find("subject", "subject name", "course title", "course name", "title")?.trim().orEmpty()
            if (code.isBlank() && title.isBlank()) return@mapNotNull null
            val combined = values.find("classes attended")?.let { Regex("(\\d+)\\s*/\\s*(\\d+)").find(it) }
            val attended = combined?.groupValues?.get(1)?.toIntOrNull() ?: values.find("classes attended", "attended classes", "attended")?.firstInt()
            val held = combined?.groupValues?.get(2)?.toIntOrNull() ?: values.find("total classes", "classes conducted", "conducted", "held")?.firstInt()
            if (attended == null || held == null || attended < 0 || held < attended) return@mapNotNull null
            val type = values.find("type", "course type").orEmpty().trim()
            val faculty = values.find("faculty name", "faculty").orEmpty().trim()
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
            "No plausible attendance records were found; columns=${headers.joinToString("|").take(100)}; rows=${rows.drop(1).take(3).joinToString(",") { it.select("td").size.toString() }}",
        )
        else ParseResult.Success(AttendanceSnapshot(records))
    }

    private fun Map<String, String>.find(vararg names: String): String? =
        names.firstNotNullOfOrNull { this[normalize(it)] }

    private fun normalize(value: String) = value.trim().lowercase().replace(Regex("\\s+"), " ")
    private fun String.firstInt(): Int? = Regex("\\d+").find(this)?.value?.toIntOrNull()
    private fun stableId(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
