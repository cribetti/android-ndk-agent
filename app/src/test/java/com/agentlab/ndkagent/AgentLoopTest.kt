package com.agentlab.ndkagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

private class FakeEngine(private val scriptedOutputs: List<String>) : InferenceEngine {
    private var idx = 0
    override fun initModel(modelPath: String, contextSize: Int): Boolean = true
    override fun generate(prompt: String, maxTokens: Int): String {
        val out = scriptedOutputs.getOrElse(idx) { "Done." }
        idx++
        return out
    }
    override fun unload() {}
}

class AgentLoopTest {
    @Test
    fun toolBusBlocksPathTraversal() {
        val root = Files.createTempDirectory("ndk-agent-jail").toFile()
        try {
            val bus = ToolBus(root)
            val result = bus.execute(ToolCall("read_note", mapOf("path" to "../../etc/passwd")))
            assertTrue("Expected access denied, got: $result", result.contains("Access denied"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun toolBusWriteAndReadNoteInsideSandbox() {
        val root = Files.createTempDirectory("ndk-agent-rw").toFile()
        try {
            val bus = ToolBus(root)
            val writeOut = bus.execute(
                ToolCall("write_note", mapOf("path" to "todo.txt", "content" to "buy milk"))
            )
            assertTrue(writeOut.startsWith("Wrote 8 chars"))
            assertEquals("buy milk", bus.execute(ToolCall("read_note", mapOf("path" to "todo.txt"))))
            assertEquals("todo.txt", bus.execute(ToolCall("list_notes", emptyMap())))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun agentLoopExecutesToolCallThenReturnsFinalAnswer() {
        val root = Files.createTempDirectory("ndk-agent-loop").toFile()
        try {
            val bus = ToolBus(root)
            val engine = FakeEngine(
                listOf(
                    """{"name": "write_note", "arguments": {"path": "status.txt", "content": "ok"}}""",
                    "Saved status.txt cleanly.",
                )
            )
            val loop = AgentLoop(engine, bus, maxTurns = 4)
            val res = loop.run("Save ok to status.txt")

            assertEquals("Saved status.txt cleanly.", res.finalAnswer)
            assertEquals(2, res.steps.size)
            assertEquals("write_note", res.steps[0].toolCall?.name)
            assertTrue(File(root, "status.txt").exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
