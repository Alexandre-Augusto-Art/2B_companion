package br.offgr1d.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.offgr1d.companion.chat.ChatMessage
import br.offgr1d.companion.chat.Companion2B
import br.offgr1d.companion.chat.Mood
import br.offgr1d.companion.chat.Persona
import br.offgr1d.companion.chat.Tool
import br.offgr1d.companion.chat.UiState
import br.offgr1d.companion.knowledge.Doc
import br.offgr1d.companion.knowledge.FeedParser
import br.offgr1d.companion.knowledge.KnowledgeIndex
import br.offgr1d.companion.knowledge.brDate
import br.offgr1d.companion.knowledge.todayBR
import br.offgr1d.companion.links.Level
import br.offgr1d.companion.links.Verdict
import br.offgr1d.companion.llm.ModelCatalog
import br.offgr1d.companion.store.Settings

object Palette {
    val Bg = Color(0xFF0E0D10)
    val Panel = Color(0xFF1B191C)
    val Line = Color(0xFF3A363D)
    val Text = Color(0xFFE0F2FE)
    val Dim = Color(0xFF9CA3AF)
    val Green = Color(0xFF00FF84)
    val Cyan = Color(0xFF22D3EE)
    val Yellow = Color(0xFFFACC15)
    val Red = Color(0xFFF43F5E)
    /** Cor do rosto da 2B, a mesma da arte original. */
    val Face = Color(0xFF00FF00)
}

fun Mood.color(): Color = when (this) {
    Mood.IDLE -> Palette.Green
    Mood.THINKING -> Palette.Yellow
    Mood.TALKING -> Palette.Cyan
    Mood.ALERT -> Palette.Red
}

/** O que muda de um sistema para outro. */
class PlatformActions(
    val openUrl: (String) -> Unit,
    /** Pede uma captura da tela; null se não houver. */
    val captureScreen: (() -> Unit)?,
    /** Avisos como notificação do sistema; null se esta versão não tiver. */
    val alerts: AlertActions? = null,
    val autoScreenNote: String = "A tela fica só na memória e só é usada se você enviar.",
    /** Controles próprios do sistema nos Ajustes (bolha, assistente padrão...). */
    val extraSettings: @Composable ColumnScope.() -> Unit = {},
    /** O rosto da 2B. */
    val avatar: @Composable (Mood, Modifier) -> Unit = { mood, m -> Face(mood, m) },
)

class AlertActions(
    /** Pede a permissão de notificação e começa a procurar novidades. */
    val enabled: () -> Unit,
    val test: () -> Unit,
    /** O sistema deixa este app notificar? */
    val allowed: Boolean,
    val openSettings: () -> Unit,
)

private enum class Tab(val label: String) { CHAT("CHAT"), TODAY("HOJE"), MODELS("MODELOS"), MEMORY("MEMÓRIA"), INSPECT("INSPETOR"), SETTINGS("AJUSTES") }

@Composable
fun CompanionApp(app: Companion2B, platform: PlatformActions, compact: Boolean = false, onClose: (() -> Unit)? = null, startTab: String? = null) {
    val state by app.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf((Tab.entries.firstOrNull { it.label == startTab } ?: Tab.CHAT).name) }
    val current = if (compact) Tab.CHAT else Tab.valueOf(tab)
    LaunchedEffect(state.chatFocus) { if (state.chatFocus > 0) tab = Tab.CHAT.name }
    LaunchedEffect(current, state.alerts) { if (current == Tab.CHAT) app.markAlertsRead() }

    MaterialTheme(darkColorScheme(primary = Palette.Green, background = Palette.Bg, surface = Palette.Panel, onSurface = Palette.Text)) {
        Surface(color = Palette.Bg, contentColor = Palette.Text, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Header(state, platform, onClose)
                if (!compact) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Tab.entries.forEach { t ->
                        TextButton(onClick = { tab = t.name }) { Mono(t.label, if (t == current) Palette.Green else Palette.Dim, 12) }
                    }
                }
                state.notice?.let { n ->
                    Row(Modifier.fillMaxWidth().background(Palette.Panel).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(n, Modifier.weight(1f), color = Palette.Yellow, fontSize = 13.sp)
                        TextButton(onClick = app::dismissNotice) { Mono("OK", Palette.Yellow) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    when (current) {
                        Tab.CHAT -> Chat(app, state, platform, compact)
                        Tab.TODAY -> Today(app, state, platform) { tab = Tab.CHAT.name }
                        Tab.MODELS -> Models(app, state)
                        Tab.MEMORY -> Memory(app, state)
                        Tab.INSPECT -> Inspector(state)
                        Tab.SETTINGS -> SettingsScreen(app, state, platform)
                    }
                }
            }
        }
    }
}

