package br.offgr1d.companion.chat

import br.offgr1d.companion.knowledge.DocKind
import br.offgr1d.companion.knowledge.KnowledgeBase
import br.offgr1d.companion.links.LinkReport
import br.offgr1d.companion.links.Verdict
import br.offgr1d.companion.llm.LlmEngine
import br.offgr1d.companion.llm.LlmRequest
import br.offgr1d.companion.llm.ModelCatalog
import br.offgr1d.companion.llm.ModelSpec
import br.offgr1d.companion.llm.ModelStore
import br.offgr1d.companion.llm.Turn
import br.offgr1d.companion.net.NetEvent
import br.offgr1d.companion.net.NetLog
import br.offgr1d.companion.net.SafeHttp
import br.offgr1d.companion.screen.ScreenContext
import br.offgr1d.companion.store.AppData
import br.offgr1d.companion.store.BlobStore
import br.offgr1d.companion.store.SavedMessage
import br.offgr1d.companion.store.Settings
import br.offgr1d.companion.web.WebSearch
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class Mood { IDLE, THINKING, TALKING, ALERT }

data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val sources: List<Source> = emptyList(),
    val links: List<LinkReport> = emptyList(),
    val withScreen: Boolean = false,
    /** Escrito pela IA: a interface avisa que pode errar. */
    val generated: Boolean = false,
    val interrupted: Boolean = false,
)

/** O que a 2B viu na última pergunta, para o Inspetor. */
data class Trace(val system: String, val user: String, val historyTurns: Int, val hadImage: Boolean, val usedModel: Boolean)

data class UiState(
    val messages: List<ChatMessage> = emptyList(),
    val busy: Boolean = false,
    val status: String = "",
    val mood: Mood = Mood.IDLE,
    val screen: ScreenContext? = null,
    val web: Boolean = false,
    val settings: Settings = Settings(),
    val memories: List<String> = emptyList(),
    val docs: Int = 0,
    val readyModels: Set<String> = emptySet(),
    val downloading: String? = null,
    val downloaded: Long = 0,
    val trace: Trace? = null,
    val net: List<NetEvent> = emptyList(),
    val notice: String? = null,
)

