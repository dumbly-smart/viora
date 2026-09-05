package app.viora.calendar

import app.viora.database.AcademicDao
import app.viora.database.ImportedCalendarEventEntity
import java.time.Duration
import java.time.Instant

data class CalendarImportSummary(
    val imported: Int,
    val skipped: Int,
)

interface ImportedCalendarStore {
    suspend fun replaceImportedCalendarEvents(rows: List<ImportedCalendarEventEntity>)
}

class DaoImportedCalendarStore(private val dao: AcademicDao) : ImportedCalendarStore {
    override suspend fun replaceImportedCalendarEvents(rows: List<ImportedCalendarEventEntity>) =
        dao.replaceImportedCalendarEvents(rows)
}

class CalendarInterchangeRepository(
    private val store: ImportedCalendarStore,
    private val maxImportBytes: Int = 5 * 1024 * 1024,
) {
    suspend fun importIcs(content: String, now: Instant): CalendarImportSummary {
        require(content.toByteArray(Charsets.UTF_8).size <= maxImportBytes) { "Calendar file is larger than 5 MiB" }
        val decoded = IcsCalendarCodec.decode(content, now, now.plus(Duration.ofDays(180)))
        val rows = decoded.events.map { event ->
            ImportedCalendarEventEntity(
                id = event.uid,
                title = event.title,
                details = event.details,
                location = event.location,
                startsEpochMillis = event.startsAt.toEpochMilli(),
                endsEpochMillis = event.endsAt.toEpochMilli(),
            )
        }
        require(rows.map { it.id }.distinct().size == rows.size) { "Calendar contains duplicate event identifiers" }
        store.replaceImportedCalendarEvents(rows)
        return CalendarImportSummary(rows.size, decoded.skipped)
    }

    fun exportIcs(calendarName: String, events: List<CalendarInterchangeEvent>): String =
        IcsCalendarCodec.encode(calendarName, events)
}
