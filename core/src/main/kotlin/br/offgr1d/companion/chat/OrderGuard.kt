package br.offgr1d.companion.chat

import br.offgr1d.companion.text.Text

/**
 * Tira de textos de terceiros as frases que tentam dar ordens à IA. Pega os truques comuns, não todos:
 * a proteção de verdade é a 2B não ter poder de agir, só de responder.
 */
object OrderGuard {
    const val REMOVED = "[trecho que dava ordens à IA, removido]"

    private val ORDERS = listOf(
        Regex("\\b(ignor|esquec|desconsider|desobedec)\\w*\\b.{0,40}\\b(regra|instruc|orden|comando|anterior|acima|foi dito|sistema|prompt)"),
        Regex("\\b(respond|escrev|dig|fal|repit|imprim)\\w{0,4}\\b.{0,15}\\b(apenas|somente|so|unicamente|exatamente)\\b"),
        Regex("\\b(ignore|disregard|forget)\\b.{0,40}\\b(previous|above|instruction|rule|prompt)"),
        Regex("\\b(reply|respond|answer|say)\\b.{0,10}\\b(only|exactly)\\b"),
        Regex("\\b(a partir de agora|de agora em diante|from now on)\\b.{0,30}\\b(voce|you|ia|ai)\\b"),
        Regex("\\b(voce agora e|you are now|aja como|act as|modo desenvolvedor|developer mode|system prompt)\\b"),
        Regex("\\bresposta\\b.{0,20}\\b(para|a) (qualquer|toda)\\b"),
    )
    private val SENTENCE = Regex("[^.!?\\n]+[.!?]*|\\n")

    /** @return texto limpo e se algo foi removido */
    fun clean(text: String): Pair<String, Boolean> {
        var found = false
        val out = SENTENCE.replace(text) { m ->
            val n = Text.normalize(m.value)
            if (ORDERS.any { it.containsMatchIn(n) }) {
                found = true
                " $REMOVED"
            } else m.value
        }
        return (if (found) out.trim() else text) to found
    }
}
