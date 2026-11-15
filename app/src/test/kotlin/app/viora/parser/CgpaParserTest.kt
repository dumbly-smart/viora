package app.viora.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class CgpaParserTest {
    @Test fun `parses horizontal credit and cgpa summary as used by CLI Top`() {
        val html = """<table><thead><tr><th>Credits Registered</th><th>Credits Earned</th><th>CGPA</th><th>S Grades</th><th>A Grades</th></tr></thead>
            <tbody><tr><td>96</td><td>92</td><td>8.74</td><td>3</td><td>7</td></tr></tbody></table>"""
        val result = CgpaParser().parse(html) as ParseResult.Success
        assertEquals(96.0, result.value.registeredCredits)
        assertEquals(92.0, result.value.earnedCredits)
        assertEquals(8.74, result.value.cgpa)
        assertEquals(3, result.value.gradeCounts["S"])
    }

    @Test fun `parses credit totals when vtop uses adjacent table cells`() {
        val html = """
            <div id="MenuBlock">Student Profile</div>
            <table>
              <tr><td>Credits Registered</td><td>96</td></tr>
              <tr><td>Credits Earned</td><td>92</td></tr>
              <tr><td>CGPA</td><td>8.74</td></tr>
            </table>
        """.trimIndent()

        val result = CgpaParser().parse(html) as ParseResult.Success
        assertEquals(96.0, result.value.registeredCredits)
        assertEquals(92.0, result.value.earnedCredits)
        assertEquals(8.74, result.value.cgpa)
    }
}
