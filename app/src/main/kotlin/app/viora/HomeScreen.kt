package app.viora

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeScreen(
    state: VioraUiState,
    refresh: () -> Unit,
    reauthenticate: () -> Unit = {},
    openDetail: (DetailSelection) -> Unit = {},
    modifier: Modifier = Modifier,
    nowEpochMillis: Long? = null,
) {
    val referenceNow = remember(nowEpochMillis) { nowEpochMillis ?: System.currentTimeMillis() }
    val timeline = remember(state.slots, state.assignments, state.exams, state.calendar, referenceNow) {
        state.homeTimeline(referenceNow)
    }
    val attention = remember(state.assignments, state.attendance, state.attendanceTarget, referenceNow) {
        state.homeNeedsAttention(referenceNow)
    }
    val eventDates = remember(timeline) { timeline.academicDates() }
    val today = remember(referenceNow) { Instant.ofEpochMilli(referenceNow).atZone(academicZone).toLocalDate() }
    val nextUp = remember(timeline) { timeline.firstOrNull { it.kind == HomeTimelineKind.CLASS } ?: timeline.firstOrNull() }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            stickyHeader(key = "today-header") {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                    TodayHeader(
                        today = today,
                        headerAction = if (state.reauthRequired) reauthenticate else refresh,
                        headerActionDescription = if (state.reauthRequired) "Sign in to VTOP" else "Sync Viora",
                        showSignIn = state.reauthRequired,
                        loading = state.loading,
                    )
                }
            }
            item("next-up") {
                NextUpHero(nextUp, openDetail)
            }
            item("date-rail") {
                DateRail(today, eventDates)
            }
            if (attention.isNotEmpty()) {
                item("attention-heading") {
                    SectionHeading("Needs attention")
                }
                items(attention, key = HomeAttentionItem::id) { item ->
                    AttentionRow(item, openDetail)
                }
            }
            item("timeline-heading") {
                SectionHeading("Academic timeline")
            }
            if (timeline.isEmpty()) {
                item("empty") { HomeEmptyState() }
            } else {
                items(timeline, key = HomeTimelineItem::id) { item ->
                    HomeTimelineRow(item, openDetail)
                }
            }
        }
    }
}

@Composable
private fun TodayHeader(
    today: LocalDate,
    headerAction: () -> Unit,
    headerActionDescription: String,
    showSignIn: Boolean,
    loading: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Today", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(
                today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (showSignIn) {
            Button(onClick = headerAction, enabled = !loading, modifier = Modifier.semantics { contentDescription = headerActionDescription }) {
                Text("Sign in")
            }
        } else {
            IconButton(onClick = headerAction, enabled = !loading) {
                Icon(Icons.Rounded.MoreVert, contentDescription = headerActionDescription)
            }
        }
    }
}

@Composable
private fun NextUpHero(item: HomeTimelineItem?, openDetail: (DetailSelection) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(item?.heroLabel() ?: "Next up", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            if (item == null) {
                Text("Nothing scheduled", style = MaterialTheme.typography.titleLarge)
                Text("Your cached academic timeline is clear.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    KindBadge(item.kind, Modifier.size(48.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(item.heroDetail(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        item.location.takeIf(String::isNotBlank)?.let {
                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                item.detailSelection()?.let { selection ->
                    Button(
                        onClick = { openDetail(selection) },
                        modifier = Modifier.semantics { contentDescription = item.heroContentDescription() },
                    ) {
                        Text(item.actionLabel())
                    }
                }
            }
        }
    }
}

@Composable
private fun DateRail(today: LocalDate, eventDates: Set<LocalDate>) {
    val dates = remember(today) { (0L..13L).map(today::plusDays) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(dates) { date ->
            CalendarDay(date, today, date in eventDates)
        }
    }
}

