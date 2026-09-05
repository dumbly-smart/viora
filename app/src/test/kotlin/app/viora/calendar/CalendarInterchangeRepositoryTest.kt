package app.viora.calendar

import app.viora.database.ImportedCalendarEventEntity
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarInterchangeRepositoryTest {
    @Test
    fun `valid import replaces the previous set after complete decoding`() = runBlocking {
        val store = FakeStore(listOf(ImportedCalendarEventEntity("old", "Old", "", "", 1, 2)))
        val repository = CalendarInterchangeRepository(store)

        val summary = repository.importIcs(validIcs, Instant.parse("2026-09-01T00:00:00Z"))

        assertEquals(1, summary.imported)
        assertEquals(listOf("event"), store.rows.map { it.id })
        assertTrue(store.rows.single().startsEpochMillis > 0)
    }

    @Test
    fun `malformed import preserves previous rows`() = runBlocking {
        val old = ImportedCalendarEventEntity("old", "Old", "", "", 1, 2)
        val store = FakeStore(listOf(old))
        val repository = CalendarInterchangeRepository(store)

        val result = runCatching { repository.importIcs("not a calendar", Instant.EPOCH) }

        assertTrue(result.isFailure)
        assertEquals(listOf(old), store.rows)
        assertEquals(0, store.replacements)
    }

    @Test
    fun `oversized import is rejected before replacement`() = runBlocking {
        val store = FakeStore(emptyList())
        val repository = CalendarInterchangeRepository(store, maxImportBytes = 20)

        val result = runCatching { repository.importIcs(validIcs, Instant.EPOCH) }

        assertTrue(result.isFailure)
        assertEquals(0, store.replacements)
    }

    private class FakeStore(initial: List<ImportedCalendarEventEntity>) : ImportedCalendarStore {
        var rows = initial
        var replacements = 0
        override suspend fun replaceImportedCalendarEvents(rows: List<ImportedCalendarEventEntity>) {
            replacements++
            this.rows = rows
        }
    }

    private val validIcs = """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        UID:event
        SUMMARY:Imported class
        DTSTART:20260907T033000Z
        DTEND:20260907T043000Z
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()
}
