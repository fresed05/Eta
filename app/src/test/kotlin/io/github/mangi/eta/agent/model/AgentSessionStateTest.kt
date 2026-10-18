package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.runtime.AgentEvent
import io.github.mangi.eta.agent.runtime.AgentRunController
import io.github.mangi.eta.agent.runtime.AgentTokenUsage
import io.github.mangi.eta.agent.tool.AgentTaskTools
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AgentSessionStateTest {
    private val config = AgentModelClient.ModelConfig(baseUrl = "https://example.test/v1", apiKey = "test", model = "test", systemPrompt = "instructions")

    private fun write(plan: String = "Original plan\n1. inspect\n2. implement\n3. test") = JSONObject()
        .put("plan", plan).put("todos", JSONArray().put(JSONObject().put("id", "1").put("content", "inspect").put("status", "pending")))

    @Test fun taskUpdatesPreserveOriginalPlanAndAreAtomic() {
        val created = AgentTaskTools.reduce(AgentTaskState(), write())
        assertTrue(created.result.getBoolean("ok"))
        val updated = AgentTaskTools.reduce(created.state, JSONObject().put("action", "update").put("id", "1").put("status", "completed"))
        assertEquals(created.state.plan, updated.state.plan)
        assertEquals(created.state.originalTodos, updated.state.originalTodos)
        assertEquals("completed", updated.state.todos.single().status)
        val invalid = AgentTaskTools.reduce(updated.state, JSONObject().put("todos", JSONArray()
            .put(JSONObject().put("id", "2").put("content", "valid").put("status", "pending"))
            .put(JSONObject().put("id", "3").put("content", "bad").put("status", "unknown"))))
        assertFalse(invalid.result.getBoolean("ok"))
        assertEquals(updated.state, invalid.state)
        assertEquals(updated.state, AgentTaskState.decode(updated.state.encode()))
    }

    @Test fun explicitNewPlanAndClearDoNotResurrectOldTasks() {
        val initial = AgentTaskTools.reduce(AgentTaskState(), write()).state
        assertFalse(AgentTaskTools.reduce(initial, write("Replacement")).result.getBoolean("ok"))
        val changed = AgentTaskTools.reduce(initial, write("Replacement").put("new_plan", true)).state
        assertEquals("Replacement", changed.plan)
        val cleared = AgentTaskTools.reduce(changed, JSONObject().put("action", "clear")).state
        assertTrue(cleared.isEmpty)
        assertTrue(cleared.revision > changed.revision)
        val session = AgentSessionState(tasks = cleared).withTasks(initial)
        assertEquals(cleared, session.tasks)
    }

    @Test fun compositionIncludesSystemSchemasSkillsUsersAndToolResults() {
        val call = JSONObject().put("id", "skill").put("function", JSONObject().put("name", "skills_read").put("arguments", "{}"))
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", "instructions".repeat(20)))
            .put(JSONObject().put("role", "system").put("content", "skill index".repeat(10)).put("_eta_context_category", "tools_skills"))
            .put(JSONObject().put("role", "user").put("content", "user text".repeat(10)))
            .put(JSONObject().put("role", "assistant").put("content", "agent answer").put("tool_calls", JSONArray().put(call)))
            .put(JSONObject().put("role", "tool").put("tool_call_id", "skill").put("content", "loaded skill".repeat(100)))
            .put(JSONObject().put("role", "tool").put("tool_call_id", "other").put("content", "tool result".repeat(50)))
        val measured = AgentContextComposition.measure(ProviderRequest(config, messages, JSONArray().put(AgentTaskToolCatalog.schema())))
        assertTrue(measured.instructions > 0)
        assertTrue(measured.toolsAndSkills > 0)
        assertTrue(measured.user > 0)
        assertTrue(measured.agent > 0)
        for (total in listOf(0, 1, 2, 17, 100_003)) assertEquals(total, measured.calibrated(total).total)
    }

    @Test fun streamUsageReplacesSameRequestAndCountsCompactionSeparately() {
        val events = mutableListOf<AgentEvent>()
        val provider = object : AgentProviderClient {
            override val id = "fake"
            override val capabilities = ProviderCapabilities(EndpointKind.CHAT_COMPLETIONS, true, true, true, true, false, false)
            override fun complete(request: ProviderRequest, runController: AgentRunController, onEvent: (ProviderEvent) -> Unit): ProviderResponse {
                onEvent(ProviderEvent.Usage(AgentTokenUsage(inputTokens = 100, outputTokens = 1, cachedTokens = 40)))
                onEvent(ProviderEvent.Usage(AgentTokenUsage(outputTokens = 30)))
                return ProviderResponse(JSONObject().put("role", "assistant").put("content", "ok").put("finish_reason", "stop"))
            }
        }
        val observer = AgentRequestObserver(provider, "run", events::add)
        val request = ProviderRequest(config, JSONArray().put(AgentConversationCodec.userTextMessage("hi")), JSONArray())
        observer.complete(request, AgentRunController()) {}
        observer.complete(request.copy(purpose = ProviderRequestPurpose.COMPACTION), AgentRunController()) {}
        var session = AgentSessionState()
        events.filterIsInstance<AgentEvent.RequestRecorded>().forEach { session = session.withRequest(it.record) }
        assertEquals(2, session.requests.size)
        assertEquals(listOf("CHAT", "COMPACTION"), session.requests.map { it.purpose })
        assertEquals(100, session.requests.first().usage?.inputTokens)
        assertEquals(30, session.requests.first().usage?.outputTokens)
        assertEquals(40, session.requests.first().usage?.cachedTokens)
        assertEquals(session, AgentSessionState.decode(session.encode()))
    }
    @Test fun planModeBlocksActionsUntilPlanAndTodosAreSaved() {
        val events = mutableListOf<AgentEvent>()
        var round = 0
        var actualCalls = 0
        val provider = object : AgentProviderClient {
            override val id = "fake"
            override val capabilities = ProviderCapabilities(EndpointKind.CHAT_COMPLETIONS, true, true, true, true, false, false)
            override fun complete(request: ProviderRequest, runController: AgentRunController, onEvent: (ProviderEvent) -> Unit): ProviderResponse {
                round++
                if (round == 4) return ProviderResponse(JSONObject().put("role", "assistant").put("content", "done").put("finish_reason", "stop"))
                val name = if (round == 2) "todo_write" else "get_current_context"
                val args = if (round == 2) write().toString() else "{}"
                return ProviderResponse(JSONObject().put("role", "assistant").put("content", "")
                    .put("finish_reason", "tool_calls").put("tool_calls", JSONArray().put(JSONObject()
                        .put("id", "call-$round").put("type", "function").put("function", JSONObject()
                            .put("name", name).put("arguments", args)))))
            }
        }
        val result = AgentModelClient.complete(config.copy(planningMode = true, taskStateJson = AgentTaskState().encode()),
            "implement", AgentModelClient.ToolExecutor { actualCalls++; AgentModelClient.ToolResult("{\"ok\":true}") },
            provider = provider, onEvent = events::add)
        assertEquals(1, actualCalls)
        assertEquals(1, events.filterIsInstance<AgentEvent.TasksUpdated>().size)
        assertTrue(result.transcript.any { it.role == "tool" && it.content.contains("PLAN_REQUIRED") })
        assertEquals("done", result.content)
    }

}
