package com.agentlab.ndkagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val engine by lazy { NativeLlamaEngine() }
    private val toolBus by lazy {
        ToolBus(getExternalFilesDir("agent_notes") ?: File(filesDir, "agent_notes"))
    }
    private val agentLoop by lazy { AgentLoop(engine, toolBus) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modelFile = File(filesDir, "models/qwen2.5-coder-1.5b-q4_k_m.gguf")

        setContent {
            var modelStatus by remember { mutableStateOf("Loading ${modelFile.name}...") }

            LaunchedEffect(modelFile.absolutePath) {
                modelStatus = withContext(Dispatchers.IO) {
                    if (!modelFile.exists()) {
                        "Model missing: push .gguf to files/models/${modelFile.name}"
                    } else if (engine.initModel(modelFile.absolutePath, contextSize = 2048)) {
                        "Model ready: ${modelFile.name}"
                    } else {
                        "Failed to load ${modelFile.name}"
                    }
                }
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AgentScreen(
                        modelStatus = modelStatus,
                        onSend = { prompt ->
                            withContext(Dispatchers.IO) {
                                agentLoop.run(prompt)
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        engine.unload()
        super.onDestroy()
    }
}

@Composable
fun AgentScreen(
    modelStatus: String,
    onSend: suspend (String) -> AgentResult,
) {
    var input by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    val timeline = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("On-Device NDK Agent (llama.cpp + JNI)", style = MaterialTheme.typography.titleMedium)
        Text(modelStatus, style = MaterialTheme.typography.bodySmall)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(timeline) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(entry, modifier = Modifier.padding(12.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                enabled = !isRunning,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask offline agent...") },
            )
            Button(
                enabled = !isRunning && input.isNotBlank(),
                onClick = {
                    val prompt = input.trim()
                    if (prompt.isNotEmpty()) {
                        input = ""
                        isRunning = true
                        timeline.add("USER: $prompt")
                        scope.launch {
                            try {
                                val res = onSend(prompt)
                                res.steps.forEach { step ->
                                    if (step.toolCall != null) {
                                        timeline.add("TOOL[${step.toolCall.name}]: ${step.toolResult}")
                                    }
                                }
                                timeline.add("AGENT: ${res.finalAnswer}")
                            } finally {
                                isRunning = false
                            }
                        }
                    }
                },
            ) {
                Text(if (isRunning) "Running..." else "Run")
            }
        }
    }
}
