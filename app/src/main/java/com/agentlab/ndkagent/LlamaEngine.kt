package com.agentlab.ndkagent

interface InferenceEngine {
    fun initModel(modelPath: String, contextSize: Int = 2048): Boolean
    fun generate(prompt: String, maxTokens: Int = 512): String
    fun unload()
}

/**
 * JNI-backed inference engine loading libllama_jni.so compiled via Android NDK + CMake.
 */
class NativeLlamaEngine : InferenceEngine {
    companion object {
        init {
            System.loadLibrary("llama_jni")
        }
    }

    private external fun nativeInitModel(modelPath: String, nCtx: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String
    private external fun nativeUnload()

    override fun initModel(modelPath: String, contextSize: Int): Boolean =
        nativeInitModel(modelPath, contextSize)

    override fun generate(prompt: String, maxTokens: Int): String =
        nativeGenerate(prompt, maxTokens)

    override fun unload() = nativeUnload()
}
