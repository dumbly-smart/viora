package app.viora.parser

import app.viora.network.DigitalAssignmentRecord
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class DigitalAssignmentParser {
    fun parse(html: String): ParseResult<List<DigitalAssignmentRecord>> {
        val document = Jsoup.parse(html)
        if (VtopDocument.isAuthenticationPage(document)) return ParseResult.AuthenticationRequired
        val table = document.selectFirst("#DataTable, table.customTable, table.table")
            ?: return ParseResult.InvalidDocument("Digital assignment table was not found")
        val headers = table.select("thead th").map { normalize(it.text()) }
        if (headers.isEmpty()) return ParseResult.InvalidDocument("Digital assignment headers were not found")
        val assignments = table.select("tbody tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.isEmpty()) return@mapNotNull null
            val values = headers.zip(cells.map(Element::text)).toMap()
            val code = values.find("course code", "course", "subject code")?.trim().orEmpty()
            val title = values.find("title", "assignment title", "digital assignment")?.trim().orEmpty()
            if (code.isBlank() || title.isBlank()) return@mapNotNull null
            val dueText = values.find("due date", "due date & time", "deadline")
            val id = row.attr("data-id").ifBlank {
                stableId("$code-$title-${dueText.orEmpty()}")
            }
            DigitalAssignmentRecord(
                id = id,
                courseCode = code,
                title = title,
                dueAt = VtopDateParser.dateTime(dueText),
                lastUpload = values.find("last upload", "last_upload", "uploaded on").orEmpty().trim(),
                status = values.find("status", "upload status").orEmpty().trim(),
            )
        }.distinctBy(DigitalAssignmentRecord::id)
        return ParseResult.Success(assignments)
    }

    private fun Map<String, String>.find(vararg names: String): String? =
        names.firstNotNullOfOrNull { this[normalize(it)] }
    private fun normalize(value: String) = value.trim().lowercase().replace(Regex("[_\\s]+"), " ")
    private fun stableId(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
