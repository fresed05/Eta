package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.tool.AgentTaskTools
import org.json.JSONObject

/** One-time migration from full raw journal, never from the localized trace summary. */
internal object AgentTaskHistoryRecovery {
    fun recover(history: List<AgentModelClient.ConversationMessage>): AgentTaskState {
        var state = AgentTaskState()
        val pending = mutableMapOf<String, AgentModelClient.ToolCall>()
        history.forEach { message ->
            if (message.role == "assistant") {
                runCatching { AgentConversationCodec.toJsonObject(message) }.getOrNull()?.let { json ->
                    AgentConversationCodec.parseToolCalls(json).filter { it.name in setOf("todo_write", "task_manage", "tasks") }
                        .forEach { pending[it.id] = it }
                }
            } else if (message.role == "tool") {
                val call = pending.remove(message.toolCallId) ?: return@forEach
                val result = runCatching { JSONObject(message.content) }.getOrNull() ?: return@forEach
                if (!result.optBoolean("ok")) return@forEach
                val args = runCatching { JSONObject(call.argumentsJson) }.getOrNull() ?: return@forEach
                val outcome = AgentTaskTools.reduce(state, args)
                if (outcome.result.optBoolean("ok")) state = outcome.state
            }
        }
        return state
    }
}
