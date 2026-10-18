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
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val reasoningTokens: Int? = null,
    val cachedTokens: Int? = null,
    val userTokens: Int? = null,
    val assistantTokens: Int? = null,
    val toolTokens: Int? = null,
    val systemTokens: Int? = null,
) {
    val progress: Float?
        get() = contextUsageProgress(contextTokens, contextWindow)

    val cachePercent: Float?
        get() = if (inputTokens != null && inputTokens > 0 && cachedTokens != null && cachedTokens > 0) {
            ((cachedTokens.toFloat() / inputTokens.toFloat()) * 100f).coerceIn(0f, 100f)
        } else null
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
): AgentContextUsageUi {
    var latestUsage: TokenUsageUi? = null
    for (message in messages.asReversed()) {
        when (message) {
            is AgentMessageUi -> if (message.usage?.contextTokens != null) {
                latestUsage = message.usage
                break
            }
            is ThinkingMessageUi -> if (message.usage?.contextTokens != null) {
                latestUsage = message.usage
                break
            }
            is ToolActivityMessageUi -> if (message.usage?.contextTokens != null) {
                latestUsage = message.usage
                break
            }
            is SystemNoticeMessageUi -> if (message.code == SystemNoticeCode.ContextCompaction) {
                return AgentContextUsageUi(null, selectedModel?.contextWindow)
            }
            else -> Unit
        }
    }

    if (latestUsage == null || latestUsage.contextTokens == null) {
        return AgentContextUsageUi(null, selectedModel?.contextWindow)
    }

    var userChars = 0
    var assistantChars = 0
    var toolChars = 0

    messages.forEach { msg ->
        when (msg) {
            is UserMessageUi -> userChars += msg.content.length
            is AgentMessageUi -> assistantChars += msg.content.length
            is ThinkingMessageUi -> assistantChars += msg.content.length
            is ToolActivityMessageUi -> {
                toolChars += msg.argumentsSummary.length + (msg.resultSummary?.length ?: 0) + (msg.command?.length ?: 0)
            }
            else -> Unit
        }
    }

    val totalChars = (userChars + assistantChars + toolChars).coerceAtLeast(1)
    val totalContext = latestUsage.contextTokens
    val rawUserTokens = ((userChars.toDouble() / totalChars.toDouble()) * totalContext).toInt()
    val rawAssistantTokens = ((assistantChars.toDouble() / totalChars.toDouble()) * totalContext).toInt()
    val rawToolTokens = totalContext - rawUserTokens - rawAssistantTokens

    return AgentContextUsageUi(
        contextTokens = latestUsage.contextTokens,
        contextWindow = selectedModel?.contextWindow,
        inputTokens = latestUsage.inputTokens,
        outputTokens = latestUsage.outputTokens,
        reasoningTokens = latestUsage.reasoningTokens,
        cachedTokens = latestUsage.cachedTokens,
        userTokens = rawUserTokens.coerceAtLeast(0),
        assistantTokens = rawAssistantTokens.coerceAtLeast(0),
        toolTokens = rawToolTokens.coerceAtLeast(0),
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

internal fun formatCompactTokenCount(value: Int, locale: Locale = Locale.getDefault()): String {
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
