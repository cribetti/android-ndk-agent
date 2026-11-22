package com.agentlab.ndkagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

class MainActivity : ComponentActivity() {
    private val engine by lazy { NativeLlamaEngine() }
    private val toolBus by lazy { ToolBus(File(filesDir, "agent_notes")) }
    private val agentLoop by lazy { AgentLoop(engine, toolBus) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val defaultModel = File(filesDir, "models/qwen2.5-coder-1.5b-q4_k_m.gguf").absolutePath
        engine.initModel(defaultModel, contextSize = 2048)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AgentScreen(onSend = { prompt ->
                        agentLoop.run(prompt)
                    })
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
fun AgentScreen(onSend: (String) -> AgentResult) {
    var input by remember { mutableStateOf("") }
    val timeline = remember { mutableStateListOf<String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("On-Device NDK Agent (llama.cpp + JNI)", style = MaterialTheme.typography.titleMedium)

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
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask offline agent...") },
            )
            Button(onClick = {
                val prompt = input.trim()
                if (prompt.isNotEmpty()) {
                    input = ""
                    timeline.add("USER: $prompt")
                    val res = onSend(prompt)
                    res.steps.forEach { step ->
                        if (step.toolCall != null) {
                            timeline.add("TOOL[${step.toolCall.name}]: ${step.toolResult}")
                        }
                    }
                    timeline.add("AGENT: ${res.finalAnswer}")
                }
            }) {
                Text("Run")
            }
        }
    }
}
