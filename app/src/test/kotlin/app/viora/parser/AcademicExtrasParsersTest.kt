package app.viora.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicExtrasParsersTest {
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
