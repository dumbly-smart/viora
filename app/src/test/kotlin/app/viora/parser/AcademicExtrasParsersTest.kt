package app.viora.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicExtrasParsersTest {
    @Test fun `calendar programs retain branch names and their VTOP request values`() {
        val html = """<select id="semester"><option value="term">Fall</option></select>
            <select name="classGroupId">
            <option value="0">Select class group</option>
            <option value="SYN-CSE">B.Tech CSE</option>
            <option value="SYN-MECH">B.Tech Mechanical</option>
            <option value="COMB">B.Tech All</option></select>"""
        assertEquals(
            listOf("SYN-CSE" to "B.Tech CSE", "SYN-MECH" to "B.Tech Mechanical", "COMB" to "B.Tech All"),
            AcademicCalendarParser().groups(html).map { it.id to it.name },
        )
    }

    @Test fun `calendar month links decode HTML entities and accept JavaScript argument variations`() {
        val html = """<a onclick="javascript:processViewCalendar(&#39;01-NOV-2026&#39;);">November</a>
            <a onclick="processViewCalendar(this, &quot;01-DEC-2026&quot;)">December</a>"""
        assertEquals(listOf("01-NOV-2026", "01-DEC-2026"), AcademicCalendarParser().months(html))
    }

    @Test fun `calendar parses VTOP month grid with exam notes and day orders`() {
        val html = """<table class="calendar-table"><tr><td><span>9</span><span>Exam Day</span><span>FAT exams commence</span></td>
            <td><span>10</span><span>No Instructional Day</span></td><td><span>11</span><span>Monday order</span><span>Instructional Day</span></td></tr></table>"""
        val result = AcademicCalendarParser().parse(html, java.time.YearMonth.of(2026, 11)) as ParseResult.Success
        assertEquals(3, result.value.size)
        assertTrue(result.value.first().title.contains("FAT"))
        assertEquals(java.time.LocalDate.of(2026, 11, 9), result.value.first().date)
    }

    @Test fun `calendar parser accepts rows and valid empty calendars`() {
        val html = """<table><tbody><tr><td>15-Aug-2026</td><td>Holiday</td><td>Independence Day</td></tr></tbody></table>"""
        val rows = (AcademicCalendarParser().parse(html) as ParseResult.Success).value
        assertEquals(1, rows.size)
        assertEquals("Holiday", rows.single().title)
        assertTrue(AcademicCalendarParser().parse("<div id='MenuBlock'>No events</div>") is ParseResult.Success)
    }

    @Test fun `message parser treats no messages as a valid snapshot`() {
        val result = ClassMessageParser().parse("<div id='MenuBlock'>No class messages found</div>") as ParseResult.Success
        assertTrue(result.value.isEmpty())
    }
}
