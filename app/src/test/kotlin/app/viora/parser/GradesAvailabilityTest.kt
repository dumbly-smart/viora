package app.viora.parser

import org.junit.Assert.assertTrue
import org.junit.Test

class GradesAvailabilityTest {
    @Test fun `explicit unpublished grades are a valid empty result`() {
        val result = GradesParser().parse("<div class='alert'>No Data Found for the selected semester</div>")
        assertTrue(result is ParseResult.Success && result.value.records.isEmpty())
    }
    @Test fun `grade history accepts headers inside tbody`() {
        val result = GradesParser().parse("<table><tbody><tr><th>Course Code</th><th>Course Title</th><th>Grade</th></tr><tr><td>CSE1001</td><td>Synthetic Course</td><td>A</td></tr></tbody></table>")
        assertTrue(result is ParseResult.Success && result.value.records.single().grade == "A")
    }
    @Test fun `missing grade markup still fails validation`() {
        assertTrue(GradesParser().parse("<html><body>Unexpected response</body></html>") is ParseResult.InvalidDocument)
    }
}
