package io.github.mangi.eta.ui.components

import java.util.Locale

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.AgentContextUsageUi
import io.github.mangi.eta.ui.model.AgentModelOptionUi
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.defaultExpandedModelProviderIds
import io.github.mangi.eta.ui.model.formatContextUsage
import io.github.mangi.eta.ui.model.formatCompactTokenCount
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import java.text.NumberFormat
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.RichTooltip
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.TooltipDefaults
import top.yukonga.miuix.kmp.basic.rememberTooltipState
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AgentModelPickerButton(
    state: AgentModelPickerUiState,
    isStreaming: Boolean,
    popupAnchorTopPx: Int,
    popupMaxHeight: Dp,
    onModelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPopup by remember { mutableStateOf(false) }
    var expandedProviderIds by remember { mutableStateOf(emptySet<String>()) }
    val selected = state.selectedModel
    val enabled = !isStreaming && !state.isChanging && state.providerGroups.isNotEmpty()
    LaunchedEffect(enabled) {
        if (!enabled) showPopup = false
    }
    val density = LocalDensity.current
    val popupWindowInsetPx = with(density) { 14.dp.roundToPx() }
    val popupPositionProvider = remember(popupAnchorTopPx, popupWindowInsetPx) {
        InputPopupPositionProvider(
            inputContainerTopPx = popupAnchorTopPx,
            windowHorizontalInsetPx = popupWindowInsetPx,
        )
    }
    val currentModel = selected?.displayName ?: stringResource(R.string.model_not_selected)
    val switchModelDescription = stringResource(R.string.model_switch_current, currentModel)
    Box(modifier = modifier) {
        IconButton(
            onClick = {
                expandedProviderIds = defaultExpandedModelProviderIds(state.selectedModel)
                showPopup = true
            },
            enabled = enabled,
            minWidth = ChatInputActionSize,
            minHeight = ChatInputActionSize,
            modifier = Modifier.semantics {
                contentDescription = switchModelDescription
            },
        ) {
            ModelBrandMark(
                modelId = selected?.modelId,
                sourceType = selected?.providerSourceType,
                size = ChatInputActionIconSize,
            )
        }

        OverlayListPopup(
            show = showPopup && popupAnchorTopPx > 0,
            popupPositionProvider = popupPositionProvider,
            alignment = PopupPositionProvider.Align.TopEnd,
            onDismissRequest = { showPopup = false },
            maxHeight = popupMaxHeight,
            minWidth = 236.dp,
        ) {
            ModelPickerPopupContent(
                state = state,
                expandedProviderIds = expandedProviderIds,
                onProviderExpandedChange = { providerId, expanded ->
                    expandedProviderIds = if (expanded) {
                        expandedProviderIds + providerId
                    } else {
                        expandedProviderIds - providerId
                    }
                },
                onModelSelected = { modelId ->
                    showPopup = false
                    onModelSelected(modelId)
                },
            )
        }
    }
}

