package app.viora.parser

import app.viora.network.SemesterOption
import org.jsoup.Jsoup

class SemesterParser {
    fun parse(html: String): ParseResult<List<SemesterOption>> {
        val document = Jsoup.parse(html)
        if (VtopDocument.isAuthenticationPage(document)) return ParseResult.AuthenticationRequired
        val options = document.select(
            "select[name=semesterSubId] option, select#semesterSubId option, " +
                "select[name=semesterId] option",
        ).mapNotNull { option ->
            val id = option.attr("value").trim()
            val name = option.text().trim()
            if (id.isBlank() || name.isBlank() || id == "0" || name.contains("select", true)) null
            else SemesterOption(id, name)
        }.distinctBy(SemesterOption::id)
        return if (options.isEmpty()) ParseResult.InvalidDocument("No semesters were found")
        else ParseResult.Success(options)
    }
}
