package io.github.mangi.eta.agent.model

import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.floor

/** Estimates from the actual request, never from truncated/rendered chat bubbles. */
@Serializable
internal data class AgentContextComposition(
    val instructions: Int = 0,
    val toolsAndSkills: Int = 0,
    val user: Int = 0,
    val agent: Int = 0,
    val estimated: Boolean = true,
) {
    val total: Int get() = instructions + toolsAndSkills + user + agent

    fun calibrated(inputTokens: Int?): AgentContextComposition {
        val measured = inputTokens?.takeIf { it >= 0 } ?: return this
        val weights = listOf(instructions, toolsAndSkills, user, agent)
        val sum = weights.sumOf { it.toLong() }
        if (sum == 0L) return copy(agent = measured)
        val exact = weights.map { it.toDouble() * measured / sum }
        val allocated = exact.map { floor(it).toInt() }.toMutableList()
        val remainder = measured - allocated.sum()
        exact.indices.sortedByDescending { exact[it] - allocated[it] }.take(remainder).forEach { allocated[it]++ }
        return copy(instructions = allocated[0], toolsAndSkills = allocated[1], user = allocated[2], agent = allocated[3])
    }

    companion object {
        fun measure(request: ProviderRequest): AgentContextComposition {
            var instructions = 0
            var fixed = estimate(request.effectiveTools.toString()).takeIf { request.effectiveTools.length() > 0 } ?: 0
            var user = 0
            var agent = 0
            val skillResults = mutableSetOf<String>()
            for (i in 0 until request.messages.length()) {
                val message = request.messages.optJSONObject(i) ?: continue
                AgentConversationCodec.parseToolCalls(message).filter { it.name in setOf("skills_read", "skills_read_resource") }
                    .forEach { skillResults += it.id }
                val content = textContent(message.opt("content"))
                val cost = estimate(content) + estimate(message.optString("reasoning_content")) +
                    estimate(message.optJSONArray("tool_calls")?.toString().orEmpty()) + 4
                when {
                    message.optString("_eta_context_category") == "tools_skills" -> fixed += cost
                    message.optString("_eta_context_category") == "agent" -> agent += cost
                    message.optString("role") in setOf("system", "developer") -> instructions += cost
                    message.optString("role") == "tool" && message.optString("tool_call_id") in skillResults -> fixed += cost
                    message.optString("role") == "user" && !message.optBoolean("_eta_observation") -> user += cost
                    else -> agent += cost
                }
            }
            return AgentContextComposition(instructions, fixed, user, agent)
        }

        private fun textContent(value: Any?): String = when (value) {
            null, JSONObject.NULL -> ""
            is String -> value
            is JSONArray -> buildString {
                for (i in 0 until value.length()) {
                    val part = value.optJSONObject(i) ?: continue
                    // Base64 byte length is not a token count. Image cost remains provider-calibrated.
                    if (part.has("text")) append(part.optString("text"))
                }
            }
            else -> value.toString()
        }

        private fun estimate(text: String): Int {
            if (text.isEmpty()) return 0
            var units = 0.0
            var offset = 0
            while (offset < text.length) {
                val cp = text.codePointAt(offset)
                units += when {
                    cp < 128 -> 0.25
                    Character.UnicodeScript.of(cp) in setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HANGUL,
                        Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA) -> 1.0
                    else -> 0.6
                }
                offset += Character.charCount(cp)
            }
            return ceil(units).toInt()
        }
    }
}