@Composable
private fun ModelPickerPopupContent(
    state: AgentModelPickerUiState,
    expandedProviderIds: Set<String>,
    onProviderExpandedChange: (String, Boolean) -> Unit,
    onModelSelected: (String) -> Unit,
) {
    ListPopupColumn {
        state.providerGroups.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
            }
            val expanded = group.providerId in expandedProviderIds
            ModelProviderGroupHeader(
                name = group.providerName,
                expanded = expanded,
                onClick = {
                    onProviderExpandedChange(group.providerId, !expanded)
                },
            )
            if (expanded) {
                group.models.forEach { model ->
                    ModelPickerRow(
                        model = model,
                        selected = model.id == state.selectedModel?.id,
                        onClick = { onModelSelected(model.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelProviderGroupHeader(
    name: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "model_provider_arrow",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 14.dp, top = 11.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) {
                stringResource(R.string.model_collapse_provider, name)
            } else {
                stringResource(R.string.model_expand_provider, name)
            },
            modifier = Modifier
                .size(15.dp)
                .graphicsLayer { rotationZ = arrowRotation },
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
    }
}

@Composable
private fun ModelPickerRow(
    model: AgentModelOptionUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .squircleSurface(
                color = if (selected) {
                    MiuixTheme.colorScheme.surfaceContainerHigh
                } else {
                    Color.Transparent
                },
                cornerRadius = 12.dp,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = model.displayName,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.ui_current_model_a0af8f),
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

@Composable
internal fun AgentContextUsageButton(
    usage: AgentContextUsageUi,
    onCompact: () -> Unit = {},
    canCompact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val tooltipState = rememberTooltipState(isPersistent = true)
    val progress = usage.progress
    val progressColor = when {
        progress == null -> MiuixTheme.colorScheme.onSurfaceVariantActions
        progress >= 0.95f -> StatusError
        progress >= 0.80f -> StatusWarning
        else -> MiuixTheme.colorScheme.primary
    }
    val locale = LocalConfiguration.current.locales[0]
    val summary = formatContextUsage(
        usage = usage,
        noUsageText = stringResource(R.string.context_no_previous_usage),
        noLimitText = stringResource(R.string.context_no_model_limit),
        locale = locale,
    )
    val usageDescription = stringResource(
        R.string.context_usage_description,
        summary.replace('\n', ' '),
    )
    val tooltipColors = TooltipDefaults.richTooltipColors()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            RichTooltip(
                title = {
                    Text(
                        text = stringResource(R.string.ui_contextual_usage_d12810),
                        color = tooltipColors.titleContentColor,
                        style = MiuixTheme.textStyles.subtitle,
                        fontWeight = FontWeight.Bold,
                    )
                },
                action = if (canCompact) {
                    {
                        TextButton(
                            text = stringResource(R.string.context_compact_action),
                            onClick = {
                                onCompact()
                                tooltipState.dismiss()
                            },
                            minWidth = 0.dp,
                            minHeight = 34.dp,
                            cornerRadius = 17.dp,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                } else null,
                colors = tooltipColors,
            ) {
                ContextUsageTooltipDetails(
                    usage = usage,
                    locale = locale,
                    summary = summary,
                )
            }
        },
        state = tooltipState,
        focusable = true,
        modifier = modifier,
    ) {
        IconButton(
            onClick = { scope.launch { tooltipState.show() } },
            minWidth = ChatInputActionSize,
            minHeight = ChatInputActionSize,
        ) {
            CircularProgressIndicator(
                progress = progress ?: 0f,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = progressColor,
                    disabledForegroundColor = progressColor,
                    backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                ),
                strokeWidth = 2.5.dp,
                size = ChatInputActionIconSize,
                modifier = Modifier.semantics {
                    contentDescription = usageDescription
                },
            )
        }
    }
}

@Composable
private fun ContextUsageTooltipDetails(
    usage: AgentContextUsageUi,
    locale: Locale,
    summary: String,
) {
    val categories = listOf(
        Triple(R.string.context_instructions_tokens, usage.systemTokens, Color(0xFF8B5CF6)),
        Triple(R.string.context_fixed_tokens, usage.toolTokens, Color(0xFFF59E0B)),
        Triple(R.string.context_user_tokens, usage.userTokens, Color(0xFF388AF6)),
        Triple(R.string.context_agent_all_tokens, usage.assistantTokens, Color(0xFF10B981)),
    )
    val limit = usage.contextWindow?.takeIf { it > 0 }
    val used = usage.contextTokens
    val free = if (used != null && limit != null) (limit - used).coerceAtLeast(0) else null
    val hasComposition = categories.all { it.second != null }
    val freeColor = MiuixTheme.colorScheme.secondaryContainer
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.context_current_title), style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.SemiBold)
        Text(summary, style = MiuixTheme.textStyles.body2)
        if (hasComposition && used != null) {
            Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(freeColor)) {
                categories.forEach { (_, amount, color) ->
                    if (amount != null && amount > 0) Box(Modifier.weight(amount.toFloat()).height(10.dp).background(color))
                }
                if (free != null && free > 0) Box(Modifier.weight(free.toFloat()).height(10.dp).background(freeColor))
            }
            categories.forEach { (label, amount, color) ->
                UsageDetailRow(color, stringResource(label), amount ?: 0, used, locale)
            }
            if (free != null) UsageDetailRow(MiuixTheme.colorScheme.onSurfaceVariantActions,
                stringResource(R.string.context_free_tokens), free, limit ?: 0, locale)
            Text(stringResource(R.string.context_composition_estimated), style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        } else {
            Text(stringResource(R.string.context_usage_after_response), style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MiuixTheme.colorScheme.dividerLine))
        Text(stringResource(R.string.context_chat_totals_title), style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.SemiBold)
        TokenMetricRow(stringResource(R.string.context_request_count), usage.requestCount.toLong(), locale)
        TokenMetricRow(stringResource(R.string.context_measured_requests), usage.measuredRequests.toLong(), locale)
        TokenMetricRow(stringResource(R.string.context_failed_requests), usage.failedRequests.toLong(), locale)
        TokenMetricRow(stringResource(R.string.context_compaction_requests), usage.compactionRequests.toLong(), locale)
        TokenMetricRow(stringResource(R.string.context_input_tokens), usage.inputTokens, locale)
        TokenMetricRow(stringResource(R.string.context_output_tokens), usage.outputTokens, locale)
        TokenMetricRow(stringResource(R.string.context_reasoning_tokens), usage.reasoningTokens, locale)
        TokenMetricRow(stringResource(R.string.context_cached_tokens), usage.cachedTokens, locale)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            Text(stringResource(R.string.context_cache_percent), style = MiuixTheme.textStyles.body2)
            Text(usage.cachePercent?.let {
                java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(it) + "%"
            } ?: "—", color = StatusSuccess, style = MiuixTheme.textStyles.body2)
        }
        Text(stringResource(R.string.context_cache_formula), style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        if (usage.usageIncomplete || usage.cacheIncomplete) Text(stringResource(R.string.context_totals_incomplete),
            style = MiuixTheme.textStyles.footnote1, color = StatusWarning)
    }
}

@Composable
private fun TokenMetricRow(label: String, value: Long?, locale: Locale) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 8.dp), style = MiuixTheme.textStyles.body2)
        Text(value?.let { java.text.NumberFormat.getIntegerInstance(locale).format(it) } ?: "—",
            style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun UsageDetailRow(color: Color, label: String, tokens: Int, total: Int, locale: Locale) {
    val pct = if (total > 0) 100.0 * tokens / total else 0.0
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, modifier = Modifier.weight(1f).padding(end = 8.dp), style = MiuixTheme.textStyles.footnote1)
        val percent = java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(pct)
        Text("${java.text.NumberFormat.getIntegerInstance(locale).format(tokens)} · $percent%",
            style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun ModelBrandMark(
    modelId: String?,
    sourceType: String?,
    size: Dp,
) {
    val logo = modelOrProviderBrandLogoRes(modelId, sourceType)
    if (logo != null) {
        Image(
            painter = painterResource(logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(MiuixTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Dns,
                contentDescription = null,
                modifier = Modifier.size(size * 0.56f),
                tint = MiuixTheme.colorScheme.primary,
            )
        }
    }
}
