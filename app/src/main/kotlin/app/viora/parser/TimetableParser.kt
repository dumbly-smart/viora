package app.viora.parser

import app.viora.model.ClassSlot
import app.viora.model.ClassType
import app.viora.model.Course
import app.viora.network.TimetableSnapshot
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class TimetableParser {
    fun parse(html: String): ParseResult<TimetableSnapshot> {
        val document = Jsoup.parse(html)
        if (VtopDocument.isAuthenticationPage(document)) return ParseResult.AuthenticationRequired

        val table = document.selectFirst("table.customTable, table.table")
            ?: return ParseResult.InvalidDocument("Timetable table was not found")
        val headers = table.select("thead th").map { normalize(it.text()) }
        if (headers.isEmpty()) return ParseResult.InvalidDocument("Timetable headers were not found")

        val courses = linkedMapOf<String, Course>()
        val slots = mutableListOf<ClassSlot>()
        table.select("tbody tr").forEachIndexed { rowIndex, row ->
            val cells = row.select("td")
            if (cells.isEmpty()) return@forEachIndexed
            val values = headers.zip(cells.map(Element::text)).toMap()
            val code = values.find("course code", "code") ?: return@forEachIndexed
            val title = values.find("course title", "course name", "title").orEmpty()
            val courseId = stableId(code)
            courses.putIfAbsent(
                courseId,
                Course(courseId, code.trim(), title.trim(), values.find("faculty", "faculty name").orEmpty().trim()),
            )

            val day = parseDay(values.find("day")) ?: return@forEachIndexed
            val start = parseTime(values.find("start time", "start", "from")) ?: return@forEachIndexed
            val end = parseTime(values.find("end time", "end", "to")) ?: return@forEachIndexed
            val typeText = values.find("type", "class type").orEmpty()
            slots += ClassSlot(
                id = "$courseId-${day.value}-$start-${stableId(values.find("slot").orEmpty())}-$rowIndex",
                courseId = courseId,
                day = day,
                start = start,
                end = end,
                venue = values.find("venue", "room").orEmpty().trim(),
                type = when {
                    "lab" in typeText.lowercase() -> ClassType.LAB
                    "project" in typeText.lowercase() -> ClassType.PROJECT
                    typeText.isBlank() -> ClassType.UNKNOWN
                    else -> ClassType.THEORY
                },
            )
        }

        if (courses.isEmpty() || slots.isEmpty()) {
            return ParseResult.InvalidDocument("No plausible timetable records were found")
        }
        return ParseResult.Success(TimetableSnapshot(courses.values.toList(), slots))
    }

    private fun Map<String, String>.find(vararg alternatives: String): String? =
        alternatives.firstNotNullOfOrNull { this[normalize(it)] }

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun stableId(value: String): String = value.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private fun parseDay(value: String?): DayOfWeek? = value?.trim()?.takeIf(String::isNotEmpty)?.let {
        runCatching { DayOfWeek.valueOf(it.uppercase(Locale.ROOT)) }.getOrNull()
    }

    private fun parseTime(value: String?): LocalTime? {
        if (value.isNullOrBlank()) return null
        val formats = listOf("H:mm", "HH:mm", "h:mm a", "hh:mm a")
        return formats.firstNotNullOfOrNull { pattern ->
            runCatching {
                LocalTime.parse(value.trim().uppercase(Locale.ROOT), DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
            }.getOrNull()
        }
    }
}
