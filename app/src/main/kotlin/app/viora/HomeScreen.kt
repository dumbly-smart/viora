package app.viora

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val attention = remember(state.attendance, state.attendanceTarget, referenceNow) {
        state.homeNeedsAttention(referenceNow)
    }
    val eventDates = remember(timeline) { timeline.academicDates() }
    val today = remember(referenceNow) { Instant.ofEpochMilli(referenceNow).atZone(academicZone).toLocalDate() }
    val nextUp = remember(timeline) { timeline.firstOrNull { it.kind == HomeTimelineKind.CLASS } ?: timeline.firstOrNull() }
    var selectedDate by remember(today) { mutableStateOf(today) }
    val selectedTimeline = remember(timeline, selectedDate) {
        timeline.filter { Instant.ofEpochMilli(it.at).atZone(academicZone).toLocalDate() == selectedDate }
    }
    val listState = rememberLazyListState()
    val collapseRange = with(LocalDensity.current) { 156.dp.toPx() }
    var collapseOffset by remember { mutableFloatStateOf(0f) }
    val collapseFraction = homeUpcomingCollapseFraction(collapseOffset, collapseRange)
    val nestedScrollConnection = remember(listState, collapseRange) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val delta = available.y
                if (delta < 0f && collapseOffset < collapseRange) {
                    val consumed = minOf(-delta, collapseRange - collapseOffset)
                    collapseOffset += consumed
                    return Offset(0f, -consumed)
                }
                if (delta > 0f && collapseOffset > 0f && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    val consumed = minOf(delta, collapseOffset)
                    collapseOffset -= consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }
        }
    }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.fillMaxSize()) {
            TodayHeader(today, if (state.reauthRequired) reauthenticate else refresh, if (state.reauthRequired) "Sign in to VTOP" else "Sync Viora", state.reauthRequired, state.loading, Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            CollapsingUpcomingPanel(nextUp, openDetail, collapseFraction)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            item("date-rail") {
                DateRail(today, selectedDate, eventDates) { selectedDate = it }
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
            if (selectedTimeline.isEmpty()) {
                item("empty") { HomeEmptyState() }
            } else {
                items(selectedTimeline, key = HomeTimelineItem::id) { item ->
                    HomeTimelineRow(item, openDetail)
                }
            }
            }
        }
    }
}

@Composable
private fun CollapsingUpcomingPanel(
    item: HomeTimelineItem?,
    openDetail: (DetailSelection) -> Unit,
    collapseFraction: Float,
) {
    val panelHeight by animateDpAsState(lerp(200.dp, 62.dp, collapseFraction), label = "upcoming-panel-height")
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(panelHeight),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        if (collapseFraction < 0.55f) {
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Upcoming", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                if (item == null) {
                    Text("Nothing scheduled", style = MaterialTheme.typography.titleLarge)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { item.detailSelection()?.let(openDetail) },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.heroDetail(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            item.location.takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize().clickable(enabled = item?.detailSelection() != null) { item?.detailSelection()?.let(openDetail) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item?.let {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Upcoming", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Text(it.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(it.heroDetail(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                } ?: Text("Nothing upcoming", style = MaterialTheme.typography.titleMedium)
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
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
private fun DateRail(today: LocalDate, selectedDate: LocalDate, eventDates: Set<LocalDate>, onSelect: (LocalDate) -> Unit) {
    val dates = remember(today) { (0L..13L).map(today::plusDays) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(dates) { date ->
            CalendarDay(date, today, date == selectedDate, date in eventDates, onSelect)
        }
    }
}

@Composable
private fun CalendarDay(date: LocalDate, today: LocalDate, selected: Boolean, hasEvent: Boolean, onSelect: (LocalDate) -> Unit) {
    Surface(
        modifier = Modifier.width(54.dp).clickable { onSelect(date) }.semantics {
            contentDescription = homeCalendarDayDescription(date, today, hasEvent)
            this.selected = selected
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
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (item.subtitle.isNotBlank()) {
                    Text(item.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            if (item.subtitle.isNotBlank()) Text(item.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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

internal fun homeUpcomingCollapseFraction(scrollOffset: Float, collapseRange: Float): Float =
    (scrollOffset / collapseRange).coerceIn(0f, 1f)
