package app.viora.parser

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

internal object VtopDateParser {
    private val dateTimeFormats = listOf(
        "dd-MMM-yyyy hh:mm a",
        "dd MMM yyyy hh:mm a",
        "dd-MMM-yyyy HH:mm",
        "dd MMM yyyy HH:mm",
        "dd/MM/yyyy HH:mm",
        "yyyy-MM-dd HH:mm",
    ).map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    private val dateFormats = listOf("dd-MMM-yyyy", "dd MMM yyyy", "dd/MM/yyyy", "yyyy-MM-dd")
        .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }
    private val timeFormats = listOf("hh:mm a", "h:mm a", "HH:mm", "H:mm")
        .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    fun dateTime(value: String?): LocalDateTime? {
        val normalized = value?.trim()?.replace(Regex("\\s+"), " ") ?: return null
        return dateTimeFormats.firstNotNullOfOrNull { format ->
            tryParse { LocalDateTime.parse(normalized.uppercase(Locale.ENGLISH), format) }
        }
    }

    fun dateAndTime(date: String?, time: String?): LocalDateTime? {
        val dateValue = date?.trim() ?: return null
        val timeValue = time?.trim()?.uppercase(Locale.ENGLISH) ?: return null
        val day = dateFormats.firstNotNullOfOrNull { format ->
            tryParse { LocalDate.parse(dateValue, format) }
        } ?: return null
        val clock = timeFormats.firstNotNullOfOrNull { format ->
            tryParse { LocalTime.parse(timeValue, format) }
        } ?: return null
        return LocalDateTime.of(day, clock)
    }

    private inline fun <T> tryParse(block: () -> T): T? = try {
        block()
    } catch (_: DateTimeParseException) {
        null
    }
}
