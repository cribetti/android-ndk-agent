import com.agentlab.ndkagent.ToolBus
import org.junit.Test

class AgentLoopTest {
    @Test
    fun testClearNotes() {
        val toolBus = ToolBus(File("/path/to/sandbox"))
        assertTrue(toolBus.clear_notes())
        val sandboxRoot = File("/path/to/sandbox")
        val toolBus = ToolBus(sandboxRoot)
    }
}
