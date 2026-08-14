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
    @Test fun `materials accept lowercase button download actions`() {
        val html = """
            <table><tbody><tr><td>Week 1 notes</td><td><button onclick="downloadcoursematerial('42')">Get</button></td></tr></tbody></table>
        """.trimIndent()
        val result = CourseMaterialParser().parse(html, "CSE1001") as ParseResult.Success
        assertEquals("Week 1 notes", result.value.single().title)
        assertTrue(result.value.single().downloadPath.contains("downloadcoursematerial"))
    }
    @Test fun `materials parse consolidated module topic date and file id`() {
        val html = """
            <table id="materialTable"><tbody><tr>
              <td>1</td><td>ignored</td>
              <td><div class="mt-1"><span style="color:#2E86C1">Network notes</span><span style="color:#28B463">3</span></div></td>
              <td><div class="mt-1"><span>1001 - Faculty - SCOPE</span><span>14-Aug-2026</span></div></td>
              <td><button name="downloadmat" data-fileid="file-42">Download</button></td>
            </tr></tbody></table>
        """.trimIndent()
        val material = (CourseMaterialParser().parse(html, "CSE1001") as ParseResult.Success).value.single()
        assertEquals("Module 3 · Network notes", material.title)
        assertEquals("fileId:file-42", material.downloadPath)
        assertEquals(14, material.postedAt?.dayOfMonth)
    }
    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/fixtures/$name")).readText()
}
