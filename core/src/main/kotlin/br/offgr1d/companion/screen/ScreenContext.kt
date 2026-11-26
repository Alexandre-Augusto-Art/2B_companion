package br.offgr1d.companion.screen

/** O que vai junto da pergunta. */
enum class Attachment(val label: String) { SCREEN("tela"), PHOTO("foto"), VIDEO("vídeo") }

/**
 * O que foi lido da tela, ou a foto ou o vídeo que a pessoa escolheu. Fica só na memória e só é usado se a pessoa enviar.
 * Num vídeo, [image] é uma grade com 4 quadros, em ordem: o motor aceita uma imagem por pergunta.
 */
class ScreenContext(val text: String?, val image: ByteArray?, val kind: Attachment = Attachment.SCREEN) {
    val isEmpty get() = text.isNullOrBlank() && image == null

    fun describe() = if (kind != Attachment.SCREEN) kind.label else listOfNotNull(
        text?.takeIf { it.isNotBlank() }?.let { "${it.length} letras" },
        image?.let { "imagem" },
    ).joinToString(" + ").ifEmpty { "vazia" }
}
