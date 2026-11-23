package com.agentlab.ndkagent

import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class ToolCall(
    val name: String,
    val arguments: Map<String, String>,
)

/**
 * Safe, path-jailed on-device tool bus for the local Kotlin agent.
 * Every file operation is canonicalized and confined inside [sandboxRoot].
 */
class ToolBus(private val sandboxRoot: File) {

    init {
        if (!sandboxRoot.exists()) {
            sandboxRoot.mkdirs()
        }
    }

    private val canonicalRoot: String by lazy { sandboxRoot.canonicalPath }

    fun safePath(relPath: String): File {
        require(relPath.isNotBlank()) { "Path cannot be blank" }
        val candidate = File(sandboxRoot, relPath)
        val canonicalTarget = candidate.canonicalPath
        val rootPrefix = if (canonicalRoot.endsWith(File.separator)) canonicalRoot else canonicalRoot + File.separator
        if (canonicalTarget != canonicalRoot && !canonicalTarget.startsWith(rootPrefix)) {
            throw SecurityException("Access denied: '$relPath' escapes sandbox")
        }
        return File(canonicalTarget)
    }

    fun execute(call: ToolCall): String = try {
        when (call.name) {
            "list_notes" -> {
                val files = sandboxRoot.listFiles()
                    ?.filter { it.isFile }
                    ?.map { it.name }
                    ?.sorted()
                    ?: emptyList()
                if (files.isEmpty()) "(no notes)" else files.joinToString("\n")
            }

            "read_note" -> {
                val path = call.arguments["path"] ?: return "ERROR: missing 'path'"
                val target = safePath(path)
                if (!target.exists()) "ERROR: '$path' does not exist"
                else target.readText(Charsets.UTF_8)
            }

            "write_note" -> {
                val path = call.arguments["path"] ?: return "ERROR: missing 'path'"
                val content = call.arguments["content"] ?: ""
                val target = safePath(path)
                target.parentFile?.mkdirs()
                target.writeText(content, Charsets.UTF_8)
                "Wrote ${content.length} chars to $path"
            }

            "device_info" -> {
                "os=${System.getProperty("os.name")} arch=${System.getProperty("os.arch")} cores=${Runtime.getRuntime().availableProcessors()}"
            }

            "current_time" -> {
                ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)
            }

            else -> "ERROR: Unknown tool '${call.name}'"
        }
    } catch (sec: SecurityException) {
        "ERROR: ${sec.message}"
    } catch (e: Exception) {
        "ERROR: ${e.message}"
    }

    companion object {
        private val NAME_RX = Regex(""""name"\s*:\s*"([^"]+)"""")
        private val ARGS_BLOCK_RX = Regex(""""arguments"\s*:\s*\{(.*)\}""", setOf(RegexOption.DOT_MATCHES_ALL))
        private val KV_RX = Regex(""""([^"]+)"\s*:\s*"((?:[^"\\]|\\.)*)"""")

        fun parseToolCall(text: String): ToolCall? {
            val trimmed = text.trim()
            if (!trimmed.startsWith("{") || !trimmed.contains("\"name\"")) return null
            val name = NAME_RX.find(trimmed)?.groupValues?.get(1) ?: return null
            val argsBody = ARGS_BLOCK_RX.find(trimmed)?.groupValues?.get(1).orEmpty()
            val args = mutableMapOf<String, String>()
            for (match in KV_RX.findAll(argsBody)) {
                val key = match.groupValues[1]
                val rawVal = match.groupValues[2]
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                args[key] = rawVal
            }
            return ToolCall(name = name, arguments = args)
        }
    }
}
