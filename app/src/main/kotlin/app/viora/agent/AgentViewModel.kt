package app.viora.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.viora.VioraGraph
import app.viora.security.EncryptedPreferencesBlobStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class AgentState(
    val provider: AgentProvider = AgentProvider.OPENAI,
    val model: String = "",
    val permission: AgentPermission = AgentPermission.ASK,
    val lowReasoning: Boolean = true,
    val connected: Boolean = false,
    val messages: List<AgentMessage> = emptyList(),
    val localResult: String? = null,
    val downloadPlan: AgentDownloadPlan? = null,
    val downloaded: List<AgentMaterial> = emptyList(),
    val pending: AgentAction? = null,
    val opening: AgentPage? = null,
    val automatic: Boolean = false,
    val busy: Boolean = false,
    val status: String? = null,
)

internal class AgentViewModel(app: Application) : AndroidViewModel(app) {
    private val vault = EncryptedPreferencesBlobStore(app)
    private val local by lazy { AgentLocalActions(VioraGraph(app)) }
    private val client = AgentClient()
    private val mutable = MutableStateFlow(AgentState(busy = true))
    val state = mutable.asStateFlow()
    private var job: Job? = null

    init { launchWork { withContext(Dispatchers.IO) { load() } } }

    private fun load() {
        val raw = vault.get(CONFIG) ?: return
        try {
            val json = JSONObject(raw.toString(Charsets.UTF_8))
            val provider = AgentProvider.valueOf(json.getString("provider"))
            val permission = AgentPermission.valueOf(json.getString("permission"))
            val key = vault.get(keyName(provider))
            val connected = key != null
            key?.fill(0)
            mutable.update { it.copy(provider = provider, model = json.getString("model"), permission = permission, lowReasoning = json.optBoolean("lowReasoning", provider == AgentProvider.OPENAI), connected = connected) }
        } finally { raw.fill(0) }
    }

