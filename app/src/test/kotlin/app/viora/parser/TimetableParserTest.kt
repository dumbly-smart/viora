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

    @Test fun `parses VTOP course list and weekly grid`() {
        val html = """
            <div id="studentDetailsList"><table>
              <tr><th>Course</th><th>L T P J C</th><th>Slot - Venue</th><th>Faculty</th></tr>
              <tr><td>BCSE302L - Database Systems (Theory Only)</td><td>3 0 0 0 3</td><td>A1 - AB1-101</td><td>Redacted Faculty</td></tr>
              <tr><td>BCSE302P - Database Systems Lab (Lab Only)</td><td>0 0 2 0 1</td><td>L1 - AB1-201</td><td>Redacted Faculty</td></tr>
            </table></div>
            <table class="w3-table-all">
              <tr><td>THEORY</td><td>Start</td><td>08:00</td><td>08:55</td></tr>
              <tr><td>End</td><td>08:50</td><td>09:45</td></tr>
              <tr><td>LAB</td><td>Start</td><td>14:00</td><td>14:50</td></tr>
              <tr><td>End</td><td>14:50</td><td>15:40</td></tr>
              <tr><td>MON</td><td>THEORY</td><td>A1-BCSE302L-TH-AB1-101-ALL</td><td>A2</td></tr>
              <tr><td>LAB</td><td>L1-BCSE302P-LO-AB1-201-ALL</td><td>L1-BCSE302P-LO-AB1-201-ALL</td></tr>
            </table>
        """.trimIndent()

        val result = parser.parse(html) as ParseResult.Success

        assertEquals(listOf("BCSE302L", "BCSE302P"), result.value.courses.map { it.code })
        assertEquals(2, result.value.slots.size)
        assertEquals("15:40", result.value.slots.single { it.type == ClassType.LAB }.end.toString())
    }

    @Test fun `weekly grid keeps theory and lab types separate when course code is shared`() {
        val html = """
            <table>
              <tr><th>Course</th><th>Slot - Venue</th><th>Faculty</th></tr>
              <tr><td>BEEE101 - Embedded Systems (Embedded Theory)</td><td>A1 - AB1-101</td><td>Faculty T</td></tr>
              <tr><td>BEEE101 - Embedded Systems (Embedded Lab)</td><td>L1 - AB1-201</td><td>Faculty L</td></tr>
            </table>
            <table>
              <tr><td>THEORY</td><td>Start</td><td>08:00</td></tr>
              <tr><td>End</td><td>08:50</td></tr>
              <tr><td>LAB</td><td>Start</td><td>14:00</td></tr>
              <tr><td>End</td><td>14:50</td></tr>
              <tr><td>MON</td><td>THEORY</td><td>A1-BEEE101-TH-AB1-101-ALL</td></tr>
              <tr><td>LAB</td><td>L1-BEEE101-LO-AB1-201-ALL</td></tr>
            </table>
        """.trimIndent()

        val slots = (parser.parse(html) as ParseResult.Success).value.slots

        assertEquals(ClassType.THEORY, slots.single { it.start.toString() == "08:00" }.type)
        assertEquals(ClassType.LAB, slots.single { it.start.toString() == "14:00" }.type)
    }
}
