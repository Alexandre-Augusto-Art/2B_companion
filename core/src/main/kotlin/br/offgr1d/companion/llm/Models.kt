package br.offgr1d.companion.llm

import br.offgr1d.companion.net.SafeHttp
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ModelSpec(
    val id: String,
    val name: String,
    val note: String,
    val url: String,
    val bytes: Long,
    val sha256: String,
    val vision: Boolean,
    val license: String,
    /** false: modelo fraco demais para orientar. Com guia ou link, o app mostra o texto conferido no lugar da IA. */
    val advises: Boolean = true,
)

object ModelCatalog {
    // Revisão, tamanho e SHA-256 fixos: um arquivo trocado no servidor é recusado.
    val ALL = listOf(
        ModelSpec(
            id = "gemma-4-e2b", name = "Gemma 4 E2B", note = "Recomendado. Entende imagens. Pede celular com 8 GB de RAM ou mais.",
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
                "2e12bfd869b2962984f97a1f2a26807316d534cc/gemma-4-E2B-it.litertlm",
            bytes = 2_588_147_712, sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            vision = true, license = "Apache-2.0",
        ),
        ModelSpec(
            id = "qwen3-0.6b", name = "Qwen3 0.6B", note = "Leve, só texto. Serve para conversar; em segurança e direitos o app mostra o guia pronto, porque ele erra.",
            url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/" +
                "8d42ab5f1db14cad987fac57aeb17d070f9e180d/Qwen3-0.6B.litertlm",
            bytes = 613_722_264, sha256 = "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108",
            vision = false, license = "Apache-2.0", advises = false,
        ),
    )

    fun byId(id: String): ModelSpec = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

class ModelStore(private val dir: File, private val http: SafeHttp) {
    fun file(m: ModelSpec) = File(dir, "${m.id}.litertlm")
    private fun part(m: ModelSpec) = File(dir, "${m.id}.part")

    fun isReady(m: ModelSpec) = file(m).length() == m.bytes
    fun partialBytes(m: ModelSpec) = part(m).length()

    /** Continua de onde parou. Só vira modelo utilizável depois de conferir tamanho e SHA-256. */
    suspend fun download(m: ModelSpec, onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        if (isReady(m)) return@withContext
        dir.mkdirs()
        val part = part(m)
        if (part.length() > m.bytes) part.delete()
        val missing = m.bytes - part.length()
        if (dir.usableSpace < missing + MARGIN) throw IOException("espaço insuficiente: faltam ${(missing + MARGIN) / MB} MB livres")

        if (missing > 0) {
            http.open(m.url, "baixar modelo ${m.name}", part.length()).use { r ->
                val resume = r.code == 206
                FileOutputStream(part, resume).use { out ->
                    var done = if (resume) part.length() else 0L
                    val buf = ByteArray(256 * 1024)
                    var lastReport = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = r.stream.read(buf)
                        if (n < 0) break
                        if (done + n > m.bytes) throw IOException("arquivo maior que o esperado")
                        out.write(buf, 0, n)
                        done += n
                        if (done - lastReport > 4 * MB) { onProgress(done); lastReport = done }
                    }
                }
            }
        }
        if (part.length() != m.bytes) throw IOException("download incompleto: tente de novo para continuar")
        onProgress(m.bytes)
        if (sha256(part) != m.sha256) {
            part.delete()
            throw IOException("o arquivo baixado não confere com o original e foi apagado")
        }
        val target = file(m)
        target.delete()
        if (!part.renameTo(target)) throw IOException("não foi possível salvar o modelo")
    }

    fun delete(m: ModelSpec) {
        file(m).delete()
        part(m).delete()
    }

    fun deleteAll(): Boolean {
        dir.listFiles()?.forEach { it.deleteRecursively() }
        return dir.listFiles().isNullOrEmpty()
    }

    companion object {
        private const val MB = 1024L * 1024
        private const val MARGIN = 500 * MB

        fun sha256(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { s ->
                val buf = ByteArray(1024 * 1024)
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
