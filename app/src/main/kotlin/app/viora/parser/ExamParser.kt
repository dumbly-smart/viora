package app.viora.parser

import app.viora.network.ExamRecord
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class ExamParser {
    fun parse(html: String): ParseResult<List<ExamRecord>> {
        val document = Jsoup.parse(html)
        if (VtopDocument.isAuthenticationPage(document)) return ParseResult.AuthenticationRequired
        val tables = document.select("#ExamScheduleDataTable, table.customTable, table.table")
            .ifEmpty { document.select("table") }
        if (tables.isEmpty()) return ParseResult.InvalidDocument("Exam schedule table was not found")

        var foundHeaders = false
        val records = tables.flatMap { table ->
            val rows = table.select("tr")
            val headerIndex = rows.indexOfFirst { row ->
                val headings = row.select("th, td").map { normalize(it.text()) }
                headings.any { it.matchesHeader("course code", "subject code") } &&
                    headings.any { it.matchesHeader("exam date", "date") }
            }
            if (headerIndex < 0) return@flatMap emptyList()
            foundHeaders = true
            val headers = rows[headerIndex].select("th, td").map { normalize(it.text()) }
            var groupedExamType = ""
            rows.drop(headerIndex + 1).mapNotNull { row ->
                val cells = row.select("td")
                if (cells.isEmpty()) return@mapNotNull null
                if (cells.size == 1 || (cells.size < headers.size && cells.any { it.hasAttr("colspan") })) {
                    groupedExamType = cells.joinToString(" ") { it.text() }.trim()
                    return@mapNotNull null
                }
                record(headers, cells, groupedExamType)
            }
        }.distinctBy(ExamRecord::id)

        if (!foundHeaders) return ParseResult.InvalidDocument("Exam headers were not found")
        return ParseResult.Success(records)
    }

    private fun record(headers: List<String>, cells: List<Element>, groupedExamType: String): ExamRecord? {
        val values = headers.zip(cells.map(Element::text)).toMap()
        val code = values.find("course code", "course", "subject code")?.trim().orEmpty()
        val examType = values.findExact("exam type", "exam", "type")?.trim().orEmpty()
            .ifBlank { groupedExamType }
        val date = values.find("exam date", "date")
        val timeRange = values.find("exam time", "time", "session")
        val times = timeRange?.split(Regex("\\s*[-–—]\\s*"), limit = 2).orEmpty()
        val startTime = times.firstOrNull()?.trim()
        val endTime = times.getOrNull(1)?.trim()
        val startsAt = VtopDateParser.dateAndTime(date, startTime)
            ?: VtopDateParser.dateTime(values.find("exam date & time", "date and time"))
        if (code.isBlank() || examType.isBlank() || startsAt == null) return null
        val parsedEnd = VtopDateParser.dateAndTime(date, endTime)
        val endsAt = parsedEnd?.let { if (it.isBefore(startsAt)) it.plusDays(1) else it }
        return ExamRecord(
            id = stableId("$examType-$code-$startsAt"),
            courseCode = code,
            courseTitle = values.find("course title", "course name", "title").orEmpty().trim(),
            examType = examType,
            startsAt = startsAt,
            endsAt = endsAt,
            venue = values.find("venue", "room", "location").orEmpty().trim(),
            seatNumber = values.find("seat no", "seat number", "seat no.").orEmpty().trim(),
        )
    }

    private fun Map<String, String>.find(vararg names: String): String? =
        findExact(*names) ?: names.firstNotNullOfOrNull { name ->
            val wanted = normalize(name)
            entries.firstOrNull { (header, _) -> header.matchesHeader(wanted) }?.value
        }

    private fun Map<String, String>.findExact(vararg names: String): String? =
        names.firstNotNullOfOrNull { this[normalize(it)] }

    private fun String.matchesHeader(vararg names: String): Boolean = names.any { name ->
        this == normalize(name) || this.startsWith("${normalize(name)} ")
    }

    private fun normalize(value: String) = value.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun stableId(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
