package br.offgr1d.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import br.offgr1d.companion.chat.UiState
import br.offgr1d.companion.links.Level
import br.offgr1d.companion.links.Verdict
import br.offgr1d.companion.llm.ModelCatalog

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
    /** Controles próprios do sistema nos Ajustes (bolha, assistente padrão...). */
    val extraSettings: @Composable ColumnScope.() -> Unit = {},
    /** Imagem da 2B. Enquanto a arte oficial não chega, usa o símbolo provisório. */
    val avatar: @Composable (Mood, Modifier) -> Unit = { mood, m -> DefaultAvatar(mood, m) },
)

private enum class Tab(val label: String) { CHAT("CHAT"), MODELS("MODELOS"), MEMORY("MEMÓRIA"), INSPECT("INSPETOR"), SETTINGS("AJUSTES") }

@Composable
fun CompanionApp(app: Companion2B, platform: PlatformActions, compact: Boolean = false, onClose: (() -> Unit)? = null) {
    val state by app.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.CHAT.name) }
    val current = if (compact) Tab.CHAT else Tab.valueOf(tab)

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
                        Tab.CHAT -> Chat(app, state, platform)
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
        platform.avatar(state.mood, Modifier.size(44.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(Persona.NAME, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Mono(state.status.ifEmpty { if (state.settings.useTor) "local, rede só por Tor" else "local" }, state.mood.color(), 11)
        }
        if (onClose != null) TextButton(onClick = onClose) { Mono("FECHAR", Palette.Dim, 12) }
    }
}

// ---------- chat ----------

@Composable
private fun Chat(app: Companion2B, state: UiState, platform: PlatformActions) {
    val list = rememberLazyListState()
    var draft by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        if (state.messages.isNotEmpty()) list.scrollToItem(state.messages.lastIndex, Int.MAX_VALUE)
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.messages) { Bubble(it, platform) }
        }
        state.screen?.let { s ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Mono("TELA PRONTA (${s.describe()}): só vai se você enviar", Palette.Cyan, 11) }
                TextButton(onClick = { app.attachScreen(null) }) { Mono("TIRAR", Palette.Red, 11) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(if (state.web) "WEB: LIGADA" else "WEB: DESLIGADA", state.web) { app.setWeb(!state.web) }
            platform.captureScreen?.let { capture -> Chip("LER TELA", false) { capture() } }
            if (!state.busy) Chip("LIMPAR", false) { app.clearChat() }
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
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val color = if (on) Palette.Cyan else Palette.Dim
    Box(
        Modifier.border(1.dp, if (on) Palette.Cyan else Palette.Line, RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp, vertical = 12.dp),
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
            m.sources.forEach { s ->
                val url = s.url
                val label = "[${s.n}] ${s.title} (${s.origin})"
                if (url == null) Mono(label, Palette.Dim, 11)
                else Box(Modifier.fillMaxWidth().clickable(role = Role.Button) { platform.openUrl(url) }.padding(vertical = 6.dp)) { Mono(label, Palette.Cyan, 11) }
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

    Mono("CONTEÚDO DO 0FF-GR1D: ${state.docs} itens", Palette.Green)
    TextButton(onClick = { app.updateContent() }, enabled = state.status.isEmpty()) { Mono("ATUALIZAR AGORA", Palette.Cyan) }

    platform.extraSettings(this)

    Mono("PRIVACIDADE", Palette.Green)
    Toggle("Usar Tor (precisa do Orbot ligado)", s.useTor) { app.saveSettings(s.copy(useTor = it)) }
    Toggle("Guardar conversas, cifradas", s.saveHistory) { app.saveSettings(s.copy(saveHistory = it)) }
    Toggle("Bloquear prints deste app", s.blockScreenshots) { app.saveSettings(s.copy(blockScreenshots = it)) }
    Toggle("Usar a placa de vídeo (experimental)", s.useGpu) { app.saveSettings(s.copy(useGpu = it)) }

    Mono("BUSCA NA WEB", Palette.Green)
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

@Composable
fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(on, onChange, Modifier.semantics { contentDescription = label })
    }
}
