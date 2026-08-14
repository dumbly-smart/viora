package app.viora.parser

import app.viora.network.AcademicCalendarRecord
import app.viora.network.ClassMessageRecord
import app.viora.network.CourseMaterialRecord
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

class AcademicCalendarParser {
    fun parse(html: String): ParseResult<List<AcademicCalendarRecord>> {
        val doc = Jsoup.parse(html); if (VtopDocument.isAuthenticationPage(doc)) return ParseResult.AuthenticationRequired
        val rows = doc.select("table tbody tr").mapNotNull { row ->
            val cells = row.select("td").map(Element::text); if (cells.size < 2) return@mapNotNull null
            val date = cells.firstNotNullOfOrNull(::parseDate) ?: return@mapNotNull null
            val title = cells.drop(1).firstOrNull { it.isNotBlank() }.orEmpty()
            val type = cells.firstOrNull { it.contains("holiday", true) || it.contains("exam", true) || it.contains("instruction", true) }.orEmpty().ifBlank { title }
            AcademicCalendarRecord(stable("$date-$title-$type"), date, title, type)
        }.distinctBy { it.id }
        return ParseResult.Success(rows)
    }
}

class ClassMessageParser {
    fun parse(html: String): ParseResult<List<ClassMessageRecord>> = parseTable(html) { values, index ->
        val body = values.find("message", "message details", "content", "description").orEmpty()
        val subject = values.find("subject", "title").orEmpty()
        if (body.isBlank() && subject.isBlank()) null else ClassMessageRecord(stable("$index-$subject-$body"), values.find("course code").orEmpty(), values.find("course title", "course name").orEmpty(), values.find("faculty", "faculty name").orEmpty(), subject, body, VtopDateParser.dateTime(values.find("posted date", "date", "posted on")))
    }
}

class CourseMaterialParser {
    fun parse(html: String, courseCode: String, requestedFaculty: String = ""): ParseResult<List<CourseMaterialRecord>> {
        val doc = Jsoup.parse(html); if (VtopDocument.isAuthenticationPage(doc)) return ParseResult.AuthenticationRequired
        val consolidatedRows = doc.select("table#materialTable tbody tr")
        if (consolidatedRows.isEmpty()) return ParseResult.Success(parseLegacyMaterials(doc, courseCode))
        val rows = consolidatedRows.mapIndexedNotNull { index, row ->
            val cells = row.select("td")
            if (cells.size < 5) return@mapIndexedNotNull null
            val facultyText = cells[3].select("div.mt-1 span").firstOrNull()?.text().orEmpty()
            if (requestedFaculty.isNotBlank() && !facultyText.contains(requestedFaculty, true)) return@mapIndexedNotNull null
            val materialSpans = cells[2].select("div.mt-1 span")
            val topic = materialSpans.firstOrNull { it.attr("style").contains("#2E86C1", true) }
                ?.text()?.trim().orEmpty().ifBlank { materialSpans.firstOrNull()?.text()?.trim().orEmpty() }
            val module = materialSpans.firstOrNull { it.attr("style").contains("#28B463", true) }?.text()?.trim().orEmpty()
            val fileId = cells[4].selectFirst("button[name=downloadmat][data-fileid]")?.attr("data-fileid").orEmpty()
            if (fileId.isBlank()) return@mapIndexedNotNull null
            val title = listOf(module.takeIf(String::isNotBlank)?.let { "Module $it" }, topic).filterNotNull().filter(String::isNotBlank).joinToString(" · ").ifBlank { "Course material ${index + 1}" }
            val postedAt = cells[3].select("div.mt-1 span").getOrNull(1)?.text()?.let { VtopDateParser.dateAndTime(it, "12:00 AM") }
            CourseMaterialRecord(stable("$courseCode-$fileId"), courseCode, title, topic.ifBlank { title }, "fileId:$fileId", postedAt)
        }
        return ParseResult.Success(rows)
    }

    private fun parseLegacyMaterials(doc: org.jsoup.nodes.Document, courseCode: String) =
        doc.select("table tbody tr").mapIndexedNotNull { index, row ->
            val cells = row.select("td")
            val control = row.select("a,button,input").firstOrNull { element ->
                listOf(element.attr("href"), element.attr("onclick"), element.attr("data-url"))
                    .any { it.contains("download", true) || it.contains("material", true) }
            }
            val title = cells.firstOrNull { it.text().isNotBlank() }?.text().orEmpty()
            if (control == null && title.isBlank()) null else {
                val path = control?.attr("href").orEmpty().ifBlank { control?.attr("onclick").orEmpty() }.ifBlank { control?.attr("data-url").orEmpty() }
                CourseMaterialRecord(stable("$courseCode-$index-$title-$path"), courseCode, title.ifBlank { control?.text().orEmpty() }, control?.attr("download").orEmpty().ifBlank { control?.text().orEmpty() }, path, null)
            }
        }
}

private fun <T> parseTable(html: String, row: (Map<String, String>, Int) -> T?): ParseResult<List<T>> {
    val doc = Jsoup.parse(html); if (VtopDocument.isAuthenticationPage(doc)) return ParseResult.AuthenticationRequired
    val result = doc.select("table").flatMap { table -> val headers = table.select("thead th").map { norm(it.text()) }; table.select("tbody tr").mapIndexedNotNull { i, tr -> row(headers.zip(tr.select("td").map(Element::text)).toMap(), i) } }
    return ParseResult.Success(result)
}
private fun parseDate(value: String): LocalDate? { val clean = value.trim(); return listOf("dd-MMM-yyyy", "dd MMM yyyy", "dd/MM/yyyy", "yyyy-MM-dd").firstNotNullOfOrNull { runCatching { LocalDate.parse(clean, DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH)) }.getOrNull() } }
private fun Map<String, String>.find(vararg keys: String) = keys.firstNotNullOfOrNull { this[norm(it)] }
private fun norm(value: String) = value.trim().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
private fun stable(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
