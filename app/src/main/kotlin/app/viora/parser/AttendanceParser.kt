package app.viora.parser

import app.viora.network.AttendanceRecord
import app.viora.network.AttendanceSnapshot
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class AttendanceParser {
    fun parse(html: String): ParseResult<AttendanceSnapshot> {
        val document = Jsoup.parse(html)
        if (VtopDocument.isAuthenticationPage(document)) return ParseResult.AuthenticationRequired
        val table = document.selectFirst("#AttendanceDetailDataTable, table.customTable, table.table")
            ?: return ParseResult.InvalidDocument("Attendance table was not found")
        val headers = table.select("thead th").map { normalize(it.text()) }
        if (headers.isEmpty()) return ParseResult.InvalidDocument("Attendance headers were not found")

        val records = table.select("tbody tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.isEmpty()) return@mapNotNull null
            val values = headers.zip(cells.map(Element::text)).toMap()
            val code = values.find("course code", "course", "code")?.trim().orEmpty()
            if (code.isBlank()) return@mapNotNull null
            val attended = values.find("classes attended", "attended classes", "attended")?.firstInt()
            val held = values.find("total classes", "classes conducted", "conducted", "held")?.firstInt()
            if (attended == null || held == null || attended < 0 || held < attended) return@mapNotNull null
            AttendanceRecord(
                courseCode = code,
                courseTitle = values.find("course title", "course name", "title").orEmpty().trim(),
                attended = attended,
                held = held,
            )
        }.distinctBy(AttendanceRecord::courseCode)

        return if (records.isEmpty()) ParseResult.InvalidDocument("No plausible attendance records were found")
        else ParseResult.Success(AttendanceSnapshot(records))
    }

    private fun Map<String, String>.find(vararg names: String): String? =
        names.firstNotNullOfOrNull { this[normalize(it)] }

    private fun normalize(value: String) = value.trim().lowercase().replace(Regex("\\s+"), " ")
    private fun String.firstInt(): Int? = Regex("\\d+").find(this)?.value?.toIntOrNull()
}
