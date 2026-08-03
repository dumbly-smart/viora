package app.viora.parser

import app.viora.model.ClassType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableParserTest {
    private val parser = TimetableParser()

    @Test fun `parses a redacted timetable table`() {
        val html = """
            <html><head><title>Timetable</title></head><body>
            <table class="customTable">
              <thead><tr>
                <th>Course Code</th><th>Course Title</th><th>Faculty</th><th>Day</th>
                <th>Start Time</th><th>End Time</th><th>Venue</th><th>Type</th><th>Slot</th>
              </tr></thead>
              <tbody><tr>
                <td>CSE1001</td><td>Example Course</td><td>Redacted Faculty</td><td>Monday</td>
                <td>09:00</td><td>09:50</td><td>SJT000</td><td>Theory</td><td>A1</td>
              </tr></tbody>
            </table></body></html>
        """.trimIndent()

        val result = parser.parse(html) as ParseResult.Success
        assertEquals("CSE1001", result.value.courses.single().code)
        assertEquals(ClassType.THEORY, result.value.slots.single().type)
    }

    @Test fun `detects an authentication response before parsing`() {
        val result = parser.parse("<html><body><div id='captchaBlock'>Verify</div></body></html>")
        assertTrue(result is ParseResult.AuthenticationRequired)
    }

    @Test fun `does not accept an unexpectedly empty page as a snapshot`() {
        val result = parser.parse("<html><body>Temporarily unavailable</body></html>")
        assertTrue(result is ParseResult.InvalidDocument)
    }
}
