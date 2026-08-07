package app.viora.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VtopVariantFixturesTest {
    @Test fun `attendance accepts subject-only campus layout`() {
        val result = AttendanceParser().parse(fixture("attendance_chennai_variant.html")) as ParseResult.Success
        assertEquals(16, result.value.records.single().held)
        assertEquals("Lab Only", result.value.records.single().courseType)
    }
    @Test fun `exam accepts subject and session aliases`() {
        val result = ExamParser().parse(fixture("exams_session_variant.html")) as ParseResult.Success
        assertEquals("RED1001", result.value.single().courseCode)
        assertEquals("AB-101", result.value.single().venue)
    }
    @Test fun `grades accept common history layout`() {
        val result = GradesParser().parse(fixture("grades_variant.html")) as ParseResult.Success
        assertEquals(8.75, result.value.gpa!!, 0.001)
        assertEquals("A", result.value.records.single().grade)
    }
    @Test fun `materials preserve VTOP download action`() {
        val result = CourseMaterialParser().parse(fixture("materials_variant.html"), "RED1001") as ParseResult.Success
        assertTrue(result.value.single().downloadPath.contains("downloadCourseMaterial"))
    }
    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/fixtures/$name")).readText()
}
