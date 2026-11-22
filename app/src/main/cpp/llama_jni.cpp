#include <jni.h>
#include <android/log.h>
#include <mutex>
#include <string>

#if USE_LLAMA_CPP
#include "llama.h"
#endif

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {
std::mutex g_engine_mutex;
std::string g_model_path;
bool g_initialized = false;

#if USE_LLAMA_CPP
llama_model* g_model = nullptr;
llama_context* g_ctx = nullptr;
#endif
}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_agentlab_ndkagent_NativeLlamaEngine_nativeInitModel(
    JNIEnv* env,
    jobject /* thiz */,
    jstring model_path,
    jint n_ctx) {
    const char* raw_path = env->GetStringUTFChars(model_path, nullptr);
    if (!raw_path) {
        return JNI_FALSE;
    }
    std::string path(raw_path);
    env->ReleaseStringUTFChars(model_path, raw_path);

    std::lock_guard<std::mutex> lock(g_engine_mutex);
#if USE_LLAMA_CPP
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    llama_backend_init();
    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(path.c_str(), mparams);
    if (!g_model) {
        g_initialized = false;
        return JNI_FALSE;
    }
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(n_ctx > 0 ? n_ctx : 2048);
    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        llama_model_free(g_model);
        g_model = nullptr;
        g_initialized = false;
        return JNI_FALSE;
    }
#endif
    g_model_path = path;
    g_initialized = true;
    LOGI("Initialized model: %s (USE_LLAMA_CPP=%d)", g_model_path.c_str(), USE_LLAMA_CPP);
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_agentlab_ndkagent_NativeLlamaEngine_nativeGenerate(
    JNIEnv* env,
    jobject /* thiz */,
    jstring prompt,
    jint max_tokens) {
    const char* raw_prompt = env->GetStringUTFChars(prompt, nullptr);
    if (!raw_prompt) {
        return env->NewStringUTF("ERROR: Null prompt");
    }
    std::string input(raw_prompt);
    env->ReleaseStringUTFChars(prompt, raw_prompt);

    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (!g_initialized) {
        return env->NewStringUTF("ERROR: Model not initialized. Call initModel() first.");
    }

#if USE_LLAMA_CPP
    // Tokenization & sampling loop wired here once llama.cpp submodule is attached.
    (void)max_tokens;
    std::string response = "[llama.cpp active: " + g_model_path + "] Prompt length=" +
                           std::to_string(input.size());
    return env->NewStringUTF(response.c_str());
#else
    (void)max_tokens;
    std::string stub =
        "{\"name\": \"list_notes\", \"arguments\": {}}";
    if (input.find("[TOOL RESULT]") != std::string::npos) {
        stub = "On-device NDK stub inference complete. Workspace checked via ToolBus.";
    }
    return env->NewStringUTF(stub.c_str());
#endif
}

JNIEXPORT void JNICALL
Java_com_agentlab_ndkagent_NativeLlamaEngine_nativeUnload(
    JNIEnv* /* env */,
    jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
#if USE_LLAMA_CPP
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    llama_backend_free();
#endif
    g_initialized = false;
    g_model_path.clear();
}

}  // extern "C"
