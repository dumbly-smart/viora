package app.viora.calendar

import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

object IcsCalendarCodec {
    private val academicZone = ZoneId.of("Asia/Kolkata")
    private val localDateTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val shortLocalDateTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm")

    fun encode(calendarName: String, events: List<CalendarInterchangeEvent>): String {
        val lines = buildList {
            add("BEGIN:VCALENDAR")
            add("VERSION:2.0")
            add("PRODID:-//Viora//Calendar Interchange//EN")
            add("CALSCALE:GREGORIAN")
            add("X-WR-CALNAME:${escape(calendarName)}")
            events.sortedWith(compareBy<CalendarInterchangeEvent> { it.startsAt }.thenBy { it.uid }).forEach { event ->
                require(event.endsAt.isAfter(event.startsAt)) { "Calendar event must end after it starts" }
                add("BEGIN:VEVENT")
                add("UID:${escape(event.uid)}")
                add("SUMMARY:${escape(event.title)}")
                if (event.details.isNotBlank()) add("DESCRIPTION:${escape(event.details)}")
                if (event.location.isNotBlank()) add("LOCATION:${escape(event.location)}")
                add("DTSTART;TZID=Asia/Kolkata:${format(event.startsAt)}")
                add("DTEND;TZID=Asia/Kolkata:${format(event.endsAt)}")
                add("END:VEVENT")
            }
            add("END:VCALENDAR")
        }
        return lines.flatMap(::fold).joinToString("\r\n", postfix = "\r\n")
    }

