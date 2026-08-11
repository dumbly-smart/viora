package app.viora

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.viora.database.CourseMaterialEntity
import app.viora.ui.VioraTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class CourseDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun courseShowsAssignmentsDueAndMaterialsAcrossCodeFormats() {
        val course = AttendanceUi("attendance", "CSE1001", "Synthetic Course", "Theory", "Faculty", 8, 10, 10, 80.0, 2, 0, 1, 2, 0)
        val due = LocalDateTime.of(2026, 8, 20, 23, 59).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        val state = VioraUiState(
            attendance = listOf(course),
            assignments = listOf(AssignmentUi("assignment", "CSE 1001 (Theory)", "Synthetic DA", due, "Open")),
            materials = listOf(CourseMaterialEntity("semester", "material", "CSE1001 - ETH", "Synthetic notes", "notes.pdf", "/download/notes", null, 0)),
        )

        compose.setContent {
            VioraTheme {
                DetailScreen(state, DetailSelection("course", course.id)) { _, _ -> }
            }
        }

        compose.onNodeWithText("Digital assignments").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Synthetic DA").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Due Thu, 20 Aug", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("11:59", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Course materials").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Synthetic notes").performScrollTo().assertIsDisplayed()
    }
}
