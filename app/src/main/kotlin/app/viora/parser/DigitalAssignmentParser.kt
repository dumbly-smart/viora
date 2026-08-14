package app.viora.parser

import app.viora.network.DigitalAssignmentRecord
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

data class DigitalAssignmentSubject(val classId: String, val courseCode: String, val courseTitle: String)

class DigitalAssignmentParser {
    fun parseSubjects(html: String): ParseResult<List<DigitalAssignmentSubject>> {
        val document = Jsoup.parse(html)
        if (VtopDocument.isAuthenticationPage(document)) return ParseResult.AuthenticationRequired
        val subjects = document.select("tr.tableContent").mapNotNull { row ->
            val cells = row.select("td").map(Element::text)
            if (cells.size < 5) return@mapNotNull null
            val classId = cells[1].trim()
            val code = cells[2].trim()
            val title = cells[3].trim()
            if (classId.isBlank() || code.isBlank() || title.isBlank()) null
            else DigitalAssignmentSubject(classId, code, title)
        }.distinctBy(DigitalAssignmentSubject::classId)
        return ParseResult.Success(subjects)
    }

    fun parseDetails(html: String, subject: DigitalAssignmentSubject): ParseResult<List<DigitalAssignmentRecord>> {
        val document = Jsoup.parse(html)
        if (VtopDocument.isAuthenticationPage(document)) return ParseResult.AuthenticationRequired
        val table = document.select("table.customTable").firstOrNull { candidate ->
            candidate.select("tr.tableHeader td, thead th").map { normalize(it.text()) }.let { headers ->
                headers.any { it == "title" } && headers.any { it == "due date" }
            }
        } ?: return ParseResult.InvalidDocument("Digital assignment detail table was not found")
        val headerRow = table.selectFirst("tr.tableHeader") ?: table.selectFirst("thead tr")
            ?: return ParseResult.InvalidDocument("Digital assignment headers were not found")
        val headers = headerRow.select("td,th").map { normalize(it.text()) }
        val rows = table.select("tr.tableContent").ifEmpty { table.select("tbody tr").filterNot { it == headerRow } }
        return ParseResult.Success(rows.mapNotNull { row -> detailRow(row, headers, subject) }.distinctBy(DigitalAssignmentRecord::id))
    }

    /** Fixture/backward-compatible parser for a table that already includes course codes. */
    fun parse(html: String): ParseResult<List<DigitalAssignmentRecord>> {
        val document = Jsoup.parse(html)
        if (VtopDocument.isAuthenticationPage(document)) return ParseResult.AuthenticationRequired
        val table = document.selectFirst("#DataTable, table.customTable, table.table")
            ?: return ParseResult.InvalidDocument("Digital assignment table was not found")
        val headerRow = table.selectFirst("thead tr") ?: table.selectFirst("tr.tableHeader")
            ?: return ParseResult.InvalidDocument("Digital assignment headers were not found")
        val headers = headerRow.select("th,td").map { normalize(it.text()) }
        val rows = table.select("tbody tr, tr.tableContent").filterNot { it == headerRow }
        val assignments = rows.mapNotNull { row ->
            val cells = row.select("td")
            val values = headers.zip(cells.map(Element::text)).toMap()
            val code = values.find("course code", "course", "subject code")?.trim().orEmpty()
            if (code.isBlank()) return@mapNotNull null
            detailRow(row, headers, DigitalAssignmentSubject(code, code, code))
        }
        return ParseResult.Success(assignments.distinctBy(DigitalAssignmentRecord::id))
    }

    private fun detailRow(row: Element, headers: List<String>, subject: DigitalAssignmentSubject): DigitalAssignmentRecord? {
        val cells = row.select("td")
        if (cells.isEmpty()) return null
        val values = headers.zip(cells.map(Element::text)).toMap()
        val title = values.find("title", "assignment title", "digital assignment")?.trim().orEmpty()
        if (title.isBlank() || title.equals(subject.courseCode, true)) return null
        val dueText = values.find("due date", "due date & time", "deadline")?.trim()
        val due = VtopDateParser.dateTime(dueText) ?: VtopDateParser.dateAndTime(dueText, "11:59 PM")
        val lastUpload = values.find("last updated", "last upload", "last_upload", "uploaded on").orEmpty().trim().ifBlank { "N/A" }
        val status = values.find("status", "upload status").orEmpty().trim()
        val assignmentCode = row.selectFirst("input[name=code]")?.attr("value")
            .orEmpty().ifBlank { row.selectFirst("button[data-editcode]")?.attr("data-editcode").orEmpty() }
        return DigitalAssignmentRecord(
            id = stableId("${subject.classId}-$assignmentCode-$title-${dueText.orEmpty()}"),
            courseCode = subject.courseCode,
            title = title,
            dueAt = due,
            lastUpload = lastUpload,
            status = status,
        )
    }

    private fun Map<String, String>.find(vararg names: String): String? =
        names.firstNotNullOfOrNull { this[normalize(it)] }
    private fun normalize(value: String) = value.trim().lowercase().replace(Regex("[_\\s]+"), " ")
    private fun stableId(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
