package app.viora.agent

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AgentProtocolTest {
    @Test fun localResultsAreExcludedAndPermissionOffHidesTools() {
        val request = AgentProtocol.request(true, "fixture-model", listOf(AgentMessage(true, "Show attendance")), AgentPermission.OFF)
        assertFalse(request.has("tools"))
        assertFalse(request.getBoolean("store"))
        assertEquals("Show attendance", request.getJSONArray("input").getJSONObject(0).getString("content"))
    }

    @Test fun lunaLowUsesResponsesFunctionTools() {
        val messages = listOf(AgentMessage(true, "Download CAT-2 materials"))
        val request = AgentProtocol.request(true, "gpt-6-luna", messages, AgentPermission.ASK, lowReasoning = true)
        assertEquals("gpt-6-luna", request.getString("model"))
        assertEquals("low", request.getJSONObject("reasoning").getString("effort"))
        assertTrue((0 until request.getJSONArray("tools").length()).any { index ->
            request.getJSONArray("tools").getJSONObject(index).getString("name") == "download_viora_materials"
        })
    }

    @Test fun acceptsOnlyAllowlistedCallsWithExactArguments() {
        val body = JSONObject("""{"output":[
            {"type":"function_call","name":"open_viora_page","arguments":"{\"page\":\"https://example.org\"}"},
            {"type":"function_call","name":"read_viora_data","arguments":"{\"data\":\"attendance\",\"course\":\"CSE1001\"}"}
        ]}""")
        assertEquals(AgentAction.Read(AgentData.ATTENDANCE, "CSE1001"), AgentProtocol.reply(true, body, AgentPermission.ASK).action)
        val chat = JSONObject("""{"choices":[{"message":{"content":"Answer","tool_calls":[
            {"function":{"name":"open_viora_page","arguments":"{\"page\":\"attendance\"}"}}
        ]}}]}""")
        assertNull(AgentProtocol.reply(false, chat, AgentPermission.OFF).action)
        assertEquals(AgentAction.Open(AgentPage.ATTENDANCE), AgentProtocol.reply(false, chat, AgentPermission.ASK).action)
    }
}
