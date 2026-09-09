package app.viora

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.viora.database.SlotWithCourse
import app.viora.ui.VioraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import java.time.LocalDateTime
import java.time.ZoneId

class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeShowsReferenceHeroAndChronologicalAcademicRows() {
        val now = LocalDateTime.of(2026, 8, 12, 8, 0)
            .atZone(ZoneId.of("Asia/Kolkata"))
            .toInstant()
            .toEpochMilli()
        val state = VioraUiState(
            slots = listOf(
                SlotWithCourse("slot", "course", "CSE1001", "Synthetic Course", "Faculty", 3, 10 * 60, 11 * 60, "SJT 101", "Theory"),
            ),
            assignments = listOf(
                AssignmentUi("assignment", "CSE1002", "Synthetic assignment", now + 60 * 60_000, "Pending", courseTitle = "Course Two"),
            ),
        )

        compose.setContent {
            VioraTheme {
                HomeScreen(state = state, refresh = {}, nowEpochMillis = now)
            }
        }

        compose.onNodeWithText("Upcoming").assertIsDisplayed()
        compose.onNodeWithText("In the next 2 weeks").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sync Viora").assertIsDisplayed()
        compose.onNodeWithText("Synthetic assignment").assertIsDisplayed()
        compose.onNodeWithContentDescription("Synthetic Course, 10:00 AM, August 12", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Upcoming class").assertDoesNotExist()
    }

    @Test
    fun homeKeepsReauthenticationReachableFromTheReferenceHeader() {
        var requested = false

        compose.setContent {
            VioraTheme {
                HomeScreen(
                    state = VioraUiState(reauthRequired = true),
                    refresh = {},
                    reauthenticate = { requested = true },
                    nowEpochMillis = 1_786_498_200_000,
                )
            }
        }

        compose.onNodeWithText("Sign in").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sign in to VTOP").performClick()
        compose.runOnIdle { assertTrue(requested) }
    }

    @Test
    fun homeRowsOpenTheirAcademicDetail() {
        val now = 1_786_498_200_000
        var opened: DetailSelection? = null
        val assignment = AssignmentUi("assignment", "CSE1002", "Open me", now + 60 * 60_000, "Pending")

        compose.setContent {
            VioraTheme {
                HomeScreen(
                    state = VioraUiState(assignments = listOf(assignment)),
                    refresh = {},
                    openDetail = { opened = it },
                    nowEpochMillis = now,
                )
            }
        }

        compose.onNodeWithText("Open me").performClick()
        compose.runOnIdle { assertTrue(opened == DetailSelection("assignment", "assignment")) }
    }

    @Test
    fun floatingNavigationAnnouncesTheSelectedDestination() {
        compose.setContent {
            VioraTheme {
                VioraFloatingNavigationBar(selected = 0, lightBackground = true, onSelect = {})
            }
        }

        compose.onNodeWithContentDescription("Home").assertIsSelected()
        compose.onNodeWithContentDescription("Courses").assertIsNotSelected()
    }
}
