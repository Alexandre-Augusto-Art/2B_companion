package br.offgr1d.companion.llm

import java.io.File
import kotlinx.coroutines.flow.Flow

data class Turn(val fromUser: Boolean, val text: String)

class LlmRequest(
    val system: String,
    val history: List<Turn>,
    val user: String,
    val image: ByteArray? = null,
    val maxTokens: Int = 700,
    val temperature: Double = 0.6,
)

/**
 * O LiteRT-LM 0.19.1 derruba o app inteiro se receber emoji ou par de caracteres quebrado (visto no Windows).
 * Tudo que vai para o motor passa por aqui.
 */
object NativeText {
    fun safe(s: String): String = s.filterNot { it.isSurrogate() || it.code == 0 }

    /** A 2B não usa emoji; o modelo às vezes escapa. Tira os do plano astral e os seletores de variação. */
    fun noEmoji(s: String): String = s.filterNot { it.isSurrogate() || it.code == 0xFE0F || it.code == 0x200D }.replace(Regex("[ \t]{2,}"), " ")
}

/** Motor de IA local. Trocar de motor não muda o resto do app. */
interface LlmEngine : AutoCloseable {
    /** Demora alguns segundos: chame fora da thread da interface. */
    fun load(model: File, vision: Boolean, useGpu: Boolean, cacheDir: File)

    /** Pedaços da resposta, na ordem. Cancelar a coleta interrompe a geração. */
    fun generate(request: LlmRequest): Flow<String>
}
