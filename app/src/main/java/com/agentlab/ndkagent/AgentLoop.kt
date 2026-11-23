package com.agentlab.ndkagent

data class AgentStep(
    val turn: Int,
    val modelOutput: String,
    val toolCall: ToolCall? = null,
    val toolResult: String? = null,
)

data class AgentResult(
    val finalAnswer: String,
    val steps: List<AgentStep>,
)

/**
 * Multi-turn on-device agent loop pairing an [InferenceEngine] with a path-jailed [ToolBus].
 */
class AgentLoop(
    private val engine: InferenceEngine,
    private val toolBus: ToolBus,
    private val maxTurns: Int = 4,
) {
    fun run(
        userPrompt: String,
        onToken: (String) -> Unit = {},
    ): AgentResult {
        val history = StringBuilder()
        history.append("<|im_start|>system\n")
        history.append("You are an offline Android systems assistant.\n")
        history.append("TOOLS: list_notes(), read_note(path), write_note(path, content), device_info(), current_time().\n")
        history.append("Reply with ONLY a JSON object {\"name\": ..., \"arguments\": {...}} to call a tool, or plain text for the final answer.")
        history.append("<|im_end|>\n")
        history.append("<|im_start|>user\n$userPrompt<|im_end|>\n")
        history.append("<|im_start|>assistant\n")

        val steps = mutableListOf<AgentStep>()
        val seenCalls = mutableSetOf<ToolCall>()
        for (turn in 1..maxTurns) {
            val output = engine.generateStream(history.toString(), maxTokens = 128, onToken = onToken).trim()
            val call = ToolBus.parseToolCall(output)
            if (call == null) {
                steps.add(AgentStep(turn = turn, modelOutput = output))
                return AgentResult(finalAnswer = output, steps = steps)
            }
            if (!seenCalls.add(call) && steps.isNotEmpty()) {
                val prevResult = steps.last().toolResult ?: "(no output)"
                return AgentResult(finalAnswer = prevResult, steps = steps)
            }
            val result = toolBus.execute(call)
            steps.add(AgentStep(turn = turn, modelOutput = output, toolCall = call, toolResult = result))
            history.append("$output<|im_end|>\n")
            history.append("<|im_start|>user\n[TOOL RESULT]: $result\nAnswer the user in plain text now. Do not call the same tool again.<|im_end|>\n")
            history.append("<|im_start|>assistant\n")
        }

        val fallback = steps.lastOrNull()?.toolResult
            ?: "Max turns ($maxTurns) reached without final answer."
        return AgentResult(finalAnswer = fallback, steps = steps)
    }
}
