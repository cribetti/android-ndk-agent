# android-ndk-agent (Milestone 3)

An offline, zero-cloud Android AI agent runtime combining **Android NDK (`CMake` + `JNI` -> `libllama_jni.so`)**, **Kotlin Jetpack Compose UI**, and a **path-jailed Kotlin tool-calling loop**.

## Architecture
- `app/src/main/cpp/CMakeLists.txt` & `llama_jni.cpp` — C++17 JNI bridge with `std::mutex` state protection. Automatically links `llama.cpp` if present at `app/src/main/cpp/third_party/llama.cpp`, or builds cleanly in offline stub mode.
- `app/src/main/java/com/agentlab/ndkagent/LlamaEngine.kt` — `InferenceEngine` abstraction + `NativeLlamaEngine` JNI bindings.
- `app/src/main/java/com/agentlab/ndkagent/ToolBus.kt` — Path-jailed (`canonicalPath`) on-device tools (`list_notes`, `read_note`, `write_note`, `device_info`) and JSON tool-call parser.
- `app/src/main/java/com/agentlab/ndkagent/AgentLoop.kt` — Multi-turn agent loop (`run`) routing tool calls until final answer or turn cap.
- `app/src/main/java/com/agentlab/ndkagent/MainActivity.kt` — Jetpack Compose Material 3 chat & tool-trace UI.

## Build & Verify
```bash
# Run JVM unit tests (ToolBus path-jail + AgentLoop)
./gradlew testDebugUnitTest --stacktrace

# Build debug APK (requires Android SDK + NDK)
./gradlew assembleDebug
```

## Attaching `llama.cpp` for Real `.gguf` Inference
```bash
git init
git submodule add https://github.com/ggerganov/llama.cpp app/src/main/cpp/third_party/llama.cpp
```
Then push a quantized `.gguf` model (e.g. `qwen2.5-coder-1.5b-q4_k_m.gguf`) to the app's `files/models/` directory.
