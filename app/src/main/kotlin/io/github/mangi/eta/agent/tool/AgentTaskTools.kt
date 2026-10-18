package io.github.mangi.eta.agent.tool

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal object AgentTaskTools {
    data class TaskItem(
        val id: String,
        val content: String,
        val status: String,
    )

    private val tasksByRun = ConcurrentHashMap<String, MutableList<TaskItem>>()

    fun execute(runId: String, args: JSONObject): JSONObject {
        val tasks = tasksByRun.computeIfAbsent(runId) { mutableListOf() }
        val action = args.optString("action", if (args.has("todos")) "write" else "list")

        when (action) {
            "write" -> {
                val array = args.optJSONArray("todos")
                if (array != null) {
                    tasks.clear()
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val id = item.optString("id").ifBlank { (i + 1).toString() }
                        val content = item.optString("content").ifBlank { item.optString("title") }
                        val status = item.optString("status", "pending")
                        if (content.isNotBlank()) {
                            tasks.add(TaskItem(id, content, status))
                        }
                    }
                }
            }
            "add" -> {
                val content = args.optString("content").ifBlank { args.optString("title") }
                if (content.isNotBlank()) {
                    val id = args.optString("id").ifBlank { (tasks.size + 1).toString() }
                    val status = args.optString("status", "pending")
                    tasks.add(TaskItem(id, content, status))
                }
            }
            "update" -> {
                val id = args.optString("id")
                val newStatus = args.optString("status")
                val newContent = args.optString("content").ifBlank { args.optString("title") }
                val index = tasks.indexOfFirst { it.id == id }
                if (index >= 0) {
                    val current = tasks[index]
                    tasks[index] = current.copy(
                        status = if (newStatus.isNotBlank()) newStatus else current.status,
                        content = if (newContent.isNotBlank()) newContent else current.content,
                    )
                }
            }
            "clear" -> {
                tasks.clear()
            }
            "list" -> Unit
        }

        val completed = tasks.count { it.status == "completed" }
        val inProgress = tasks.count { it.status == "in_progress" }
        val pending = tasks.count { it.status == "pending" }
        val cancelled = tasks.count { it.status == "cancelled" }

        val outArray = JSONArray()
        tasks.forEach { t ->
            outArray.put(
                JSONObject()
                    .put("id", t.id)
                    .put("content", t.content)
                    .put("status", t.status),
            )
        }

        val summary = "$completed/${tasks.size} выполнено" +
            if (inProgress > 0) ", $inProgress в процессе" else ""

        return JSONObject()
            .put("ok", true)
            .put("total", tasks.size)
            .put("completed", completed)
            .put("in_progress", inProgress)
            .put("pending", pending)
            .put("cancelled", cancelled)
            .put("summary", summary)
            .put("todos", outArray)
    }

    fun clearRun(runId: String) {
        tasksByRun.remove(runId)
    }
}
