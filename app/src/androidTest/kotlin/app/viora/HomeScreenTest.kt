package app.viora

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.viora.database.SlotWithCourse
import app.viora.ui.VioraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun todayHeroPrioritizesNextClassOverEarlierDeadline() {
        val now = LocalDateTime.of(2026, 8, 12, 8, 0)
            .atZone(ZoneId.of("Asia/Kolkata"))
            .toInstant()
            .toEpochMilli()
        val state = VioraUiState(
            slots = listOf(
                SlotWithCourse("slot", "course", "CSE1001", "Synthetic Course", "Faculty", 3, 10 * 60, 11 * 60, "SJT 101", "Theory"),
            ),
            assignments = listOf(
                AssignmentUi("assignment", "CSE1002", "Earlier DA", now + 30 * 60_000, "Pending", courseTitle = "Course Two"),
            ),
        )
        var opened: DetailSelection? = null

        compose.setContent {
            VioraTheme {
                HomeScreen(state = state, refresh = {}, openDetail = { opened = it }, nowEpochMillis = now)
            }
        }

        compose.onNodeWithText("Next class").assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Next class: Synthetic Course at 10:00 AM in SJT 101. Open course details.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Open course").performClick()
        compose.runOnIdle { assertTrue(opened == DetailSelection("course", "CSE1001")) }
    }

    @Test
    fun todayTimelineOrdersAcademicDeadlinesChronologically() {
        val now = LocalDateTime.of(2026, 8, 12, 8, 0)
            .atZone(ZoneId.of("Asia/Kolkata"))
            .toInstant()
            .toEpochMilli()
        val state = VioraUiState(
            slots = listOf(
                SlotWithCourse("slot", "course", "CSE1004", "Hero Class", "Faculty", 4, 10 * 60, 11 * 60, "SJT 101", "Theory"),
            ),
            assignments = listOf(
                AssignmentUi("later", "CSE1002", "Later DA", now + 5 * 60 * 60_000, "Pending", courseTitle = "Course Two"),
                AssignmentUi("earlier", "CSE1001", "Earlier DA", now + 90 * 60_000, "Pending", courseTitle = "Course One"),
            ),
            exams = listOf(
                ExamUi("exam", "CSE1003", "Exam Course", "CAT", now + 60 * 60_000, null, "AB1", "A12"),
            ),
        )

        compose.setContent {
            VioraTheme {
                HomeScreen(state = state, refresh = {}, nowEpochMillis = now)
            }
        }

        compose.onNodeWithText("Academic timeline").assertIsDisplayed()
        compose.onNodeWithText("Exam Course").assertIsDisplayed()
        compose.onNodeWithText("Earlier DA").assertIsDisplayed()
        compose.onNodeWithText("Later DA").performScrollTo().assertIsDisplayed()
        assertEquals(
            listOf("Exam Course", "Earlier DA", "Later DA", "Hero Class"),
            state.homeTimeline(now).map(HomeTimelineItem::title),
        )
    }

    @Test
    fun todayOmitsNeedsAttentionWhenNoDeadlineOrAttendanceNeedsAction() {
        val now = LocalDateTime.of(2026, 8, 12, 8, 0)
            .atZone(ZoneId.of("Asia/Kolkata"))
            .toInstant()
            .toEpochMilli()
        val state = VioraUiState(
            assignments = listOf(
                AssignmentUi("submitted", "CSE1001", "Submitted DA", now + 2 * 60 * 60_000, "Submitted", courseTitle = "Course One"),
            ),
            attendance = listOf(
                AttendanceUi("attendance", "CSE1001", "Course One", "Theory", "Faculty", 18, 20, 20, 90.0, 5, 0, 1, 5, 0),
            ),
        )

        compose.setContent {
            VioraTheme {
                HomeScreen(state = state, refresh = {}, nowEpochMillis = now)
            }
        }

        compose.onNodeWithText("Academic timeline").assertIsDisplayed()
        compose.onNodeWithText("Needs attention").assertDoesNotExist()
    }

    @Test
    fun todayKeepsAttendanceRiskButHidesOverdueAssessmentsAtLargeFont() {
        val now = LocalDateTime.of(2026, 8, 12, 8, 0)
            .atZone(ZoneId.of("Asia/Kolkata"))
            .toInstant()
            .toEpochMilli()
        val state = VioraUiState(
            assignments = listOf(
                AssignmentUi("overdue", "CSE1002", "Lab record", now - 30 * 60_000, "Pending", courseTitle = "Course Two"),
            ),
            attendance = listOf(
                AttendanceUi("risk", "CSE1001", "Synthetic Risk", "Theory", "Faculty", 13, 18, 18, 72.2, 0, 2, 1, 0, 2),
            ),
        )

        compose.setContent {
            VioraTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                    Box(Modifier.width(360.dp)) {
                        HomeScreen(state = state, refresh = {}, nowEpochMillis = now)
                    }
                }
            }
        }

        compose.onNodeWithText("Needs attention").assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Attendance risk: Synthetic Risk is 72 percent. Attend next 2 classes to reach 75 percent.",
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lab record").assertDoesNotExist()
    }

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

        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithText("Next class").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sync Viora").assertIsDisplayed()
        compose.onNodeWithText("Synthetic assignment").assertIsDisplayed()
        compose.onNodeWithContentDescription("Synthetic Course, 10:00 AM, August 12", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Wednesday, August 12, today, events scheduled").assertIsDisplayed()
        compose.onNodeWithContentDescription("Upcoming class").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pending assignment").assertDoesNotExist()
    }

    @Test
    fun homeDateRailSelectsAnotherDay() {
        val today = LocalDate.of(2026, 8, 12)
        compose.setContent {
            VioraTheme {
                HomeScreen(state = VioraUiState(), refresh = {}, nowEpochMillis = today.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli())
            }
        }

        compose.onNodeWithContentDescription("Thursday, August 13, no events scheduled").performClick().assertIsSelected()
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
        val leadAssignment = AssignmentUi("lead", "CSE1001", "First task", now + 30 * 60_000, "Pending")
        val assignment = AssignmentUi("assignment", "CSE1002", "Open me", now + 60 * 60_000, "Pending")

        compose.setContent {
            VioraTheme {
                HomeScreen(
                    state = VioraUiState(assignments = listOf(leadAssignment, assignment)),
                    refresh = {},
                    openDetail = { opened = it },
                    nowEpochMillis = now,
                )
            }
        }

        compose.onNodeWithContentDescription("Open me, Due", substring = true).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(opened == DetailSelection("assignment", "assignment")) }
    }

    @Test
    fun dashboardDefaultsToExactlyThreeAcademicDestinations() {
        compose.setContent {
            VioraTheme {
                TestDashboard()
            }
        }

        compose.onAllNodes(isSelectable()).assertCountEquals(3)
        compose.onNodeWithContentDescription("Today").assertIsSelected()
        compose.onNodeWithContentDescription("Plan").assertIsNotSelected()
        compose.onNodeWithContentDescription("Library").assertIsNotSelected()
    }

    @Test
    fun dashboardOpensPlanAndLibraryContent() {
        compose.setContent {
            VioraTheme {
                TestDashboard()
            }
        }

        compose.onNodeWithContentDescription("Plan").performClick()
        compose.onNodeWithText("No classes").assertIsDisplayed()

        compose.onNodeWithContentDescription("Library").performClick()
        compose.onNodeWithText("Consolidated courses").assertIsDisplayed()
    }

    @Test
    fun dashboardKeepsSettingsBehindTheProfileSheet() {
        compose.setContent {
            VioraTheme {
                TestDashboard()
            }
        }

        compose.onNodeWithContentDescription("Open profile and settings").performClick()
        compose.onNodeWithText("More").assertIsDisplayed()
    }

    @Test
    fun attendanceDeepLinkReturnsToCoursesAfterExplicitLibrarySelection() {
        compose.setContent {
            VioraTheme {
                TestDashboard(initialDestination = "attendance")
            }
        }

        compose.onNodeWithText("Skip allowance").assertIsDisplayed()
        compose.onNodeWithContentDescription("Plan").performClick()
        compose.onNodeWithContentDescription("Library").performClick()

        compose.onNodeWithText("Consolidated courses").assertIsDisplayed()
        compose.onNodeWithText("Skip allowance").assertDoesNotExist()
    }
}

@Composable
private fun TestDashboard(initialDestination: String? = null) {
    Dashboard(
        state = VioraUiState(),
        refresh = {},
        selectSemester = {},
        reauthenticate = {},
        logout = {},
        setDeadlineNotifications = {},
        setExamNotifications = {},
        openMaterial = { _, _ -> },
        downloadMaterial = {},
        downloadMaterials = {},
        uploadAssignment = {},
        setSearchQuery = {},
        setQuietHours = {},
        setSyncHours = {},
        refreshDiagnostics = {},
        clearDownloads = {},
        clearAcademicCache = {},
        shareTimetableQr = {},
        markClass = { _, _ -> },
        exportToDeviceCalendar = {},
        exportIcs = {},
        importIcs = {},
        shareCalendarIcs = {},
        initialDestination = initialDestination,
    )
}
