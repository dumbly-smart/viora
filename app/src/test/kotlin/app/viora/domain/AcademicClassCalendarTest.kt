package app.viora.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AcademicClassCalendarTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun `calendar classifies dates and applies makeup day orders`() {
        val calendar = AcademicClassCalendar(listOf(
            ExamCalendarDate(monday, "Regular day", "Instructional Day"),
            ExamCalendarDate(monday.plusDays(1), "Campus event", "Non-Instructional Day"),
            ExamCalendarDate(monday.plusDays(2), "Assessment", "Examination Day"),
            ExamCalendarDate(monday.plusDays(5), "Tuesday order", "Instructional Day"),
        ), emptyList(), zone)

        assertEquals(DayOfWeek.MONDAY, calendar.timetableDay(monday))
        assertNull(calendar.timetableDay(monday.plusDays(1)))
        assertNull(calendar.timetableDay(monday.plusDays(2)))
        assertEquals(DayOfWeek.TUESDAY, calendar.timetableDay(monday.plusDays(5)))
    }

    @Test fun `old timetable ends at FAT even when a later date is instructional`() {
        val calendar = AcademicClassCalendar(listOf(
            ExamCalendarDate(monday, "FAT registration opens", ""),
            ExamCalendarDate(monday.plusDays(2), "Final Assessment Test commences", ""),
            ExamCalendarDate(monday.plusDays(4), "FAT examinations conclude", ""),
            ExamCalendarDate(monday.plusWeeks(1), "Monday order", "Instructional Day"),
        ), emptyList(), zone)

        assertEquals(DayOfWeek.MONDAY, calendar.timetableDay(monday))
        assertNull(calendar.timetableDay(monday.plusDays(2)))
        assertNull(calendar.timetableDay(monday.plusWeeks(1)))
    }
}
