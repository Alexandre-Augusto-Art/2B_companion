package br.offgr1d.companion.llm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** LiteRT-LM (Google, Apache-2.0): a mesma API no Android e no Windows. */
class LiteRtEngine(private val threads: Int? = null) : LlmEngine {
    private var engine: Engine? = null

    override fun load(model: File, vision: Boolean, useGpu: Boolean, cacheDir: File) {
        close()
        cacheDir.mkdirs()
        Engine.setNativeMinLogSeverity(LogSeverity.ERROR) // o log nativo não pode guardar conversas
        engine = if (useGpu) {
            runCatching { start(model, vision, Backend.GPU(), cacheDir) }.getOrElse { start(model, vision, Backend.CPU(threadCount = threads), cacheDir) }
        } else {
            start(model, vision, Backend.CPU(threadCount = threads), cacheDir)
        }
    }

    private fun start(model: File, vision: Boolean, backend: Backend, cacheDir: File): Engine {
        val e = Engine(EngineConfig(
            modelPath = model.absolutePath,
            backend = backend,
            visionBackend = if (vision) backend else null,
            maxNumTokens = CONTEXT_TOKENS,
            maxNumImages = if (vision) 1 else null,
            cacheDir = cacheDir.absolutePath,
        ))
        try {
            e.initialize()
        } catch (t: Throwable) {
            runCatching { e.close() }
            throw t
        }
        return e
    }

    override fun generate(request: LlmRequest): Flow<String> = flow {
        val e = engine ?: error("modelo não carregado")
        val conversation = e.createConversation(ConversationConfig(
            systemInstruction = Contents.of(NativeText.safe(request.system)),
            initialMessages = request.history.map { if (it.fromUser) Message.user(NativeText.safe(it.text)) else Message.model(NativeText.safe(it.text)) },
            samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = request.temperature),
            automaticToolCalling = false,
            maxOutputToken = request.maxTokens,
            thinkingConfig = ThinkingConfig(enableThinking = false),
        ))
        var finished = false
        try {
            val image = request.image
            val user = NativeText.safe(request.user)
            val contents = if (image != null) Contents.of(Content.ImageBytes(image), Content.Text(user))
            else Contents.of(user)
            conversation.sendMessageAsync(contents).collect { m ->
                val piece = m.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                if (piece.isNotEmpty()) emit(piece)
            }
            finished = true
        } finally {
            if (!finished) runCatching { conversation.cancelProcess() }
            conversation.close()
        }
    }.flowOn(Dispatchers.IO)

    override fun close() {
        engine?.close()
        engine = null
    }

    companion object { const val CONTEXT_TOKENS = 4096 }
}