@Composable
private fun Mono(text: String, color: Color = Palette.Text, size: Int = 13) =
    Text(text, color = color, fontSize = size.sp, fontFamily = FontFamily.Monospace)

@Composable
private fun Header(state: UiState, platform: PlatformActions, onClose: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        platform.avatar(state.mood, Modifier.size(60.dp, 48.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(Persona.NAME, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Mono(state.status.ifEmpty { idleStatus(state) }, state.mood.color(), 11)
        }
        if (onClose != null) TextButton(onClick = onClose) { Mono("FECHAR", Palette.Dim, 12) }
    }
}

/** O que fica ligado sozinho aparece sempre, para ninguém esquecer. */
private fun idleStatus(state: UiState): String = listOfNotNull(
    "IA local",
    "AIGF".takeIf { state.settings.aigf },
    "web ligada".takeIf { state.settings.web },
    "lê a tela".takeIf { state.settings.autoScreen },
    "rede só por Tor".takeIf { state.settings.useTor },
).joinToString(" · ")

// ---------- chat ----------

@Composable
private fun Chat(app: Companion2B, state: UiState, platform: PlatformActions, compact: Boolean) {
    val list = rememberLazyListState()
    var draft by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        if (state.messages.isNotEmpty()) list.scrollToItem(state.messages.lastIndex, Int.MAX_VALUE)
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.messages) { Bubble(it, platform) }
            if (state.askAdult) item { AskAdult(app) }
            platform.alerts?.takeIf { !compact && !state.settings.alertsAsked }?.let { a -> item { AskAlerts(app, state, a) } }
        }
        state.screen?.let { s ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Mono(if (state.screenUsed) "TELA GUARDADA (${s.describe()}): volta se você falar dela" else "TELA PRONTA (${s.describe()}): só vai se você enviar", Palette.Cyan, 11) }
                TextButton(onClick = { app.attachScreen(null) }) { Mono("TIRAR", Palette.Red, 11) }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tool.entries.forEach { t -> Chip(t.label, false, enabled = !state.busy) { app.use(t) } }
            Chip(if (state.settings.web) "WEB: LIGADA" else "WEB: DESLIGADA", state.settings.web) { app.setWeb(!state.settings.web) }
            platform.captureScreen?.let { capture -> Chip("LER TELA", false) { capture() } }
            Chip("LIMPAR", false, enabled = !state.busy) { app.clearChat() }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it.take(2000) }, modifier = Modifier.weight(1f), maxLines = 5,
                placeholder = { Text("Fale com a 2B", color = Palette.Dim) },
            )
            if (state.busy) TextButton(onClick = app::stop) { Mono("PARAR", Palette.Red) }
            else TextButton(onClick = { if (app.send(draft) != null) draft = "" }, enabled = draft.isNotBlank()) { Mono("ENVIAR", Palette.Green) }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val color = if (on) Palette.Cyan else if (enabled) Palette.Text else Palette.Dim
    Box(
        Modifier.border(1.dp, if (on) Palette.Cyan else Palette.Line, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 10.dp, vertical = 12.dp),
    ) { Mono(label, color, 11) }
}

