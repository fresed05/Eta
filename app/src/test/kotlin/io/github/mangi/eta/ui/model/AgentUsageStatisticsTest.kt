package io.github.mangi.eta.ui.model

import io.github.mangi.eta.agent.model.AgentContextComposition
import io.github.mangi.eta.agent.model.AgentModelRequestRecord
import io.github.mangi.eta.agent.model.AgentSessionState
import io.github.mangi.eta.agent.runtime.AgentTokenUsage
import org.junit.Assert.*
import org.junit.Test

class AgentUsageStatisticsTest {
    @Test fun totalsAreWeightedAcrossRequestsNotPercentAveragesOrChatBlocks() {
        val first = AgentModelRequestRecord("a", "CHAT", "completed", AgentTokenUsage(inputTokens = 100, outputTokens = 10, cachedTokens = 90))
        val second = AgentModelRequestRecord("b", "CHAT", "completed", AgentTokenUsage(inputTokens = 900, outputTokens = 100, reasoningTokens = 30, cachedTokens = 90),
            AgentContextComposition(100, 200, 250, 350))
        var session = AgentSessionState().withRequest(first).withRequest(second)
        session = session.withRequest(second) // Replayed event must not charge twice.
        val messages = listOf(AgentMessageUi("one", "answer", usage = TokenUsageUi(inputTokens = 999999)),
            ThinkingMessageUi("thinking", "reasoning", false, usage = TokenUsageUi(inputTokens = 999999)))
        val usage = latestContextUsage(messages, null, session)
        assertEquals(2, usage.requestCount)
        assertEquals(1000L, usage.inputTokens)
        assertEquals(110L, usage.outputTokens)
        assertEquals(30L, usage.reasoningTokens)
        assertEquals(180L, usage.cachedTokens)
        assertEquals(18f, usage.cachePercent!!, 0.001f)
        assertEquals(900, usage.contextTokens)
        assertEquals(200, usage.toolTokens)
        assertEquals(350, usage.assistantTokens)
    }

    @Test fun compactionInvalidatesOnlyOccupancyNotAccumulatedUsage() {
        val record = AgentModelRequestRecord("a", "CHAT", "completed", AgentTokenUsage(inputTokens = 500, outputTokens = 50, cachedTokens = 0))
        val session = AgentSessionState().withRequest(record).copy(contextInvalidated = true)
        val usage = latestContextUsage(emptyList(), null, session)
        assertNull(usage.contextTokens)
        assertEquals(500L, usage.inputTokens)
        assertEquals(50L, usage.outputTokens)
        assertEquals(0f, usage.cachePercent!!, 0f)
        assertEquals(session, AgentSessionState.decode(session.encode()))
    }

    @Test fun missingCacheDoesNotBecomeFakeZeroAndTotalsUseLong() {
        val first = AgentModelRequestRecord("a", "CHAT", "completed", AgentTokenUsage(inputTokens = Int.MAX_VALUE, outputTokens = 1, cachedTokens = 20))
        val second = first.copy(id = "b", usage = first.usage!!.copy(cachedTokens = null))
        val usage = latestContextUsage(emptyList(), null, AgentSessionState().withRequest(first).withRequest(second))
        assertEquals(2L * Int.MAX_VALUE, usage.inputTokens)
        assertNull(usage.cachePercent)
        assertTrue(usage.cacheIncomplete)
    }
}
