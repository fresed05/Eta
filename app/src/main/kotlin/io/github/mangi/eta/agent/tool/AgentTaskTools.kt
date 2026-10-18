package io.github.mangi.eta.agent.tool

import io.github.mangi.eta.agent.model.AgentTaskItem
import io.github.mangi.eta.agent.model.AgentTaskState
import org.json.JSONArray
import org.json.JSONObject

internal object AgentTaskTools {
    private val tasksByRun = mutableMapOf<String, AgentTaskState>()
    private val statuses = setOf("pending", "in_progress", "completed", "cancelled")
    data class Outcome(val state: AgentTaskState, val result: JSONObject)

    /** Validate the whole update before replacing anything. No partial or silent updates. */
    fun reduce(previous: AgentTaskState, args: JSONObject): Outcome = try {
        val action = args.optString("action", if (args.has("todos")) "write" else "list")
        var todos = previous.todos
        var plan = previous.plan
        var originals = previous.originalTodos
        val newPlan = args.optBoolean("new_plan", false)
        if (newPlan) require(action == "write") { "new_plan requires action=write" }
        when (action) {
            "write" -> {
                val array = args.optJSONArray("todos") ?: error("todos is required")
                todos = (0 until array.length()).map { i ->
                    val item = array.getJSONObject(i)
                    task(item.optString("id").ifBlank { (i + 1).toString() }, item.getString("content"), item.optString("status", "pending"))
                }
                require(todos.map { it.id }.distinct().size == todos.size) { "Task IDs must be unique" }
            }
            "add" -> {
                val id = args.optString("id").ifBlank {
                    generateSequence(1) { it + 1 }.map(Int::toString).first { candidate -> todos.none { it.id == candidate } }
                }
                require(todos.none { it.id == id }) { "Task ID already exists: $id" }
                todos = todos + task(id, args.getString("content"), args.optString("status", "pending"))
            }
            "update" -> {
                val id = args.getString("id")
                require(todos.any { it.id == id }) { "Unknown task ID: $id" }
                todos = todos.map { current -> if (current.id != id) current else task(id,
                    if (args.has("content")) args.getString("content") else current.content,
                    if (args.has("status")) args.getString("status") else current.status) }
            }
            "clear" -> { todos = emptyList(); plan = ""; originals = emptyList() }
            "list" -> Unit
            else -> error("Unknown action: $action")
        }
        if (args.has("plan")) {
            val supplied = args.getString("plan")
            require(supplied.length <= 32_000) { "Plan exceeds 32000 characters" }
            require(plan.isBlank() || supplied == plan || newPlan) { "Original plan is immutable; use new_plan=true to replace it explicitly" }
            plan = supplied
        }
        require(todos.size <= 200) { "At most 200 tasks are supported" }
        if (newPlan || originals.isEmpty()) originals = todos
        if (newPlan && !args.has("plan")) plan = ""
        val changed = plan != previous.plan || todos != previous.todos || originals != previous.originalTodos
        val state = AgentTaskState(previous.revision + if (changed || newPlan) 1 else 0, plan, originals, todos)
        require(state.encode().toByteArray(Charsets.UTF_8).size <= 256_000) { "Task state exceeds the 256 KB transport budget" }
        Outcome(state, result(state))
    } catch (failure: Exception) {
        Outcome(previous, JSONObject().put("ok", false).put("code", "INVALID_TASK_UPDATE").put("message", failure.message))
    }

    @Synchronized
    fun execute(runId: String, args: JSONObject): JSONObject {
        val outcome = reduce(tasksByRun[runId] ?: AgentTaskState(), args)
        tasksByRun[runId] = outcome.state
        return outcome.result
    }

    @Synchronized
    fun clearRun(runId: String) { tasksByRun.remove(runId) }

    private fun task(id: String, content: String, status: String): AgentTaskItem {
        require(id.isNotBlank() && id.length <= 100) { "Task ID must have 1–100 characters" }
        require(content.isNotBlank() && content.length <= 8000) { "Task content must have 1–8000 characters" }
        require(status in statuses) { "Invalid task status: $status" }
        return AgentTaskItem(id, content, status)
    }

    private fun result(state: AgentTaskState): JSONObject {
        val completed = state.todos.count { it.status == "completed" }
        return JSONObject().put("ok", true).put("revision", state.revision).put("plan", state.plan)
            .put("total", state.todos.size).put("completed", completed)
            .put("in_progress", state.todos.count { it.status == "in_progress" })
            .put("pending", state.todos.count { it.status == "pending" })
            .put("cancelled", state.todos.count { it.status == "cancelled" })
            .put("summary", "$completed/${state.todos.size}")
            .put("todos", JSONArray().also { array -> state.todos.forEach {
                array.put(JSONObject().put("id", it.id).put("content", it.content).put("status", it.status))
            } })
    }
}