@Composable
private fun Bubble(m: ChatMessage, platform: PlatformActions) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start) {
        Column(
            Modifier.widthIn(max = 520.dp).background(if (m.fromUser) Color(0xFF12302A) else Palette.Panel, RoundedCornerShape(10.dp))
                .border(1.dp, Palette.Line, RoundedCornerShape(10.dp)).padding(12.dp),
        ) {
            if (m.withScreen) Mono("[com a tela]", Palette.Cyan, 10)
            m.alert?.let { Mono("AVISO · ${it.feed.label}", Palette.Yellow, 10) }
            m.links.forEach { r ->
                Mono("LINK ${r.summary()}", when (r.verdict) {
                    Verdict.DANGER -> Palette.Red
                    Verdict.WARN -> Palette.Yellow
                    Verdict.OK -> Palette.Green
                    Verdict.UNKNOWN -> Palette.Dim
                }, 12)
                r.signals.filter { it.level == Level.DANGER || it.level == Level.WARN }.forEach { Text("${it.title}: ${it.detail}", color = Palette.Dim, fontSize = 12.sp) }
            }
            SelectionContainer { Text(m.text.ifEmpty { "…" }, color = Palette.Text, fontSize = 15.sp, lineHeight = 21.sp) }
            if (m.generated && m.text.isNotEmpty()) Mono(if (m.interrupted) "IA local, resposta incompleta" else "IA local: pode errar, confira", Palette.Dim, 10)
            if (m.generated) m.guide?.let { Guide(it) }
            m.sources.forEach { s ->
                val url = s.url
                val label = "[${s.n}] ${s.title} (${s.origin})"
                if (url == null) Mono(label, Palette.Dim, 11)
                else Box(Modifier.fillMaxWidth().clickable(role = Role.Button) { platform.openUrl(url) }.padding(vertical = 6.dp)) { Mono(label, Palette.Cyan, 11) }
            }
        }
    }
}

