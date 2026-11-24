package br.offgr1d.companion.store

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Cifra do sistema: Keystore no Android, DPAPI no Windows. */
interface BlobCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(sealed: ByteArray): ByteArray

    /** Apaga a chave, quando o sistema permite. */
    fun destroyKey()
}

class BlobStore(private val dir: File, private val cipher: BlobCipher) {
    init { dir.mkdirs() }

    private fun file(name: String): File {
        require(NAME.matches(name)) { "nome inválido" }
        return File(dir, "$name.bin")
    }

    /** null se não existe. Conteúdo ilegível dá erro: nunca vira vazio em silêncio. */
    @Synchronized
    fun read(name: String): ByteArray? = file(name).takeIf { it.exists() }?.let { cipher.decrypt(it.readBytes()) }

    @Synchronized
    fun write(name: String, data: ByteArray) {
        dir.mkdirs()
        val target = file(name)
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(cipher.encrypt(data))
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    @Synchronized
    fun delete(name: String) { file(name).delete() }

    /** Apaga a chave primeiro: se algum arquivo sobrar, fica ilegível. @return false se algo não pôde ser apagado. */
    @Synchronized
    fun wipe(): Boolean {
        val keyGone = runCatching { cipher.destroyKey() }.isSuccess
        dir.listFiles()?.forEach { it.deleteRecursively() }
        return keyGone && dir.listFiles().isNullOrEmpty()
    }

    private companion object { val NAME = Regex("[a-z0-9_-]+") }
}
