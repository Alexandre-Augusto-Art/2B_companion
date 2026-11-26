package br.offgr1d.companion.chat

import br.offgr1d.companion.alerts.Alert
import br.offgr1d.companion.alerts.AlertFeed
import br.offgr1d.companion.alerts.AlertState
import br.offgr1d.companion.alerts.Alerts
import br.offgr1d.companion.knowledge.Daily
import br.offgr1d.companion.knowledge.Doc
import br.offgr1d.companion.knowledge.DocKind
import br.offgr1d.companion.knowledge.FeedParser
import br.offgr1d.companion.knowledge.KnowledgeBase
import br.offgr1d.companion.knowledge.todayBR
import br.offgr1d.companion.links.LinkReport
import br.offgr1d.companion.links.Verdict
import br.offgr1d.companion.llm.LlmEngine
import br.offgr1d.companion.llm.LlmRequest
import br.offgr1d.companion.llm.ModelCatalog
import br.offgr1d.companion.llm.ModelSpec
import br.offgr1d.companion.llm.ModelStore
import br.offgr1d.companion.llm.NativeText
import br.offgr1d.companion.llm.Turn
import br.offgr1d.companion.net.NetEvent
import br.offgr1d.companion.net.NetLog
import br.offgr1d.companion.net.SafeHttp
import br.offgr1d.companion.screen.Attachment
import br.offgr1d.companion.screen.ScreenContext
import br.offgr1d.companion.store.AppData
import br.offgr1d.companion.store.BlobStore
import br.offgr1d.companion.store.SavedMessage
import br.offgr1d.companion.store.Settings
import br.offgr1d.companion.text.Text
import br.offgr1d.companion.web.WebSearch
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class Mood { IDLE, THINKING, TALKING, ALERT }

data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val sources: List<Source> = emptyList(),
    val links: List<LinkReport> = emptyList(),
    val withScreen: Boolean = false,
    /** Aviso que chegou sozinho, sem pergunta. */
    val alert: Alert? = null,
    val tool: Tool? = null,
    /** Guia conferido, mostrado junto da resposta da IA. */
    val guide: Doc? = null,
    /** Escrito pela IA: a interface avisa que pode errar. */
    val generated: Boolean = false,
    val interrupted: Boolean = false,
    /** Foto ou vídeo, de quem mandou. */
    val media: Media? = null,
)

/** O que a 2B viu na última pergunta, para o Inspetor. */
data class Trace(val system: String, val user: String, val historyTurns: Int, val hadImage: Boolean, val usedModel: Boolean)

