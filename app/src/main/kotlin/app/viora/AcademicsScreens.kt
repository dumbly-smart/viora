package app.viora

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.viora.domain.AttendanceMilestone

@Composable
internal fun AcademicsScreen(
    state: VioraUiState,
    showCourseDetail: (String, String) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Courses", "Marks", "Attendance")
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEachIndexed { index, label ->
                FilterChip(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    label = { Text(label) },
                )
            }
        }
        when (selectedTab) {
            0 -> CoursesScreen(state, showCourseDetail)
            1 -> MarksScreen(state)
            else -> AttendanceScreen(state)
        }
    }
}

@Composable
private fun MarksScreen(state: VioraUiState) {
    val sections = state.marks.markSections()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Assessment marks",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text("Cached VTOP assessment marks by course.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(sections, key = MarkSectionUi::courseCode) { section ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(section.courseCode, style = MaterialTheme.typography.titleLarge)
                    if (section.courseTitle.isNotBlank() && section.courseTitle != section.courseCode) {
                        Text(section.courseTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    section.marks.forEach { mark ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(mark.title, style = MaterialTheme.typography.titleMedium)
                            Text("Raw score: ${mark.scoredMark.displayMark()} / ${mark.maxMarks.displayMark()}")
                            Text("Weighted score: ${mark.weightageMark.displayMark()}")
                            Text("Percentage weight: ${mark.weightagePercent.displayMark()}%")
                            Text(mark.status.ifBlank { "—" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (sections.isEmpty()) item { Text("No assessment marks have been cached yet.") }
    }
}

@Composable
private fun AttendanceScreen(state: VioraUiState) {
    val milestones = state.attendanceMilestones(System.currentTimeMillis()).groupBy { it.attendance.id }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Skip allowance",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Skip allowance is calculated against the active ${state.attendanceTarget}% attendance target.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(state.attendance, key = AttendanceUi::id) { attendance ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AttendanceCard(attendance)
                    val milestonesForCourse = milestones[attendance.id].orEmpty().associateBy { it.milestone }
                    AttendanceMilestone.entries.forEach { milestone ->
                        AttendanceMilestoneRow(milestone, milestonesForCourse[milestone])
                    }
                }
            }
        }
        if (state.attendance.isEmpty()) item { Text("No attendance has been cached yet.") }
    }
}

@Composable
private fun AttendanceMilestoneRow(
    milestone: AttendanceMilestone,
    projection: CourseAttendanceMilestoneUi?,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(milestone.label, style = MaterialTheme.typography.titleSmall)
        Text(projection?.stateCopy ?: "Not scheduled", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val AttendanceMilestone.label: String
    get() = when (this) {
        AttendanceMilestone.CAT_1 -> "CAT 1"
        AttendanceMilestone.CAT_2 -> "CAT 2"
        AttendanceMilestone.FAT -> "FAT"
    }

private val CourseAttendanceMilestoneUi.stateCopy: String
    get() = when (state) {
        MilestoneState.SCHEDULED -> "Safe to skip $skippableOccurrences classes"
        MilestoneState.PASSED -> "Passed"
        MilestoneState.NOT_SCHEDULED -> "Not scheduled"
        MilestoneState.NO_CLASSES -> "No matching classes before this exam"
    }

private fun Double?.displayMark(): String = this?.let { value ->
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value)
} ?: "—"
