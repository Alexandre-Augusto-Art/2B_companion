package br.offgr1d.companion.web

import br.offgr1d.companion.net.SafeHttp
import br.offgr1d.companion.text.Text
import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class WebResult(val title: String, val text: String, val url: String, val source: String)

/**
 * Busca na web, só quando a pessoa liga para aquela pergunta. Recebe apenas a pergunta digitada:
 * texto da tela, memórias e histórico nunca entram na consulta. Links dos resultados não são abertos.
 */
class WebSearch(private val fetch: (url: String, purpose: String) -> String) {
    constructor(http: SafeHttp) : this({ url, purpose -> http.text(url, purpose, 1024 * 1024) })

    /** @param searxUrl instância SearXNG da pessoa; vazio usa a Wikipédia. */
    fun search(query: String, searxUrl: String, limit: Int = 3): List<WebResult> {
        val q = URLEncoder.encode(Text.cut(query.trim(), 200), "UTF-8")
        return if (searxUrl.isBlank()) {
            parseWikipedia(fetch("$WIKI?action=query&generator=search&gsrsearch=$q&gsrlimit=$limit&prop=extracts%7Cinfo" +
                "&exintro=1&explaintext=1&exlimit=$limit&inprop=url&format=json&formatversion=2", "busca na Wikipédia"), limit)
        } else {
            parseSearx(fetch("${searxUrl.trim().trimEnd('/')}/search?q=$q&format=json&language=pt-BR", "busca no SearXNG"), limit)
        }
    }

    companion object {
        private const val WIKI = "https://pt.wikipedia.org/w/api.php"

        fun parseWikipedia(raw: String, limit: Int): List<WebResult> {
            val pages = ((Json.parseToJsonElement(raw) as? JsonObject)?.get("query") as? JsonObject)?.get("pages") as? JsonArray
                ?: return emptyList()
            return pages.filterIsInstance<JsonObject>()
                .sortedBy { (it["index"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: Int.MAX_VALUE }
                .mapNotNull { p ->
                    val url = p.s("fullurl")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                    WebResult(p.s("title") ?: return@mapNotNull null, Text.cut(p.s("extract") ?: return@mapNotNull null, 900), url, "Wikipédia")
                }.take(limit)
        }

        fun parseSearx(raw: String, limit: Int): List<WebResult> {
            val list = (Json.parseToJsonElement(raw) as? JsonObject)?.get("results") as? JsonArray ?: return emptyList()
            return list.filterIsInstance<JsonObject>().mapNotNull { r ->
                val url = r.s("url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                WebResult(r.s("title") ?: return@mapNotNull null, Text.cut(Text.stripHtml(r.s("content").orEmpty()), 600), url,
                    url.removePrefix("https://").substringBefore('/'))
            }.take(limit)
        }

        private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.ifEmpty { null }
    }
}
