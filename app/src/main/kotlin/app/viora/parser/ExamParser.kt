package app.viora.parser

import app.viora.network.ExamRecord
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class ExamParser {
    fun parse(html: String): ParseResult<List<ExamRecord>> {
        val document = Jsoup.parse(html)
        if (VtopDocument.isAuthenticationPage(document)) return ParseResult.AuthenticationRequired
        val table = document.selectFirst("#ExamScheduleDataTable, table.customTable, table.table")
            ?: return ParseResult.InvalidDocument("Exam schedule table was not found")
        val headers = table.select("thead th").map { normalize(it.text()) }
        if (headers.isEmpty()) return ParseResult.InvalidDocument("Exam headers were not found")
        val records = table.select("tbody tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.isEmpty()) return@mapNotNull null
            val values = headers.zip(cells.map(Element::text)).toMap()
            val code = values.find("course code", "course", "subject code")?.trim().orEmpty()
            val examType = values.find("exam type", "exam", "type")?.trim().orEmpty()
            val startsAt = VtopDateParser.dateAndTime(
                values.find("exam date", "date"),
                values.find("exam time", "time", "session"),
            ) ?: VtopDateParser.dateTime(values.find("exam date & time", "date and time"))
            if (code.isBlank() || examType.isBlank() || startsAt == null) return@mapNotNull null
            ExamRecord(
                id = stableId("$examType-$code-$startsAt"),
                courseCode = code,
                courseTitle = values.find("course title", "course name", "title").orEmpty().trim(),
                examType = examType,
                startsAt = startsAt,
                venue = values.find("venue", "room").orEmpty().trim(),
                seatNumber = values.find("seat no", "seat number").orEmpty().trim(),
            )
        }.distinctBy(ExamRecord::id)
        return ParseResult.Success(records)
    }

    private fun Map<String, String>.find(vararg names: String): String? =
        names.firstNotNullOfOrNull { this[normalize(it)] }
    private fun normalize(value: String) = value.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
    private fun stableId(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
