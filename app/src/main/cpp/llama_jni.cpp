#include <jni.h>
#include <android/log.h>
#include <functional>
#include <mutex>
#include <string>
#include <vector>

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

std::string run_generation(
    const std::string& input,
    jint max_tokens,
    const std::function<void(const std::string&)>& on_piece) {
    if (!g_initialized) {
        return "ERROR: Model not initialized. Call initModel() first.";
    }

#if USE_LLAMA_CPP
    if (!g_model || !g_ctx) {
        return "ERROR: llama.cpp model/context not ready.";
    }

    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    if (!vocab) {
        return "ERROR: Failed to obtain model vocab.";
    }

    llama_memory_clear(llama_get_memory(g_ctx), true);

    const int n_prompt_estimate = -llama_tokenize(
        vocab, input.c_str(), static_cast<int32_t>(input.size()), nullptr, 0, true, true);
    if (n_prompt_estimate <= 0) {
        return "ERROR: Failed to tokenize prompt.";
    }

    std::vector<llama_token> prompt_tokens(static_cast<size_t>(n_prompt_estimate));
    const int n_tokens = llama_tokenize(
        vocab,
        input.c_str(),
        static_cast<int32_t>(input.size()),
        prompt_tokens.data(),
        static_cast<int32_t>(prompt_tokens.size()),
        true,
        true);
    if (n_tokens < 0) {
        return "ERROR: Tokenization buffer mismatch.";
    }
    prompt_tokens.resize(static_cast<size_t>(n_tokens));

    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()));
    if (llama_decode(g_ctx, batch) != 0) {
        return "ERROR: llama_decode failed on prompt.";
    }

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.1f));
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    std::string response;
    const int limit = (max_tokens > 0 && max_tokens < 160) ? max_tokens : 160;
    int generated = 0;
    for (int step = 0; step < limit; ++step) {
        llama_token next_tok = llama_sampler_sample(smpl, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, next_tok)) {
            break;
        }
        ++generated;

        char piece_buf[256];
        int piece_len = llama_token_to_piece(vocab, next_tok, piece_buf, sizeof(piece_buf), 0, true);
        if (piece_len > 0) {
            std::string piece(piece_buf, static_cast<size_t>(piece_len));
            response.append(piece);
            if (on_piece) {
                on_piece(piece);
            }
        }

        llama_batch next_batch = llama_batch_get_one(&next_tok, 1);
        if (llama_decode(g_ctx, next_batch) != 0) {
            break;
        }
    }

    LOGI("Generated %d tokens (prompt=%d)", generated, n_tokens);
    llama_sampler_free(smpl);
    return response;
#else
    (void)max_tokens;
    std::string stub = "{\"name\": \"list_notes\", \"arguments\": {}}";
    if (input.find("[TOOL RESULT]") != std::string::npos) {
        stub = "On-device NDK stub inference complete. Workspace checked via ToolBus.";
    }
    if (on_piece) {
        on_piece(stub);
    }
    return stub;
#endif
}
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
    cparams.n_ctx = static_cast<uint32_t>(n_ctx > 0 ? n_ctx : 1024);
    cparams.n_threads = 6;
    cparams.n_threads_batch = 6;
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
    std::string response = run_generation(input, max_tokens, nullptr);
    return env->NewStringUTF(response.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_agentlab_ndkagent_NativeLlamaEngine_nativeGenerateStream(
    JNIEnv* env,
    jobject /* thiz */,
    jstring prompt,
    jint max_tokens,
    jobject callback) {
    const char* raw_prompt = env->GetStringUTFChars(prompt, nullptr);
    if (!raw_prompt) {
        return env->NewStringUTF("ERROR: Null prompt");
    }
    std::string input(raw_prompt);
    env->ReleaseStringUTFChars(prompt, raw_prompt);

    jclass cb_cls = callback ? env->GetObjectClass(callback) : nullptr;
    jmethodID on_token_mid = cb_cls ? env->GetMethodID(cb_cls, "onToken", "(Ljava/lang/String;)V") : nullptr;

    std::lock_guard<std::mutex> lock(g_engine_mutex);
    std::string response = run_generation(input, max_tokens, [&](const std::string& piece) {
        if (!callback || !on_token_mid) {
            return;
        }
        jstring jpiece = env->NewStringUTF(piece.c_str());
        if (jpiece) {
            env->CallVoidMethod(callback, on_token_mid, jpiece);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
            env->DeleteLocalRef(jpiece);
        }
    });
    if (cb_cls) {
        env->DeleteLocalRef(cb_cls);
    }
    return env->NewStringUTF(response.c_str());
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
