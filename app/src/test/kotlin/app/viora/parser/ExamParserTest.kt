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
}
