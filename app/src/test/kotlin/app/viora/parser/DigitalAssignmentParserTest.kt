package app.viora.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class DigitalAssignmentParserTest {
    @Test fun `parses assignment deadline and upload state`() {
        val html = """
            <div id="MenuBlock"></div><table class="customTable">
              <thead><tr><th>Course Code</th><th>Title</th><th>Due Date</th><th>Last Upload</th><th>Status</th></tr></thead>
              <tbody><tr><td>CSE1001</td><td>DA 1</td><td>09-Aug-2026 11:59 PM</td><td>Not uploaded</td><td>Open</td></tr></tbody>
            </table>
        """.trimIndent()
        val result = DigitalAssignmentParser().parse(html) as ParseResult.Success
        val assignment = result.value.single()
        assertEquals(LocalDateTime.of(2026, 8, 9, 23, 59), assignment.dueAt)
        assertEquals("Not uploaded", assignment.lastUpload)
    }

    @Test fun `keeps assignment when VTOP omits due time`() {
        val html = """
            <table class="customTable"><thead><tr><th>Course Code</th><th>Title</th><th>Due Date</th></tr></thead>
            <tbody><tr><td>MAT1001</td><td>Practice</td><td>--</td></tr></tbody></table>
        """.trimIndent()
        val result = DigitalAssignmentParser().parse(html) as ParseResult.Success
        assertNull(result.value.single().dueAt)
    }
}
