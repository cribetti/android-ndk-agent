# AGENTS.md — `android-ndk-agent`

Project rules and architecture reference for AI agents working in this repository.

## 1. Architecture
- **Native C++ / JNI (`app/src/main/cpp/`):**
  - `CMakeLists.txt` builds `libllama_jni.so`. If `app/src/main/cpp/third_party/llama.cpp/CMakeLists.txt` exists, it links `llama.cpp` (`USE_LLAMA_CPP=1`); otherwise it compiles in offline stub mode (`USE_LLAMA_CPP=0`).
  - `llama_jni.cpp` exposes `nativeInitModel`, `nativeGenerate`, and `nativeUnload` guarded by `std::mutex`.
- **Kotlin Agent Runtime (`app/src/main/java/com/agentlab/ndkagent/`):**
  - `LlamaEngine.kt`: `InferenceEngine` interface + `NativeLlamaEngine` JNI wrapper.
  - `ToolBus.kt`: Path-jailed on-device tool executor (`list_notes`, `read_note`, `write_note`, `device_info`) using `canonicalPath` jail checks.
  - `AgentLoop.kt`: Multi-turn tool-calling loop (`maxTurns = 5`).
  - `MainActivity.kt`: Jetpack Compose Material 3 UI.

## 2. Security Rules
- All file tools in `ToolBus.kt` must resolve `canonicalPath` via `safePath()` and reject any `../` or symlink escaping `sandboxRoot`.
- Never execute shell commands or untrusted reflection from model output.

## 3. Verification
```bash
./gradlew testDebugUnitTest --stacktrace
```
