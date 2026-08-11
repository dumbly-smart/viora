package app.viora.widget

import app.viora.database.AcademicCalendarEntity
import app.viora.database.SlotWithCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NextClassResolverTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val mondayClass = slot("monday", 1, 600, 660)
    private val tuesdayClass = slot("tuesday", 2, 540, 600)

    @Test fun `returns an in-progress class before a later class`() {
        val now = ZonedDateTime.of(2026, 8, 10, 10, 30, 0, 0, zone)
        val result = NextClassResolver.resolve(listOf(mondayClass), emptyList(), now)

        assertEquals("monday", result?.slot?.slotId)
        assertTrue(result?.happeningNow == true)
    }

    @Test fun `skips holidays and uses an instructional day order`() {
        val now = ZonedDateTime.of(2026, 8, 10, 12, 0, 0, 0, zone)
        val calendar = listOf(
            calendar(2026, 8, 11, "University holiday"),
            calendar(2026, 8, 12, "Tuesday order"),
        )
        val result = NextClassResolver.resolve(listOf(mondayClass, tuesdayClass), calendar, now)

        assertEquals("tuesday", result?.slot?.slotId)
        assertEquals(12, result?.startsAt?.dayOfMonth)
    }

    @Test fun `returns null when no class is in the search window`() {
        val now = ZonedDateTime.of(2026, 8, 10, 12, 0, 0, 0, zone)
        assertNull(NextClassResolver.resolve(emptyList(), emptyList(), now))
    }

    private fun slot(id: String, day: Int, start: Int, end: Int) = SlotWithCourse(
        id, "course-$id", id.uppercase(), "$id class", "Faculty", day, start, end, "Room 1", "Theory",
    )

    private fun calendar(year: Int, month: Int, day: Int, title: String) = AcademicCalendarEntity(
        "semester", "$year-$month-$day", java.time.LocalDate.of(year, month, day).toEpochDay(), title, "", 0,
    )
}
