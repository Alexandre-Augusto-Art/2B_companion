package br.offgr1d.companion.knowledge

import br.offgr1d.companion.text.Text
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

/** Lê os JSON do 0FF-GR1D. Um item quebrado é pulado; um arquivo com formato errado dá erro. */
object FeedParser {
    const val SITE = "https://alexandre-augusto-art.github.io/0FF-GR1D_WEB/"

    /** Feeds baixados do site. chatbot e factcheck também vão embutidos no app. */
    val FEEDS = listOf("chatbot", "factcheck", "factchecks", "organize", "reflections", "motivations")

    fun feedUrl(name: String) = "${SITE}data/$name.json"

    fun parse(name: String, raw: String): List<Doc> {
        val root = Json.parseToJsonElement(raw)
        return when (name) {
            "chatbot" -> root.list("entries").mapNotNull(::tool)
            "factchecks" -> root.list("items").mapNotNull(::check)
            "organize" -> root.list("eventos").mapNotNull(::event)
            "reflections" -> root.jsonArray.mapNotNull(::reflection)
            "motivations" -> root.jsonArray.mapNotNull(::quote)
            "guides" -> root.list("guides").mapNotNull(::guide)
            "factcheck" -> { // configuração do analisador de links: só valida
                require(root.list("trusted").isNotEmpty()) { "lista de sites vazia" }
                emptyList()
            }
            else -> error("feed desconhecido: $name")
        }
    }

    private fun tool(e: JsonElement): Doc? {
        val id = e.str("id") ?: return null
        val link = e.str("link")
        return Doc(
            id = "site:$id", kind = DocKind.FERRAMENTA,
            title = e.str("title") ?: return null, text = e.str("answer") ?: return null,
            url = link?.let { if (it.startsWith("https://")) it else SITE + it.trimStart('/') },
            source = "0FF-GR1D", keywords = e.strings("keywords") + e.strings("examples"),
        )
    }

    private fun check(e: JsonElement): Doc? {
        val link = e.https("link") ?: return null
        val rating = e.str("rating") ?: "CHECAGEM"
        return Doc(
            id = "check:$link", kind = DocKind.CHECAGEM, title = e.str("title") ?: return null,
            text = "Classificação da agência: $rating. " + Text.stripHtml(e.str("summary").orEmpty()),
            url = link, source = e.str("source"), date = e.str("date")?.take(10), keywords = listOf(rating),
        )
    }

    private fun event(e: JsonElement): Doc? {
        val id = e.str("id") ?: return null
        val title = e.str("titulo") ?: return null
        val fonte = (e as? JsonObject)?.get("fonte")
        val eventDate = e.str("data")
        val published = e.str("publicadoEm")?.take(10)
        val kind = if (e.str("tipo") == "convocacao") "Convocação" else "Notícia sobre ato, não é convocação"
        val place = listOfNotNull(e.str("cidade"), e.str("uf")).joinToString(" - ")
        return Doc(
            id = "ato:$id", kind = DocKind.ATO, title = title,
            text = "$kind. Local: ${place.ifBlank { "não informado" }}. " +
                "Data do ato: ${eventDate ?: "não informada, confira na fonte"}. " +
                "Publicado em ${published ?: "data desconhecida"}.",
            url = fonte?.https("url"), source = fonte?.str("nome"), date = published, uf = e.str("uf"),
            keywords = listOf("ato", "manifestacao", "protesto"), eventDate = eventDate,
        )
    }

    private fun reflection(e: JsonElement): Doc? {
        val obj = e as? JsonObject ?: return null
        val news = obj["news"]
        val refl = obj["reflection"]
        return Doc(
            id = "w4k3:${e.str("id") ?: return null}", kind = DocKind.REFLEXAO,
            title = refl?.str("title") ?: return null,
            text = "Notícia que motivou: ${news?.str("title").orEmpty()} (${news?.str("source").orEmpty()}). " +
                "Reflexão: " + Text.stripHtml(refl.str("content").orEmpty()),
            url = news?.https("link") ?: (SITE + "w4k3-protocol.html"), source = "W4K3 PR0T0C0L", date = e.str("date"),
            keywords = listOf("w4k3", "reflexao"),
        )
    }

    private fun quote(e: JsonElement): Doc? = Doc(
        id = "frase:${e.str("date") ?: return null}", kind = DocKind.FRASE, title = "Frase do dia",
        text = "\"${e.str("content") ?: return null}\" (${e.str("author").orEmpty()})",
        url = SITE + "motivacao.html", source = "Motivação Diária", date = e.str("date"),
        keywords = listOf("frase", "motivacao", "inspiracao"),
    )

    private fun guide(e: JsonElement): Doc? = Doc(
        id = "guia:${e.str("id") ?: return null}", kind = DocKind.GUIA, title = e.str("title") ?: return null,
        text = e.str("text") ?: return null, url = e.https("url"), source = e.str("source"),
        date = e.str("reviewed"), keywords = e.strings("keywords"),
    )

    private fun JsonElement.list(key: String): JsonArray =
        (this as? JsonObject)?.get(key) as? JsonArray ?: error("faltou a lista \"$key\"")

    private fun JsonElement.str(key: String): String? =
        ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.ifEmpty { null }

    private fun JsonElement.https(key: String): String? = str(key)?.takeIf { it.startsWith("https://") }

    private fun JsonElement.strings(key: String): List<String> =
        ((this as? JsonObject)?.get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
}
