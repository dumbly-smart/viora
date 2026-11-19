package app.viora.agent

import org.junit.Assert.*
import org.junit.Test

class AgentMaterialsTest {
    @Test fun `CAT II announced modules match their files without matching CAT 1`() {
        val modules = assessmentModules("CAT II portion: Modules 3, 5–6", "CAT-2")
        assertEquals(setOf(3, 5, 6), modules)
        assertTrue(matchesAssessment("Module 5 · Systems", "", "CAT-2", modules))
        assertTrue(matchesAssessment("Review", "CAT-2 practice.pdf", "CAT-2", emptySet()))
        assertFalse(matchesAssessment("Module 4", "CAT-1 practice.pdf", "CAT-2", modules))
        assertFalse(matchesAssessment("Module 50", "", "CAT-2", modules))
        assertTrue(assessmentModules("CAT-1 portion: Module 8", "CAT-2").isEmpty())
    }

    @Test fun `teacher matching uses whole name tokens`() {
        assertTrue(facultyMatches("Dr. Mira Patel", "Mira Patel"))
        assertFalse(facultyMatches("Miranda Patel", "Mira Patel"))
        assertFalse(facultyMatches("", "Mira Patel"))
        assertFalse(facultyMatches("Dr. Mira Patel", "Dr."))
    }
}
