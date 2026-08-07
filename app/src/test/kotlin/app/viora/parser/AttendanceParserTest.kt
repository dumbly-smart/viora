package app.viora.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceParserTest {
    private val parser = AttendanceParser()

    @Test fun `parses redacted attendance and ignores displayed percentage`() {
        val html = """
            <html><body><div id="MenuBlock"></div>
            <table id="AttendanceDetailDataTable">
              <thead><tr>
                <th>Course Code</th><th>Course Title</th><th>Classes Attended</th>
                <th>Total Classes</th><th>Attendance Percentage</th>
              </tr></thead>
              <tbody><tr>
                <td>CSE1001</td><td>Example Course</td><td>18</td><td>20</td><td>90.00</td>
              </tr></tbody>
            </table></body></html>
        """.trimIndent()

        val result = parser.parse(html) as ParseResult.Success

        assertEquals(18, result.value.records.single().attended)
        assertEquals(20, result.value.records.single().held)
    }

    @Test fun `rejects impossible values without replacing cache`() {
        val html = """
            <table id="AttendanceDetailDataTable">
              <thead><tr><th>Course Code</th><th>Attended</th><th>Held</th></tr></thead>
              <tbody><tr><td>CSE1001</td><td>11</td><td>10</td></tr></tbody>
            </table>
        """.trimIndent()
        assertTrue(parser.parse(html) is ParseResult.InvalidDocument)
    }

    @Test fun `detects session expiry`() {
        assertTrue(
            parser.parse("<html><body>Session Timed Out<input type='password'></body></html>")
                is ParseResult.AuthenticationRequired,
        )
    }
}
