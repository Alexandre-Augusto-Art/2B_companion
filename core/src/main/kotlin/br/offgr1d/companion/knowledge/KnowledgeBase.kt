package br.offgr1d.companion.knowledge

import br.offgr1d.companion.links.LinkAnalyzer
import br.offgr1d.companion.links.LinkConfig
import br.offgr1d.companion.store.BlobStore
import java.util.concurrent.ConcurrentHashMap

/** Conteúdo embutido + última versão baixada do site. Um feed com erro não derruba os outros. */
class KnowledgeBase(
    private val store: BlobStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val fetch: (String) -> String,
) {
    @Volatile var daily: Daily = Daily()
        private set
    private val fetchedAt = ConcurrentHashMap<String, Long>()
    @Volatile var index: KnowledgeIndex = KnowledgeIndex(emptyList())
        private set
    @Volatile var links: LinkAnalyzer = LinkAnalyzer(LinkConfig())
        private set

    data class UpdateResult(val ok: List<String>, val failed: Map<String, String>)

    @Synchronized
    fun load() {
        val docs = ArrayList<Doc>()
        docs += FeedParser.parse("guides", seed("guides")!!)
        docs += FeedParser.parse("extras", seed("extras")!!)
        var reflections = emptyList<Reflection>()
        var quotes = emptyList<Quote>()
        var acts = emptyList<Act>()
        for (name in FeedParser.FEEDS) {
            val cached = runCatching { store.read(blob(name))?.toString(Charsets.UTF_8) }.getOrNull()
            val parsed = cached?.let { runCatching { FeedParser.parse(name, it) }.getOrNull() }
            docs += parsed ?: seed(name)?.let { FeedParser.parse(name, it) }.orEmpty()
            if (cached != null && parsed != null) when (name) {
                "reflections" -> reflections = FeedParser.reflections(cached)
                "motivations" -> quotes = FeedParser.quotes(cached)
                "organize" -> acts = FeedParser.acts(cached)
            }
            if (name == "factcheck") {
                val cfg = cached?.let { runCatching { LinkConfig.parse(it) }.getOrNull() }?.takeIf { it.trusted.isNotEmpty() }
                links = LinkAnalyzer(cfg ?: LinkConfig.parse(seed("factcheck")!!))
            }
        }
        index = KnowledgeIndex(docs.distinctBy { it.id })
        daily = Daily(reflections, quotes, acts)
    }

    /** Baixado há pouco nesta sessão: não precisa pedir de novo. */
    fun fresh(name: String, maxAgeMs: Long) = fetchedAt[name]?.let { now() - it in 0..maxAgeMs } == true

    private var epoch = 0

    /** Pânico: um download em andamento não pode gravar depois da limpeza. */
    @Synchronized
    fun forgetFetches() {
        epoch++
        fetchedAt.clear()
    }

    @Synchronized
    private fun epoch() = epoch

    @Synchronized
    private fun save(name: String, raw: String, started: Int) {
        check(epoch == started) { "cancelado" }
        store.write(blob(name), raw.toByteArray())
        fetchedAt[name] = now()
    }

    fun update(names: List<String> = FeedParser.FEEDS): UpdateResult {
        val ok = ArrayList<String>()
        val failed = LinkedHashMap<String, String>()
        val started = epoch()
        for (name in names) {
            runCatching {
                val raw = fetch(FeedParser.feedUrl(name))
                FeedParser.parse(name, raw) // valida antes de trocar o que já funciona
                save(name, raw, started)
            }.onSuccess { ok += name }.onFailure { failed[name] = it.message ?: it.javaClass.simpleName }
        }
        load()
        return UpdateResult(ok, failed)
    }

    private fun blob(name: String) = "feed-$name"

    private fun seed(name: String): String? =
        javaClass.getResourceAsStream("/seed/$name.json")?.use { it.readBytes().toString(Charsets.UTF_8) }
}
