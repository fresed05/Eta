package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

internal object AgentTaskToolCatalog {
    const val TODO_WRITE = "todo_write"

    fun schema(): JSONObject = AgentToolSchema.function(
        name = TODO_WRITE,
        description = "Управление списком задач текущего сеанса (постановка, планирование и отметка выполнения). " +
            "Используй для структурирования многоэтапных или сложных задач (от 3 шагов). " +
            "Передавай актуальный массив todos с обновленными статусами (pending, in_progress, completed, cancelled) " +
            "или используй action (list, add, update, clear).",
        parameters = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()
                .put("action", JSONObject()
                    .put("type", "string")
                    .put("description", "Действие: write (по умолчанию при передаче todos), add, update, list, clear")
                    .put("enum", JSONArray(listOf("write", "add", "update", "list", "clear"))))
                .put("todos", JSONObject()
                    .put("type", "array")
                    .put("description", "Полный или обновленный список задач")
                    .put("items", JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject()
                            .put("id", JSONObject().put("type", "string").put("description", "Уникальный ID задачи (1, 2, ...)"))
                            .put("content", JSONObject().put("type", "string").put("description", "Описание задачи"))
                            .put("status", JSONObject()
                                .put("type", "string")
                                .put("description", "Статус: pending, in_progress, completed, cancelled")
                                .put("enum", JSONArray(listOf("pending", "in_progress", "completed", "cancelled")))))
                        .put("required", JSONArray(listOf("content", "status")))))
                .put("id", JSONObject().put("type", "string").put("description", "ID задачи (для action=update)"))
                .put("content", JSONObject().put("type", "string").put("description", "Текст задачи (для action=add/update)"))
                .put("status", JSONObject()
                    .put("type", "string")
                    .put("description", "Статус (для action=add/update)")
                    .put("enum", JSONArray(listOf("pending", "in_progress", "completed", "cancelled"))))),
    )

    fun appendTo(tools: JSONArray) {
        tools.put(schema())
    }
}