    fun decode(content: String, importStart: Instant, importEnd: Instant): IcsDecodeResult {
        require(importEnd.isAfter(importStart)) { "Import range is invalid" }
        val lines = unfold(content)
        require(lines.firstOrNull()?.equals("BEGIN:VCALENDAR", true) == true &&
            lines.lastOrNull()?.equals("END:VCALENDAR", true) == true) { "Malformed VCALENDAR" }
        val blocks = mutableListOf<List<String>>()
        var current: MutableList<String>? = null
        lines.drop(1).dropLast(1).forEach { line ->
            when {
                line.equals("BEGIN:VEVENT", true) -> {
                    require(current == null) { "Nested VEVENT is invalid" }
                    current = mutableListOf()
                }
                line.equals("END:VEVENT", true) -> {
                    val completed = requireNotNull(current) { "VEVENT end has no beginning" }
                    blocks += completed
                    current = null
                }
                current != null -> current.add(line)
            }
        }
        require(current == null) { "VEVENT is not closed" }

        var skipped = 0
        val decoded = mutableListOf<CalendarInterchangeEvent>()
        blocks.forEach { block ->
            val properties = block.mapNotNull(::property)
            val startProperty = properties.firstOrNull { it.name == "DTSTART" }
                ?: throw IllegalArgumentException("VEVENT is missing DTSTART")
            val endProperty = properties.firstOrNull { it.name == "DTEND" }
                ?: throw IllegalArgumentException("VEVENT is missing DTEND")
            if (startProperty.parameters["VALUE"] == "DATE" || endProperty.parameters["VALUE"] == "DATE") {
                skipped++
                return@forEach
            }
            val start = parseInstant(startProperty)
            val end = parseInstant(endProperty)
            require(end.isAfter(start)) { "VEVENT DTEND must be after DTSTART" }
            val title = unescape(properties.firstOrNull { it.name == "SUMMARY" }?.value.orEmpty()).ifBlank { "Imported event" }
            val details = unescape(properties.firstOrNull { it.name == "DESCRIPTION" }?.value.orEmpty())
            val location = unescape(properties.firstOrNull { it.name == "LOCATION" }?.value.orEmpty())
            val suppliedUid = unescape(properties.firstOrNull { it.name == "UID" }?.value.orEmpty())
            val uid = suppliedUid.ifBlank { fallbackUid(title, start, end) }
            val recurrence = properties.firstOrNull { it.name == "RRULE" }
            if (recurrence == null) {
                if (!start.isBefore(importStart) && start.isBefore(importEnd)) {
                    decoded += CalendarInterchangeEvent(uid, title, details, location, start, end)
                }
            } else {
                val rule = recurrence.value.split(';').mapNotNull { part ->
                    val pieces = part.split('=', limit = 2)
                    pieces.takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1].uppercase() }
                }.toMap()
                if (rule["FREQ"] != "WEEKLY") {
                    skipped++
                    return@forEach
                }
                decoded += materializeWeekly(uid, title, details, location, start, end, rule, importStart, importEnd)
            }
        }
        return IcsDecodeResult(decoded.sortedWith(compareBy<CalendarInterchangeEvent> { it.startsAt }.thenBy { it.uid }), skipped)
    }

    private fun materializeWeekly(
        uid: String,
        title: String,
        details: String,
        location: String,
        start: Instant,
        end: Instant,
        rule: Map<String, String>,
        importStart: Instant,
        importEnd: Instant,
    ): List<CalendarInterchangeEvent> {
        val startLocal = start.atZone(academicZone)
        val duration = Duration.between(start, end)
        val interval = rule["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val days = rule["BYDAY"]?.split(',')?.mapNotNull(::dayOfWeek)?.toSet().orEmpty()
            .ifEmpty { setOf(startLocal.dayOfWeek) }
        val until = rule["UNTIL"]?.let { raw ->
            parseInstant(Property("UNTIL", emptyMap(), raw))
        }
        val count = rule["COUNT"]?.toIntOrNull()?.takeIf { it > 0 }
        val anchorWeek = startLocal.toLocalDate().with(DayOfWeek.MONDAY)
        val rangeStartDate = maxOf(startLocal.toLocalDate(), importStart.atZone(academicZone).toLocalDate())
        val rangeEndDate = importEnd.minusNanos(1).atZone(academicZone).toLocalDate()
        val results = mutableListOf<CalendarInterchangeEvent>()
        var generated = 0
        var cursor = startLocal.toLocalDate()
        while (!cursor.isAfter(rangeEndDate)) {
            val weeks = ChronoUnit.WEEKS.between(anchorWeek, cursor.with(DayOfWeek.MONDAY))
            if (weeks % interval == 0L && cursor.dayOfWeek in days) {
                val occurrenceStart = ZonedDateTime.of(cursor, startLocal.toLocalTime(), academicZone).toInstant()
                if (!occurrenceStart.isBefore(start)) {
                    generated++
                    if (count != null && generated > count) break
                    if (until != null && occurrenceStart.isAfter(until)) break
                    if (!cursor.isBefore(rangeStartDate) && !occurrenceStart.isBefore(importStart) && occurrenceStart.isBefore(importEnd)) {
                        results += CalendarInterchangeEvent(
                            uid = "$uid:$generated",
                            title = title,
                            details = details,
                            location = location,
                            startsAt = occurrenceStart,
                            endsAt = occurrenceStart.plus(duration),
                        )
                    }
                }
            }
            cursor = cursor.plusDays(1)
        }
        return results
    }

    private data class Property(val name: String, val parameters: Map<String, String>, val value: String)

    private fun property(line: String): Property? {
        val colon = line.indexOf(':')
        if (colon < 0) return null
        val parts = line.substring(0, colon).split(';')
        val params = parts.drop(1).mapNotNull { raw ->
            val pair = raw.split('=', limit = 2)
            pair.takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] }
        }.toMap()
        return Property(parts.first().uppercase(), params, line.substring(colon + 1))
    }

    private fun parseInstant(property: Property): Instant {
        val raw = property.value.trim()
        return try {
            if (raw.endsWith('Z')) {
                val local = parseLocal(raw.dropLast(1))
                local.toInstant(java.time.ZoneOffset.UTC)
            } else {
                val zone = property.parameters["TZID"]?.let(ZoneId::of) ?: academicZone
                parseLocal(raw).atZone(zone).toInstant()
            }
        } catch (error: DateTimeParseException) {
            throw IllegalArgumentException("Invalid ${property.name}", error)
        }
    }

    private fun parseLocal(raw: String): LocalDateTime = when (raw.length) {
        13 -> LocalDateTime.parse(raw, shortLocalDateTime)
        else -> LocalDateTime.parse(raw, localDateTime)
    }

    private fun format(instant: Instant): String = localDateTime.format(instant.atZone(academicZone))

    private fun dayOfWeek(value: String): DayOfWeek? = when (value) {
        "MO" -> DayOfWeek.MONDAY
        "TU" -> DayOfWeek.TUESDAY
        "WE" -> DayOfWeek.WEDNESDAY
        "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY
        "SA" -> DayOfWeek.SATURDAY
        "SU" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun unfold(content: String): List<String> {
        val result = mutableListOf<String>()
        content.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
            if ((line.startsWith(' ') || line.startsWith('\t')) && result.isNotEmpty()) {
                result[result.lastIndex] += line.drop(1)
            } else if (line.isNotEmpty()) {
                result += line
            }
        }
        return result
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace(";", "\\;")
        .replace(",", "\\,")

    private fun unescape(value: String): String {
        val result = StringBuilder()
        var escaped = false
        value.forEach { char ->
            if (escaped) {
                result.append(if (char == 'n' || char == 'N') '\n' else char)
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else {
                result.append(char)
            }
        }
        if (escaped) result.append('\\')
        return result.toString()
    }

    private fun fold(line: String): List<String> {
        if (line.toByteArray(Charsets.UTF_8).size <= 75) return listOf(line)
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var bytes = 0
        line.codePoints().forEach { point ->
            val value = String(Character.toChars(point))
            val size = value.toByteArray(Charsets.UTF_8).size
            val limit = if (result.isEmpty()) 75 else 74
            if (bytes + size > limit && current.isNotEmpty()) {
                result += (if (result.isEmpty()) "" else " ") + current.toString()
                current = StringBuilder()
                bytes = 0
            }
            current.append(value)
            bytes += size
        }
        if (current.isNotEmpty()) result += (if (result.isEmpty()) "" else " ") + current.toString()
        return result
    }

    private fun fallbackUid(title: String, start: Instant, end: Instant): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$title|$start|$end".toByteArray())
        return "imported:" + bytes.take(12).joinToString("") { "%02x".format(it) }
    }
}
