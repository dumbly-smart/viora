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
    fun parse(html: String, courseCode: String): ParseResult<List<CourseMaterialRecord>> {
        val doc = Jsoup.parse(html); if (VtopDocument.isAuthenticationPage(doc)) return ParseResult.AuthenticationRequired
        val rows = doc.select("table tbody tr").mapIndexedNotNull { index, row ->
            val cells = row.select("td")
            val link = row.select("a, button, input").firstOrNull { element ->
                listOf(element.attr("href"), element.attr("onclick"), element.attr("data-url"))
                    .any { action -> action.contains("download", true) || action.contains("material", true) }
            }
            val title = cells.firstOrNull { it.text().isNotBlank() }?.text().orEmpty(); if (link == null && title.isBlank()) null else {
                val path = link?.attr("href").orEmpty()
                    .ifBlank { link?.attr("onclick").orEmpty() }
                    .ifBlank { link?.attr("data-url").orEmpty() }
                CourseMaterialRecord(stable("$courseCode-$index-$title-$path"), courseCode, title.ifBlank { link?.text().orEmpty() }, link?.attr("download").orEmpty().ifBlank { link?.text().orEmpty() }, path, null)
            }
        }
        return ParseResult.Success(rows)
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
