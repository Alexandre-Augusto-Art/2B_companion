package br.offgr1d.companion.chat

import br.offgr1d.companion.screen.Attachment

/**
 * Foto ou vídeo numa mensagem. Fica só na memória: a conversa guardada leva só o texto.
 * @param preview miniatura em JPEG, quando já está no aparelho
 * @param url vídeo no site do 0FF-GR1D
 * @param posterUrl capa do vídeo no site
 */
class Media(val kind: Attachment, val preview: ByteArray? = null, val url: String? = null, val posterUrl: String? = null)