    fun connect(provider: AgentProvider, model: String, key: String, permission: AgentPermission, lowReasoning: Boolean) {
        val id = model.trim()
        val secret = key.trim()
        if (!id.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_./:@+~-]{0,159}")) ||
            (secret.isNotEmpty() && (secret.length > 4096 || secret.any { it !in '!'..'~' }))
        ) {
            mutable.update { it.copy(status = "Enter a valid model ID and API key.") }
            return
        }
        if (state.value.busy) return
        launchWork {
            withContext(Dispatchers.IO) {
                if (secret.isNotEmpty()) writeSecret(keyName(provider), secret)
                else {
                    val saved = vault.get(keyName(provider)) ?: throw AgentFailure("Enter an API key for ${provider.title}.")
                    saved.fill(0)
                }
                val config = JSONObject().put("provider", provider.name).put("model", id).put("permission", permission.name)
                    .put("lowReasoning", lowReasoning)
                writeSecret(CONFIG, config.toString())
            }
            mutable.update {
                it.copy(provider = provider, model = id, permission = permission, lowReasoning = lowReasoning, connected = true, messages = emptyList(),
                    pending = null, localResult = null, downloadPlan = null, downloaded = emptyList(), status = "Settings saved.")
            }
        }
    }

    fun disconnect(provider: AgentProvider) {
        if (state.value.busy) return
        launchWork {
            withContext(Dispatchers.IO) {
                vault.remove(keyName(provider))
                if (provider == state.value.provider) vault.remove(CONFIG)
            }
            if (provider == state.value.provider) {
                mutable.update { AgentState(provider = provider, status = "API key removed.") }
            } else mutable.update { it.copy(status = "API key removed for ${provider.title}.") }
        }
    }

    fun send(prompt: String) {
        val current = state.value
        val text = prompt.trim()
        if (current.busy || !current.connected || text.isEmpty() || text.length > 4000) return
        launchWork {
            val bytes = withContext(Dispatchers.IO) { vault.get(keyName(current.provider)) }
                ?: throw AgentFailure("Add your API key in Agent settings.")
            val key = try { bytes.toString(Charsets.UTF_8) } finally { bytes.fill(0) }
            if (text.contains(key) || KEY_IN_PROMPT.containsMatchIn(text)) {
                throw AgentFailure("Put API keys in Agent settings.")
            }
            val history = (current.messages + AgentMessage(true, text)).takeLast(24)
            mutable.update { it.copy(messages = history, pending = null, localResult = null, downloadPlan = null, downloaded = emptyList(), status = "Waiting for ${current.provider.title}…") }
            val reply = client.ask(current.provider, current.model, key, history, current.permission, current.lowReasoning)
            val auto = current.permission == AgentPermission.AUTO &&
                (reply.action !is AgentAction.Download ||
                    (DOWNLOAD_REQUEST.containsMatchIn(text) && MATERIAL_REQUEST.containsMatchIn(text)))
            mutable.update { it.copy(messages = history + reply, pending = reply.action, automatic = auto, status = null) }
        }
    }

    fun execute() {
        val action = state.value.pending ?: return
        if (state.value.busy || state.value.permission == AgentPermission.OFF) return
        val auto = state.value.automatic
        launchWork {
            mutable.update { it.copy(pending = null, automatic = false) }
            when (action) {
                is AgentAction.Open -> mutable.update { it.copy(opening = action.page) }
                is AgentAction.Read -> {
                    val result = local.read(action)
                    mutable.update { it.copy(localResult = result) }
                }
                is AgentAction.Download -> {
                    val plan = local.prepareDownloads(action, ::showProgress)
                    if (auto && plan.files.size <= 30 && plan.note.isBlank()) download(plan)
                    else mutable.update { it.copy(downloadPlan = plan, status = null) }
                }
            }
        }
    }

    fun approveDownloads() {
        val plan = state.value.downloadPlan ?: return
        if (state.value.busy) return
        launchWork { download(plan) }
    }

    private suspend fun download(plan: AgentDownloadPlan) {
        mutable.update { it.copy(downloadPlan = null) }
        val result = local.download(plan, ::showProgress)
        mutable.update { it.copy(localResult = result.summary, downloaded = result.files, status = null) }
    }

    fun openMaterial(entry: AgentMaterial) {
        if (state.value.busy) return
        launchWork { local.openMaterial(entry) }
    }

    private fun showProgress(status: String) = mutable.update { it.copy(status = status) }

    fun dismissAction() = mutable.update { it.copy(pending = null, automatic = false, downloadPlan = null) }
    fun navigated() = mutable.update { it.copy(opening = null) }

    fun cancel() {
        job?.cancel()
        mutable.update { it.copy(status = "Canceled", pending = null, automatic = false) }
    }

    private fun launchWork(block: suspend () -> Unit) {
        mutable.update { it.copy(busy = true, status = null) }
        job = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: AgentFailure) { mutable.update { it.copy(status = e.message) } }
            catch (_: Exception) { mutable.update { it.copy(status = "Viora couldn’t finish this action. Try again.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }

    private fun writeSecret(name: String, value: String) {
        val data = value.toByteArray()
        try { vault.put(name, data) } finally { data.fill(0) }
    }

    private fun keyName(provider: AgentProvider) = "agent.key.${provider.name}"

    private companion object {
        const val CONFIG = "agent.config"
        val KEY_IN_PROMPT = Regex("""\b(?:sk-[A-Za-z0-9_-]{15,}|AIza[A-Za-z0-9_-]{20,})""")
        val DOWNLOAD_REQUEST = Regex("""\b(?:download|save|fetch)\b""", RegexOption.IGNORE_CASE)
        val MATERIAL_REQUEST = Regex("""\b(?:materials?|files?|notes?|slides?|pdfs?)\b""", RegexOption.IGNORE_CASE)
    }
}
