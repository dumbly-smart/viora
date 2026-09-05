package app.viora

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import app.viora.database.CourseMaterialEntity
import app.viora.ui.VioraTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import app.viora.domain.AttendanceMilestone

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
                DetailScreen(
                    state = state,
                    selection = DetailSelection("course", course.id),
                    openMaterial = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText("Digital assignments").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Synthetic DA").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Due Thu, 20 Aug", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("11:59", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Course materials").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Synthetic notes").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun academicsTabsShowCoursesMarksAndAttendance() {
        val attendance = AttendanceUi(
            "attendance",
            "CSE1001",
            "Synthetic Course",
            "Theory",
            "Faculty",
            8,
            10,
            10,
            80.0,
            2,
            0,
            1,
            2,
            0,
        )
        val state = VioraUiState(
            attendance = listOf(attendance),
            attendanceTarget = 80,
            marks = listOf(
                MarkUi("theory", "CSE1001", "Synthetic Course", "Theory", "CAT 1", 20.0, 10.0, "Published", 18.0, 9.0),
                MarkUi("fat", "CSE1001", "Synthetic Course", "Theory", "FAT", 100.0, 40.0, "Published", 81.0, 32.4),
                MarkUi("quiz", "CSE1001", "Synthetic Course", "Theory", "Quiz", 10.0, null, "Published", 9.0, null),
                MarkUi("da", "CSE1001", "Synthetic Course", "Theory", "Digital Assessment", 10.0, null, "Published", 8.0, null),
                MarkUi("lab", "CSE 1001 (Lab)", "Synthetic Course", "Lab", "Lab Exercise", 20.0, null, "Pending", null, null),
            ),
        )
        compose.setContent {
            VioraTheme {
                AcademicsScreen(state) { _, _ -> }
            }
        }

        compose.onNodeWithText("Courses").performClick()
        compose.onNodeWithText("Marks").performClick()
        listOf("CAT 1", "FAT", "Quiz", "Digital Assessment", "Lab Exercise").forEach { title ->
            compose.onNodeWithText(title).assertExists()
        }
        compose.onAllNodesWithText("VTOP type: Theory").assertCountEquals(4)
        compose.onNodeWithText("VTOP type: Lab").assertExists()
        compose.onNodeWithText("Raw score: 18 / 20").assertExists()
        compose.onNodeWithText("Maximum score: 20").assertExists()
        compose.onAllNodesWithText("Weightage unavailable").assertCountEquals(3)
        compose.onNodeWithText("Publication: Pending").assertExists()
        compose.onNodeWithText("Attendance").performClick()
        compose.onNodeWithText("Skip allowance").assertExists()
        compose.onNodeWithText("active 80%", substring = true).assertExists()
        compose.onAllNodesWithText("Not scheduled").assertCountEquals(3)
    }

    @Test
    fun ninePointCgpaOpensAttendanceWithoutThresholdWarnings() {
        val attendance = AttendanceUi(
            "attendance", "CSE1001", "Synthetic Course", "Theory", "Faculty",
            7, 10, 10, 70.0, 0, 1, 1, 0, 1,
        )
        val state = VioraUiState(attendance = listOf(attendance), cgpa = 9.0)

        compose.setContent {
            VioraTheme {
                AcademicsScreen(state, initialTab = 2) { _, _ -> }
            }
        }

        compose.onNodeWithText("Attendance overview").assertIsDisplayed()
        compose.onNodeWithText("7/10 classes").assertIsDisplayed()
        compose.onNodeWithText("The 9-point attendance rule applies based on the cached VTOP CGPA. Raw attendance remains visible.").assertIsDisplayed()
        compose.onNodeWithText("Attend next", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Safe to skip", substring = true).assertDoesNotExist()
    }

    @Test
    fun assessmentsShowWeeklyStatusesAndCourseDrillDown() {
        val now = System.currentTimeMillis()
        val state = VioraUiState(assignments = listOf(
            AssignmentUi("pending", "CSE1001", "Pending DA", now + 86_400_000, "Pending", courseTitle = "Synthetic Course"),
            AssignmentUi("submitted", "CSE1001", "Submitted DA", now + 2 * 86_400_000, "Open", "answer.pdf", "Synthetic Course"),
        ))
        var openedCourse = ""
        val selection = mutableStateOf<DetailSelection?>(null)
        compose.setContent {
            VioraTheme {
                val detail = selection.value
                if (detail == null) {
                    AssessmentsScreen(
                        state,
                        uploadAssignment = {},
                        showAssignment = {},
                        showCourse = {
                            openedCourse = it
                            selection.value = DetailSelection("assessments-course", it)
                        },
                    )
                } else {
                    DetailScreen(state, detail, openMaterial = { _, _ -> })
                }
            }
        }

        compose.onNodeWithText("Due this week").assertExists()
        compose.onNodeWithText("Pending").assertExists()
        compose.onNodeWithText("Submitted").assertExists()
        compose.onNodeWithText("2 assessments", substring = true).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("CSE1001", openedCourse) }
        compose.onNodeWithText("Pending DA").assertExists()
        compose.onNodeWithText("Submitted DA").assertExists()
        compose.onNodeWithText("Submit file").assertExists()
        compose.onNodeWithText("Replace submission").assertExists()
    }

    @Test
    fun assessmentDetailOffersSubmitOrReplacementOnlyBeforeDeadline() {
        val now = System.currentTimeMillis()
        var uploaded = ""
        val assignment = mutableStateOf(AssignmentUi("pending", "CSE1001", "Pending DA", now + 86_400_000, "Pending"))
        compose.setContent {
            VioraTheme {
                DetailScreen(
                    VioraUiState(assignments = listOf(assignment.value)),
                    DetailSelection("assignment", assignment.value.id),
                    openMaterial = { _, _ -> },
                    uploadAssignment = { uploaded = it.id },
                )
            }
        }

        compose.onNodeWithText("Submit file").performClick()
        compose.runOnIdle { assertEquals("pending", uploaded) }

        compose.runOnIdle {
            assignment.value = AssignmentUi("submitted", "CSE1001", "Submitted DA", now + 86_400_000, "Submitted")
        }
        compose.onNodeWithText("Replace submission").assertExists()

        compose.runOnIdle {
            assignment.value = AssignmentUi("closed", "CSE1001", "Closed DA", now - 1, "Pending")
        }
        compose.onNodeWithText("Submit file").assertDoesNotExist()
        compose.onNodeWithText("Replace submission").assertDoesNotExist()
    }

    @Test
    fun estimatedAttendanceWindowIsLabelled() {
        val attendance = AttendanceUi("attendance", "CSE1001", "Synthetic Course", "Theory", "Faculty", 14, 20, 20, 70.0, 0, 1, 1, 0, 1)
        val projection = CourseAttendanceMilestoneUi(
            attendance = attendance,
            milestone = AttendanceMilestone.FAT,
            state = MilestoneState.SCHEDULED,
            occurrenceCount = 9,
            skippableOccurrences = 1,
            estimatedWindow = true,
        )

        compose.setContent { VioraTheme { AttendanceMilestoneRow(AttendanceMilestone.FAT, projection) } }

        compose.onNodeWithText("Estimated from timetable and exam dates").assertExists()
    }
}
