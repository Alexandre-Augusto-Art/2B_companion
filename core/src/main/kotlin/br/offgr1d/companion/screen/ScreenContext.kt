package br.offgr1d.companion.screen

/** O que foi lido da tela. Fica só na memória e só é usado se a pessoa enviar a pergunta com ele anexado. */
class ScreenContext(val text: String?, val image: ByteArray?) {
    val isEmpty get() = text.isNullOrBlank() && image == null

    fun describe() = listOfNotNull(
        text?.takeIf { it.isNotBlank() }?.let { "${it.length} letras" },
        image?.let { "imagem" },
    ).joinToString(" + ").ifEmpty { "vazia" }
}
