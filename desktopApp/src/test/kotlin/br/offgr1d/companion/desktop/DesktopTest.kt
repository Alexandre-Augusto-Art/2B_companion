package br.offgr1d.companion.desktop

import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import br.offgr1d.companion.store.Settings
import br.offgr1d.companion.chat.Tool
import br.offgr1d.companion.llm.LiteRtEngine
import br.offgr1d.companion.store.BlobStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Só roda no Windows. */
class DesktopTest {
    private val windows = System.getProperty("os.name").startsWith("Windows")
    private val dir = tempDir("2b-desktop")

    /** Só falha no celular, em tempo de execução: o compilador e o lint não pegam. */
    @Test fun `manifesto do Android tem as permissões que o código exige`() {
        val android = File("../androidApp/src/main")
        val manifest = File(android, "AndroidManifest.xml").readText()
        val code = android.walk().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        mapOf(
            "setRequiredNetworkType" to listOf("INTERNET", "ACCESS_NETWORK_STATE"),
            "setPersisted(true)" to listOf("RECEIVE_BOOT_COMPLETED"),
            "FOREGROUND_SERVICE_TYPE_SPECIAL_USE" to listOf("FOREGROUND_SERVICE", "FOREGROUND_SERVICE_SPECIAL_USE"),
            "FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION" to listOf("FOREGROUND_SERVICE", "FOREGROUND_SERVICE_MEDIA_PROJECTION"),
            "TYPE_APPLICATION_OVERLAY" to listOf("SYSTEM_ALERT_WINDOW"),
        ).forEach { (use, needed) ->
            assertTrue(use in code, "o código não usa mais $use: atualize o teste")
            needed.forEach { assertTrue("\"android.permission.$it\"" in manifest, "$use exige $it") }
        }
    }

    /** Opcional, LIVE=1: consulta o site de verdade. */
    @Test fun `site no ar alimenta atalhos e avisos`() {
        if (System.getenv("LIVE") != "1") return
        runBlocking {
            val app = createCompanion(dir)
            app.awaitReady()
            for (t in Tool.entries) {
                app.send(t.ask)!!.join()
                println("\n### ${t.label}\n" + app.state.value.messages.last().text)
                assertFalse("Não consegui atualizar" in app.state.value.messages.last().text)
            }
            app.send(Tool.NEWS.ask)!!.join()
            val media = app.state.value.messages.last().media!!
            val poster = app.picture(media.posterUrl!!)
            val video = app.video(media.url!!)
            println("\n### VÍDEO ${media.url} capa=${poster.size} vídeo=${video.length()} ${video.name}")
            assertTrue(poster.size > 1000 && video.length() > 100_000 && app.cachedVideo(media.url!!) == video)
            app.saveSettings(Settings(alertW4k3 = true, alertQuote = true, alertActs = true, alertUfs = listOf("SP")))
            assertEquals(0, app.checkAlerts()!!.size)
            assertEquals(0, app.checkAlerts()!!.size)
            println(app.state.value.net.map { "${it.url} ${it.result}" })
            app.panic(includeModels = true).join()
            assertFalse(video.exists(), "o pânico apaga o vídeo")
        }
    }

    @Test fun `cifra com a conta do Windows e o pânico destrói a chave`() {
        if (!windows) return
        val keyFile = File(dir, "key.bin")
        val store = BlobStore(File(dir, "data"), DpapiCipher(keyFile))
        store.write("memories", "segredo".toByteArray())
        val file = File(dir, "data/memories.bin")
        assertFalse("segredo" in file.readText(Charsets.ISO_8859_1))
        assertContentEquals("segredo".toByteArray(), store.read("memories"))

        val copy = file.readBytes()
        assertTrue(store.wipe())
        assertFalse(keyFile.exists())
        // Uma cópia antiga do arquivo não abre mais.
        File(dir, "data").mkdirs()
        file.writeBytes(copy)
        assertFails { store.read("memories") }
    }

    @Test fun `biblioteca nativa do modelo carrega e recusa arquivo inválido`() {
        if (!windows) return
        val fake = File(dir, "x.litertlm").apply { writeText("não é um modelo") }
        assertFails { LiteRtEngine().load(fake, vision = false, useGpu = false, cacheDir = File(dir, "c")) }
            .also { assertFalse(it is UnsatisfiedLinkError || it is NoClassDefFoundError, it.toString()) }
    }
}
