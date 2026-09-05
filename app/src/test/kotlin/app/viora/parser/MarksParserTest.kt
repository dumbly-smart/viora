package app.viora.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarksParserTest {
    @Test fun `parses every component across supported VTOP table layouts`() {
        val html = checkNotNull(javaClass.getResource("/fixtures/marks_variants.html")).readText()

        val records = (MarksParser().parse(html) as ParseResult.Success).value

        assertEquals(listOf("CAT 1", "FAT", "Quiz", "Digital Assessment", "Lab Exercise 1"), records.map { it.title })
        assertEquals(listOf(42.0, 81.0, 9.0, 8.0, 18.0), records.map { it.scoredMark })
        assertEquals("CSE1001", records[0].courseCode)
        assertEquals("Theory", records[0].courseType)
        assertEquals("CSE2001", records[4].courseCode)
        assertEquals("Synthetic Programming Lab", records[4].courseTitle)
        assertEquals("Lab", records[4].courseType)
        assertNull(records[2].weightagePercent)
        assertNull(records[4].weightageMark)
        assertEquals("Published", records[4].status)
    }
}
