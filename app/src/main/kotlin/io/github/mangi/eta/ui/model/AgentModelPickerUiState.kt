package io.github.mangi.eta.ui.model

import androidx.compose.runtime.Immutable
import io.github.mangi.eta.data.model.Model
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.provider.ProviderSourceRegistry
import java.text.NumberFormat
import java.util.Locale

@Immutable
internal data class AgentModelPickerUiState(
    val providerGroups: List<AgentModelProviderGroupUi> = emptyList(),
    val selectedModel: AgentModelOptionUi? = null,
    val isChanging: Boolean = false,
)

@Immutable
internal data class AgentModelProviderGroupUi(
    val providerId: String,
    val providerName: String,
    val providerSourceType: String,
    val models: List<AgentModelOptionUi>,
)

@Immutable
internal data class AgentModelOptionUi(
    val id: String,
    val providerId: String,
    val providerName: String,
    val providerSourceType: String,
    val modelId: String,
    val displayName: String,
    val contextWindow: Int?,
)

@Immutable
internal data class AgentContextUsageUi(
    val contextTokens: Int?,
    val contextWindow: Int?,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val reasoningTokens: Long? = null,
    val cachedTokens: Long? = null,
    val userTokens: Int? = null,
    val assistantTokens: Int? = null,
    val toolTokens: Int? = null,
    val systemTokens: Int? = null,
    val requestCount: Int = 0,
    val measuredRequests: Int = 0,
    val failedRequests: Int = 0,
    val compactionRequests: Int = 0,
    val usageIncomplete: Boolean = false,
    val cacheIncomplete: Boolean = false,
    val compositionEstimated: Boolean = true,
) {
    val progress: Float? get() = contextUsageProgress(contextTokens, contextWindow)
    // Cache is a subset of input. Output and reasoning must never enter this denominator.
    val cachePercent: Float? get() = if (inputTokens != null && inputTokens > 0 && cachedTokens != null && !cacheIncomplete)
        (100.0 * cachedTokens / inputTokens).toFloat().coerceIn(0f, 100f) else null
}

internal object AgentModelPickerProjector {
    fun project(
        providers: List<ProviderSetting>,
        selectedProviderId: String?,
        selectedModelId: String?,
    ): AgentModelPickerUiState {
        val enabledProviders = providers
            .asSequence()
            .filter(ProviderSetting::isEnabled)
            .sortedBy(ProviderSetting::sortOrder)
            .toList()
        val selectedProvider = enabledProviders.firstOrNull { it.id == selectedProviderId }
        val selectedModel = selectedProvider
            ?.models
            ?.firstOrNull { it.id == selectedModelId && it.isEnabled }
            ?.let { model -> selectedProvider.toOption(model) }
            ?: enabledProviders.asSequence()
                .flatMap { provider ->
                    provider.models.asSequence()
                        .filter { it.isEnabled }
                        .map { model -> provider.toOption(model) }
                }
                .firstOrNull { it.id == selectedModelId }
        val groups = enabledProviders
            .asSequence()
            .filter { it.apiKey.isNotBlank() }
            .mapNotNull { provider ->
                val sourceType = ProviderSourceRegistry.resolve(provider)
                val models = provider.models
                    .asSequence()
                    .filter { it.isEnabled }
                    .sortedBy { it.sortOrder }
                    .map { model -> provider.toOption(model) }
                    .toList()
                models.takeIf(List<*>::isNotEmpty)?.let {
                    AgentModelProviderGroupUi(
                        providerId = provider.id,
                        providerName = provider.name,
                        providerSourceType = sourceType,
                        models = models,
                    )
                }
            }
            .toList()
        return AgentModelPickerUiState(
            providerGroups = groups,
            selectedModel = selectedModel,
        )
    }

    private fun ProviderSetting.toOption(model: Model): AgentModelOptionUi =
        AgentModelOptionUi(
            id = model.id,
            providerId = id,
            providerName = name,
            providerSourceType = ProviderSourceRegistry.resolve(this),
            modelId = model.modelId,
            displayName = model.displayName.ifBlank { model.modelId },
            contextWindow = model.effectiveContextWindow,
        )
}

internal fun defaultExpandedModelProviderIds(selectedModel: AgentModelOptionUi?): Set<String> =
    selectedModel?.providerId?.let(::setOf).orEmpty()

