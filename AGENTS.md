# AGENTS.md — `android-ndk-agent`

Project rules and architecture reference for AI agents working in this repository.

## 1. Architecture
- **Native C++ / JNI (`app/src/main/cpp/`):**
  - `CMakeLists.txt` builds `libllama_jni.so`. If `app/src/main/cpp/third_party/llama.cpp/CMakeLists.txt` exists, it links `llama.cpp` with `-O3` (`USE_LLAMA_CPP=1`, `LLAMA_CURL=OFF`, `GGML_OPENMP=OFF`); otherwise it compiles in offline stub mode (`USE_LLAMA_CPP=0`).
  - `llama_jni.cpp` exposes `nativeInitModel`, `nativeGenerate`, `nativeGenerateStream` (live token callback), and `nativeUnload` guarded by `std::mutex`, using `n_threads = 6`, `n_threads_batch = 6`, KV cache clearing (`llama_memory_clear`), and `temp=0.1f` + greedy sampling.
- **Kotlin Agent Runtime (`app/src/main/java/com/agentlab/ndkagent/`):**
  - `LlamaEngine.kt`: `InferenceEngine` interface + `NativeLlamaEngine` JNI wrapper (with `generateStream`).
  - `ToolBus.kt`: Path-jailed on-device tool executor (`list_notes`, `read_note`, `write_note`, `device_info`, `current_time`) using `canonicalPath` jail checks (rejecting `../`, symlinks, and leading `/`).
  - `AgentLoop.kt`: Multi-turn Qwen ChatML (`<|im_start|>...<|im_end|>`) tool-calling loop (`maxTurns = 2`) with `seenCalls` duplicate-tool-call guard and tool-result fallback.
  - `MainActivity.kt`: Async Jetpack Compose Material 3 UI (`Dispatchers.IO` for model load/inference, live token streaming card, `.systemBarsPadding()` + `.imePadding()`, notes stored in `getExternalFilesDir("agent_notes")`).

## 2. Security Rules
- All file tools in `ToolBus.kt` must resolve `canonicalPath` via `safePath()` and reject any `../` or symlink escaping `sandboxRoot`.
- Never execute shell commands or untrusted reflection from model output.

## 3. Verification
```bash
./gradlew testDebugUnitTest --stacktrace
```

## Android / ADB Environment & Rules

- **ADB Binary:** `/Users/chris/Library/Android/sdk/platform-tools/adb`
- **Target Device (Samsung S21 Ultra):** `adb-R5CR31AKG2H-DuyMYf._adb-tls-connect._tcp`

### Mandatory Rules
1. **NEVER run `adb kill-server` or `adb start-server`.** The ADB server daemon is managed externally as a persistent macOS `launchd` service (`com.android.adb.server`) to preserve macOS Local Network permissions outside VS Code. Killing it breaks wireless connectivity.
2. **Always target the device by its mDNS ID** using `-s adb-R5CR31AKG2H-DuyMYf._adb-tls-connect._tcp` (e.g., `adb -s adb-R5CR31AKG2H-DuyMYf._adb-tls-connect._tcp shell ...`). Never hardcode `192.168.1.145:<port>` because Android randomizes wireless debugging ports, and using `-s` avoids `more than one device/emulator` errors.
3. **Reconnect via mDNS if offline:** If the device shows as `offline` or missing in `adb devices`, run:
   ```bash
   /Users/chris/Library/Android/sdk/platform-tools/adb disconnect
   /Users/chris/Library/Android/sdk/platform-tools/adb connect adb-R5CR31AKG2H-DuyMYf._adb-tls-connect._tcp
   ```
4. **Do not initiate `adb pair`** unless explicitly instructed by the user—the host is already paired via `~/.android/adbkey`.
