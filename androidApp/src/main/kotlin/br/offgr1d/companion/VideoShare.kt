package br.offgr1d.companion

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** Salvar o vídeo na galeria e mandar para outros apps. */
object VideoShare {
    const val FOLDER = "0FF-GR1D"

    /** Copia para Filmes/0FF-GR1D. O Android 10+ não pede permissão para o app gravar ali. */
    fun save(c: Context, file: File, name: String) {
        val r = c.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$FOLDER")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = r.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("sem espaço para salvar")
        try {
            (r.openOutputStream(uri) ?: error("não abriu")).use { out -> file.inputStream().use { it.copyTo(out) } }
            r.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            r.delete(uri, null, null)
            throw e
        }
    }

    /**
     * Pedido para o Android mostrar os apps que recebem o vídeo. A cópia fica no cache de mídia do app,
     * com nome legível, e o pânico apaga.
     */
    fun intent(c: Context, file: File, name: String, text: String): Intent {
        val dir = File(file.parentFile, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val copy = File(dir, name)
        file.copyTo(copy, overwrite = true)
        val uri: Uri = FileProvider.getUriForFile(c, c.packageName + ".files", copy)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Compartilhar vídeo").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
