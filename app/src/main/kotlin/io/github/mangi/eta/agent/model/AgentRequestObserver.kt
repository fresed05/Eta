package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.runtime.AgentEvent
import io.github.mangi.eta.agent.runtime.AgentRunController
import io.github.mangi.eta.agent.runtime.AgentTokenUsage

/** Shared by chat, retries and compaction; streaming usage replaces a record, never increments totals. */
internal class AgentRequestObserver(
    private val delegate: AgentProviderClient,
    private val operationId: String,
    private val onEvent: (AgentEvent) -> Unit,
) : AgentProviderClient {
    override val id get() = delegate.id
    override val capabilities get() = delegate.capabilities
    private var sequence = 0L

    override fun complete(request: ProviderRequest, runController: AgentRunController, onEvent: (ProviderEvent) -> Unit): ProviderResponse {
        val composition = AgentContextComposition.measure(request)
        var record = AgentModelRequestRecord("$operationId-request-${++sequence}", request.purpose.name, composition = composition)
        fun publish() = this.onEvent(AgentEvent.RequestRecorded(record))
        publish()
        try {
            val response = delegate.complete(request, runController) { event ->
                if (event is ProviderEvent.Usage) {
                    val previous = record.usage
                    val usage = AgentTokenUsage(
                        contextTokens = event.usage.contextTokens ?: previous?.contextTokens,
                        inputTokens = event.usage.inputTokens ?: previous?.inputTokens,
                        outputTokens = event.usage.outputTokens ?: previous?.outputTokens,
                        reasoningTokens = event.usage.reasoningTokens ?: previous?.reasoningTokens,
                        cachedTokens = event.usage.cachedTokens ?: previous?.cachedTokens,
                    )
                    record = record.copy(usage = usage, composition = composition.calibrated(usage.inputTokens))
                    publish()
                }
                onEvent(event)
            }
            record = record.copy(status = "completed")
            publish()
            return response
        } catch (failure: Throwable) {
            record = record.copy(status = "failed")
            publish()
            throw failure
        }
    }
}
