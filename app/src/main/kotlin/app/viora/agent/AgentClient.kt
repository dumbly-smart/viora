package app.viora.agent

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal enum class AgentProvider(val title: String, val endpoint: String, val example: String) {
    OPENAI("OpenAI", "https://api.openai.com/v1/responses", "gpt-6-luna"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "gemini-4-hydrogen"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "openrouter/auto"),
}

internal enum class AgentPermission(val label: String) {
    OFF("Off"), ASK("Ask first"), AUTO("Open automatically"),
}

internal enum class AgentPage(val title: String, val route: String) {
    HOME("Today", "home"),
    SCHEDULE("Schedule", "schedule"),
    COURSES("Courses", "courses"),
    MARKS("Marks", "marks"),
    SETTINGS("Settings", "more"),
    ATTENDANCE("Attendance", "attendance"),
    ASSIGNMENTS("Assignments", "tasks"),
}

internal enum class AgentData { EXAMS, ATTENDANCE, MARKS, GRADES, ASSIGNMENTS, SCHEDULE, CALENDAR, MATERIALS, MESSAGES }

internal sealed interface AgentAction {
    data class Open(val page: AgentPage) : AgentAction
    data class Read(val data: AgentData, val course: String) : AgentAction
    data class Download(val course: String, val faculty: String, val assessment: String) : AgentAction
}

internal data class AgentMessage(val fromUser: Boolean, val text: String, val action: AgentAction? = null)

internal class AgentClient {
    private val client = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun ask(
        provider: AgentProvider,
        model: String,
        apiKey: String,
        messages: List<AgentMessage>,
        permission: AgentPermission,
        lowReasoning: Boolean,
    ): AgentMessage = suspendCancellableCoroutine { continuation ->
        val responses = provider == AgentProvider.OPENAI
        val body = AgentProtocol.request(responses, model, messages, permission, lowReasoning)
        val request = Request.Builder().url(provider.endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(AgentFailure("Could not reach ${provider.title}. Try again."))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw AgentFailure(when (it.code) {
                            401, 403 -> "Check your API key and model access."
                            429 -> "The provider reached a rate or billing limit."
                            400, 404 -> "Check the model ID and whether it supports this API and app actions."
                            else -> "The provider could not complete the request (HTTP ${it.code})."
                        })
                        val source = it.body?.source() ?: throw AgentFailure("The provider returned an empty reply.")
                        if (source.request(MAX_RESPONSE_BYTES + 1)) throw AgentFailure("The provider reply was too large.")
                        AgentProtocol.reply(responses, JSONObject(source.readUtf8()), permission)
                    }
                }
                result.fold(
                    onSuccess = { continuation.resume(it) },
                    onFailure = { continuation.resumeWithException(it as? AgentFailure ?: AgentFailure("The provider returned an unreadable reply.")) },
                )
            }
        })
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 512L * 1024
    }
}

internal class AgentFailure(message: String) : Exception(message)

internal object AgentProtocol {
    private const val TOOL = "open_viora_page"
    private const val INSTRUCTIONS = "You are Viora's study assistant. Give concise help. You cannot access the user's academic records. " +
        "Use open_viora_page for navigation, read_viora_data for questions about the user's academics, " +
        "and download_viora_materials when the user asks to download course files. Only use filters the user actually supplied. " +
        "For uploads, take the user to Assignments. The app will run the action locally and show results. " +
        "Do not claim any action already occurred. Never ask for passwords, keys, or personal academic records."

