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

        parseFlatTable(document.select("table").toList())?.let { return ParseResult.Success(it) }

        val courseDetails = parseCourseDetails(document.select("table").toList())
        val gridSlots = parseWeeklyGrid(document.select("table").toList(), courseDetails)
        if (gridSlots.isEmpty()) return ParseResult.InvalidDocument("Timetable grid was not found")

        val courses = gridSlots.map { it.courseId }.distinct().map { id ->
            courseDetails[id]?.course ?: Course(id, id.uppercase(Locale.ROOT), id.uppercase(Locale.ROOT), "")
        }
        return ParseResult.Success(TimetableSnapshot(courses, mergeAdjacent(gridSlots)))
    }

    private fun parseFlatTable(tables: List<Element>): TimetableSnapshot? {
        tables.forEach { table ->
            val headerRow = table.selectFirst("thead tr") ?: table.selectFirst("tr") ?: return@forEach
            val headers = headerRow.select("th, td").map { normalize(it.text()) }
            if (headers.none { it == "day" } || headers.none { "start" in it }) return@forEach
            val courses = linkedMapOf<String, Course>()
            val slots = mutableListOf<ClassSlot>()
            table.select("tr").drop(1).forEachIndexed { rowIndex, row ->
                val values = headers.zip(row.select("td").map(Element::text)).toMap()
                val code = values.find("course code", "code") ?: return@forEachIndexed
                val day = parseDay(values.find("day")) ?: return@forEachIndexed
                val start = parseTime(values.find("start time", "start", "from")) ?: return@forEachIndexed
                val end = parseTime(values.find("end time", "end", "to")) ?: return@forEachIndexed
                val id = stableId(code)
                val type = parseType(values.find("type", "class type"))
                courses.putIfAbsent(id, Course(id, code.trim(), values.find("course title", "course name", "title").orEmpty().trim(), values.find("faculty", "faculty name").orEmpty().trim()))
                slots += ClassSlot("$id-${day.value}-$start-$rowIndex", id, day, start, end, values.find("venue", "room").orEmpty().trim(), type)
            }
            if (courses.isNotEmpty() && slots.isNotEmpty()) return TimetableSnapshot(courses.values.toList(), slots)
        }
        return null
    }

    private fun parseCourseDetails(tables: List<Element>): Map<String, CourseDetail> {
        val details = linkedMapOf<String, CourseDetail>()
        tables.forEach { table ->
            val rows = table.select("tr")
            val header = rows.firstOrNull() ?: return@forEach
            val headings = header.select("th, td").map { normalize(it.text()) }
            val combinedIndex = headings.indexOfFirst { it == "course" }
            val codeIndex = headings.indexOfFirst { "course" in it && "code" in it }
            val titleIndex = headings.indexOfFirst { "course" in it && ("title" in it || "name" in it) }
            val slotIndex = headings.indexOfFirst { "slot" in it }
            val facultyIndex = headings.indexOfFirst { "faculty" in it }
            val typeIndex = headings.indexOfFirst { "type" in it }
            if (combinedIndex < 0 && codeIndex < 0) return@forEach

            rows.drop(1).forEach { row ->
                val cells = row.select("td").map { it.text().replace(Regex("\\s+"), " ").trim() }
                fun cell(index: Int) = cells.getOrNull(index).orEmpty()
                val combined = cell(combinedIndex)
                val code = if (codeIndex >= 0) cell(codeIndex) else combined.substringBefore("-").trim()
                if (code.isBlank() || code.equals("course", true)) return@forEach
                val title = if (titleIndex >= 0) cell(titleIndex) else combined.substringAfter("-", "").substringBeforeLast("(").trim()
                val slotVenue = cell(slotIndex)
                val venue = slotVenue.substringAfter("-", "").trim()
                val id = stableId(code)
                details[id] = CourseDetail(
                    course = Course(id, code, title.ifBlank { code }, cell(facultyIndex)),
                    venue = venue,
                    type = parseType(cell(typeIndex).ifBlank { combined.substringAfterLast("(", "").substringBefore(")") }),
                )
            }
        }
        return details
    }

    private fun parseWeeklyGrid(tables: List<Element>, details: Map<String, CourseDetail>): List<ClassSlot> {
        val table = tables.firstOrNull { candidate ->
            candidate.select("tr").any { row -> parseDay(row.select("th, td").firstOrNull()?.text()) != null }
        } ?: return emptyList()
        var theoryStarts = emptyList<LocalTime?>()
        var theoryEnds = emptyList<LocalTime?>()
        var labStarts = emptyList<LocalTime?>()
        var labEnds = emptyList<LocalTime?>()
        var currentDay: DayOfWeek? = null
        val slots = mutableListOf<ClassSlot>()

        fun addCells(cells: List<String>, starts: List<LocalTime?>, ends: List<LocalTime?>, type: ClassType, day: DayOfWeek) {
            cells.forEachIndexed { index, value ->
                val parts = value.split("-").map(String::trim)
                if (parts.size < 2 || value.equals("lunch", true)) return@forEachIndexed
                val code = parts[1]
                if (code.none(Char::isDigit) || code.any(Char::isWhitespace)) return@forEachIndexed
                val start = starts.getOrNull(index) ?: return@forEachIndexed
                val end = ends.getOrNull(index) ?: return@forEachIndexed
                val id = stableId(code)
                val gridVenue = if (parts.size >= 5) parts.subList(3, parts.lastIndex).joinToString("-") else ""
                slots += ClassSlot(
                    id = "$id-${day.value}-$start-${stableId(parts[0])}-$index",
                    courseId = id,
                    day = day,
                    start = start,
                    end = end,
                    venue = gridVenue.ifBlank { details[id]?.venue.orEmpty() },
                    // The weekly grid is the source of truth for whether this is a
                    // theory or lab period. Course-detail rows may share one code,
                    // so looking up by code can otherwise apply the last row's type
                    // to every slot of an embedded course.
                    type = type,
                )
            }
        }

        table.select("tr").forEach { row ->
            val cells = row.select("th, td").map { it.text().replace(Regex("\\s+"), " ").trim() }
            if (cells.isEmpty()) return@forEach
            val first = cells[0].uppercase(Locale.ROOT)
            val second = cells.getOrNull(1).orEmpty().uppercase(Locale.ROOT)
            when {
                first == "THEORY" && second == "START" -> theoryStarts = cells.drop(2).map(::parseTime)
                first == "LAB" && second == "START" -> labStarts = cells.drop(2).map(::parseTime)
                first == "END" && theoryEnds.isEmpty() -> theoryEnds = cells.drop(1).map(::parseTime)
                first == "END" -> labEnds = cells.drop(1).map(::parseTime)
                parseDay(first) != null -> {
                    currentDay = parseDay(first)
                    addCells(cells.drop(2), theoryStarts, theoryEnds, ClassType.THEORY, currentDay!!)
                }
                first == "LAB" && currentDay != null -> addCells(cells.drop(1), labStarts, labEnds, ClassType.LAB, currentDay)
            }
        }
        return slots
    }

    private fun mergeAdjacent(slots: List<ClassSlot>): List<ClassSlot> = slots
        .sortedWith(compareBy(ClassSlot::day, ClassSlot::start))
        .fold(mutableListOf()) { merged, slot ->
            val previous = merged.lastOrNull()
            val consecutive = previous != null && (slot.start == previous.end || slot.start == previous.end.plusMinutes(1))
            if (previous != null && consecutive && previous.courseId == slot.courseId && previous.day == slot.day && previous.venue == slot.venue && previous.type == slot.type) {
                merged[merged.lastIndex] = previous.copy(end = slot.end)
            } else merged += slot
            merged
        }

    private fun Map<String, String>.find(vararg alternatives: String): String? = alternatives.firstNotNullOfOrNull { this[normalize(it)] }
    private fun normalize(value: String) = value.trim().lowercase().replace(Regex("\\s+"), " ")
    private fun stableId(value: String) = value.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    private fun parseDay(value: String?): DayOfWeek? = when (value?.trim()?.take(3)?.uppercase(Locale.ROOT)) {
        "MON" -> DayOfWeek.MONDAY; "TUE" -> DayOfWeek.TUESDAY; "WED" -> DayOfWeek.WEDNESDAY
        "THU" -> DayOfWeek.THURSDAY; "FRI" -> DayOfWeek.FRIDAY; "SAT" -> DayOfWeek.SATURDAY
        "SUN" -> DayOfWeek.SUNDAY; else -> null
    }
    private fun parseTime(value: String?): LocalTime? {
        if (value.isNullOrBlank() || value.equals("lunch", true)) return null
        return listOf("H:mm", "HH:mm", "h:mm a", "hh:mm a").firstNotNullOfOrNull { pattern ->
            runCatching { LocalTime.parse(value.trim().uppercase(Locale.ROOT), DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)) }.getOrNull()
        }
    }
    private fun parseType(value: String?): ClassType = when {
        value?.contains("lab", true) == true -> ClassType.LAB
        value?.contains("project", true) == true -> ClassType.PROJECT
        value.isNullOrBlank() -> ClassType.UNKNOWN
        else -> ClassType.THEORY
    }

    private data class CourseDetail(val course: Course, val venue: String, val type: ClassType)
}