internal fun latestContextUsage(
    messages: List<AgentChatMessageUi>,
    selectedModel: AgentModelOptionUi?,
    session: io.github.mangi.eta.agent.model.AgentSessionState = io.github.mangi.eta.agent.model.AgentSessionState(),
): AgentContextUsageUi {
    val requests = session.requests
    val latest = requests.lastOrNull { it.purpose == "CHAT" }
    val composition = latest?.composition?.takeUnless { session.contextInvalidated }
    val latestInput = latest?.usage?.inputTokens
    // Compatibility only: old usage was copied to several UI blocks, so it cannot be safely summed.
    var legacyContext: Int? = null
    if (requests.isEmpty() && !session.contextInvalidated) {
        for (message in messages.asReversed()) {
            if (message is SystemNoticeMessageUi && message.code == SystemNoticeCode.ContextCompaction) break
            val usage = when (message) {
                is AgentMessageUi -> message.usage
                is ThinkingMessageUi -> message.usage
                is ToolActivityMessageUi -> message.usage
                else -> null
            }
            legacyContext = usage?.inputTokens ?: usage?.contextTokens
            if (legacyContext != null) break
        }
    }
    fun sum(selector: (io.github.mangi.eta.agent.runtime.AgentTokenUsage) -> Int?): Long? {
        val values = requests.mapNotNull { it.usage?.let(selector) }
        return if (values.isEmpty()) null else values.sumOf { it.coerceAtLeast(0).toLong() }
    }
    return AgentContextUsageUi(
        contextTokens = if (session.contextInvalidated) null else latestInput ?: composition?.total ?: legacyContext,
        contextWindow = selectedModel?.contextWindow,
        inputTokens = sum { it.inputTokens },
        outputTokens = sum { it.outputTokens },
        reasoningTokens = sum { it.reasoningTokens },
        cachedTokens = sum { it.cachedTokens },
        systemTokens = composition?.instructions,
        toolTokens = composition?.toolsAndSkills,
        userTokens = composition?.user,
        assistantTokens = composition?.agent,
        requestCount = requests.size,
        measuredRequests = requests.count { it.usage?.inputTokens != null && it.usage.outputTokens != null },
        failedRequests = requests.count { it.status == "failed" },
        compactionRequests = requests.count { it.purpose == "COMPACTION" },
        usageIncomplete = session.legacyUsageIncomplete || requests.any { it.usage?.inputTokens == null || it.usage.outputTokens == null },
        cacheIncomplete = session.legacyUsageIncomplete || requests.any { it.usage?.cachedTokens == null || it.usage.inputTokens == null },
    )
}

internal fun contextUsageProgress(contextTokens: Int?, contextWindow: Int?): Float? {
    if (contextTokens == null || contextTokens < 0 || contextWindow == null || contextWindow <= 0) {
        return null
    }
    return (contextTokens.toFloat() / contextWindow.toFloat()).coerceIn(0f, 1f)
}

internal fun formatContextUsage(
    usage: AgentContextUsageUi,
    noUsageText: String = "No usage data yet",
    noLimitText: String = "Context window unknown, so automatic compaction is off",
    locale: Locale = Locale.getDefault(),
): String = when {
    usage.contextWindow == null || usage.contextWindow <= 0 -> noLimitText
    usage.contextTokens == null -> noUsageText
    else -> {
        val percent = usage.contextTokens.toDouble() / usage.contextWindow.toDouble() * 100.0
        val percentFormat = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
        "${formatCompactTokenCount(usage.contextTokens, locale)} / " +
            "${formatCompactTokenCount(usage.contextWindow, locale)} tokens · " +
            "${percentFormat.format(percent)}%"
    }
}

internal fun formatCompactTokenCount(value: Int, locale: Locale = Locale.getDefault()): String =
    formatCompactTokenCount(value.toLong(), locale)

internal fun formatCompactTokenCount(value: Long, locale: Locale = Locale.getDefault()): String {
    val absolute = kotlin.math.abs(value.toLong())
    val divisor = when {
        absolute >= 1_000_000 -> 1_000_000.0
        absolute >= 1_000 -> 1_000.0
        else -> return NumberFormat.getIntegerInstance(locale).format(value)
    }
    val suffix = if (divisor == 1_000_000.0) "M" else "K"
    val formatted = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
        isGroupingUsed = false
    }.format(value / divisor)
    return "$formatted$suffix"
}