/** Cérebro do app, igual no Android e no Windows. A interface só mostra [state] e chama estas funções. */
class Companion2B(
    private val scope: CoroutineScope,
    store: BlobStore,
    modelDir: File,
    private val cacheDir: File,
    private val engineFactory: () -> LlmEngine,
    private val idleUnloadMs: Long = 5 * 60_000,
) {
    private val blobs = store
    private val data = AppData(store)
    private val netLog = NetLog()
    private val http = SafeHttp(netLog) { _state.value.settings.useTor }
    private val models = ModelStore(modelDir, http)
    private val web = WebSearch(http)
    private val kb = KnowledgeBase(store) { http.text(it, "atualizar conteúdo do 0FF-GR1D") }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val engineLock = Mutex()
    private var engine: LlmEngine? = null
    private var engineKey = ""
    private var turnJob: Job? = null
    private var downloadJob: Job? = null
    private var unloadJob: Job? = null

    private val startJob = scope.launch(Dispatchers.IO) {
        kb.load()
        val problems = ArrayList<String>()
        fun <T> safe(what: String, default: T, read: () -> T): T =
            runCatching(read).getOrElse { problems += what; default }
        val settings = safe("ajustes", Settings(), data::settings)
        val memories = safe("memórias", emptyList(), data::memories)
        val history = if (settings.saveHistory) safe("conversas", emptyList(), data::history) else emptyList()
        _state.update {
            it.copy(
                settings = settings, memories = memories, docs = kb.index.docs.size,
                messages = listOf(ChatMessage(false, Persona.GREETINGS.random())) +
                    history.map { m -> ChatMessage(m.fromUser, m.text) },
                readyModels = ready(),
                notice = problems.takeIf { p -> p.isNotEmpty() }?.let { p -> "Não consegui ler: ${p.joinToString()}." },
            )
        }
    }

    suspend fun awaitReady() = startJob.join()

    private fun ready() = ModelCatalog.ALL.filter(models::isReady).map { it.id }.toSet()
    private fun model(): ModelSpec = ModelCatalog.byId(_state.value.settings.modelId)
    private fun syncNet() = _state.update { it.copy(net = netLog.events) }

    // ---------- conversa ----------

    fun attachScreen(s: ScreenContext?) = _state.update {
        if (s != null && s.isEmpty) it.copy(screen = null, notice = "Não consegui ler esta tela. O app pode bloquear capturas.")
        else it.copy(screen = s)
    }

    fun setWeb(on: Boolean) = _state.update { it.copy(web = on) }
    fun dismissNotice() = _state.update { it.copy(notice = null) }
    fun notice(text: String) = _state.update { it.copy(notice = text) }

    fun send(text: String): Job? {
        val question = text.trim()
        val s = _state.value
        if (question.isEmpty() || s.busy) return null
        val history = historyOf(s.messages.drop(1))
        // Tela e web valem só para esta pergunta.
        _state.update {
            it.copy(
                messages = it.messages + ChatMessage(true, question, withScreen = s.screen != null),
                busy = true, status = "lendo", mood = Mood.THINKING, screen = null, web = false, notice = null,
            )
        }
        return scope.launch {
            try {
                answer(question, history, s.screen, s.web)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                addAnswer("Tive um problema para responder: ${e.message ?: e.javaClass.simpleName}", Mood.ALERT)
            } finally {
                withContext(NonCancellable) {
                    _state.update {
                        val last = it.messages.lastOrNull()
                        it.copy(
                            messages = if (last != null && !last.fromUser && last.text.isEmpty()) it.messages.dropLast(1) else it.messages,
                            busy = false, status = "", mood = if (it.mood == Mood.ALERT) Mood.ALERT else Mood.IDLE, net = netLog.events,
                        )
                    }
                    persistHistory()
                }
            }
        }.also { turnJob = it }
    }

    fun stop() { turnJob?.cancel() }

    private fun addAnswer(text: String, mood: Mood, sources: List<Source> = emptyList(), links: List<LinkReport> = emptyList()) =
        _state.update { it.copy(messages = it.messages + ChatMessage(false, text, sources, links), mood = mood) }

    private suspend fun answer(question: String, history: List<Turn>, screen: ScreenContext?, useWeb: Boolean) {
        REMEMBER.matchEntire(question)?.let { m ->
            val fact = m.groupValues[1].trim().trimEnd('.')
            addMemory(fact)
            addAnswer("Guardado: \"$fact\". Você pode apagar em Memória.", Mood.IDLE)
            return
        }
        val crisis = Crisis.detect(question)
        if (crisis) addAnswer(Crisis.NOTE, Mood.IDLE)

        val links = kb.links.analyzeAll(question + "\n" + screen?.text.orEmpty())
        val hits = kb.index.search(question)
        var webFailed: String? = null
        val results = if (!useWeb || crisis) emptyList() else {
            _state.update { it.copy(status = "buscando na web") }
            runCatching { withContext(Dispatchers.IO) { web.search(question, _state.value.settings.searxUrl) } }
                .getOrElse { webFailed = it.message; emptyList() }
        }
        if (webFailed != null) notice("A busca na web falhou: $webFailed")

        val spec = model()
        val hasModel = models.isReady(spec)
        val prompt = PromptBuilder.build(question, hits, results, links, screen, history, _state.value.memories, spec.vision && hasModel)
        if (prompt.ordersRemoved) notice("Cuidado: este conteúdo tinha texto tentando dar ordens à IA. Tirei esse trecho.")
        _state.update {
            it.copy(trace = Trace(prompt.request.system, prompt.request.user, prompt.request.history.size, prompt.request.image != null, hasModel))
        }
        // Modelo fraco não orienta sobre segurança, direitos ou links: vale o texto conferido.
        val fixedText = !spec.advises && screen == null && (links.isNotEmpty() || hits.firstOrNull()?.doc?.kind == DocKind.GUIA)
        if (!hasModel || fixedText) {
            _state.update { it.copy(trace = it.trace?.copy(usedModel = false)) }
            val text = withoutModel(prompt.sources, hits.map { it.doc.text }, links)
            addAnswer(if (hasModel) text else "$text\n\n(Modo sem modelo: baixe um em Modelos para conversar.)", mood(links), prompt.sources, links)
            return
        }
        if (screen?.image != null && !spec.vision && screen.text.isNullOrBlank()) {
            notice("${spec.name} não entende imagens. Use um modelo com visão para ler esta tela.")
        }
        generate(spec, prompt.request, prompt.sources, links)
    }

    /** Pergunta interrompida ou sem resposta fica fora: o modelo voltaria a respondê-la. */
    private fun historyOf(messages: List<ChatMessage>): List<Turn> {
        val out = ArrayList<Turn>()
        var question: String? = null
        for (m in messages) {
            if (m.fromUser) question = m.text
            else if (question != null && !m.interrupted && m.text.isNotEmpty()) {
                out += Turn(true, question)
                out += Turn(false, m.text)
                question = null
            }
        }
        return out
    }

    private fun mood(links: List<LinkReport>) = if (links.any { it.verdict == Verdict.DANGER }) Mood.ALERT else Mood.IDLE

    /** Sem modelo a 2B ainda responde com o conteúdo local e a análise de links. */
    private fun withoutModel(sources: List<Source>, texts: List<String>, links: List<LinkReport>): String {
        val parts = ArrayList<String>()
        // Os avisos de cada link já aparecem no cartão da mensagem.
        when {
            links.any { it.verdict == Verdict.DANGER } -> parts += "PERIGO: não abra esse link. Os motivos estão no aviso."
            links.any { it.verdict == Verdict.WARN } -> parts += "Esse link pede cuidado. Veja o aviso."
            links.any { it.verdict == Verdict.OK } -> parts += "O endereço é de um site oficial. Isso não garante o conteúdo."
            links.isNotEmpty() -> parts += "Não conheço esse site. Confira quem é o dono antes de confiar."
        }
        sources.zip(texts).take(2).forEach { (s, t) -> parts += "[${s.n}] ${s.title}\n$t" }
        if (parts.isEmpty()) parts += "Não achei nada sobre isso no que tenho aqui."
        return parts.joinToString("\n\n")
    }

    private suspend fun generate(spec: ModelSpec, request: LlmRequest, sources: List<Source>, links: List<LinkReport>) {
        unloadJob?.cancel()
        engineLock.withLock {
            val settings = _state.value.settings
            val key = "${spec.id}/${settings.useGpu}"
            val ready = engine?.takeIf { engineKey == key } ?: run {
                _state.update { it.copy(status = "carregando o modelo") }
                closeEngine()
                withContext(Dispatchers.IO) {
                    val e = engineFactory()
                    try { e.load(models.file(spec), spec.vision, settings.useGpu, File(cacheDir, "llm")) }
                    catch (t: Throwable) { runCatching { e.close() }; throw t }
                    e
                }.also { engine = it; engineKey = key }
            }
            _state.update {
                it.copy(status = "pensando", messages = it.messages + ChatMessage(false, "", sources, links, generated = true, interrupted = true))
            }
            val raw = StringBuilder()
            ready.generate(request).collect { piece ->
                raw.append(piece)
                setLast(PromptBuilder.onlyKnownLinks(raw.toString(), sources), Mood.TALKING)
            }
            // Link que só existe na imagem: se a IA o leu, passa pela mesma análise.
            val seen = if (request.image == null) emptyList() else
                kb.links.analyzeAll(raw.toString()).filter { r -> r.verdict != Verdict.OK && links.none { it.host == r.host } }
            val allLinks = links + seen
            val used = CITATION.findAll(raw).map { it.groupValues[1].toInt() }.toSet()
            _state.update {
                val last = it.messages.last()
                // Mostra as fontes citadas; se não citou nenhuma, mostra todas as consultadas.
                val shown = sources.filter { s -> s.n in used }.ifEmpty { sources }
                it.copy(
                    messages = it.messages.dropLast(1) + last.copy(
                        text = last.text.trim().ifEmpty { "Não consegui formar uma resposta. Tente perguntar de outro jeito." }, sources = shown, links = allLinks, interrupted = false),
                    mood = mood(allLinks),
                )
            }
        }
        unloadJob = scope.launch {
            delay(idleUnloadMs)
            engineLock.withLock { closeEngine() }
        }
    }

    private fun setLast(text: String, mood: Mood) = _state.update {
        it.copy(messages = it.messages.dropLast(1) + it.messages.last().copy(text = text), mood = mood, status = "")
    }

    private suspend fun closeEngine() = withContext(NonCancellable + Dispatchers.IO) {
        runCatching { engine?.close() }
        engine = null
        engineKey = ""
    }

    /** Pouca memória no aparelho: descarrega o modelo se não estiver respondendo. */
    fun releaseMemory() {
        scope.launch { if (!_state.value.busy) engineLock.withLock { closeEngine() } }
    }

    fun clearChat() {
        if (_state.value.busy) return
        _state.update { it.copy(messages = listOf(ChatMessage(false, Persona.GREETINGS.random())), trace = null, screen = null) }
        scope.launch(Dispatchers.IO) { data.saveHistory(emptyList()) }
    }

    private suspend fun persistHistory() = withContext(Dispatchers.IO) {
        if (_state.value.settings.saveHistory && !wiping) {
            runCatching { data.saveHistory(_state.value.messages.drop(1).map { SavedMessage(it.fromUser, it.text) }) }
                .onFailure { notice("Não consegui salvar a conversa.") }
        }
    }

    // ---------- memória e ajustes ----------

    fun addMemory(text: String) {
        val t = text.trim().take(300)
        if (t.isEmpty()) return
        saveMemories((_state.value.memories + t).distinct().takeLast(50))
    }

    fun removeMemory(text: String) = saveMemories(_state.value.memories - text)

    private fun saveMemories(list: List<String>) {
        _state.update { it.copy(memories = list) }
        scope.launch(Dispatchers.IO) { runCatching { data.saveMemories(list) }.onFailure { notice("Não consegui salvar a memória.") } }
    }

    fun saveSettings(s: Settings) {
        if (s.searxUrl.isNotBlank() && !s.searxUrl.trim().startsWith("https://")) return notice("O endereço do SearXNG precisa começar com https://")
        _state.update { it.copy(settings = s) }
        scope.launch(Dispatchers.IO) {
            runCatching {
                data.save(s)
                if (s.saveHistory) data.saveHistory(_state.value.messages.drop(1).map { SavedMessage(it.fromUser, it.text) })
                else data.saveHistory(emptyList())
            }.onFailure { notice("Não consegui salvar os ajustes.") }
        }
    }

    // ---------- conteúdo e modelos ----------

    fun updateContent(): Job = scope.launch {
        _state.update { it.copy(status = "atualizando conteúdo") }
        val r = withContext(Dispatchers.IO) { kb.update() }
        _state.update {
            it.copy(
                status = "", docs = kb.index.docs.size, net = netLog.events,
                notice = if (r.failed.isEmpty()) "Conteúdo atualizado." else
                    "Atualizei ${r.ok.size} de ${r.ok.size + r.failed.size}. Falhou: ${r.failed.keys.joinToString()}.",
            )
        }
    }

    fun download(m: ModelSpec) {
        if (_state.value.downloading != null) return
        _state.update { it.copy(downloading = m.id, downloaded = models.partialBytes(m), notice = null) }
        downloadJob = scope.launch {
            try {
                models.download(m) { n -> _state.update { it.copy(downloaded = n) } }
                notice("${m.name} pronto.")
                saveSettings(_state.value.settings.copy(modelId = m.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice("Download: ${e.message}")
            } finally {
                _state.update { it.copy(downloading = null, readyModels = ready(), net = netLog.events) }
            }
        }
    }

    fun cancelDownload() { downloadJob?.cancel() }

    fun deleteModel(m: ModelSpec) {
        if (_state.value.busy || _state.value.downloading == m.id) return
        scope.launch {
            engineLock.withLock { closeEngine() }
            withContext(Dispatchers.IO) { models.delete(m) }
            _state.update { it.copy(readyModels = ready()) }
        }
    }

    // ---------- pânico ----------

    @Volatile private var wiping = false

    /** Apaga conversas, memórias, ajustes e conteúdo. Com [includeModels], apaga também os modelos. */
    fun panic(includeModels: Boolean): Job = scope.launch {
        wiping = true
        try {
            // A tela some antes de qualquer espera.
            _state.update { UiState(docs = it.docs, busy = true, status = "apagando") }
            downloadJob?.cancelAndJoin()
            turnJob?.cancelAndJoin()
            unloadJob?.cancelAndJoin()
            engineLock.withLock { closeEngine() }
            val ok = withContext(NonCancellable + Dispatchers.IO) {
                var all = blobs.wipe()
                if (includeModels) all = models.deleteAll() && all
                all = File(cacheDir, "llm").let { !it.exists() || it.deleteRecursively() } && all
                netLog.clear()
                kb.load()
                all
            }
            _state.value = UiState(
                messages = listOf(ChatMessage(false, "Tudo limpo.")), docs = kb.index.docs.size, readyModels = ready(),
                notice = if (ok) null else "Algum arquivo não pôde ser apagado. Desinstale o app para garantir.",
            )
        } finally {
            wiping = false
        }
    }

    companion object {
        private val REMEMBER = Regex("(?is)(?:lembre|lembra|guarde|guarda|memorize)(?:-se)?\\s+(?:de\\s+)?que\\s+(.+)")
        private val CITATION = Regex("\\[(\\d{1,2})]")
    }
}
