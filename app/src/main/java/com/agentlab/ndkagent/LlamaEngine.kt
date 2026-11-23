package com.agentlab.ndkagent

interface InferenceEngine {
    fun initModel(modelPath: String, contextSize: Int = 2048): Boolean
    fun generate(prompt: String, maxTokens: Int = 512): String
    fun generateStream(prompt: String, maxTokens: Int = 512, onToken: (String) -> Unit): String =
        generate(prompt, maxTokens).also { onToken(it) }
    fun unload()
}

/**
 * JNI-backed inference engine loading libllama_jni.so compiled via Android NDK + CMake.
 */
class NativeLlamaEngine : InferenceEngine {
    interface TokenCallback {
        fun onToken(token: String)
    }

    companion object {
        init {
            System.loadLibrary("llama_jni")
        }
    }

    private external fun nativeInitModel(modelPath: String, nCtx: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String
    private external fun nativeGenerateStream(
        prompt: String,
        maxTokens: Int,
        callback: TokenCallback,
    ): String
    private external fun nativeUnload()

    override fun initModel(modelPath: String, contextSize: Int): Boolean =
        nativeInitModel(modelPath, contextSize)

    override fun generate(prompt: String, maxTokens: Int): String =
        nativeGenerate(prompt, maxTokens)

    override fun generateStream(
        prompt: String,
        maxTokens: Int,
        onToken: (String) -> Unit,
    ): String = nativeGenerateStream(
        prompt,
        maxTokens,
        object : TokenCallback {
            override fun onToken(token: String) {
                onToken(token)
            }
        },
    )

    override fun unload() = nativeUnload()
}
