package br.offgr1d.companion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import br.offgr1d.companion.screen.Attachment
import br.offgr1d.companion.screen.ScreenContext
import java.io.ByteArrayOutputStream

/** Foto ou vídeo escolhido na galeria, pronto para a 2B. Nada é copiado para fora da memória. */
object MediaFiles {
    private const val MAX = 1024

    fun read(c: Context, uri: Uri): ScreenContext {
        val type = c.contentResolver.getType(uri).orEmpty()
        return if (type.startsWith("video/")) ScreenContext(null, videoGrid(c, uri), Attachment.VIDEO)
        else ScreenContext(null, photo(c, uri), Attachment.PHOTO)
    }

    private fun photo(c: Context, uri: Uri): ByteArray {
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.contentResolver, uri)) { d, info, _ ->
            val s = minOf(1.0, MAX.toDouble() / maxOf(info.size.width, info.size.height))
            d.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return jpeg(bmp)
    }

    /** 4 quadros espalhados pelo vídeo numa grade 2x2: o motor aceita uma imagem por pergunta. */
    private fun videoGrid(c: Context, uri: Uri): ByteArray {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(c, uri)
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val half = MAX / 2
            val frames = listOf(0.1, 0.35, 0.6, 0.85).mapNotNull { f ->
                r.getScaledFrameAtTime((ms * f * 1000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC, half, half)
            }
            if (frames.isEmpty()) error("vídeo sem quadros")
            val cell = frames.maxOf { maxOf(it.width, it.height) }
            val grid = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888)
            Canvas(grid).apply {
                drawColor(Color.BLACK)
                frames.forEachIndexed { i, b ->
                    val x = (i % 2) * cell + (cell - b.width) / 2
                    val y = (i / 2) * cell + (cell - b.height) / 2
                    drawBitmap(b, null, Rect(x, y, x + b.width, y + b.height), null)
                    b.recycle()
                }
            }
            return jpeg(grid)
        } finally {
            r.release()
        }
    }

    private fun jpeg(b: Bitmap): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray().also { b.recycle() }
}
