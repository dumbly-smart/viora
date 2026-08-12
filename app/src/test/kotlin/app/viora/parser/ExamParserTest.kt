package app.viora.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ExamParserTest {
    @Test fun `parses exam schedule details`() {
        val html = """
            <div id="MenuBlock"></div><table class="customTable">
              <thead><tr><th>Exam Type</th><th>Course Code</th><th>Course Title</th><th>Exam Date</th>
              <th>Exam Time</th><th>Venue</th><th>Seat No.</th></tr></thead>
              <tbody><tr><td>CAT 1</td><td>CSE1001</td><td>Example Course</td><td>12-Aug-2026</td>
              <td>09:30 AM</td><td>PRP101</td><td>42</td></tr></tbody>
            </table>
        """.trimIndent()
        val result = ExamParser().parse(html) as ParseResult.Success
        val exam = result.value.single()
        assertEquals(LocalDateTime.of(2026, 8, 12, 9, 30), exam.startsAt)
        assertEquals("42", exam.seatNumber)
    }

    @Test fun `parses every grouped exam schedule when headings use td cells`() {
        val html = """
            <div id="MenuBlock"></div><table class="customTable">
              <tr><td>S.No.</td><td>Course Code</td><td>Course Title</td><td>Course Type</td><td>Course Id</td>
              <td>Slot</td><td>Exam Date</td><td>Session</td><td>Reporting Time</td><td>Exam Time</td>
              <td>Venue</td><td>Seat Location</td><td>Seat No.</td></tr>
              <tr><td colspan="13">CAT 1</td></tr>
              <tr><td>1</td><td>CSE1001</td><td>First Course</td><td>TH</td><td>1</td><td>A1</td>
              <td>12-Aug-2026</td><td>FN</td><td>09:00 AM</td><td>09:30 AM - 11:00 AM</td><td>PRP101</td><td>R1C1</td><td>42</td></tr>
              <tr><td colspan="13">CAT 2</td></tr>
              <tr><td>2</td><td>CSE1002</td><td>Second Course</td><td>TH</td><td>2</td><td>B1</td>
              <td>13-Aug-2026</td><td>AN</td><td>01:30 PM</td><td>02:00 PM - 03:30 PM</td><td>SJT202</td><td>R2C2</td><td>7</td></tr>
              <tr><td colspan="13">CAT 3</td></tr>
              <tr><td>3</td><td>CSE1003</td><td>Third Course</td><td>TH</td><td>3</td><td>C1</td>
              <td>14-Aug-2026</td><td>FN</td><td>09:00 AM</td><td>09:30 AM - 11:00 AM</td><td>TT301</td><td>R3C3</td><td>9</td></tr>
            </table>
        """.trimIndent()

        val result = ExamParser().parse(html) as ParseResult.Success

        assertEquals(listOf("CAT 1", "CAT 2", "CAT 3"), result.value.map { it.examType })
        assertEquals(LocalDateTime.of(2026, 8, 14, 9, 30), result.value.last().startsAt)
        assertEquals(LocalDateTime.of(2026, 8, 14, 11, 0), result.value.last().endsAt)
        assertEquals("TT301", result.value.last().venue)
        assertEquals("9", result.value.last().seatNumber)
    }
}
