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

    @Test fun `preserves separate theory and lab rows from live VTOP shape`() {
        val html = """
            <table class="customTable"><thead><tr><th>Subject</th><th>Type</th><th>Faculty Name</th><th>Classes Attended</th><th>Percentage</th></tr></thead>
            <tbody><tr><td>Example</td><td>Embedded Theory</td><td>Faculty A</td><td>13/15</td><td>86.67</td></tr>
            <tr><td>Example</td><td>Embedded Lab</td><td>Faculty A</td><td>9/10</td><td>90</td></tr></tbody></table>
        """.trimIndent()
        val records = (parser.parse(html) as ParseResult.Success).value.records
        assertEquals(2, records.size)
        assertEquals(listOf(15, 10), records.map { it.held })
    }

    @Test fun `detects session expiry`() {
        assertTrue(
            parser.parse("<html><body>Session Timed Out<input type='password'></body></html>")
                is ParseResult.AuthenticationRequired,
        )
    }

    @Test fun `parses consolidated course faculty and class detail columns`() {
        val html = """
            <table id="getStudentDetails">
              <tr><th>Sl.No.</th><th>Class Group</th><th>Course Detail</th><th>Class Detail</th><th>Faculty Detail</th><th>Attended Classes</th><th>Total Classes</th></tr>
              <tr><td>1</td><td>General</td><td>BAHUM107 - Example Course</td><td>Theory Only</td><td>Faculty A</td><td>14</td><td>16</td></tr>
            </table>
        """.trimIndent()

        val record = (parser.parse(html) as ParseResult.Success).value.records.single()

        assertEquals("BAHUM107", record.courseCode)
        assertEquals("Example Course", record.courseTitle)
        assertEquals("Theory Only", record.courseType)
        assertEquals("Faculty A", record.faculty)
        assertEquals(14, record.attended)
        assertEquals(16, record.held)
    }

    @Test fun `parses current VTOP getStudentDetails layout without thead`() {
        val html = """
            <table id="getStudentDetails">
              <tr><th>Course Code</th><th>Course Title</th><th>Course Type</th><th>Slot</th><th>Attended Classes</th><th>Total Classes</th><th>Attendance Percentage</th></tr>
              <tr><td>BACSE202</td><td>Database Systems</td><td>Theory Only</td><td>A1</td><td>18</td><td>20</td><td>90</td></tr>
            </table>
        """.trimIndent()

        val result = parser.parse(html) as ParseResult.Success

        assertEquals("BACSE202", result.value.records.single().courseCode)
        assertEquals(18, result.value.records.single().attended)
        assertEquals(20, result.value.records.single().held)
    }

    @Test fun `current class detail does not replace embedded theory and lab types`() {
        val html = """
            <table id="getStudentDetails">
              <tr><th>Course Detail</th><th>Class Detail</th><th>Faculty Detail</th><th>Attended Classes</th><th>Total Classes</th></tr>
              <tr><td>BACSE202 - Database Systems - Embedded Theory</td><td>VL0001 - E1+TE1 - ROOM1</td><td>Faculty A</td><td>18</td><td>20</td></tr>
              <tr><td>BACSE202 - Database Systems - Embedded Lab</td><td>VL0002 - L33+L34 - ROOM2</td><td>Faculty A</td><td>10</td><td>10</td></tr>
            </table>
        """.trimIndent()

        val records = (parser.parse(html) as ParseResult.Success).value.records

        assertEquals(listOf("Embedded Theory", "Embedded Lab"), records.map { it.courseType })
        assertEquals(listOf(20, 10), records.map { it.held })
        assertEquals(listOf(18, 10), records.map { it.attended })
    }
}
