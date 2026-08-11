package app.viora.parser

import app.viora.network.SemesterOption
import org.jsoup.Jsoup

class SemesterParser {
    fun parse(html: String): ParseResult<List<SemesterOption>> {
        val document = Jsoup.parse(html)
        if (VtopDocument.isAuthenticationPage(document)) return ParseResult.AuthenticationRequired
        val options = document.select("select")
            .filter { select ->
                select.attr("name").contains("semester", ignoreCase = true) ||
                    select.id().contains("semester", ignoreCase = true)
            }
            .flatMap { it.select("option") }
            .mapNotNull { option ->
            val id = option.attr("value").trim()
            val name = option.text().trim()
            if (id.isBlank() || name.isBlank() || id == "0" || name.contains("select", true)) null
            else SemesterOption(id, name)
        }.distinctBy(SemesterOption::id)
        return if (options.isEmpty()) ParseResult.InvalidDocument("No semesters were found")
        else ParseResult.Success(options)
    }
}
