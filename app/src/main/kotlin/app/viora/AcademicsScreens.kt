package app.viora

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.viora.domain.AttendanceMilestone
import app.viora.notifications.AttendanceNotificationPolicy

@Composable
internal fun AcademicsScreen(
    state: VioraUiState,
    initialTab: Int = 0,
    showCourseDetail: (String, String) -> Unit,
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    val tabs = listOf("Courses", "Marks", "Attendance")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { contentDescription = "Academic record sections" },
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
    val sections = remember(state.marks) { state.marks.markSections() }
    LazyColumn(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Marks",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        items(sections, key = MarkSectionUi::courseCode) { section ->
            AcademicCourseSurface {
                AcademicCourseHeading(section.courseCode, section.courseTitle)
                section.marks.forEach { mark ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MarkDetails(mark)
                }
            }
        }
        if (sections.isEmpty()) item { Text("No marks have been cached yet.") }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("CGPA", style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
                    Text(
                        state.cgpa?.displayMark() ?: "CGPA unavailable",
                        style = if (state.cgpa == null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.displaySmall,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AcademicCourseSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
internal fun AcademicCourseHeading(code: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (code.isNotBlank() && title.isNotBlank() && title != code) {
            Text(code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            title.ifBlank { code },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
internal fun MarkDetails(mark: MarkUi) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val rawScore = when {
        mark.scoredMark != null && mark.maxMarks != null -> "Raw score: ${mark.scoredMark.displayMark()} / ${mark.maxMarks.displayMark()}"
        mark.scoredMark != null -> "Raw score: ${mark.scoredMark.displayMark()}"
        mark.maxMarks != null -> "Maximum score: ${mark.maxMarks.displayMark()}"
        else -> "Raw score unavailable"
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(mark.title, style = MaterialTheme.typography.titleMedium)
        if (mark.courseType.isNotBlank()) {
            Text("VTOP type: ${mark.courseType}", style = MaterialTheme.typography.labelMedium, color = muted)
        }
        Text(
            rawScore,
            style = MaterialTheme.typography.titleLarge,
            color = if (mark.scoredMark != null) MaterialTheme.colorScheme.primary else muted,
        )
        if (mark.weightageMark != null) Text("Weighted score: ${mark.weightageMark.displayMark()}", style = MaterialTheme.typography.bodyMedium)
        if (mark.weightagePercent != null) Text("Percentage weight: ${mark.weightagePercent.displayMark()}%", style = MaterialTheme.typography.bodyMedium)
        if (mark.weightageMark == null && mark.weightagePercent == null) {
            Text("Weightage unavailable", style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        Text(
            mark.status.takeIf(String::isNotBlank)?.let { "Publication: $it" } ?: "Publication status unavailable",
            style = MaterialTheme.typography.labelMedium,
            color = muted,
        )
    }
}

@Composable
private fun AttendanceScreen(state: VioraUiState) {
    val ninePointRule = AttendanceNotificationPolicy.hasNinePointRule(state.cgpa)
    val milestones = if (ninePointRule) {
        emptyMap()
    } else {
        val now = produceState(System.currentTimeMillis()) {
            while (true) {
                kotlinx.coroutines.delay(60_000L - value % 60_000L)
                value = System.currentTimeMillis()
            }
        }.value
        rememberBackgroundProjection(state.attendance, state.slots, state.exams, state.calendar, state.attendanceTarget, now) {
            state.attendanceMilestones(now).groupBy { it.attendance.id }
        }
    }
    val showCalculationSource = !ninePointRule && state.attendance.isNotEmpty()
    LazyColumn(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (ninePointRule) "Attendance overview" else "Skip allowance",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    if (ninePointRule) "The 9-point attendance rule applies based on the cached VTOP CGPA. Raw attendance remains visible."
                    else "Based on your active ${state.attendanceTarget}% attendance target.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.attendance, key = AttendanceUi::id) { attendance ->
            AttendanceCard(attendance, ninePointRule) {
                if (!ninePointRule) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Exam milestones", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    val milestonesForCourse = milestones?.get(attendance.id).orEmpty().associateBy { it.milestone }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (milestones == null) Text("Calculating exam milestones…")
                    else AttendanceMilestone.entries.forEach { milestone ->
                            AttendanceMilestoneRow(milestone, milestonesForCourse[milestone])
                        }
                    }
                }
            }
        }
        if (state.attendance.isEmpty()) item { Text("No attendance has been cached yet.") }
        if (showCalculationSource) {
            item {
                Text(
                    "Calculated from timetable and exam dates",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun AttendanceMilestoneRow(
    milestone: AttendanceMilestone,
    projection: CourseAttendanceMilestoneUi?,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(milestone.label, style = MaterialTheme.typography.titleSmall)
        Text(
            projection?.stateCopy ?: "Not scheduled",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