data class UiState(
    val messages: List<ChatMessage> = emptyList(),
    val busy: Boolean = false,
    val status: String = "",
    val mood: Mood = Mood.IDLE,
    val screen: ScreenContext? = null,
    /** Com a leitura automática ligada, a tela já usada só volta se a pergunta falar dela. */
    val screenUsed: Boolean = false,
    val daily: Daily = Daily(),
    val pages: List<Doc> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    /** Muda quando a interface deve mostrar a conversa (a pessoa tocou numa notificação). */
    val chatFocus: Int = 0,
    /** Esperando a pessoa confirmar que tem 18 anos ou mais. */
    val askAdult: Boolean = false,
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
    /** Só para testes: troca a rede dos feeds e o que conta como modelo pronto. */
    fetch: ((String) -> String)? = null,
    modelReady: ((ModelSpec) -> Boolean)? = null,
) {
    private val blobs = store
    private val data = AppData(store)
    private val netLog = NetLog()
    private val http = SafeHttp(netLog) { _state.value.settings.useTor }
    private val models = ModelStore(modelDir, http)
    private val isReady = modelReady ?: models::isReady
    private val web = WebSearch(http)
    private val kb = KnowledgeBase(store, fetch = fetch ?: { http.text(it, "atualizar conteúdo do 0FF-GR1D") })

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val engineLock = Mutex()
    private val alertLock = Mutex()
    @Volatile private var alertState = AlertState()
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
        alertState = safe("avisos", AlertState(), data::alerts)
        _state.update {
            it.copy(
                settings = settings, memories = memories, docs = kb.index.docs.size, daily = kb.daily, pages = Toolbox.pages(kb.index.docs),
                alerts = alertState.inbox,
                messages = listOf(ChatMessage(false, Persona.greeting(settings.aigf))) + history.map { m -> ChatMessage(m.fromUser, m.text) } +
                    alertState.inbox.filter { a -> !a.read }.map(::alertMessage),
                readyModels = ready(),
                notice = problems.takeIf { p -> p.isNotEmpty() }?.let { p -> "Não consegui ler: ${p.joinToString()}." },
            )
        }
    }

    suspend fun awaitReady() = startJob.join()

    private fun ready() = ModelCatalog.ALL.filter(isReady).map { it.id }.toSet()
    private fun model(): ModelSpec = ModelCatalog.byId(_state.value.settings.modelId)
    private fun syncNet() = _state.update { it.copy(net = netLog.events) }

    // ---------- conversa ----------

    fun attachScreen(s: ScreenContext?) = _state.update {
        if (s != null && s.isEmpty) it.copy(
            screen = null,
            notice = if (s.kind == Attachment.SCREEN) "Não consegui ler esta tela. O app pode bloquear capturas." else "Não consegui abrir esse arquivo.",
        )
        else it.copy(screen = s, screenUsed = false)
    }

    fun setWeb(on: Boolean) = saveSettings(_state.value.settings.copy(web = on))
    fun focusChat() = _state.update { it.copy(chatFocus = it.chatFocus + 1) }
    fun dismissNotice() = _state.update { it.copy(notice = null) }
    fun notice(text: String) = _state.update { it.copy(notice = text) }

    fun send(text: String): Job? {
        val s = _state.value
        val picked = s.screen?.takeIf { it.kind != Attachment.SCREEN }
        val question = text.trim().ifEmpty {
            when (picked?.kind) {
                Attachment.PHOTO -> "O que você acha dessa foto?"
                Attachment.VIDEO -> "O que você acha desse vídeo?"
                else -> ""
            }
        }
        if (question.isEmpty() || s.busy) return null
        if (AIGF_COMMAND.matches(question.lowercase())) return toggleAigf(question)
        val history = historyOf(s.messages.drop(1))
        val lastTool = s.messages.lastOrNull()?.tool
        val tool = Toolbox.match(question) ?: Toolbox.followUp(lastTool, question)
        val casual = SmallTalk.isCasual(question, s.settings.aigf)
        // Leitura manual vale para uma pergunta. Automática: a primeira sobre a tela e as que falarem dela de novo.
        val auto = s.settings.autoScreen
        val aboutScreen = SCREEN_WORDS.containsMatchIn(Text.normalize(question))
        val screen = s.screen?.takeIf { tool == null && (picked != null || !auto || aboutScreen || (!s.screenUsed && !casual)) }
        // Avisos mostrados depois da última pergunta: a pessoa pode estar respondendo a eles.
        val alerts = s.messages.takeLastWhile { !it.fromUser }.mapNotNull { it.alert }.map { "${it.title}\n${it.text}" } +
            listOfNotNull(s.daily.reflections.firstOrNull()?.takeIf { lastTool == Tool.NEWS }?.let { "OPINIÃO do W4K3 PR0T0C0L: ${it.title}\n${it.content}" })
        _state.update {
            it.copy(
                messages = it.messages + ChatMessage(
                    true, question, withScreen = screen != null, media = screen?.takeIf { p -> p === picked }?.let { p -> Media(p.kind, p.image) },
                ),
                busy = true, status = "lendo", mood = Mood.THINKING, notice = null,
                screen = if ((auto && picked == null) || screen == null) it.screen else null,
                screenUsed = if (picked != null) it.screenUsed else it.screenUsed || screen != null,
            )
        }
        return scope.launch {
            try {
                if (tool != null && !Crisis.detect(question)) useTool(tool, question)
                else answer(question, history, screen, s.settings.web, casual, alerts, onlyMedia = text.isBlank() && screen === picked && picked != null)
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

    /** Conteúdo do 0FF-GR1D, baixado na hora se a cópia for velha. Não passa pela IA. */
    private suspend fun useTool(tool: Tool, question: String) {
        var stale = false
        tool.feed?.takeIf { !kb.fresh(it, FRESH_MS) }?.let { feed ->
            _state.update { it.copy(status = "buscando no 0FF-GR1D") }
            stale = withContext(Dispatchers.IO) { kb.update(listOf(feed)) }.failed.isNotEmpty()
            syncContent()
        }
        val s = _state.value
        val a = Toolbox.answer(tool, question, kb.daily, kb.index.docs, s.settings.alertUfs, todayBR(), stale)
        val video = if (tool == Tool.NEWS) kb.daily.reflections.firstOrNull()?.takeIf { it.video != null } else null
        _state.update {
            it.copy(
                messages = it.messages + ChatMessage(false, a.text, a.sources, tool = tool, media = video?.let { r -> Media(Attachment.VIDEO, url = r.video, posterUrl = r.poster) }),
                mood = Mood.IDLE,
            )
        }
    }

    /** Toque num atalho do 0FF-GR1D. */
    fun use(tool: Tool) = send(tool.ask)

    private fun syncContent() = _state.update {
        it.copy(docs = kb.index.docs.size, daily = kb.daily, pages = Toolbox.pages(kb.index.docs), net = netLog.events)
    }

    /** @param onlyMedia foto ou vídeo sem texto: a pergunta padrão não vira busca. */
    private suspend fun answer(
        question: String, history: List<Turn>, screen: ScreenContext?, useWeb: Boolean, casual: Boolean, alerts: List<String>, onlyMedia: Boolean = false,
    ) {
        REMEMBER.matchEntire(question)?.let { m ->
            val fact = m.groupValues[1].trim().trimEnd('.')
            addMemory(fact)
            addAnswer("Guardado: \"$fact\". Você pode apagar em Memória.", Mood.IDLE)
            return
        }
        val crisis = Crisis.detect(question)
        if (crisis) addAnswer(Crisis.NOTE, Mood.IDLE)

        val links = kb.links.analyzeAll(question + "\n" + screen?.text.orEmpty())
        val chat = casual && screen == null && links.isEmpty()
        val hits = if (chat || onlyMedia) emptyList() else kb.index.search(question)
        var webFailed: String? = null
        val results = if (!useWeb || crisis || chat || onlyMedia) emptyList() else {
            _state.update { it.copy(status = "buscando na web") }
            runCatching { withContext(Dispatchers.IO) { web.search(question, _state.value.settings.searxUrl) } }
                .getOrElse { webFailed = it.message; emptyList() }
        }
        if (webFailed != null) notice("A busca na web falhou: $webFailed")

        val spec = model()
        val hasModel = isReady(spec)
        val prompt = PromptBuilder.build(question, hits, results, links, screen, history, _state.value.memories, spec.vision && hasModel,
            alerts = alerts, casual = chat, aigf = _state.value.settings.aigf, address = _state.value.settings.address)
        if (prompt.ordersRemoved) notice("Cuidado: este conteúdo tinha texto tentando dar ordens à IA. Tirei esse trecho.")
        _state.update {
            it.copy(trace = Trace(prompt.request.system, prompt.request.user, prompt.request.history.size, prompt.request.image != null, hasModel))
        }
        // Modelo fraco não orienta sobre segurança, direitos ou links: vale o texto conferido.
        val fixedText = !spec.advises && screen == null && (links.isNotEmpty() || hits.firstOrNull()?.doc?.kind == DocKind.GUIA)
        if (onlyMedia && (!hasModel || !spec.vision)) {
            _state.update { it.copy(trace = it.trace?.copy(usedModel = false)) }
            return addAnswer("Para eu ver fotos e vídeos, preciso de um modelo com visão: baixe o Gemma 4 E2B em Modelos.", Mood.IDLE)
        }
        if (!hasModel || fixedText) {
            _state.update { it.copy(trace = it.trace?.copy(usedModel = false)) }
            SmallTalk.withoutModel(question)?.takeIf { chat && !hasModel }?.let { return addAnswer(it, Mood.IDLE) }
            val text = withoutModel(prompt.sources, hits.map { it.doc.text }, links)
            addAnswer(if (hasModel) text else "$text\n\n(Modo sem modelo: baixe um em Modelos para conversar.)", mood(links), prompt.sources, links)
            return
        }
        if (screen?.image != null && !spec.vision && screen.text.isNullOrBlank()) {
            notice("${spec.name} não entende imagens. Use um modelo com visão para ver ${if (screen.kind == Attachment.SCREEN) "esta tela" else "fotos e vídeos"}.")
        }
        val q = Text.normalize(question)
        val urgent = if (chat) null else kb.index.search(question, limit = 12).firstOrNull { URGENT[it.doc.id]?.containsMatchIn(q) == true }?.doc
        val guide = (urgent ?: hits.firstOrNull()?.doc)?.takeIf { it.kind == DocKind.GUIA && screen == null }
        // Urgência e direitos: vale o texto conferido. A IA pode trocar uma palavra e mudar o sentido ("saia contra o vento").
        if (guide != null && guide === urgent && links.isEmpty() && _state.value.messages.none { it.guide?.id == guide.id }) {
            _state.update {
                it.copy(
                    trace = it.trace?.copy(usedModel = false), mood = Mood.IDLE,
                    messages = it.messages + ChatMessage(
                        false, if (guide.id == "guia:direitos-ato") "Isso você precisa ter na ponta da língua:\n\n${guide.text}"
                        else "Tô contigo. Faz isso, com calma:\n\n${guide.text}\n\nMe conta como você está e em que passo travou.",
                        listOf(Source(1, guide.title, guide.source.orEmpty(), guide.url)), guide = guide,
                    ),
                )
            }
            return
        }
        // Sem citação, só aparece fonte que orienta: opinião ou notícia solta confundiria.
        // Pergunta sobre a história dela não ganha guia ou site como fonte.
        val loose = if (hits.firstOrNull()?.doc?.kind == DocKind.HISTORIA) emptyList() else prompt.sources.filter { s -> hits.getOrNull(s.n - 1)?.doc?.kind.let { it == null || it == DocKind.GUIA || it == DocKind.FERRAMENTA } }
        generate(spec, prompt.request, prompt.sources, links, guide, loose)
    }

    /** Pergunta interrompida ou sem resposta fica fora: o modelo voltaria a respondê-la. */
    private fun historyOf(messages: List<ChatMessage>): List<Turn> {
        val out = ArrayList<Turn>()
        var question: String? = null
        for (m in messages) {
            if (m.fromUser && AIGF_COMMAND.matches(m.text.lowercase())) question = null
            else if (m.fromUser) question = m.text
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

    private suspend fun generate(spec: ModelSpec, request: LlmRequest, sources: List<Source>, links: List<LinkReport>, guide: Doc?, loose: List<Source>) {
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
                setLast(PromptBuilder.onlyKnownLinks(NativeText.noEmoji(raw.toString()), sources), Mood.TALKING)
            }
            // Link que só existe na imagem: se a IA o leu, passa pela mesma análise.
            val seen = if (request.image == null) emptyList() else
                kb.links.analyzeAll(raw.toString()).filter { r -> r.verdict != Verdict.OK && links.none { it.host == r.host } }
            val allLinks = links + seen
            val used = CITATION.findAll(raw).map { it.groupValues[1].toInt() }.toSet()
            _state.update {
                val last = it.messages.last()
                // Mostra as fontes citadas; se não citou nenhuma, as que orientam.
                val shown = sources.filter { s -> s.n in used }.ifEmpty { loose.take(2) }
                it.copy(
                    messages = it.messages.dropLast(1) + last.copy(
                        text = last.text.trim().ifEmpty { "Não consegui formar uma resposta. Tente perguntar de outro jeito." }, sources = shown, links = allLinks, guide = guide, interrupted = false),
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
        _state.update { it.copy(messages = listOf(ChatMessage(false, Persona.greeting(it.settings.aigf))), trace = null, screen = null, askAdult = false) }
        markAlertsRead()
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
        _state.update { it.copy(settings = s, screen = if (it.settings.autoScreen && !s.autoScreen) null else it.screen) }
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (wiping) return@launch
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
        syncContent()
        _state.update {
            it.copy(
                status = "",
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

    // ---------- modo AIGF ----------

    /** #AIGF liga e desliga. Na primeira vez, pede a confirmação de idade. Não passa pela IA nem entra no histórico. */
    private fun toggleAigf(command: String): Job? {
        val s = _state.value.settings
        val reply = when {
            s.aigf -> {
                saveSettings(s.copy(aigf = false))
                "Voltei pro modo amiga. Continuo aqui do mesmo jeito."
            }
            !s.adult -> {
                _state.update { it.copy(askAdult = true) }
                "Esse modo é só para maiores de 18. Você confirma que tem 18 anos ou mais?"
            }
            else -> {
                saveSettings(s.copy(aigf = true))
                AIGF_ON
            }
        }
        _state.update { it.copy(messages = it.messages + ChatMessage(true, command) + ChatMessage(false, reply), notice = null) }
        return Job().apply { complete() }
    }

    fun confirmAdult(yes: Boolean) {
        _state.update { it.copy(askAdult = false) }
        if (yes) saveSettings(_state.value.settings.copy(aigf = true, adult = true))
        _state.update { it.copy(messages = it.messages + ChatMessage(false, if (yes) AIGF_ON else "Tudo bem, seguimos como amigas.")) }
    }

    // ---------- avisos ----------

    private fun alertMessage(a: Alert) = ChatMessage(
        false, "${a.voice}\n\n${a.title}\n${a.text}", listOf(Source(1, "Abrir no 0FF-GR1D", a.feed.label, a.url)), alert = a,
        media = a.video?.let { Media(Attachment.VIDEO, url = it, posterUrl = a.poster) },
    )

    // ---------- fotos e vídeos do site ----------

    private val pictures = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) = size > 12
    }
    private val mediaLock = Mutex()
    private fun mediaDir() = File(cacheDir, "media")

    private fun fromSite(url: String) {
        if (!url.startsWith(FeedParser.SITE) || ".." in url) throw IllegalArgumentException("só arquivos do site do 0FF-GR1D")
    }

    /** Capa de vídeo ou imagem do site. Fica só na memória. */
    suspend fun picture(url: String): ByteArray = withContext(Dispatchers.IO) {
        fromSite(url)
        synchronized(pictures) { pictures[url] }?.let { return@withContext it }
        val bytes = try {
            http.open(url, "baixar imagem do 0FF-GR1D").use { r -> readAtMost(r.stream, MAX_PICTURE) }
        } finally {
            syncNet()
        }
        synchronized(pictures) { pictures[url] = bytes }
        bytes
    }

    /** Já está no aparelho? */
    fun cachedVideo(url: String): File? = videoFile(url).takeIf { it.isFile && it.length() > 0 }

    /**
     * Baixa o vídeo do site uma vez e guarda no cache do app (some no pânico). Passa pelo Tor se estiver ligado.
     * Ficam só os últimos [KEEP_VIDEOS].
     */
    suspend fun video(url: String): File = withContext(Dispatchers.IO) {
        fromSite(url)
        mediaLock.withLock {
            cachedVideo(url)?.let { return@withLock it }
            val target = videoFile(url)
            target.parentFile.mkdirs()
            val part = File(target.path + ".part")
            try {
                http.open(url, "baixar vídeo do W4K3").use { r ->
                    if (r.length > MAX_VIDEO) throw java.io.IOException("vídeo grande demais")
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = r.stream.read(buf)
                            if (n < 0) break
                            done += n
                            if (done > MAX_VIDEO) throw java.io.IOException("vídeo grande demais")
                            out.write(buf, 0, n)
                        }
                    }
                }
                if (!part.renameTo(target)) throw java.io.IOException("não deu para guardar o vídeo")
            } finally {
                part.delete()
                syncNet()
            }
            mediaDir().listFiles { f -> f.name.endsWith(".mp4") }?.sortedByDescending { it.lastModified() }?.drop(KEEP_VIDEOS)?.forEach { it.delete() }
            target
        }
    }

    private fun videoFile(url: String) = File(mediaDir(), url.substringAfter(FeedParser.SITE).replace(Regex("[^A-Za-z0-9._-]"), "_").take(120))

    private fun readAtMost(input: java.io.InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (out.size() + n > max) throw java.io.IOException("imagem grande demais")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /**
     * Procura novidades dos assuntos ligados. Cada uma vira mensagem da 2B; quem chama mostra a notificação.
     * @return avisos novos, ou null se não deu para consultar
     */
    suspend fun checkAlerts(): List<Alert>? {
        awaitReady()
        val on = Alerts.enabled(_state.value.settings)
        if (on.isEmpty() || wiping) return emptyList()
        return withContext(Dispatchers.IO) {
            val r = kb.update(on.map { it.feed }) // sem a trava: o pânico não espera a rede
            syncContent()
            if (r.ok.isEmpty()) return@withContext null
            alertLock.withLock {
                if (wiping) return@withLock emptyList()
                // A pessoa pode ter mudado as escolhas enquanto baixava.
                val (next, fresh) = Alerts.collect(alertState, kb.daily, _state.value.settings, r.ok.toSet(), todayBR())
                if (next != alertState) {
                    data.saveAlerts(next) // grava antes de mostrar: reiniciar não repete o aviso
                    alertState = next
                    _state.update { it.copy(alerts = next.inbox, messages = insertAlerts(it, fresh)) }
                }
                fresh
            }
        }
    }

    /** No meio de uma resposta, o aviso entra antes da pergunta em andamento, para não atrapalhar o texto que chega. */
    private fun insertAlerts(s: UiState, fresh: List<Alert>): List<ChatMessage> {
        val at = if (s.busy) s.messages.indexOfLast { it.fromUser }.coerceAtLeast(0) else s.messages.size
        return s.messages.take(at) + fresh.map(::alertMessage) + s.messages.drop(at)
    }

    /** Chamado quando a conversa está na tela. */
    fun markAlertsRead() {
        if (alertState.inbox.all { it.read }) return
        scope.launch(Dispatchers.IO) {
            alertLock.withLock {
                if (wiping) return@withLock
                alertState = alertState.copy(inbox = alertState.inbox.map { it.copy(read = true) })
                _state.update { it.copy(alerts = alertState.inbox) }
                runCatching { data.saveAlerts(alertState) }
            }
        }
    }

    /** Um aviso de exemplo, com o que já foi baixado. */
    fun sampleAlert(): Alert = kb.daily.quotes.firstOrNull()?.let(Alerts::quote)
        ?: Alert(AlertFeed.QUOTE, "teste", "Aviso de teste", "Se você está lendo isto, os avisos funcionam.", "2B na linha.", FeedParser.SITE + "motivacao.html")

    // ---------- limpeza automática ----------

    private var clearJob: Job? = null

    /** A 2B saiu da frente: com a limpeza automática ligada, apaga a conversa depois de um tempo. */
    fun onHidden(delayMs: Long = 60_000) {
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(delayMs)
            while (_state.value.busy) delay(500)
            if (_state.value.settings.autoClear) clearChat()
        }
    }

    fun onVisible() { clearJob?.cancel() }

    // ---------- pânico ----------

    @Volatile private var wiping = false

    /** Apaga conversas, memórias, ajustes e conteúdo. Com [includeModels], apaga também os modelos. */
    fun panic(includeModels: Boolean): Job = scope.launch {
        wiping = true
        try {
            // A tela some antes de qualquer espera.
            _state.update { UiState(docs = it.docs, busy = true, status = "apagando") }
            // Rede parada não pode segurar a limpeza: o que sobrar não consegue mais gravar.
            withTimeoutOrNull(1500) {
                downloadJob?.cancelAndJoin()
                turnJob?.cancelAndJoin()
            }
            unloadJob?.cancelAndJoin()
            val ok = alertLock.withLock { withContext(NonCancellable + Dispatchers.IO) {
                alertState = AlertState()
                kb.forgetFetches()
                var all = blobs.wipe()
                engineLock.withLock { closeEngine() }
                if (includeModels) all = models.deleteAll() && all
                all = File(cacheDir, "llm").let { !it.exists() || it.deleteRecursively() } && all
                all = mediaDir().let { !it.exists() || it.deleteRecursively() } && all
                synchronized(pictures) { pictures.clear() }
                netLog.clear()
                kb.load()
                all
            } }
            _state.value = UiState(
                messages = listOf(ChatMessage(false, "Tudo limpo.")), docs = kb.index.docs.size, readyModels = ready(),
                pages = Toolbox.pages(kb.index.docs), settings = Settings(alertsAsked = true),
                notice = if (ok) null else "Algum arquivo não pôde ser apagado. Desinstale o app para garantir.",
            )
        } finally {
            wiping = false
        }
    }

    companion object {
        private val REMEMBER = Regex("(?is)(?:lembre|lembra|guarde|guarda|memorize)(?:-se)?\\s+(?:de\\s+)?que\\s+(.+)")
        private val AIGF_COMMAND = Regex("\\s*#aigf\\s*")
        private const val AIGF_ON = "Modo AIGF ligado. Oi, amor. Agora é só a gente. Para voltar ao normal, mande #AIGF de novo."
        private val SCREEN_WORDS = Regex("\\b(tela|aqui|print|imagem|captura|isso|isto|essa?|esta|este|mensagem|pagina|transcrev\\w*|resum\\w*|traduz\\w*)\\b")
        /** Guia urgente e o que a pessoa precisa dizer para ele valer. */
        private val URGENT = mapOf(
            "guia:cai-no-golpe" to Regex("(^| )(cai|caiu|caimos|perdi|roubaram|transferi|enganad|fiz um pix|mandei um pix)"),
            "guia:conta-invadida" to Regex("(^| )(invad|hacke|clon)"),
            "guia:exposicao" to Regex("(^| )(expus|expos|vaz|ameac|persegu|stalk)"),
            "guia:detido-ato" to Regex("(^| )(detid|preso|presa|prend|delegacia|levad)"),
            "guia:direitos-ato" to Regex("(^| )direitos?( |$).*(ato|atos|protest|manifest|polic|abord|pres[oa]|detid)"),
            "guia:gas-ato" to Regex("(^| )(gas|gases|lacrimo|spray|bomba)( |$)"),
        )
        private const val FRESH_MS = 10 * 60_000L
        private const val MAX_PICTURE = 3 * 1024 * 1024
        private const val MAX_VIDEO = 60L * 1024 * 1024
        private const val KEEP_VIDEOS = 5
        private val CITATION = Regex("\\[(\\d{1,2})]")
    }
}
