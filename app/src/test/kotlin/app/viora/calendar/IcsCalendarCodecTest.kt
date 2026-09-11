package app.viora.calendar

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class IcsCalendarCodecTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun `round trip preserves escaped full detail and Kolkata time`() {
        val start = ZonedDateTime.of(2026, 9, 7, 9, 0, 0, 0, zone).toInstant()
        val event = CalendarInterchangeEvent(
            uid = "viora:one",
            title = "Course, theory; A",
            details = "Faculty\\Name\nBring notes",
            location = "AB1; 201",
            startsAt = start,
            endsAt = start.plusSeconds(3_600),
        )

        val encoded = IcsCalendarCodec.encode("Viora timetable", listOf(event))
        val decoded = IcsCalendarCodec.decode(
            encoded,
            start.minusSeconds(1),
            start.plusSeconds(7_200),
        )

        assertTrue(encoded.contains("\r\n"))
        assertTrue(encoded.contains("DTSTART;TZID=Asia/Kolkata:20260907T090000"))
        assertEquals(listOf(event), decoded.events)
        assertEquals(0, decoded.skipped)
    }

    @Test
    fun `weekly recurrence materializes only inside requested range`() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:weekly
            SUMMARY:Imported class
            DTSTART;TZID=Asia/Kolkata:20260907T090000
            DTEND;TZID=Asia/Kolkata:20260907T100000
            RRULE:FREQ=WEEKLY
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val from = ZonedDateTime.of(2026, 9, 8, 0, 0, 0, 0, zone).toInstant()
        val to = ZonedDateTime.of(2026, 9, 29, 0, 0, 0, 0, zone).toInstant()

        val result = IcsCalendarCodec.decode(ics, from, to)

        assertEquals(
            listOf("2026-09-14T03:30:00Z", "2026-09-21T03:30:00Z", "2026-09-28T03:30:00Z"),
            result.events.map { it.startsAt.toString() },
        )
    }

    @Test
    fun `folded lines are unfolded and unsupported all day rows are counted`() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:timed
            SUMMARY:Long imported
             title
            DTSTART:20260907T033000Z
            DTEND:20260907T043000Z
            END:VEVENT
            BEGIN:VEVENT
            UID:all-day
            SUMMARY:Holiday
            DTSTART;VALUE=DATE:20260908
            DTEND;VALUE=DATE:20260909
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = IcsCalendarCodec.decode(
            ics,
            Instant.parse("2026-09-01T00:00:00Z"),
            Instant.parse("2026-10-01T00:00:00Z"),
        )

        assertEquals("Long importedtitle", result.events.single().title)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `invalid event rejects the calendar`() {
        val ics = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:bad\nSUMMARY:Bad\nDTSTART:20260907T033000Z\nEND:VEVENT\nEND:VCALENDAR"

        try {
            IcsCalendarCodec.decode(ics, Instant.EPOCH, Instant.MAX)
            fail("Expected invalid event to reject import")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("DTEND"))
        }
    }
}
