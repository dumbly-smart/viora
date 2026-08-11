package app.viora.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemesterParserTest {
    private val parser = SemesterParser()

    @Test fun `parses and filters semester options`() {
        val html = """
            <html><body><select name="semesterSubId">
              <option value="0">Select Semester</option>
              <option value="2026-ODD">Fall Semester 2026-27</option>
              <option value="2026-EVEN">Winter Semester 2026-27</option>
            </select><div id="MenuBlock"></div></body></html>
        """.trimIndent()

        val result = parser.parse(html) as ParseResult.Success

        assertEquals(listOf("2026-ODD", "2026-EVEN"), result.value.map { it.id })
    }

    @Test fun `rejects login page instead of returning empty semesters`() {
        val result = parser.parse("<html><body><input type='password'></body></html>")
        assertTrue(result is ParseResult.AuthenticationRequired)
    }

    @Test fun `accepts renamed semester select variants`() {
        val html = """
            <select id="semesterSubIdForStudent">
              <option value="0">Select Semester</option>
              <option value="CH2026S1">Winter Semester 2025-26</option>
            </select>
        """.trimIndent()

        val result = parser.parse(html) as ParseResult.Success

        assertEquals(listOf("CH2026S1"), result.value.map { it.id })
    }
}
