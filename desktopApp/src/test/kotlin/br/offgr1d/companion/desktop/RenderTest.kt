package br.offgr1d.companion.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import br.offgr1d.companion.chat.Companion2B
import br.offgr1d.companion.screen.ScreenContext
import br.offgr1d.companion.store.BlobCipher
import br.offgr1d.companion.store.BlobStore
import br.offgr1d.companion.ui.CompanionApp
import br.offgr1d.companion.ui.PlatformActions
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

/** Desenha a conversa sem abrir janela. As imagens ficam em build/render para conferir a olho. */
class RenderTest {
    private object Plain : BlobCipher {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(sealed: ByteArray) = sealed
        override fun destroyKey() {}
    }

    private fun render(name: String, compact: Boolean, prepare: suspend Companion2B.() -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val dir = Files.createTempDirectory("2b-render").toFile()
        val app = Companion2B(scope, BlobStore(File(dir, "d"), Plain), File(dir, "m"), File(dir, "c"), { error("sem modelo") })
        app.awaitReady()
        app.prepare()
        val scene = ImageComposeScene(412, 860, Density(1f)) {
            CompanionApp(app, PlatformActions(openUrl = {}, captureScreen = {}), compact, onClose = if (compact) ({}) else null)
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

    @Test fun inicio() = render("inicio", compact = false) {}

    @Test fun golpe() = render("golpe", compact = false) { send("recebi https://g1obo.com/premio isso é golpe?")!!.join() }

    @Test fun painel() = render("painel", compact = true) {
        send("quais meus direitos num protesto?")!!.join()
        attachScreen(ScreenContext("texto da tela", byteArrayOf(1)))
        setWeb(true)
    }
}
