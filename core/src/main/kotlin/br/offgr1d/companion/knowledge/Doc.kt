package br.offgr1d.companion.knowledge

/** O rótulo vai no prompt: a IA precisa saber se está lendo checagem, ato, guia ou opinião. */
enum class DocKind(val label: String) {
    GUIA("GUIA"),
    FERRAMENTA("FERRAMENTA DO SITE"),
    CHECAGEM("CHECAGEM DE AGÊNCIA"),
    ATO("ATO / MANIFESTAÇÃO"),
    REFLEXAO("OPINIÃO (usa ficção como metáfora, não é fato)"),
    FRASE("FRASE"),
    HISTORIA("HISTÓRIA DA 2B (ficção de origem da personagem, não é fato)"),
}

data class Doc(
    val id: String,
    val kind: DocKind,
    val title: String,
    val text: String,
    val url: String? = null,
    val source: String? = null,
    /** AAAA-MM-DD */
    val date: String? = null,
    val uf: String? = null,
    val keywords: List<String> = emptyList(),
    /** Data em que o ato acontece, quando a fonte informa. */
    val eventDate: String? = null,
)

data class Hit(val doc: Doc, val score: Double)
