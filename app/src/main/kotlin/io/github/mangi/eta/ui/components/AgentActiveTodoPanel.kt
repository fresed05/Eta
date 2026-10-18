package io.github.mangi.eta.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.components.StatusError
import io.github.mangi.eta.ui.components.StatusSuccess
import io.github.mangi.eta.ui.components.StatusWarning
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class TodoItemUi(
    val id: String,
    val content: String,
    val status: String, // pending, in_progress, completed, cancelled
)

internal data class TodoListUi(
    val total: Int,
    val completed: Int,
    val inProgress: Int,
    val items: List<TodoItemUi>,
)

internal fun latestTodoList(messages: List<AgentChatMessageUi>): TodoListUi? {
    for (message in messages.asReversed()) {
        if (message is ToolActivityMessageUi &&
            (message.toolName == "todo_write" || message.toolName == "task_manage" || message.toolName == "tasks")
        ) {
            val list = parseTodoListFromResult(message.resultSummary)
                ?: parseTodoListFromArgs(message.argumentsSummary)
            if (list != null && list.items.isNotEmpty()) {
                return list
            }
        }
    }
    return null
}

private fun parseTodoListFromResult(resultSummary: String?): TodoListUi? {
    if (resultSummary.isNullOrBlank()) return null
    return runCatching {
        val json = JSONObject(resultSummary)
        val array = json.optJSONArray("todos") ?: return null
        val items = mutableListOf<TodoItemUi>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id").ifBlank { (i + 1).toString() }
            val content = obj.optString("content").ifBlank { obj.optString("title") }
            val status = obj.optString("status", "pending")
            if (content.isNotBlank()) {
                items.add(TodoItemUi(id, content, status))
            }
        }
        if (items.isEmpty()) return null
        val completed = items.count { it.status == "completed" }
        val inProgress = items.count { it.status == "in_progress" }
        TodoListUi(
            total = items.size,
            completed = completed,
            inProgress = inProgress,
            items = items,
        )
    }.getOrNull()
}

private fun parseTodoListFromArgs(argsSummary: String?): TodoListUi? {
    if (argsSummary.isNullOrBlank()) return null
    return runCatching {
        val json = JSONObject(argsSummary)
        val array = json.optJSONArray("todos") ?: return null
        val items = mutableListOf<TodoItemUi>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id").ifBlank { (i + 1).toString() }
            val content = obj.optString("content").ifBlank { obj.optString("title") }
            val status = obj.optString("status", "pending")
            if (content.isNotBlank()) {
                items.add(TodoItemUi(id, content, status))
            }
        }
        if (items.isEmpty()) return null
        val completed = items.count { it.status == "completed" }
        val inProgress = items.count { it.status == "in_progress" }
        TodoListUi(
            total = items.size,
            completed = completed,
            inProgress = inProgress,
            items = items,
        )
    }.getOrNull()
}

@Composable
internal fun AgentActiveTodoPanel(
    messages: List<AgentChatMessageUi>,
    modifier: Modifier = Modifier,
) {
    val todoList = remember(messages) { latestTodoList(messages) } ?: return
    if (todoList.items.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }

    val activeItem = todoList.items.firstOrNull { it.status == "in_progress" }
        ?: todoList.items.firstOrNull { it.status == "pending" }

    val progress = if (todoList.total > 0) {
        (todoList.completed.toFloat() / todoList.total.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val infiniteTransition = rememberInfiniteTransition(label = "todo_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .animateContentSize(),
    ) {
        // Заголовок панели (всегда виден, кликабелен для сворачивания/разворачивания)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (todoList.completed == todoList.total) StatusSuccess.copy(alpha = 0.15f)
                            else MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (todoList.completed == todoList.total) Icons.Rounded.CheckCircle
                        else Icons.Rounded.FormatListBulleted,
                        contentDescription = null,
                        tint = if (todoList.completed == todoList.total) StatusSuccess
                        else MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.todo_panel_title),
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${todoList.completed}/${todoList.total}",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    }

                    if (!isExpanded && activeItem != null) {
                        Text(
                            text = "▶ ${activeItem.content}",
                            style = MiuixTheme.textStyles.footnote1,
                            color = if (activeItem.status == "in_progress") MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else if (!isExpanded && todoList.completed == todoList.total) {
                        Text(
                            text = stringResource(R.string.todo_panel_all_done),
                            style = MiuixTheme.textStyles.footnote1,
                            color = StatusSuccess,
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Полоса прогресса задач
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = if (todoList.completed == todoList.total) StatusSuccess else MiuixTheme.colorScheme.primary,
                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
            ),
        )

        // Развернутый список задач
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                todoList.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (item.status == "in_progress") MiuixTheme.colorScheme.surface.copy(alpha = 0.5f)
                                else Color.Transparent
                            )
                            .padding(vertical = 4.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (item.status) {
                            "completed" -> {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = StatusSuccess,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            "in_progress" -> {
                                Icon(
                                    imageVector = Icons.Rounded.PlayCircle,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            "cancelled" -> {
                                Icon(
                                    imageVector = Icons.Rounded.Cancel,
                                    contentDescription = null,
                                    tint = StatusError,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = item.content,
                            style = MiuixTheme.textStyles.body2,
                            color = when (item.status) {
                                "completed" -> MiuixTheme.colorScheme.onSurfaceVariantActions
                                "in_progress" -> MiuixTheme.colorScheme.onSurface
                                "cancelled" -> StatusError
                                else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                            },
                            textDecoration = if (item.status == "completed" || item.status == "cancelled") {
                                TextDecoration.LineThrough
                            } else null,
                            fontWeight = if (item.status == "in_progress") FontWeight.Medium else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
