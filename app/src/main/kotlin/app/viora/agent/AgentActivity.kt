package app.viora.agent

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.viora.MainActivity
import app.viora.R
import app.viora.ui.VioraTheme

class AgentActivity : ComponentActivity() {
    private val model by viewModels<AgentViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Viora)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            VioraTheme {
                val state by model.state.collectAsStateWithLifecycle()
                LaunchedEffect(model) {
                    repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        model.state.collect { current ->
                            if (!current.busy && current.pending != null && current.automatic) model.execute()
                            current.opening?.let { page ->
                                model.navigated()
                                startActivity(Intent(this@AgentActivity, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    .putExtra("viora_destination", page.route))
                                finish()
                            }
                        }
                    }
                }
                AgentScreen(state, ::finish, model)
            }
        }
    }
}

@Composable
private fun AgentScreen(state: AgentState, close: () -> Unit, model: AgentViewModel) {
    var settings by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf("") }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = close) { Text("Back") }
                TextButton(onClick = { settings = !settings }, enabled = !state.busy) {
                    Text(if (settings) "Chat" else "Agent settings")
                }
            }
            Text("Viora Agent", style = MaterialTheme.typography.headlineMedium)
            if (settings || !state.connected) {
                AgentSettings(state, model)
            } else {
                Text("${state.provider.title} · ${state.model}", style = MaterialTheme.typography.labelLarge)
                val chatList = rememberLazyListState()
                val rowCount = state.messages.size + (if (state.localResult != null) 1 else 0) + (if (state.pending != null) 1 else 0) +
                    (if (state.downloadPlan != null) 1 else 0) + state.downloaded.size
                LaunchedEffect(rowCount) { if (rowCount > 0) chatList.scrollToItem(rowCount - 1) }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = chatList, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.messages.isEmpty()) item {
                        Text("Ask for your next exam, check attendance, or download materials for a teacher or assessment.",
                            Modifier.padding(top = 12.dp))
                    }
                    items(state.messages) { message ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(if (message.fromUser) "You" else state.provider.title, style = MaterialTheme.typography.labelLarge)
                                Text(message.text)
                            }
                        }
                    }
                    state.localResult?.let { result ->
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("From Viora", style = MaterialTheme.typography.labelLarge)
                                    Text(result)
                                }
                            }
                        }
                    }
                    items(state.downloaded) { entry ->
                        OutlinedButton(onClick = { model.openMaterial(entry) }, enabled = !state.busy) {
                            Text(entry.material.title.ifBlank { entry.material.fileName })
                        }
                    }
                    state.downloadPlan?.let { plan ->
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Review ${plan.files.size} matching files", style = MaterialTheme.typography.titleMedium)
                                    plan.files.take(8).forEach { entry -> Text("${entry.course} · ${entry.material.title}") }
                                    if (plan.files.size > 8) Text("And ${plan.files.size - 8} more")
                                    if (plan.note.isNotEmpty()) Text(plan.note)
                                    Button(onClick = model::approveDownloads, enabled = !state.busy) { Text("Download ${plan.files.size} files") }
                                    TextButton(onClick = model::dismissAction, enabled = !state.busy) { Text("Dismiss") }
                                }
                            }
                        }
                    }
                    state.pending?.let { action ->
                        item {
                            val label = when (action) {
                                is AgentAction.Open -> "Open ${action.page.title}"
                                is AgentAction.Read -> "Show ${action.data.name.lowercase()}"
                                is AgentAction.Download -> "Find matching materials"
                            }
                            Column {
                                if (action is AgentAction.Download) {
                                    Text(listOf(action.course, action.faculty, action.assessment).filter(String::isNotBlank)
                                        .joinToString(" · ").ifBlank { "All enrolled courses" })
                                }
                                Button(onClick = model::execute, enabled = !state.busy) { Text(label) }
                                TextButton(onClick = model::dismissAction, enabled = !state.busy) { Text("Dismiss action") }
                            }
                        }
                    }
                }
                state.status?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                OutlinedTextField(prompt, { prompt = it.take(4000) }, Modifier.fillMaxWidth(),
                    label = { Text("Ask Viora") }, maxLines = 4, enabled = !state.busy)
                Text("Your chat goes to ${state.provider.title}. Academic results stay on this phone.",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { model.send(prompt); prompt = "" }, enabled = prompt.isNotBlank() && !state.busy) { Text("Send") }
                    if (state.busy) TextButton(onClick = model::cancel) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun AgentSettings(state: AgentState, model: AgentViewModel) {
    var provider by remember(state.provider) { mutableStateOf(state.provider) }
    var modelId by remember(state.model, provider) { mutableStateOf(state.model.takeIf { provider == state.provider && it.isNotBlank() } ?: provider.example) }
    var permission by remember(state.permission) { mutableStateOf(state.permission) }
    var lowReasoning by remember(state.lowReasoning, provider) {
        mutableStateOf(provider == AgentProvider.OPENAI && (provider != state.provider || state.lowReasoning))
    }
    var key by remember(provider) { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Choose a provider and enter a model ID and your own API key. Provider usage may incur charges.")
        }
        item {
            AgentChoice("Provider", provider.title, AgentProvider.entries, { it.title }, { provider = it }, !state.busy)
        }
        item {
            OutlinedTextField(modelId, { modelId = it.take(160) }, Modifier.fillMaxWidth(),
                label = { Text("Model ID") }, placeholder = { Text(provider.example) }, singleLine = true, enabled = !state.busy)
        }
        item {
            OutlinedTextField(key, { key = it.take(4096) }, Modifier.fillMaxWidth(),
                label = { Text("API key") },
                placeholder = { if (state.connected && provider == state.provider) Text("Saved on this device") },
                singleLine = true, enabled = !state.busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        }
        if (provider == AgentProvider.OPENAI) item {
            AgentChoice("Reasoning", if (lowReasoning) "Low" else "Model default",
                listOf(true, false), { if (it) "Low" else "Model default" }, { lowReasoning = it }, !state.busy)
        }
        item {
            AgentChoice("Native actions", permission.label, AgentPermission.entries, { it.label }, { permission = it }, !state.busy)
            Text("Automatic actions can show information, open screens, and download materials you request.",
                style = MaterialTheme.typography.bodySmall)
        }
        item {
            Button(onClick = { model.connect(provider, modelId, key, permission, lowReasoning); key = "" },
                enabled = modelId.isNotBlank() && !state.busy) { Text("Save provider") }
            TextButton(onClick = { model.disconnect(provider); key = "" }, enabled = !state.busy) { Text("Remove API key") }
            state.status?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        }
    }
}

@Composable
private fun <T> AgentChoice(
    label: String, selected: String, values: List<T>, title: (T) -> String, choose: (T) -> Unit, enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text(selected) }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                values.forEach { value ->
                    DropdownMenuItem(text = { Text(title(value)) }, onClick = { expanded = false; choose(value) })
                }
            }
        }
    }
}
