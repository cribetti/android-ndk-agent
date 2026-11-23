# android-ndk-agent (Milestone 3)

An offline, zero-cloud Android AI agent runtime combining **Android NDK (`CMake` + `JNI` -> `libllama_jni.so`)**, **Kotlin Jetpack Compose UI**, and a **path-jailed Kotlin tool-calling loop**.

## Architecture
- `app/src/main/cpp/CMakeLists.txt` & `llama_jni.cpp` — C++17 JNI bridge with `std::mutex` state protection, `-O3` optimization, and 6-thread `llama.cpp` tokenization + sampling (`arm64-v8a` and `x86_64`).
- `app/src/main/java/com/agentlab/ndkagent/LlamaEngine.kt` — `InferenceEngine` abstraction + `NativeLlamaEngine` JNI bindings.
- `app/src/main/java/com/agentlab/ndkagent/ToolBus.kt` — Path-jailed (`canonicalPath`) on-device tools (`list_notes`, `read_note`, `write_note`, `device_info`) and JSON tool-call parser.
- `app/src/main/java/com/agentlab/ndkagent/AgentLoop.kt` — Multi-turn Qwen ChatML agent loop (`run`) with `seenCalls` duplicate-call guard.
- `app/src/main/java/com/agentlab/ndkagent/MainActivity.kt` — Async (`Dispatchers.IO`) Jetpack Compose Material 3 chat & tool-trace UI with edge-to-edge padding.

## Build & Verify
```bash
# If cloning fresh, initialize the llama.cpp submodule:
git submodule update --init --recursive --depth 1

# Run JVM unit tests (ToolBus path-jail + AgentLoop)
./gradlew testDebugUnitTest --stacktrace

# Build debug APK and install onto connected device
./gradlew assembleDebug
./gradlew installDebug
```

## Deploying a `.gguf` Model to Device
Push your quantized or LoRA-fused `.gguf` model (e.g. `qwen2.5-coder-1.5b-q4_k_m.gguf`) to the app's private `files/models/` directory:
```zsh
adb push qwen2.5-coder-1.5b-q4_k_m.gguf /data/local/tmp/
adb shell "run-as com.agentlab.ndkagent mkdir -p files/models && run-as com.agentlab.ndkagent cp /data/local/tmp/qwen2.5-coder-1.5b-q4_k_m.gguf files/models/qwen2.5-coder-1.5b-q4_k_m.gguf && rm -f /data/local/tmp/qwen2.5-coder-1.5b-q4_k_m.gguf"
```
