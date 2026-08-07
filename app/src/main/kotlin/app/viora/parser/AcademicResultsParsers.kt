package app.viora.parser

import app.viora.network.CgpaSnapshot
import app.viora.network.GradeRecord
import app.viora.network.GradeSnapshot
import app.viora.network.MarkRecord
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class MarksParser {
    fun parse(html: String): ParseResult<List<MarkRecord>> {
        val document = Jsoup.parse(html)
        if (VtopDocument.isAuthenticationPage(document)) return ParseResult.AuthenticationRequired
        val records = mutableListOf<MarkRecord>()
        document.select("table").forEachIndexed { tableIndex, table ->
            val headers = table.select("thead th").map { norm(it.text()) }
            if (headers.none { it == "max marks" } || headers.none { it.contains("weightage") }) return@forEachIndexed
            val heading = table.previousElementSiblings().firstOrNull { it.text().isNotBlank() }?.text().orEmpty()
            table.select("tbody tr").forEachIndexed { rowIndex, row ->
                val values = headers.zip(row.select("td").map(Element::text)).toMap()
                val title = values.find("title", "assessment")?.trim().orEmpty()
                if (title.isNotBlank()) records += MarkRecord(
                    id = stable("$tableIndex-$heading-$rowIndex-$title"), courseCode = values.find("course code").orEmpty(),
                    courseTitle = values.find("course title", "course name").orEmpty().ifBlank { heading },
                    courseType = values.find("course type", "type").orEmpty(), title = title,
                    maxMarks = values.find("max marks")?.number(), weightagePercent = values.find("weightage %", "weightage percentage")?.number(),
                    status = values.find("status").orEmpty(), scoredMark = values.find("scored mark", "marks scored")?.number(),
                    weightageMark = values.find("weightage mark", "weighted mark")?.number(),
                )
            }
        }
        return if (records.isEmpty()) ParseResult.InvalidDocument("No assessment marks were found") else ParseResult.Success(records)
    }
}

class GradesParser {
    fun parse(html: String): ParseResult<GradeSnapshot> {
        val document = Jsoup.parse(html)
        if (VtopDocument.isAuthenticationPage(document)) return ParseResult.AuthenticationRequired
        val table = document.select("table").firstOrNull { it.select("thead th").any { h -> norm(h.text()) == "grade" } }
            ?: return ParseResult.InvalidDocument("Grade table was not found")
        val headers = table.select("thead th").map { norm(it.text()) }
        val rows = table.select("tbody tr").mapNotNull { row ->
            val values = headers.zip(row.select("td").map(Element::text)).toMap()
            val code = values.find("course code")?.trim().orEmpty()
            if (code.isBlank()) null else GradeRecord(code, values.find("course title").orEmpty(), values.find("course type").orEmpty(),
                values.find("credits")?.number(), values.find("total")?.number(), values.find("grading") .orEmpty(), values.find("grade").orEmpty())
        }
        val gpa = Regex("\\bGPA\\s*[:=-]\\s*([0-9.]+)", RegexOption.IGNORE_CASE).find(document.text())?.groupValues?.get(1)?.toDoubleOrNull()
        return if (rows.isEmpty()) ParseResult.InvalidDocument("No grade records were found") else ParseResult.Success(GradeSnapshot(rows, gpa))
    }
}

class CgpaParser {
    fun parse(html: String): ParseResult<CgpaSnapshot> {
        val document = Jsoup.parse(html)
        if (VtopDocument.isAuthenticationPage(document)) return ParseResult.AuthenticationRequired
        val text = document.text()
        fun value(label: String) = Regex("$label\\s*[:=-]\\s*([0-9.]+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        val counts = document.select("table tr").mapNotNull { row ->
            val cells = row.select("th,td").map(Element::text)
            if (cells.size < 2) null else Regex("([SABCDENF])\\s*Grades?", RegexOption.IGNORE_CASE).find(cells[0])?.groupValues?.get(1)?.uppercase()?.let { it to (cells[1].filter(Char::isDigit).toIntOrNull() ?: 0) }
        }.toMap()
        val result = CgpaSnapshot(value("Credits Registered"), value("Credits Earned"), value("CGPA"), counts)
        return if (result.cgpa == null) ParseResult.InvalidDocument("CGPA was not found") else ParseResult.Success(result)
    }
}

private fun Map<String, String>.find(vararg names: String): String? = names.firstNotNullOfOrNull { this[norm(it)] }
private fun String.number(): Double? = Regex("-?[0-9]+(?:\\.[0-9]+)?").find(this)?.value?.toDoubleOrNull()
private fun norm(value: String) = value.trim().lowercase().replace(Regex("[^a-z0-9%]+"), " ").trim()
private fun stable(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
