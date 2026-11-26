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
            "chatbot", "extras" -> root.list("entries").mapNotNull(::tool)
            "factchecks" -> root.list("items").mapNotNull(::check)
            "organize" -> acts(raw).map(::event)
            "reflections" -> reflections(raw).map(::reflection)
            "motivations" -> quotes(raw).map(::quote)
            "guides" -> root.list("guides").mapNotNull(::guide)
            "lore" -> root.list("lore").mapNotNull { e ->
                Doc("historia:${e.str("id") ?: return@mapNotNull null}", DocKind.HISTORIA, e.str("title") ?: return@mapNotNull null,
                    e.str("text") ?: return@mapNotNull null, source = "2B", keywords = e.strings("keywords"))
            }
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

    // ---------- conteúdo do dia ----------

    fun acts(raw: String): List<Act> = Json.parseToJsonElement(raw).list("eventos").mapNotNull { e ->
        val fonte = (e as? JsonObject)?.get("fonte")
        Act(
            id = e.str("id") ?: return@mapNotNull null, title = e.str("titulo") ?: return@mapNotNull null,
            call = e.str("tipo") == "convocacao", uf = e.str("uf"), states = e.strings("estados"), city = e.str("cidade"),
            date = e.str("data"), hour = e.str("hora"), published = e.str("publicadoEm")?.take(10),
            source = fonte?.str("nome"), url = fonte?.https("url"),
        )
    }

    fun reflections(raw: String): List<Reflection> = Json.parseToJsonElement(raw).jsonArray.mapNotNull { e ->
        val obj = e as? JsonObject ?: return@mapNotNull null
        val news = obj["news"]
        val refl = obj["reflection"]
        val content = Text.stripHtml(refl?.str("content").orEmpty())
        Reflection(
            id = e.str("id") ?: return@mapNotNull null, date = e.str("date"), title = refl?.str("title") ?: return@mapNotNull null,
            excerpt = Text.stripHtml(refl.str("excerpt") ?: content), content = content,
            newsTitle = news?.str("title"), newsSource = news?.str("source"), newsLink = news?.https("link"),
        )
    }.sortedByDescending { it.date.orEmpty() }

    fun quotes(raw: String): List<Quote> = Json.parseToJsonElement(raw).jsonArray.mapNotNull { e ->
        val date = e.str("date") ?: return@mapNotNull null
        Quote("$date:${e.str("bankId").orEmpty()}", date, e.str("content") ?: return@mapNotNull null, e.str("author"))
    }.sortedByDescending { it.date }

    private fun event(a: Act) = Doc(
        id = "ato:${a.id}", kind = DocKind.ATO, title = a.title,
        text = "${if (a.call) "Convocação" else "Notícia sobre ato, não é convocação"}. " +
            "Local: ${listOfNotNull(a.city, a.uf).joinToString(" - ").ifBlank { "não informado" }}. " +
            "Data do ato: ${a.date ?: "não informada, confira na fonte"}. Publicado em ${a.published ?: "data desconhecida"}.",
        url = a.url, source = a.source, date = a.published, uf = a.uf,
        keywords = listOf("ato", "manifestacao", "protesto"), eventDate = a.date,
    )

    private fun reflection(r: Reflection) = Doc(
        id = "w4k3:${r.id}", kind = DocKind.REFLEXAO, title = r.title,
        text = "Notícia que motivou: ${r.newsTitle.orEmpty()} (${r.newsSource.orEmpty()}). Reflexão: ${r.content}",
        url = r.newsLink ?: (SITE + "w4k3-protocol.html"), source = "W4K3 PR0T0C0L", date = r.date, keywords = listOf("w4k3", "reflexao"),
    )

    private fun quote(q: Quote) = Doc(
        id = "frase:${q.date}", kind = DocKind.FRASE, title = "Frase do dia", text = "\"${q.content}\" (${q.author.orEmpty()})",
        url = SITE + "motivacao.html", source = "Motivação Diária", date = q.date, keywords = listOf("frase", "motivacao", "inspiracao"),
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
