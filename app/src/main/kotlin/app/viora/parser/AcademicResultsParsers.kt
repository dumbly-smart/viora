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
            val allRows = table.directRows()
            val headerRow = allRows.firstOrNull { row ->
                val cells = row.directCells().map { norm(it.text()) }
                cells.any { it in componentHeaders } && cells.any { it in scoreHeaders || it in maximumHeaders }
            } ?: return@forEachIndexed
            val headers = headerRow.directCells().map { norm(it.text()) }
            val heading = table.previousElementSiblings().firstOrNull { it.text().isNotBlank() }?.text().orEmpty()
            var context = (allRows.takeWhile { it != headerRow }.lastOrNull { it.select("td[colspan],th[colspan]").isNotEmpty() }?.text() ?: heading)
                .courseContext()
            context = table.outerCourseContext(context)
            allRows.dropWhile { it != headerRow }.drop(1).forEachIndexed { rowIndex, row ->
                val cells = row.directCells().filter { it.tagName() == "td" }
                if (cells.isEmpty()) return@forEachIndexed
                if (cells.size == 1 && cells.single().hasAttr("colspan")) {
                    context = cells.single().text().courseContext(context)
                    return@forEachIndexed
                }
                val values = headers.zip(cells.map(Element::text)).toMap()
                val title = values.find(*componentHeaders.toTypedArray())?.trim().orEmpty()
                if (title.isBlank()) return@forEachIndexed
                val courseCode = values.find("course code", "subject code")?.trim().orEmpty().ifBlank { context.courseCode }
                val courseTitle = values.find("course title", "course name", "subject title", "subject name")
                    ?.trim().orEmpty().ifBlank { context.courseTitle.ifBlank { heading } }
                val courseType = values.find("course type", "type", "subject type")?.trim().orEmpty().ifBlank { context.courseType }
                records += MarkRecord(
                    id = stable("$tableIndex-$heading-$rowIndex-$title"),
                    courseCode = courseCode,
                    courseTitle = courseTitle,
                    courseType = courseType,
                    title = title,
                    maxMarks = values.find(*maximumHeaders.toTypedArray())?.number(),
                    weightagePercent = values.find("weightage %", "weightage percentage", "weightage")?.number(),
                    status = values.find("status", "publication status", "result status").orEmpty(),
                    scoredMark = values.find(*scoreHeaders.toTypedArray())?.number(),
                    weightageMark = values.find("weightage mark", "weighted mark", "weighted score")?.number(),
                )
            }
        }
        return if (records.isEmpty()) ParseResult.InvalidDocument("No assessment marks were found") else ParseResult.Success(records)
    }

    private fun Element.outerCourseContext(fallback: CourseContext): CourseContext {
        val containerRow = parents().firstOrNull { it.tagName() == "tr" } ?: return fallback
        val outerTable = containerRow.parents().firstOrNull { it.tagName() == "table" } ?: return fallback
        val outerRows = outerTable.directRows()
        val containerIndex = outerRows.indexOfFirst { it === containerRow }
        if (containerIndex <= 0) return fallback
        val dataIndex = (containerIndex - 1 downTo 0).firstOrNull { index ->
            outerRows[index].directCells().size > 1 && outerRows[index].selectFirst("table") == null
        } ?: return fallback
        val headerRow = (dataIndex - 1 downTo 0).firstOrNull { index ->
            outerRows[index].directCells().map { norm(it.text()) }.any { it in courseHeaders }
        }?.let(outerRows::get) ?: return fallback
        val values = headerRow.directCells().map { norm(it.text()) }
            .zip(outerRows[dataIndex].directCells().map(Element::text))
            .toMap()
        return CourseContext(
            courseCode = values.find("course code", "subject code")?.trim().orEmpty().ifBlank { fallback.courseCode },
            courseTitle = values.find("course title", "course name", "subject title", "subject name")
                ?.trim().orEmpty().ifBlank { fallback.courseTitle },
            courseType = values.find("course type", "type", "subject type")?.trim().orEmpty().ifBlank { fallback.courseType },
        )
    }

    private fun Element.directRows(): List<Element> = select("tr").filter { row ->
        row.parents().firstOrNull { it.tagName() == "table" } === this
    }

    private fun Element.directCells(): List<Element> = children().filter { it.tagName() == "th" || it.tagName() == "td" }

    private data class CourseContext(val courseCode: String = "", val courseTitle: String = "", val courseType: String = "")

    private fun String.courseContext(fallback: CourseContext = CourseContext()): CourseContext {
        val text = trim()
        val code = Regex("[A-Z]{2,8}\\s*[-_]?\\s*\\d{3,5}[A-Z]?", RegexOption.IGNORE_CASE)
            .find(text)?.value?.replace(Regex("\\s+"), "").orEmpty().ifBlank { fallback.courseCode }
        val parts = text.split(Regex("\\s+-\\s+")).map(String::trim).filter(String::isNotBlank)
        val type = parts.lastOrNull()?.takeIf { candidate ->
            Regex("theory|lab|embedded|project|practical", RegexOption.IGNORE_CASE).containsMatchIn(candidate)
        }.orEmpty().ifBlank { fallback.courseType }
        val titleParts = parts.drop(if (parts.firstOrNull()?.contains(code, ignoreCase = true) == true) 1 else 0)
            .dropLast(if (type.isNotBlank() && parts.lastOrNull().equals(type, true)) 1 else 0)
        val title = titleParts.joinToString(" - ").ifBlank { fallback.courseTitle }
        return CourseContext(code, title, type)
    }

    private companion object {
        val componentHeaders = setOf("title", "mark title", "assessment", "assessment title", "component", "mark component")
        val maximumHeaders = setOf("max marks", "max mark", "maximum marks", "maximum mark")
        val scoreHeaders = setOf("scored mark", "marks scored", "score", "marks obtained", "mark obtained")
        val courseHeaders = setOf("course code", "subject code", "course title", "course name", "course type", "subject type")
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
        fun value(vararg labels: String) = labels.firstNotNullOfOrNull { label ->
            Regex("${Regex.escape(label)}\\s*(?:[:=-]\\s*)?([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
                .find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        }
        val counts = document.select("table tr").mapNotNull { row ->
            val cells = row.select("th,td").map(Element::text)
            if (cells.size < 2) null else Regex("([SABCDENF])\\s*Grades?", RegexOption.IGNORE_CASE).find(cells[0])?.groupValues?.get(1)?.uppercase()?.let { it to (cells[1].filter(Char::isDigit).toIntOrNull() ?: 0) }
        }.toMap()
        val result = CgpaSnapshot(
            value("Credits Registered", "Registered Credits"),
            value("Credits Earned", "Earned Credits"),
            value("CGPA"),
            counts,
        )
        return if (result.cgpa == null) ParseResult.InvalidDocument("CGPA was not found") else ParseResult.Success(result)
    }
}

private fun Map<String, String>.find(vararg names: String): String? = names.firstNotNullOfOrNull { this[norm(it)] }
private fun String.number(): Double? = Regex("-?[0-9]+(?:\\.[0-9]+)?").find(this)?.value?.toDoubleOrNull()
private fun norm(value: String) = value.trim().lowercase().replace(Regex("[^a-z0-9%]+"), " ").trim()
private fun stable(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
