package io.github.mangi.eta.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.model.AgentTaskState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Always available above the composer, including after completion and after compaction. */
@Composable
internal fun AgentActiveTodoPanel(
    tasks: AgentTaskState,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var original by rememberSaveable { mutableStateOf(false) }
    val completed = tasks.todos.count { it.status == "completed" }
    val cancelled = tasks.todos.count { it.status == "cancelled" }
    val active = tasks.todos.firstOrNull { it.status == "in_progress" }
        ?: tasks.todos.firstOrNull { it.status == "pending" }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.35f).dp.coerceAtMost(320.dp)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(MiuixTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FormatListBulleted, stringResource(R.string.todo_panel_title),
                tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("${stringResource(R.string.todo_panel_title)} · $completed/${tasks.todos.size}",
                    style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.SemiBold)
                val summary = active?.content ?: when {
                    tasks.isEmpty -> stringResource(R.string.todo_panel_empty)
                    cancelled > 0 -> stringResource(R.string.todo_panel_cancelled_count, cancelled)
                    tasks.todos.isNotEmpty() -> stringResource(R.string.todo_panel_all_done)
                    else -> stringResource(R.string.todo_panel_saved_plan)
                }
                Text(summary, style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                stringResource(if (expanded) R.string.todo_panel_collapse else R.string.todo_panel_expand),
                modifier = Modifier.size(24.dp))
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tasks.isEmpty) Text(stringResource(R.string.todo_panel_empty_hint), style = MiuixTheme.textStyles.body2)
                if (tasks.plan.isNotBlank() || tasks.originalTodos.isNotEmpty()) {
                    Text(stringResource(if (original) R.string.todo_panel_current else R.string.todo_panel_original),
                        modifier = Modifier.fillMaxWidth().clickable { original = !original }.padding(vertical = 6.dp),
                        color = MiuixTheme.colorScheme.primary, style = MiuixTheme.textStyles.body2)
                }
                if (original && tasks.plan.isNotBlank()) {
                    Text(tasks.plan, style = MiuixTheme.textStyles.body2)
                    Spacer(Modifier.height(4.dp))
                }
                (if (original) tasks.originalTodos else tasks.todos).forEachIndexed { index, item ->
                    val label = stringResource(when (item.status) {
                        "completed" -> R.string.todo_status_completed
                        "in_progress" -> R.string.todo_status_in_progress
                        "cancelled" -> R.string.todo_status_cancelled
                        else -> R.string.todo_status_pending
                    })
                    val color = when (item.status) {
                        "completed" -> StatusSuccess
                        "in_progress" -> MiuixTheme.colorScheme.primary
                        "cancelled" -> StatusError
                        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Icon(when (item.status) {
                            "completed" -> Icons.Rounded.CheckCircle
                            "in_progress" -> Icons.Rounded.PlayCircle
                            "cancelled" -> Icons.Rounded.Cancel
                            else -> Icons.Rounded.RadioButtonUnchecked
                        }, label, tint = color, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${index + 1}. ${item.content}", style = MiuixTheme.textStyles.body2)
                            Text(label, color = color, style = MiuixTheme.textStyles.footnote1)
                        }
                    }
                }
            }
        }
    }
}
