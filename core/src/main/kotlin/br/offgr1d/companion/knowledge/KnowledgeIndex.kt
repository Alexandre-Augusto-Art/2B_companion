package br.offgr1d.companion.knowledge

import br.offgr1d.companion.text.Text
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ln

/** Busca local por palavras (BM25). Não usa rede nem modelo. */
class KnowledgeIndex(val docs: List<Doc>) {
    private class Entry(val doc: Doc, val tf: Map<String, Double>, val length: Double, val title: String)

    private val entries = docs.map { d ->
        val tf = HashMap<String, Double>()
        fun add(text: String, weight: Double) = Text.tokens(text).forEach { tf.merge(it, weight, Double::plus) }
        add(d.title, 2.0)
        add(d.text, 1.0)
        // Conjunto: dez exemplos com a mesma palavra não podem valer dez vezes.
        d.keywords.flatMap(Text::tokens).toSet().forEach { tf.merge(it, 1.5, Double::plus) }
        Entry(d, tf, tf.values.sum(), Text.normalize(d.title))
    }
    private val avgLength = entries.map { it.length }.average().takeIf { !it.isNaN() } ?: 1.0
    private val df = HashMap<String, Int>().also { m ->
        entries.forEach { e -> e.tf.keys.forEach { k -> m.merge(k, 1, Int::plus) } }
    }

    private fun idf(t: String): Double {
        val n = df[t] ?: 0
        return ln(1 + (entries.size - n + 0.5) / (n + 0.5))
    }

    fun search(query: String, limit: Int = 4, today: LocalDate = LocalDate.now()): List<Hit> {
        val uf = detectUf(query)
        val norm = " ${Text.normalize(query)} "
        val wantsEvents = EVENT_WORDS.any { " $it" in norm }
        val wantsQuote = QUOTE_WORDS.any { " $it" in norm }
        val ufWords = uf?.let { Text.tokens(UF_NAMES.getValue(it)) + it.lowercase() }.orEmpty().toSet()
        val terms = Text.tokens(query).filter { it !in ufWords }.toSet()
        if (terms.isEmpty() && !wantsEvents && !wantsQuote) return emptyList()
        val totalIdf = terms.sumOf(::idf).coerceAtLeast(1e-9)

        return entries.mapNotNull { e ->
            val d = e.doc
            if (d.kind == DocKind.ATO) {
                // Ato que já passou não é sugerido.
                if (d.eventDate != null && daysAgo(d.eventDate, today)?.let { it > 0 } == true) return@mapNotNull null
                if (uf != null && d.uf != uf && d.uf != "BR") return@mapNotNull null
            }
            val matched = terms.filter { it in e.tf }
            val coverage = matched.sumOf(::idf) / totalIdf
            val eventBrowse = d.kind == DocKind.ATO && wantsEvents && uf != null
            val quoteBrowse = d.kind == DocKind.FRASE && wantsQuote
            if (coverage < (if (d.kind == DocKind.REFLEXAO) 0.5 else MIN_COVERAGE) && !eventBrowse && !quoteBrowse) return@mapNotNull null

            var score = matched.sumOf { t ->
                val f = e.tf.getValue(t)
                idf(t) * f * (K1 + 1) / (f + K1 * (1 - B + B * e.length / avgLength))
            } * (0.5 + coverage / 2)
            // "o que é o Tarot" fala da página Tarot, não de outra que só a cita.
            if (e.title.length > 3 && " ${e.title} " in norm) score *= 2
            if (eventBrowse) score += 2.0 + (if (uf != null && d.uf == uf) 4.0 else 0.0)
            if (quoteBrowse) score += 2.0
            // Opinião só ganha de fonte factual se for muito mais parecida com a pergunta.
            score *= when (d.kind) {
                DocKind.REFLEXAO -> 0.6
                DocKind.GUIA -> 1.15
                // Falar de protesto não é pedir a agenda: sem estado, ato só entra se for bem parecido.
                DocKind.ATO -> if (uf == null) 0.5 else 1.0
                else -> 1.0
            }
            d.date?.let { date -> daysAgo(date, today)?.let { score *= 1 + 0.15 / (1 + it.coerceAtLeast(0) / 14.0) } }
            Hit(d, score)
        }.sortedWith(compareByDescending<Hit> { it.score }.thenByDescending { it.doc.date.orEmpty() }).take(limit)
    }

    companion object {
        private const val K1 = 1.2
        private const val B = 0.75
        private const val MIN_COVERAGE = 0.34
        private val EVENT_WORDS = listOf("ato ", "atos ", "manifestac", "protesto", "passeata", "convocac", "mobilizac")
        private val QUOTE_WORDS = listOf("frase", "motivac", "inspirac", "desanimad")

        val UF_NAMES = mapOf(
            "AC" to "Acre", "AL" to "Alagoas", "AP" to "Amapá", "AM" to "Amazonas", "BA" to "Bahia", "CE" to "Ceará",
            "DF" to "Distrito Federal", "ES" to "Espírito Santo", "GO" to "Goiás", "MA" to "Maranhão",
            "MT" to "Mato Grosso", "MS" to "Mato Grosso do Sul", "MG" to "Minas Gerais", "PA" to "Pará",
            "PB" to "Paraíba", "PR" to "Paraná", "PE" to "Pernambuco", "PI" to "Piauí", "RJ" to "Rio de Janeiro",
            "RN" to "Rio Grande do Norte", "RS" to "Rio Grande do Sul", "RO" to "Rondônia", "RR" to "Roraima",
            "SC" to "Santa Catarina", "SP" to "São Paulo", "SE" to "Sergipe", "TO" to "Tocantins",
        )
        /** Capitais, só com "em" antes: "em Natal" é cidade, "no natal" não. */
        private val CAPITALS = mapOf(
            "rio branco" to "AC", "maceio" to "AL", "macapa" to "AP", "manaus" to "AM", "salvador" to "BA", "fortaleza" to "CE",
            "brasilia" to "DF", "vitoria" to "ES", "goiania" to "GO", "sao luis" to "MA", "cuiaba" to "MT", "campo grande" to "MS",
            "belo horizonte" to "MG", "belem" to "PA", "joao pessoa" to "PB", "curitiba" to "PR", "recife" to "PE", "teresina" to "PI",
            "natal" to "RN", "porto alegre" to "RS", "porto velho" to "RO", "boa vista" to "RR", "florianopolis" to "SC",
            "aracaju" to "SE", "palmas" to "TO",
        )
        private val SIGLA = Regex("\\b[A-Z]{2}\\b")

        /** Nome do estado ("Bahia") ou sigla em maiúsculas ("BA"). "para" e "se" minúsculos são palavras comuns. */
        fun detectUf(query: String): String? {
            SIGLA.findAll(query).map { it.value }.firstOrNull { it in UF_NAMES }?.let { return it }
            val norm = " ${Text.normalize(query)} "
            CAPITALS.entries.firstOrNull { " em ${it.key} " in norm }?.let { return it.value }
            return UF_NAMES.entries.sortedByDescending { it.value.length }.firstOrNull { (uf, name) ->
                if (uf == "PA") "pará" in query.lowercase().split(Regex("[^\\p{L}]+"))
                else " ${Text.normalize(name)} " in norm
            }?.key
        }

        private fun daysAgo(date: String, today: LocalDate): Long? =
            runCatching { ChronoUnit.DAYS.between(LocalDate.parse(date.take(10)), today) }.getOrNull()
    }
}