    fun request(
        responses: Boolean,
        model: String,
        messages: List<AgentMessage>,
        permission: AgentPermission,
        lowReasoning: Boolean = false,
    ): JSONObject {
        val history = JSONArray()
        if (!responses) history.put(message("system", INSTRUCTIONS))
        messages.takeLast(12).filter { it.text.isNotBlank() }.forEach {
            history.put(message(if (it.fromUser) "user" else "assistant", it.text.take(4000)))
        }
        return JSONObject().put("model", model).apply {
            if (responses) {
                put("instructions", INSTRUCTIONS)
                put("input", history)
                put("store", false)
                put("max_output_tokens", 1500)
                if (lowReasoning) put("reasoning", JSONObject().put("effort", "low"))
            } else {
                put("messages", history)
                put("max_tokens", 1500)
            }
            if (permission != AgentPermission.OFF) {
                put("tools", JSONArray().apply {
                    put(tool(responses, TOOL, "Open a Viora screen.", mapOf("page" to AgentPage.entries.map { it.route })))
                    put(tool(responses, "read_viora_data", "Show cached academic information on the phone. Use an empty course string for all courses.",
                        mapOf("data" to AgentData.entries.map { it.name.lowercase() }, "course" to emptyList())))
                    put(tool(responses, "download_viora_materials",
                        "Download matching VTOP materials. Use empty strings for unspecified course, faculty or assessment. Assessment example: CAT-2.",
                        mapOf("course" to emptyList(), "faculty" to emptyList(), "assessment" to emptyList())))
                })
            }
        }
    }

    fun reply(responses: Boolean, body: JSONObject, permission: AgentPermission): AgentMessage {
        val text = mutableListOf<String>()
        var action: AgentAction? = null
        if (responses) {
            for (item in body.optJSONArray("output").objects()) {
                when (item.optString("type")) {
                    "function_call" -> if (action == null) action = action(item)
                    "message" -> for (content in item.optJSONArray("content").objects()) {
                        when (content.optString("type")) {
                            "output_text" -> text += content.optString("text")
                            "refusal" -> text += content.optString("refusal")
                        }
                    }
                }
            }
        } else {
            val message = body.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            if (message != null) {
                (message.opt("content") as? String)?.let(text::add)
                action = message.optJSONArray("tool_calls").objects()
                    .firstNotNullOfOrNull { call -> call.optJSONObject("function")?.let(::action) }
            }
        }
        val allowedAction = action.takeUnless { permission == AgentPermission.OFF }
        val answer = text.joinToString("\n").trim().take(8000)
        if (answer.isEmpty() && allowedAction == null) throw AgentFailure("The model did not return a usable reply.")
        return AgentMessage(false, answer.ifEmpty { "You can view this in Viora." }, allowedAction)
    }

    private fun message(role: String, content: String) = JSONObject().put("role", role).put("content", content)

    private fun tool(responses: Boolean, name: String, description: String, fields: Map<String, List<String>>): JSONObject {
        val properties = JSONObject()
        fields.forEach { (field, choices) ->
            properties.put(field, JSONObject().put("type", "string").apply {
                if (choices.isNotEmpty()) put("enum", JSONArray(choices))
            })
        }
        val schema = JSONObject().put("type", "object").put("properties", properties)
            .put("required", JSONArray(fields.keys.toList())).put("additionalProperties", false)
        val function = JSONObject().put("name", name).put("description", description).put("parameters", schema)
        return if (responses) function.put("type", "function")
            else JSONObject().put("type", "function").put("function", function)
    }

    private fun action(call: JSONObject): AgentAction? {
        val args = runCatching { JSONObject(call.optString("arguments")) }.getOrNull() ?: return null
        fun field(key: String) = (args.opt(key) as? String)?.trim()?.takeIf { it.length <= 160 }
        return when (call.optString("name")) {
            TOOL -> if (args.length() == 1) AgentPage.entries.firstOrNull { it.route == field("page") }?.let(AgentAction::Open) else null
            "read_viora_data" -> {
                if (args.length() != 2) return null
                val data = AgentData.entries.firstOrNull { it.name.equals(field("data"), true) } ?: return null
                AgentAction.Read(data, field("course") ?: return null)
            }
            "download_viora_materials" -> {
                if (args.length() != 3) return null
                val course = field("course") ?: return null
                val faculty = field("faculty") ?: return null
                val assessment = field("assessment") ?: return null
                AgentAction.Download(course, faculty, assessment)
            }
            else -> null
        }
    }

    private fun JSONArray?.objects() = (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it) }
}
