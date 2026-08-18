package app.viora.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class DigitalAssignmentParserTest {
    @Test fun `parses VTOP subject list then assignment detail rows`() {
        val parser = DigitalAssignmentParser()
        val subjectsHtml = """
            <table><tbody><tr class="tableContent"><td>1</td><td>CLASS-1</td><td>CSE3001</td><td>Operating Systems</td><td>Theory</td></tr></tbody></table>
        """.trimIndent()
        val subject = (parser.parseSubjects(subjectsHtml) as ParseResult.Success).value.single()
        val detailsHtml = """
            <table class="customTable">
              <tr class="tableHeader"><td>Sl.No.</td><td>Title</td><td>Description</td><td>Start Date</td><td>Due Date</td><td>QP</td><td>Last Updated</td><td>Upload</td><td>Status</td></tr>
              <tr class="tableContent"><td>1</td><td>DA One</td><td>Processes</td><td>01-Aug-2026</td><td>20-Aug-2026</td><td>No</td><td>File Not Uploaded</td><td><input name="code" value="EDIT-1" /></td><td>Pending</td></tr>
            </table>
        """.trimIndent()

        val assignment = (parser.parseDetails(detailsHtml, subject) as ParseResult.Success).value.single()

        assertEquals("CSE3001", assignment.courseCode)
        assertEquals("Operating Systems", assignment.courseTitle)
        assertEquals("DA One", assignment.title)
        assertEquals(LocalDateTime.of(2026, 8, 20, 23, 59), assignment.dueAt)
        assertEquals("File Not Uploaded", assignment.lastUpload)
    }

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