@Composable
private fun CalendarDay(date: LocalDate, today: LocalDate, hasEvent: Boolean) {
    val selected = date == today
    Surface(
        modifier = Modifier.width(54.dp).clearAndSetSemantics {
            contentDescription = homeCalendarDayDescription(date, today, hasEvent)
        },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH), style = MaterialTheme.typography.labelMedium)
            Text(date.dayOfMonth.toString().padStart(2, '0'), style = MaterialTheme.typography.titleMedium)
            Box(Modifier.size(4.dp).background(if (hasEvent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun AttentionRow(item: HomeAttentionItem, openDetail: (DetailSelection) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth()
            .clickable { openDetail(DetailSelection(item.detailKind, item.detailId)) }
            .semantics { contentDescription = item.accessibilityLabel },
        shape = MaterialTheme.shapes.medium,
        color = when (item.kind) {
            HomeAttentionKind.ATTENDANCE -> MaterialTheme.colorScheme.errorContainer
            HomeAttentionKind.OVERDUE_ASSIGNMENT -> MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = when (item.kind) {
            HomeAttentionKind.ATTENDANCE -> MaterialTheme.colorScheme.onErrorContainer
            HomeAttentionKind.OVERDUE_ASSIGNMENT -> MaterialTheme.colorScheme.onSurface
        },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KindBadge(item.timelineKind, Modifier.size(40.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (item.subtitle.isNotBlank()) {
                    Text(item.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun HomeTimelineRow(item: HomeTimelineItem, openDetail: (DetailSelection) -> Unit) {
    val date = Instant.ofEpochMilli(item.at).atZone(academicZone).toLocalDate()
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp)
            .clickable(enabled = item.detailKind != null && item.detailId != null) {
                openDetail(DetailSelection(requireNotNull(item.detailKind), requireNotNull(item.detailId)))
            }
            .padding(vertical = 8.dp).semantics {
                contentDescription = "${item.title}, ${item.status}, ${date.format(DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH))}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Text(date.dayOfMonth.toString().padStart(2, '0'), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.width(12.dp))
        KindBadge(item.kind, Modifier.size(46.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            if (item.subtitle.isNotBlank()) Text(item.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        HomeTrailingIcon(item.kind)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun KindBadge(kind: HomeTimelineKind, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = when (kind) {
            HomeTimelineKind.CLASS -> MaterialTheme.colorScheme.primaryContainer
            HomeTimelineKind.ASSIGNMENT -> MaterialTheme.colorScheme.secondaryContainer
            HomeTimelineKind.EXAM -> MaterialTheme.colorScheme.tertiary
        },
        contentColor = when (kind) {
            HomeTimelineKind.CLASS -> MaterialTheme.colorScheme.onPrimaryContainer
            HomeTimelineKind.ASSIGNMENT -> MaterialTheme.colorScheme.onSecondaryContainer
            HomeTimelineKind.EXAM -> MaterialTheme.colorScheme.onPrimary
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(kind.artIcon(), contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun HomeTrailingIcon(kind: HomeTimelineKind) {
    when (kind) {
        HomeTimelineKind.ASSIGNMENT -> Icon(Icons.Outlined.Lock, "Pending assignment", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
        HomeTimelineKind.CLASS -> Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, "Upcoming class", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        HomeTimelineKind.EXAM -> Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, "Upcoming exam", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun HomeEmptyState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Nothing upcoming", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
        Text("Your next two weeks are clear.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun HomeTimelineItem.heroLabel(): String = when (kind) {
    HomeTimelineKind.CLASS -> "Next class"
    HomeTimelineKind.ASSIGNMENT -> "Next deadline"
    HomeTimelineKind.EXAM -> "Next exam"
}

private fun HomeTimelineItem.heroDetail(): String {
    val date = Instant.ofEpochMilli(at).atZone(academicZone)
    return "${date.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))} · ${date.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))}"
}

private fun HomeTimelineItem.heroContentDescription(): String {
    val time = Instant.ofEpochMilli(at).atZone(academicZone).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
    val place = location.takeIf(String::isNotBlank)?.let { " in $it" }.orEmpty()
    return "${heroLabel()}: $title at $time$place. ${actionLabel()} details."
}

private fun HomeTimelineItem.actionLabel(): String = when (kind) {
    HomeTimelineKind.CLASS -> "Open course"
    HomeTimelineKind.ASSIGNMENT -> "Open task"
    HomeTimelineKind.EXAM -> "Open exam"
}

private fun HomeTimelineItem.detailSelection(): DetailSelection? {
    val kind = detailKind ?: return null
    val id = detailId ?: return null
    return DetailSelection(kind, id)
}

private fun HomeTimelineKind.artIcon(): ImageVector = when (this) {
    HomeTimelineKind.CLASS -> Icons.Outlined.CalendarMonth
    HomeTimelineKind.ASSIGNMENT -> Icons.AutoMirrored.Rounded.Assignment
    HomeTimelineKind.EXAM -> Icons.Outlined.CalendarMonth
}
