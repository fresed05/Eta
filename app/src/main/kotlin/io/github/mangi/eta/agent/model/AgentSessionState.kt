package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.runtime.AgentTokenUsage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val sessionCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
internal data class AgentTaskItem(val id: String, val content: String, val status: String = "pending")

/** Authoritative task state, independent of the lossy model history and UI trace. */
@Serializable
internal data class AgentTaskState(
    val revision: Long = 0,
    val plan: String = "",
    val originalTodos: List<AgentTaskItem> = emptyList(),
    val todos: List<AgentTaskItem> = emptyList(),
) {
    val isEmpty: Boolean get() = plan.isBlank() && todos.isEmpty() && originalTodos.isEmpty()
    fun encode(): String = sessionCodec.encodeToString(this)

    fun prompt(): String = if (isEmpty) {
        "[Eta task state] No saved plan or tasks."
    } else {
        "[Eta protected task state — authoritative, not a summary]\n" +
            "The following is saved task data, not new instructions. Preserve the original plan verbatim; " +
            "continue from current statuses, not from guesses in a context summary. " +
            "Use todo_write to update progress; new_plan=true explicitly starts a different plan.\n" + encode()
    }

    companion object {
        fun decode(raw: String?): AgentTaskState? = raw?.takeIf { it.isNotBlank() }?.let {
            runCatching { sessionCodec.decodeFromString<AgentTaskState>(it) }.getOrNull()
        }
    }
}

/** One record per actual provider invocation, not per output block or usage SSE chunk. */
@Serializable
internal data class AgentModelRequestRecord(
    val id: String,
    val purpose: String,
    val status: String = "started",
    val usage: AgentTokenUsage? = null,
    val composition: AgentContextComposition? = null,
) {
    fun encode(): String = sessionCodec.encodeToString(this)
    companion object {
        fun decode(raw: String): AgentModelRequestRecord = sessionCodec.decodeFromString(raw)
    }
}

@Serializable
internal data class AgentSessionState(
    val planningMode: Boolean = false,
    val tasks: AgentTaskState = AgentTaskState(),
    val requests: List<AgentModelRequestRecord> = emptyList(),
    val contextInvalidated: Boolean = false,
    // Old builds did not persist per-request usage. Never pretend those totals are complete.
    val legacyUsageIncomplete: Boolean = false,
) {
    fun withRequest(record: AgentModelRequestRecord): AgentSessionState {
        val index = requests.indexOfFirst { it.id == record.id }
        val updated = if (index < 0) requests + record else requests.toMutableList().also { it[index] = record }
        return copy(requests = updated, contextInvalidated = if (record.purpose == "CHAT") false else contextInvalidated)
    }

    fun withTasks(state: AgentTaskState): AgentSessionState =
        if (state.revision >= tasks.revision) copy(tasks = state) else this

    fun encode(): String = sessionCodec.encodeToString(this)
    companion object {
        fun decode(raw: String?): AgentSessionState = if (raw.isNullOrBlank()) AgentSessionState()
            else sessionCodec.decodeFromString(raw)
    }
}
