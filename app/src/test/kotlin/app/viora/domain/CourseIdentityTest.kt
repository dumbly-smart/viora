package app.viora.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseIdentityTest {
    @Test fun `matches VTOP course code formatting variants`() {
        assertTrue(sameCourseCode("CSE1001", "CSE 1001 (Theory)"))
        assertTrue(sameCourseCode("BCSE203E", "BCSE203E - ETH"))
        assertTrue(sameCourseCode("MAT-1001", "MAT1001"))
    }

    @Test fun `does not match different or missing courses`() {
        assertFalse(sameCourseCode("CSE1001", "CSE1002"))
        assertFalse(sameCourseCode("", "CSE1001"))
    }
}
