package br.offgr1d.companion.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import br.offgr1d.companion.chat.Companion2B
import br.offgr1d.companion.chat.Tool
import br.offgr1d.companion.screen.Attachment
import br.offgr1d.companion.screen.ScreenContext
import br.offgr1d.companion.store.BlobCipher
import br.offgr1d.companion.store.BlobStore
import br.offgr1d.companion.ui.AlertActions
import br.offgr1d.companion.ui.CompanionApp
import br.offgr1d.companion.ui.PlatformActions
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat

/** Apagada no fim: deleteOnExit não apaga pasta com arquivos dentro. */
fun tempDir(name: String): File =
    Files.createTempDirectory(name).toFile().also { d -> Runtime.getRuntime().addShutdownHook(Thread { d.deleteRecursively() }) }

/** Desenha a conversa sem abrir janela. As imagens ficam em build/render para conferir a olho. */
class RenderTest {
    private object Plain : BlobCipher {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(sealed: ByteArray) = sealed
        override fun destroyKey() {}
    }

    private fun render(name: String, compact: Boolean, tab: String? = null, height: Int = 860, prepare: suspend Companion2B.() -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val dir = tempDir("2b-render")
        val store = BlobStore(File(dir, "d"), Plain)
        System.getenv("FEEDS_DIR")?.let { d -> listOf("reflections", "motivations", "organize").forEach { store.write("feed-$it", File(d, "$it.json").readBytes()) } }
        val app = Companion2B(scope, store, File(dir, "m"), File(dir, "c"), { error("sem modelo") })
        app.awaitReady()
        app.prepare()
        val scene = ImageComposeScene(412, height, Density(1f)) {
            CompanionApp(app, PlatformActions(openUrl = {}, captureScreen = {}, alerts = AlertActions({}, {}, true, {}), pickMedia = {}), compact, onClose = if (compact) ({}) else null, startTab = tab)
        }
        try {
            repeat(5) { scene.render(it * 50_000_000L) }
            val png = scene.render(400_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File("build/render").mkdirs()
            File("build/render/$name.png").writeBytes(png)
            assertTrue(png.size > 5000)
        } finally {
            scene.close()
            scope.cancel()
        }
    }

    /** Foto enviada, vídeo do W4K3 recebido (com FEEDS_DIR e rede) e um vídeo esperando para ir. */
    @Test fun midia() = render("midia", compact = false, height = 2300) {
        val png = File("src/main/resources/icone.png").readBytes()
        attachScreen(ScreenContext(null, png, Attachment.PHOTO))
        send("")!!.join()
        if (System.getenv("FEEDS_DIR") != null) {
            send(Tool.NEWS.ask)!!.join()
            state.value.messages.last().media?.posterUrl?.let { picture(it) }
        }
        attachScreen(ScreenContext(null, png, Attachment.VIDEO))
    }

    /** Os três quadros do rosto, grandes, para conferir contra a arte original. */
    @Test fun rosto() {
        val scene = ImageComposeScene(900, 260, Density(1f)) {
            androidx.compose.foundation.layout.Row {
                listOf(br.offgr1d.companion.chat.Mood.IDLE, br.offgr1d.companion.chat.Mood.ALERT).forEach { m ->
                    br.offgr1d.companion.ui.Face(m, androidx.compose.ui.Modifier.size(androidx.compose.ui.unit.DpSize(300.dp, 243.dp)))
                }
                androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.size(androidx.compose.ui.unit.DpSize(300.dp, 243.dp))) {
                    val k = size.width / br.offgr1d.companion.ui.FaceArt.WIDTH
                    br.offgr1d.companion.ui.FaceFrame.entries.drop(1).forEachIndexed { i, f ->
                        f.shapes.forEach { s ->
                            val p = androidx.compose.ui.graphics.Path().apply {
                                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                                s.rings.forEach { r -> moveTo(r[0] * k / 2 + i * size.width / 2, r[1] * k / 2 + 60f); for (j in 2 until r.size step 2) lineTo(r[j] * k / 2 + i * size.width / 2, r[j + 1] * k / 2 + 60f); close() }
                            }
                            drawPath(p, br.offgr1d.companion.ui.Palette.Face)
                        }
                    }
                }
            }
        }
        val png = scene.render(0).encodeToData(EncodedImageFormat.PNG)!!.bytes
        scene.close()
        File("build/render").mkdirs()
        File("build/render/rosto.png").writeBytes(png)
    }

    @Test fun inicio() = render("inicio", compact = false) {}

    @Test fun golpe() = render("golpe", compact = false) { send("recebi https://g1obo.com/premio isso é golpe?")!!.join() }

    @Test fun painel() = render("painel", compact = true) {
        send("quais meus direitos num protesto?")!!.join()
        attachScreen(ScreenContext("texto da tela", byteArrayOf(1)))
        setWeb(true)
    }

    @Test fun atos() = render("atos", compact = false) { use(Tool.GEAR)!!.join() }

    @Test fun hoje() = render("hoje", compact = false, tab = "HOJE") {}

    @Test fun ajustes() = render("ajustes", compact = false, tab = "AJUSTES", height = 2000) {}
}
