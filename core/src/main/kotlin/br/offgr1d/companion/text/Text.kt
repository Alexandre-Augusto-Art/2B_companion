package br.offgr1d.companion.text

import java.text.Normalizer

object Text {
    private val STOPWORDS = ("a o as os um uma uns umas de do da dos das em no na nos nas por para pra com sem e ou que " +
        "se me te eu voce voces ele ela isso isto esse essa este esta aqui ai la como qual quais quando onde quero queria " +
        "tem ter tenho sobre mais muito pouco ja nao sim eh ser sao estou meu minha seu sua algum alguma coisa favor oi ola " +
        "fala explica explicar saber tela resuma resumir resumo aparece print foi vai pode posso deve tudo").split(' ').toSet()

    private val MARKS = Regex("\\p{Mn}+")
    private val NON_WORD = Regex("[^a-z0-9\\s-]")
    private val SPACES = Regex("\\s+")
    private val HTML_BLOCK = Regex("(?is)<(script|style|noscript|svg)\\b[^>]*>.*?</\\1\\s*>")
    private val HTML_TAG = Regex("<[^>]*>")

    fun stripAccents(s: String): String =
        MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()

    fun normalize(s: String): String = SPACES.replace(NON_WORD.replace(stripAccents(s), " "), " ").trim()

    /** Palavras úteis, com plural simples reduzido: "golpes" e "golpe" contam como a mesma palavra. */
    fun tokens(s: String): List<String> = normalize(s).split(' ', '-')
        .filter { it.length > 1 && it !in STOPWORDS }
        .map { stem(it) }

    private fun stem(t: String): String = when {
        t.length > 5 && t.endsWith("oes") -> t.dropLast(3) + "ao"
        t.length > 4 && t.endsWith("s") && !t.endsWith("ss") -> t.dropLast(1)
        else -> t
    }

    fun stripHtml(s: String): String = SPACES.replace(
        s.replace(HTML_BLOCK, " ").replace(HTML_TAG, " ")
            .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&"),
        " ",
    ).trim()

    fun cut(s: String, max: Int): String =
        if (s.length <= max) s else s.take((max - 1).coerceAtLeast(0)).trimEnd() + "…"
}
