package app.viora

import org.junit.Assert.assertEquals
import org.junit.Test

class AcademicProjectionsTest {
    @Test
    fun `groups marks by course and orders known assessment types`() {
        val sections = listOf(mark("FAT"), mark("Quiz 1"), mark("CAT 1"))
            .markSections()

        assertEquals(listOf("CAT 1", "Quiz 1", "FAT"), sections.single().marks.map(MarkUi::title))
    }

    @Test
    fun `orders unknown assessment titles after FAT alphabetically`() {
        val sections = listOf(mark("Project"), mark("FAT"), mark("Class test"), mark("Assignment 1"))
            .markSections()

        assertEquals(
            listOf("Assignment 1", "FAT", "Class test", "Project"),
            sections.single().marks.map(MarkUi::title),
        )
    }

    @Test
    fun `groups normalized course codes and falls back to title when code is blank`() {
        val sections = listOf(
            mark("CAT 1", courseCode = " cse101 ", courseTitle = "Algorithms"),
            mark("CAT 2", courseCode = "CSE101", courseTitle = "Algorithms"),
            mark("FAT", courseCode = "", courseTitle = "Operating Systems"),
        ).markSections()

        assertEquals(listOf("CSE101", "Operating Systems"), sections.map { it.courseCode })
        assertEquals(2, sections.first().marks.size)
    }

    @Test
    fun `preserves unavailable mark values`() {
        val result = listOf(mark("Quiz 1", scoredMark = null, maxMarks = null, weightageMark = null))
            .markSections()
            .single()
            .marks
            .single()

        assertEquals(null, result.scoredMark)
        assertEquals(null, result.maxMarks)
        assertEquals(null, result.weightageMark)
    }

    private fun mark(
        title: String,
        courseCode: String = "CSE101",
        courseTitle: String = "Algorithms",
        scoredMark: Double? = 8.0,
        maxMarks: Double? = 10.0,
        weightageMark: Double? = 8.0,
    ) = MarkUi(
        id = title,
        courseCode = courseCode,
        courseTitle = courseTitle,
        courseType = "Theory",
        title = title,
        maxMarks = maxMarks,
        weightagePercent = 10.0,
        status = "",
        scoredMark = scoredMark,
        weightageMark = weightageMark,
    )
}