/** A IA resume e pode esquecer um passo: o guia inteiro fica à mão. */
@Composable
private fun Guide(doc: Doc) {
    var open by rememberSaveable(doc.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).border(1.dp, Palette.Line, RoundedCornerShape(6.dp))
            .clickable(role = Role.Button) { open = !open }.padding(10.dp),
    ) {
        Mono("${if (open) "−" else "+"} GUIA CONFERIDO: ${doc.title}", Palette.Green, 11)
        if (open) Text(doc.text, fontSize = 14.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun AskAdult(app: Companion2B) {
    Row(Modifier.fillMaxWidth().border(1.dp, Palette.Line, RoundedCornerShape(10.dp)).padding(4.dp)) {
        TextButton(onClick = { app.confirmAdult(true) }) { Mono("TENHO 18 OU MAIS", Palette.Green) }
        TextButton(onClick = { app.confirmAdult(false) }) { Mono("CANCELAR", Palette.Dim) }
    }
}

@Composable
private fun AskAlerts(app: Companion2B, state: UiState, alerts: AlertActions) {
    Column(Modifier.fillMaxWidth().border(1.dp, Palette.Green, RoundedCornerShape(10.dp)).padding(12.dp)) {
        Text("Quer que eu te avise quando sair a notícia do W4K3 PR0T0C0L, a mensagem do dia e atos novos?")
        Text("O app consulta o site do 0FF-GR1D mais ou menos de hora em hora. Dá para mudar em Ajustes.", color = Palette.Dim, fontSize = 12.sp)
        Row {
            TextButton(onClick = {
                app.saveSettings(state.settings.copy(alertW4k3 = true, alertQuote = true, alertActs = true, alertsAsked = true))
                alerts.enabled()
            }) { Mono("QUERO", Palette.Green) }
            TextButton(onClick = { app.saveSettings(state.settings.copy(alertsAsked = true)) }) { Mono("AGORA NÃO", Palette.Dim) }
        }
    }
}

// ---------- hoje ----------

@Composable
private fun Link(label: String, url: String, platform: PlatformActions) =
    Box(Modifier.fillMaxWidth().clickable(role = Role.Button) { platform.openUrl(url) }.padding(vertical = 8.dp)) { Mono(label, Palette.Cyan, 12) }

@Composable
private fun Today(app: Companion2B, state: UiState, platform: PlatformActions, openChat: () -> Unit) = Page {
    val d = state.daily
    val today = todayBR()
    TextButton(onClick = { app.updateContent() }, enabled = state.status.isEmpty()) {
        Mono(if (state.status.isEmpty()) "ATUALIZAR AGORA" else state.status.uppercase(), Palette.Cyan)
    }
    @Composable
    fun ask(tool: Tool) = TextButton(onClick = { if (app.use(tool) != null) openChat() }, enabled = !state.busy) { Mono("PERGUNTAR À 2B", Palette.Green, 12) }

    Mono("W4K3 PR0T0C0L", Palette.Green)
    val r = d.reflections.firstOrNull()
    if (r == null) Text("Toque em ATUALIZAR para baixar.", color = Palette.Dim) else {
        Mono(if (r.date == today.toString()) "HOJE" else brDate(r.date), Palette.Dim, 11)
        Text(r.title, fontWeight = FontWeight.Bold)
        Text(r.excerpt)
        val news = r.newsLink
        if (news != null) Link("Notícia: ${r.newsTitle} (${r.newsSource})", news, platform)
        Link("Ler a reflexão completa", FeedParser.SITE + "w4k3-protocol.html", platform)
        ask(Tool.NEWS)
    }

    Mono("MENSAGEM DO DIA", Palette.Green)
    val q = d.quotes.firstOrNull()
    if (q == null) Text("Toque em ATUALIZAR para baixar.", color = Palette.Dim) else {
        Mono(if (q.date == today.toString()) "HOJE" else brDate(q.date), Palette.Dim, 11)
        Text("\"${q.content}\"")
        q.author?.let { Text(it, color = Palette.Dim, fontSize = 13.sp) }
    }

    val ufs = state.settings.alertUfs
    Mono("PRÓXIMOS ATOS${if (ufs.isEmpty()) "" else ": " + ufs.sorted().joinToString(", ")}", Palette.Green)
    val acts = remember(d, ufs) { d.upcoming(ufs, today) }
    if (acts.isEmpty()) Text(if (d.acts.isEmpty()) "Toque em ATUALIZAR para baixar." else "Nenhum ato convocado na lista.", color = Palette.Dim)
    acts.take(8).forEach { a ->
        Column {
            Mono("${a.place()} · ${a.whenText(today)}", if (a.date == today.toString()) Palette.Yellow else Palette.Text, 12)
            Text(a.title, fontSize = 14.sp)
            a.url?.let { Link("Fonte: ${a.source ?: "ver"}", it, platform) }
        }
    }
    if (acts.isNotEmpty()) Text("Confirme data e local na fonte antes de ir.", color = Palette.Dim, fontSize = 13.sp)
    Link("Todos os atos, checklist e cuidados", FeedParser.SITE + "organize-se.html", platform)
    TextButton(onClick = { if (app.use(Tool.GEAR) != null) openChat() }, enabled = !state.busy) { Mono("COMO SE EQUIPAR", Palette.Green, 12) }

    Mono("FERRAMENTAS DO 0FF-GR1D", Palette.Green)
    state.pages.forEach { p -> p.url?.let { Link(p.title, it, platform) } }

    if (state.alerts.isNotEmpty()) {
        Mono("ÚLTIMOS AVISOS", Palette.Green)
        state.alerts.asReversed().forEach { a ->
            Column {
                Text(a.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(a.text, color = Palette.Dim, fontSize = 13.sp)
            }
        }
    }
}

// ---------- outras telas ----------

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
)

@Composable
private fun Models(app: Companion2B, state: UiState) = Page {
    Text("O modelo roda no aparelho. O download vem do Hugging Face e é conferido antes de usar. Prefira Wi-Fi.", color = Palette.Dim, fontSize = 13.sp)
    ModelCatalog.ALL.forEach { m ->
        val ready = m.id in state.readyModels
        val active = ready && state.settings.modelId == m.id
        Column(Modifier.fillMaxWidth().border(1.dp, if (active) Palette.Green else Palette.Line, RoundedCornerShape(8.dp)).padding(12.dp)) {
            Mono("${m.name}  ${m.bytes / 1_000_000} MB  ${m.license}", if (active) Palette.Green else Palette.Text)
            Text(m.note, color = Palette.Dim, fontSize = 13.sp)
            if (state.downloading == m.id) {
                LinearProgressIndicator(progress = { (state.downloaded.toFloat() / m.bytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                TextButton(onClick = app::cancelDownload) { Mono("PAUSAR", Palette.Yellow) }
            } else Row {
                if (!ready) TextButton(onClick = { app.download(m) }, enabled = state.downloading == null) { Mono("BAIXAR", Palette.Green) }
                if (ready && !active) TextButton(onClick = { app.saveSettings(state.settings.copy(modelId = m.id)) }) { Mono("USAR", Palette.Green) }
                if (ready) TextButton(onClick = { app.deleteModel(m) }) { Mono("APAGAR", Palette.Red) }
            }
        }
    }
}

@Composable
private fun Memory(app: Companion2B, state: UiState) = Page {
    Text("A 2B só lembra do que você pedir (\"lembre que...\"). Fica cifrado no aparelho.", color = Palette.Dim, fontSize = 13.sp)
    if (state.memories.isEmpty()) Mono("Nenhuma memória.", Palette.Dim)
    state.memories.forEach { m ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m, Modifier.weight(1f))
            TextButton(onClick = { app.removeMemory(m) }) { Mono("APAGAR", Palette.Red, 11) }
        }
    }
}

@Composable
private fun Inspector(state: UiState) = Page {
    Mono("O QUE SAIU DO APARELHO", Palette.Green)
    if (state.net.isEmpty()) Mono("Nada nesta sessão.", Palette.Dim, 12)
    state.net.asReversed().forEach { Mono("${it.at.toString().substring(11, 19)} ${it.purpose}${if (it.viaTor) " (Tor)" else ""}\n${it.url}\n${it.result}", Palette.Text, 11) }
    Mono("O QUE A 2B VIU NA ÚLTIMA PERGUNTA", Palette.Green)
    val t = state.trace
    if (t == null) Mono("Nenhuma pergunta ainda.", Palette.Dim, 12)
    else {
        Mono("modelo: ${if (t.usedModel) "sim" else "não"} | histórico: ${t.historyTurns} falas | imagem: ${if (t.hadImage) "sim" else "não"}", Palette.Dim, 11)
        SelectionContainer { Mono(t.user, Palette.Text, 11) }
        Mono("INSTRUÇÕES", Palette.Green)
        SelectionContainer { Mono(t.system, Palette.Dim, 11) }
    }
}

@Composable
private fun SettingsScreen(app: Companion2B, state: UiState, platform: PlatformActions) = Page {
    val s = state.settings
    var searx by remember(s.searxUrl) { mutableStateOf(s.searxUrl) }
    var askPanic by remember { mutableStateOf(false) }

    Mono("CONVERSA", Palette.Green)
    Toggle("Buscar na web", s.web, "Ligado: o texto de cada pergunta vai para a Wikipédia ou o seu SearXNG. Papo, atalhos do site e memórias não vão.") { app.setWeb(it) }
    if (platform.captureScreen != null) Toggle("Ler a tela sozinha", s.autoScreen, platform.autoScreenNote) { app.saveSettings(s.copy(autoScreen = it)) }
    Toggle("Limpar a conversa sozinha", s.autoClear, "Um minuto depois de você sair da 2B.") {
        app.saveSettings(s.copy(autoClear = it, saveHistory = if (it) false else s.saveHistory))
    }
    TextButton(onClick = app::clearChat, enabled = !state.busy) { Mono("LIMPAR AGORA", Palette.Cyan) }
    Text("Como a 2B fala com você")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("" to "SEM GÊNERO", "f" to "FEMININO", "m" to "MASCULINO").forEach { (v, label) ->
            Chip(label, s.address == v) { app.saveSettings(s.copy(address = v)) }
        }
    }
    Text("Sem gênero: ela evita palavras como amiga ou amigo e segue o que você usar para falar de si.", color = Palette.Dim, fontSize = 12.sp)

    platform.alerts?.let { AlertSettings(app, state, it) }

    Mono("CONTEÚDO DO 0FF-GR1D: ${state.docs} itens", Palette.Green)
    TextButton(onClick = { app.updateContent() }, enabled = state.status.isEmpty()) { Mono("ATUALIZAR AGORA", Palette.Cyan) }

    platform.extraSettings(this)

    Mono("PRIVACIDADE", Palette.Green)
    Toggle("Usar Tor (precisa do Orbot ligado)", s.useTor) { app.saveSettings(s.copy(useTor = it)) }
    Toggle("Guardar conversas, cifradas", s.saveHistory) { app.saveSettings(s.copy(saveHistory = it, autoClear = if (it) false else s.autoClear)) }
    Toggle("Bloquear prints deste app", s.blockScreenshots) { app.saveSettings(s.copy(blockScreenshots = it)) }
    Toggle("Usar a placa de vídeo (experimental)", s.useGpu) { app.saveSettings(s.copy(useGpu = it)) }

    Mono("ONDE BUSCAR NA WEB", Palette.Green)
    OutlinedTextField(searx, { searx = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Endereço do seu SearXNG (vazio = Wikipédia)") })
    TextButton(onClick = { app.saveSettings(s.copy(searxUrl = searx.trim())) }, enabled = searx != s.searxUrl) { Mono("SALVAR", Palette.Cyan) }

    Mono("PÂNICO", Palette.Red)
    Text("Apaga conversas, memórias e ajustes deste aparelho.", color = Palette.Dim, fontSize = 13.sp)
    TextButton(onClick = { askPanic = true }) { Mono("APAGAR TUDO", Palette.Red) }

    if (askPanic) AlertDialog(
        onDismissRequest = { askPanic = false }, containerColor = Palette.Panel,
        title = { Mono("APAGAR TUDO?", Palette.Red, 16) },
        text = { Text("Não dá para desfazer. Apagar também o modelo demora para baixar de novo, mas não deixa rastro do tamanho dele.") },
        confirmButton = {
            Column {
                TextButton(onClick = { askPanic = false; app.panic(includeModels = true) }) { Mono("DADOS E MODELOS", Palette.Red) }
                TextButton(onClick = { askPanic = false; app.panic(includeModels = false) }) { Mono("SÓ DADOS", Palette.Red) }
            }
        },
        dismissButton = { TextButton(onClick = { askPanic = false }) { Mono("CANCELAR", Palette.Dim) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertSettings(app: Companion2B, state: UiState, alerts: AlertActions) {
    val s = state.settings
    fun save(n: Settings) {
        app.saveSettings(n.copy(alertsAsked = true))
        if (n.anyAlert) alerts.enabled()
    }
    Mono("AVISOS", Palette.Green)
    if (s.anyAlert && !alerts.allowed) {
        Text("As notificações da 2B estão bloqueadas no Android.", color = Palette.Yellow, fontSize = 13.sp)
        TextButton(onClick = alerts.openSettings) { Mono("PERMITIR", Palette.Yellow) }
    }
    Toggle("Notícia do W4K3 PR0T0C0L", s.alertW4k3) { save(s.copy(alertW4k3 = it)) }
    Toggle("Mensagem do dia", s.alertQuote) { save(s.copy(alertQuote = it)) }
    Toggle("Atos e protestos", s.alertActs) { save(s.copy(alertActs = it)) }
    Text("Estados dos atos: ${if (s.alertUfs.isEmpty()) "todos" else s.alertUfs.sorted().joinToString(", ")}. Atos nacionais sempre entram.", color = Palette.Dim, fontSize = 13.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KnowledgeIndex.UF_NAMES.keys.forEach { uf ->
            val on = uf in s.alertUfs
            Chip(uf, on) { app.saveSettings(s.copy(alertUfs = if (on) s.alertUfs - uf else s.alertUfs + uf)) }
        }
    }
    Text("O app consulta o site mais ou menos de hora em hora, sem servidor no meio. O Android pode atrasar para economizar bateria.", color = Palette.Dim, fontSize = 13.sp)
    Row {
        TextButton(onClick = alerts.test) { Mono("TESTAR AVISO", Palette.Cyan) }
        TextButton(onClick = alerts.openSettings) { Mono("SOM E BLOQUEIO", Palette.Cyan) }
    }
}

@Composable
fun Toggle(label: String, on: Boolean, note: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label)
            if (note != null) Text(note, color = Palette.Dim, fontSize = 12.sp)
        }
        Switch(on, onChange, Modifier.semantics { contentDescription = label })
    }
}
