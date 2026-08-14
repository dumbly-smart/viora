package app.viora.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class CgpaParserTest {
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
