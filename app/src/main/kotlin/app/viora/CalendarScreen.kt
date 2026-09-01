package app.viora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.viora.ui.VioraAmber
import app.viora.ui.VioraBlue
import app.viora.ui.VioraCoral
import app.viora.ui.VioraSuccess
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun CalendarScreen(
    state: VioraUiState,
    initialDate: LocalDate = LocalDate.now(academicZone),
) {
    var month by remember { mutableStateOf(YearMonth.from(initialDate)) }
    var selectedDate by remember { mutableStateOf(initialDate) }
    val markers = state.calendarMarkers(month)
    val selectedEvents = state.eventsForDate(selectedDate)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Academic calendar", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text("Cached classes, deadlines and academic events", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        month = month.minusMonths(1)
                        selectedDate = selectedDateFor(month, selectedDate.dayOfMonth)
                    },
                    modifier = Modifier.semantics { contentDescription = "Previous month" },
                ) { Text("‹") }
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        month = month.plusMonths(1)
                        selectedDate = selectedDateFor(month, selectedDate.dayOfMonth)
                    },
                    modifier = Modifier.semantics { contentDescription = "Next month" },
                ) { Text("›") }
            }
        }
        item {
            CalendarMonthGrid(
                month = month,
                markers = markers,
                selectedDate = selectedDate,
                onSelectDate = { selectedDate = it },
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Selected day", style = MaterialTheme.typography.titleLarge)
                Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM", Locale.ENGLISH)), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selectedEvents.isEmpty()) {
            item { CalendarEmptyState() }
        } else {
            items(selectedEvents, key = AcademicDayEvent::id) { event -> CalendarEventCard(event) }
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    month: YearMonth,
    markers: Map<LocalDate, Set<AcademicCalendarMarker>>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
) {
    val firstCell = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
    val cellCount = firstCell + month.lengthOfMonth()
    val rowCount = (cellCount + DAYS_IN_WEEK - 1) / DAYS_IN_WEEK
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        repeat(rowCount) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(DAYS_IN_WEEK) { column ->
                    val index = row * DAYS_IN_WEEK + column
                    val day = index - firstCell + 1
                    if (day !in 1..month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).height(CALENDAR_DAY_HEIGHT))
                    } else {
                        val date = month.atDay(day)
                        CalendarDayCell(
                            date = date,
                            markers = markers[date].orEmpty(),
                            selected = date == selectedDate,
                            onSelectDate = onSelectDate,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    markers: Set<AcademicCalendarMarker>,
    selected: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    val description = buildString {
        append(date.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale.ENGLISH)))
        if (markers.isNotEmpty()) append(", ").append(markers.sortedBy(AcademicCalendarMarker::ordinal).joinToString { it.displayName() })
    }
    Surface(
        onClick = { onSelectDate(date) },
        modifier = modifier
            .height(CALENDAR_DAY_HEIGHT)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            Modifier.padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                markers.sortedBy(AcademicCalendarMarker::ordinal).take(MAX_MARKERS_PER_DAY).forEach { marker ->
                    Box(Modifier.size(6.dp).clip(CircleShape).background(marker.color()))
                }
            }
        }
    }
}

@Composable
private fun CalendarEventCard(event: AcademicDayEvent) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(event.marker.displayName(), style = MaterialTheme.typography.labelLarge, color = event.marker.color())
            Text(event.title, style = MaterialTheme.typography.titleMedium)
            if (event.detail.isNotBlank()) Text(event.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalendarEmptyState() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Nothing scheduled", style = MaterialTheme.typography.titleMedium)
            Text("No cached academic events for this date.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun selectedDateFor(month: YearMonth, preferredDay: Int): LocalDate = month.atDay(preferredDay.coerceAtMost(month.lengthOfMonth()))

private fun AcademicCalendarMarker.displayName(): String = when (this) {
    AcademicCalendarMarker.HOLIDAY -> "Holiday"
    AcademicCalendarMarker.EXAM -> "Exam"
    AcademicCalendarMarker.ASSIGNMENT -> "Assignment"
    AcademicCalendarMarker.CLASS -> "Class"
    AcademicCalendarMarker.DAY_ORDER -> "Day order"
    AcademicCalendarMarker.CALENDAR -> "Calendar"
}

@Composable
private fun AcademicCalendarMarker.color(): Color = when (this) {
    AcademicCalendarMarker.HOLIDAY -> VioraCoral
    AcademicCalendarMarker.EXAM -> VioraAmber
    AcademicCalendarMarker.ASSIGNMENT -> VioraBlue
    AcademicCalendarMarker.CLASS -> VioraSuccess
    AcademicCalendarMarker.DAY_ORDER -> MaterialTheme.colorScheme.tertiary
    AcademicCalendarMarker.CALENDAR -> MaterialTheme.colorScheme.primary
}

private const val DAYS_IN_WEEK = 7
private const val MAX_MARKERS_PER_DAY = 4
private val CALENDAR_DAY_HEIGHT = 58.dp
