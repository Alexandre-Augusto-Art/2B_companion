package br.offgr1d.companion.knowledge

import br.offgr1d.companion.links.LinkAnalyzer
import br.offgr1d.companion.links.LinkConfig
import br.offgr1d.companion.store.BlobStore

/** Conteúdo embutido + última versão baixada do site. Um feed com erro não derruba os outros. */
class KnowledgeBase(private val store: BlobStore, private val fetch: (String) -> String) {
    @Volatile var index: KnowledgeIndex = KnowledgeIndex(emptyList())
        private set
    @Volatile var links: LinkAnalyzer = LinkAnalyzer(LinkConfig())
        private set

    data class UpdateResult(val ok: List<String>, val failed: Map<String, String>)

    fun load() {
        val docs = ArrayList<Doc>()
        docs += FeedParser.parse("guides", seed("guides")!!)
        for (name in FeedParser.FEEDS) {
            val cached = runCatching { store.read(blob(name))?.toString(Charsets.UTF_8) }.getOrNull()
            val parsed = cached?.let { runCatching { FeedParser.parse(name, it) }.getOrNull() }
            docs += parsed ?: seed(name)?.let { FeedParser.parse(name, it) }.orEmpty()
            if (name == "factcheck") {
                val cfg = cached?.let { runCatching { LinkConfig.parse(it) }.getOrNull() }?.takeIf { it.trusted.isNotEmpty() }
                links = LinkAnalyzer(cfg ?: LinkConfig.parse(seed("factcheck")!!))
            }
        }
        index = KnowledgeIndex(docs.distinctBy { it.id })
    }

    fun update(): UpdateResult {
        val ok = ArrayList<String>()
        val failed = LinkedHashMap<String, String>()
        for (name in FeedParser.FEEDS) {
            runCatching {
                val raw = fetch(FeedParser.feedUrl(name))
                FeedParser.parse(name, raw) // valida antes de trocar o que já funciona
                store.write(blob(name), raw.toByteArray())
            }.onSuccess { ok += name }.onFailure { failed[name] = it.message ?: it.javaClass.simpleName }
        }
        load()
        return UpdateResult(ok, failed)
    }

    private fun blob(name: String) = "feed-$name"

    private fun seed(name: String): String? =
        javaClass.getResourceAsStream("/seed/$name.json")?.use { it.readBytes().toString(Charsets.UTF_8) }
}
