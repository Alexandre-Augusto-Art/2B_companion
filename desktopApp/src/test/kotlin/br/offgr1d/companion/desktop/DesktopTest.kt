package br.offgr1d.companion.desktop

import br.offgr1d.companion.llm.LiteRtEngine
import br.offgr1d.companion.store.BlobStore
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Só roda no Windows. */
class DesktopTest {
    private val windows = System.getProperty("os.name").startsWith("Windows")
    private val dir = Files.createTempDirectory("2b-desktop").toFile()

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
