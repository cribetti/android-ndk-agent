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
    private val maxTurns: Int = 5,
) {
    fun run(userPrompt: String): AgentResult {
        val history = StringBuilder()
        history.appendLine("SYSTEM: You are an offline Android systems assistant.")
        history.appendLine("TOOLS: list_notes(), read_note(path), write_note(path, content), device_info().")
        history.appendLine("Reply with a single JSON object {\"name\": ..., \"arguments\": {...}} to call a tool, or plain text for the final answer.")
        history.appendLine("USER: $userPrompt")

        val steps = mutableListOf<AgentStep>()
        for (turn in 1..maxTurns) {
            val output = engine.generate(history.toString()).trim()
            val call = ToolBus.parseToolCall(output)
            if (call == null) {
                steps.add(AgentStep(turn = turn, modelOutput = output))
                return AgentResult(finalAnswer = output, steps = steps)
            }
            val result = toolBus.execute(call)
            steps.add(AgentStep(turn = turn, modelOutput = output, toolCall = call, toolResult = result))
            history.appendLine("ASSISTANT: $output")
            history.appendLine("[TOOL RESULT]: $result")
        }

        val fallback = "Max turns ($maxTurns) reached without final answer."
        return AgentResult(finalAnswer = fallback, steps = steps)
    }
}
