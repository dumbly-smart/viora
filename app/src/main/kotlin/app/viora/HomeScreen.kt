package app.viora

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HomePanel = Color(0xFF191C25)
private val HomePanelMuted = Color(0xFFB4BAC9)
private val HomeInactiveDay = Color(0xFF282D3A)
private val HomeAccent = Color(0xFFFFA36C)

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
    val eventDates = remember(timeline) { timeline.academicDates() }
    val today = remember(referenceNow) { Instant.ofEpochMilli(referenceNow).atZone(academicZone).toLocalDate() }
    val listState = rememberLazyListState()
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 54 }
    }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            stickyHeader(key = "upcoming-header") {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    UpcomingHeader(
                        collapsed = collapsed,
                        today = today,
                        eventDates = eventDates,
                        nextItem = timeline.firstOrNull(),
                        headerAction = if (state.reauthRequired) reauthenticate else refresh,
                        headerActionDescription = if (state.reauthRequired) "Sign in to VTOP" else "Sync Viora",
                        showSignIn = state.reauthRequired,
                        loading = state.loading,
                    )
                }
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
private fun UpcomingHeader(
    collapsed: Boolean,
    today: LocalDate,
    eventDates: Set<LocalDate>,
    nextItem: HomeTimelineItem?,
    headerAction: () -> Unit,
    headerActionDescription: String,
    showSignIn: Boolean,
    loading: Boolean,
) {
    AnimatedContent(
        targetState = collapsed,
        transitionSpec = { fadeIn(spring()) togetherWith fadeOut(spring()) },
        label = "upcoming panel",
        modifier = Modifier.animateContentSize(spring()),
    ) { isCollapsed ->
        if (isCollapsed) {
            CompactUpcomingHeader(nextItem, headerAction, headerActionDescription, showSignIn, loading)
        } else {
            ExpandedUpcomingHeader(today, eventDates, headerAction, headerActionDescription, showSignIn, loading)
        }
    }
}

@Composable
private fun ExpandedUpcomingHeader(
    today: LocalDate,
    eventDates: Set<LocalDate>,
    headerAction: () -> Unit,
    headerActionDescription: String,
    showSignIn: Boolean,
    loading: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 268.dp),
        shape = RoundedCornerShape(24.dp),
        color = HomePanel,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 268.dp).padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 17.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Upcoming", fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.semantics { heading() })
                    Text("In the next 2 weeks", color = HomePanelMuted, fontSize = 17.sp, lineHeight = 21.sp)
                }
                if (showSignIn) {
                    Surface(
                        onClick = headerAction,
                        enabled = !loading,
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.semantics { contentDescription = headerActionDescription },
                    ) {
                        Text("Sign in", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    IconButton(onClick = headerAction, enabled = !loading) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = headerActionDescription, tint = Color(0xFFB4B4B8))
                    }
                }
            }
            Spacer(Modifier.height(62.dp))
            CalendarStrip(today, eventDates)
        }
    }
}

@Composable
private fun CompactUpcomingHeader(
    nextItem: HomeTimelineItem?,
    headerAction: () -> Unit,
    headerActionDescription: String,
    showSignIn: Boolean,
    loading: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 78.dp),
        shape = RoundedCornerShape(18.dp),
        color = HomePanel,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 18.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !loading, onClick = headerAction)
                .semantics { contentDescription = headerActionDescription }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(nextItem?.kind?.artIcon() ?: Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(21.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    nextItem?.let { "Upcoming  ·  ${it.at.homeDate()}" } ?: "Upcoming",
                    color = HomePanelMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
                Text(nextItem?.title ?: "Nothing scheduled", fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (showSignIn) {
                Text("Sign in", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = Color(0xFFB4B4B8), modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun CalendarStrip(today: LocalDate, eventDates: Set<LocalDate>) {
    val dates = remember(today) { (0L..13L).map(today::plusDays) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        dates.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { date -> CalendarDay(date, today, date in eventDates) }
            }
        }
    }
}

@Composable
private fun CalendarDay(date: LocalDate, today: LocalDate, hasEvent: Boolean) {
    val selected = date == today
    Column(
        modifier = Modifier.clearAndSetSemantics { contentDescription = homeCalendarDayDescription(date, today, hasEvent) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primary else HomeInactiveDay,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(date.dayOfMonth.toString(), fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            }
        }
        Box(Modifier.size(4.dp).clip(CircleShape).background(if (hasEvent) HomeAccent else Color.Transparent))
    }
}

@Composable
private fun HomeTimelineRow(item: HomeTimelineItem, openDetail: (DetailSelection) -> Unit) {
    val date = Instant.ofEpochMilli(item.at).atZone(academicZone).toLocalDate()
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp)
            .clickable(enabled = item.detailKind != null && item.detailId != null) {
                openDetail(DetailSelection(requireNotNull(item.detailKind), requireNotNull(item.detailId)))
            }
            .padding(vertical = 8.dp).semantics {
            contentDescription = "${item.title}, ${item.status}, ${date.format(DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH))}"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(date.dayOfMonth.toString().padStart(2, '0'), color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(12.dp))
        HomeEventArtwork(item.kind)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 1)
            if (item.subtitle.isNotBlank()) Text(item.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.kind != HomeTimelineKind.CLASS) {
            Spacer(Modifier.width(8.dp))
            HomeTrailingIcon(item.kind)
        }
    }
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun HomeEventArtwork(kind: HomeTimelineKind) {
    val colors = when (kind) {
        HomeTimelineKind.CLASS -> listOf(Color(0xFF7A3D25), Color(0xFFC65A32))
        HomeTimelineKind.ASSIGNMENT -> listOf(Color(0xFF6E2E25), Color(0xFFC1492E))
        HomeTimelineKind.EXAM -> listOf(Color(0xFF742E35), Color(0xFFC34B45))
    }
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        if (kind != HomeTimelineKind.CLASS) {
            Icon(kind.artIcon(), null, tint = Color.White, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun HomeTrailingIcon(kind: HomeTimelineKind) {
    when (kind) {
        HomeTimelineKind.ASSIGNMENT -> Surface(shape = CircleShape, color = Color.Transparent, modifier = Modifier.size(34.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Lock, "Pending assignment", tint = Color(0xFFB9B9BD), modifier = Modifier.size(19.dp)) }
        }
        HomeTimelineKind.CLASS -> Unit
        HomeTimelineKind.EXAM -> Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, "Upcoming exam", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun HomeEmptyState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 54.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Nothing upcoming", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text("Your next two weeks are clear.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

private fun HomeTimelineKind.artIcon(): ImageVector = when (this) {
    HomeTimelineKind.CLASS -> Icons.Outlined.CalendarMonth
    HomeTimelineKind.ASSIGNMENT -> Icons.AutoMirrored.Rounded.Assignment
    HomeTimelineKind.EXAM -> Icons.Outlined.CalendarMonth
}

private fun Long.homeDate(): String = Instant.ofEpochMilli(this).atZone(academicZone)
    .format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
