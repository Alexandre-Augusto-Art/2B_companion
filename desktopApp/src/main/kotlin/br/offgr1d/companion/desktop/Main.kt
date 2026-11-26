package br.offgr1d.companion.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import br.offgr1d.companion.chat.Companion2B
import br.offgr1d.companion.llm.LiteRtEngine
import br.offgr1d.companion.screen.ScreenContext
import br.offgr1d.companion.store.BlobStore
import br.offgr1d.companion.ui.CompanionApp
import br.offgr1d.companion.ui.PlatformActions
import java.awt.Color
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.awt.Image as AwtImage
import java.awt.MouseInfo
import java.awt.Robot
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** %LOCALAPPDATA%: não vai para o perfil móvel nem para a nuvem. */
fun dataRoot(): File = File(System.getenv("LOCALAPPDATA") ?: error("só Windows por enquanto"), "2B_companion")

fun createCompanion(root: File = dataRoot()): Companion2B = Companion2B(
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    store = BlobStore(File(root, "data"), DpapiCipher(File(root, "key.bin"))),
    modelDir = File(root, "models"),
    cacheDir = File(root, "cache"),
    engineFactory = { LiteRtEngine() },
)

/** Captura o monitor onde está o mouse. Fica só na memória. */
fun captureScreen(): ByteArray {
    val device = MouseInfo.getPointerInfo()?.device ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
    val shot = Robot(device).createScreenCapture(device.defaultConfiguration.bounds)
    val scale = minOf(1.0, 1024.0 / maxOf(shot.width, shot.height))
    val w = (shot.width * scale).toInt()
    val h = (shot.height * scale).toInt()
    val small = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    small.createGraphics().apply {
        drawImage(shot.getScaledInstance(w, h, AwtImage.SCALE_SMOOTH), 0, 0, null)
        dispose()
    }
    return ByteArrayOutputStream().also { ImageIO.write(small, "jpg", it) }.toByteArray()
}

private val appIcon: BitmapPainter? =
    Companion2B::class.java.getResourceAsStream("/icone.png")?.use { BitmapPainter(it.readBytes().decodeToImageBitmap()) }

/** Teste sem janela: `"2B Companion.exe" --teste "pergunta" saida.txt` grava a resposta e sai. */
private fun selfTest(question: String, out: File): Unit = kotlinx.coroutines.runBlocking {
    val c = createCompanion()
    c.awaitReady()
    val start = System.nanoTime()
    val text = runCatching {
        c.send(question)?.join()
        val m = c.state.value.messages.last()
        "modelo: ${c.state.value.settings.modelId} pronto=${c.state.value.readyModels}\nIA: ${m.generated}\n" +
            "${(System.nanoTime() - start) / 1_000_000} ms\n\n${m.text}"
    }.getOrElse { "ERRO: $it\n" + it.stackTraceToString() }
    out.writeText(text)
    kotlin.system.exitProcess(0)
}

fun main(args: Array<String>) {
    if (args.size == 3 && args[0] == "--teste") selfTest(args[1], File(args[2])) else gui()
}

private fun gui() = application {
    val companion = remember { createCompanion() }
    val state by companion.state.collectAsState()
    var chatOpen by remember { mutableStateOf(true) }
    var capturing by remember { mutableStateOf(false) }
    val platform = remember {
        PlatformActions(
            openUrl = { url -> if (url.startsWith("https://")) runCatching { Desktop.getDesktop().browse(URI(url)) } },
            captureScreen = { capturing = true },
            autoScreenNote = "Ao abrir a conversa pelo símbolo da 2B. Fica só na memória e só é usada se você enviar.",
        )
    }

    LaunchedEffect(capturing) {
        if (!capturing) return@LaunchedEffect
        delay(400) // as janelas da 2B somem antes da captura
        val jpeg = withContext(Dispatchers.IO) { runCatching { captureScreen() }.getOrNull() }
        companion.attachScreen(ScreenContext(null, jpeg))
        capturing = false
        chatOpen = true
    }

    // A 2B na área de trabalho: arraste para mover, clique para abrir a conversa.
    Window(
        onCloseRequest = ::exitApplication, title = "2B", undecorated = true, transparent = true, alwaysOnTop = true,
        resizable = false, focusable = false, visible = !capturing,
        state = rememberWindowState(size = DpSize(88.dp, 88.dp), position = WindowPosition(Alignment.BottomEnd)),
    ) {
        window.background = Color(0, 0, 0, 0)
        WindowDraggableArea(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures {
            when {
                chatOpen -> { chatOpen = false; companion.attachScreen(null); companion.onHidden() }
                companion.state.value.settings.autoScreen -> { companion.onVisible(); capturing = true }
                else -> { companion.onVisible(); chatOpen = true }
            }
        } }) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { platform.avatar(state.mood, Modifier.fillMaxSize()) }
        }
    }

    if (chatOpen) Window(
        onCloseRequest = { chatOpen = false; companion.attachScreen(null); companion.onHidden() }, title = "2B Companion", visible = !capturing,
        icon = appIcon,
        state = rememberWindowState(size = DpSize(460.dp, 720.dp)),
    ) {
        CompanionApp(companion, platform)
    }
}
