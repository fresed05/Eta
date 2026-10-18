package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AgentPlanningModeSelector(enabled: Boolean, changingAllowed: Boolean, onChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        TextButton(
            text = stringResource(if (enabled) R.string.planning_mode_label else R.string.planning_mode_normal),
            onClick = { onChange(!enabled) },
            enabled = changingAllowed,
            minHeight = 32.dp,
            minWidth = 0.dp,
            colors = if (enabled) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
        )
        if (enabled) Text(stringResource(R.string.planning_mode_hint), style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(vertical = 4.dp))
    }
}
