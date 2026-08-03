package app.viora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.viora.database.SlotWithCourse
import app.viora.setup.SetupAction
import app.viora.setup.SetupScreen
import app.viora.setup.SetupState
import app.viora.sync.VioraSyncScheduler
import app.viora.ui.VioraTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val graph by lazy { VioraGraph(applicationContext) }
    private val model by viewModels<VioraAppViewModel> {
        VioraAppViewModel.Factory(graph, VioraSyncScheduler(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VioraTheme {
                val state by model.state.collectAsState()
                if (state.configured) {
                    Dashboard(state, model::refresh, model::selectSemester)
                } else {
                    SetupScreen(
                        state = SetupState(
                            username = state.username,
                            password = state.password,
                            rememberLogin = state.rememberLogin,
                            loading = state.loading,
                            error = state.error,
                        ),
                        onAction = { action ->
                            when (action) {
                                is SetupAction.UsernameChanged -> model.updateUsername(action.value)
                                is SetupAction.PasswordChanged -> model.updatePassword(action.value)
                                is SetupAction.RememberLoginChanged -> model.updateRememberLogin(action.value)
                                SetupAction.Submit -> model.signIn()
                            }
                        },
                    )
                }
            }
        }
    }
}

private data class Destination(val label: String, val icon: ImageVector)

private val destinations = listOf(
    Destination("Home", Icons.Outlined.Home),
    Destination("Schedule", Icons.Outlined.CalendarMonth),
    Destination("Courses", Icons.Outlined.MenuBook),
    Destination("Tasks", Icons.Outlined.Checklist),
    Destination("More", Icons.Outlined.MoreHoriz),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    state: VioraUiState,
    refresh: () -> Unit,
    selectSemester: (app.viora.network.SemesterOption) -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Viora") },
                actions = { TextButton(onClick = refresh, enabled = !state.loading) { Text("Sync") } },
            )
        },
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
            when (selected) {
                0 -> HomeScreen(state, PaddingValues())
                1 -> ScheduleScreen(state, selectSemester)
                else -> Placeholder(destinations[selected].label)
            }
        }
    }
}

@Composable
private fun HomeScreen(state: VioraUiState, padding: PaddingValues) {
    val today = LocalDate.now().dayOfWeek.value
    val todaySlots = state.slots.filter { it.dayOfWeek == today }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Today", style = MaterialTheme.typography.headlineMedium)
            Text(
                state.activeSemester?.name ?: "No semester selected",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (todaySlots.isEmpty()) {
            item { SummaryCard("Schedule", "No cached classes", "Sync with VTOP to load your timetable") }
        } else {
            items(todaySlots, key = SlotWithCourse::slotId) { slot -> ClassCard(slot) }
        }
        state.syncMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
    }
}

@Composable
private fun ScheduleScreen(
    state: VioraUiState,
    selectSemester: (app.viora.network.SemesterOption) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Schedule", style = MaterialTheme.typography.headlineMedium) }
        if (state.semesters.size > 1) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.semesters.take(3).forEach { semester ->
                        Button(onClick = { selectSemester(semester) }, enabled = semester != state.activeSemester) {
                            Text(semester.name)
                        }
                    }
                }
            }
        }
        val grouped = state.slots.groupBy(SlotWithCourse::dayOfWeek).toSortedMap()
        grouped.forEach { (day, slots) ->
            item {
                Text(
                    DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(slots, key = SlotWithCourse::slotId) { ClassCard(it) }
        }
        if (grouped.isEmpty()) item { Text("No timetable has been cached for this semester.") }
    }
}

@Composable
private fun ClassCard(slot: SlotWithCourse) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${slot.code} · ${slot.title}", style = MaterialTheme.typography.titleMedium)
            Text("${slot.startMinute.asTime()}–${slot.endMinute.asTime()} · ${slot.venue}")
            if (slot.faculty.isNotBlank()) {
                Text(slot.faculty, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun Int.asTime(): String = "%02d:%02d".format(this / 60, this % 60)

@Composable
private fun SummaryCard(title: String, value: String, supporting: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, color = MaterialTheme.colorScheme.primary)
            Text(supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Placeholder(label: String) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(label, style = MaterialTheme.typography.headlineMedium)
        Text("This feature is queued after the timetable vertical slice.")
    }
}
