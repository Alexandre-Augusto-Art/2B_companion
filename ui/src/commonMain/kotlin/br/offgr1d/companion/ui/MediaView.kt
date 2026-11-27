package br.offgr1d.companion.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.offgr1d.companion.chat.Companion2B
import br.offgr1d.companion.chat.Media
import br.offgr1d.companion.knowledge.FeedParser
import br.offgr1d.companion.screen.Attachment
import java.io.File
import kotlinx.coroutines.launch

private fun decode(bytes: ByteArray): ImageBitmap? = runCatching { bytes.decodeToImageBitmap() }.getOrNull()

@Composable
fun Thumb(bytes: ByteArray, modifier: Modifier = Modifier) {
    val img = remember(bytes) { decode(bytes) } ?: return
    Image(img, null, modifier.clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
}

/** Nome do arquivo ao salvar ou compartilhar: "0FF-GR1D-W4K3-2026-11-26.mp4". */
fun videoName(url: String): String {
    val base = url.substringAfterLast('/').substringBefore('?').replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "video.mp4" }
    val tag = if ("/w4k3/" in url) "0FF-GR1D-W4K3-" else "0FF-GR1D-"
    return tag + if (base.contains('.')) base else "$base.mp4"
}

/** Texto que vai junto ao compartilhar: de onde veio o vídeo. */
fun shareText(url: String) =
    if ("/w4k3/" in url) "W4K3 PR0T0C0L, do 0FF-GR1D: ${FeedParser.SITE}w4k3-protocol.html" else "Do 0FF-GR1D: ${FeedParser.SITE}"

/**
 * Foto ou vídeo numa mensagem. O vídeo do site só baixa quando a pessoa toca, a não ser que o aviso já tenha baixado.
 * Vídeo que a própria pessoa mandou aparece pela grade de quadros que a 2B viu.
 */
@Composable
fun MediaView(media: Media, app: Companion2B, platform: PlatformActions, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val poster by produceState(media.preview?.let(::decode), media.posterUrl) {
        val url = media.posterUrl
        if (value == null && url != null) value = runCatching { app.picture(url) }.getOrNull()?.let(::decode)
    }
    val url = media.url
    var file by remember(url) { mutableStateOf(url?.let(app::cachedVideo)) }
    var playing by remember(url) { mutableStateOf(false) }
    var status by remember(url) { mutableStateOf<String?>(null) }

    /** Baixa se ainda não estiver no aparelho e então faz [then]. */
    fun withFile(then: (File) -> Unit) {
        if (url == null || status == "BAIXANDO") return
        file?.let { return then(it) }
        status = "BAIXANDO"
        scope.launch {
            runCatching { app.video(url) }
                .onSuccess { f -> file = f; status = null; then(f) }
                .onFailure { e -> status = "NÃO BAIXOU: ${e.message ?: "sem rede"}" }
        }
    }

    fun play() = withFile { f -> if (platform.videoPlayer != null) playing = true else platform.openFile(f) }

    val ratio = if (media.kind == Attachment.VIDEO && url != null) 9f / 16f else poster?.let { it.width.toFloat() / it.height } ?: 1f
    Column(modifier.widthIn(max = if (ratio < 1f) 240.dp else 320.dp)) {
        val shape = RoundedCornerShape(8.dp)
        val frame = Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.4f, 2.5f))
            .clip(shape).border(1.dp, Palette.Line, shape).background(Palette.Bg)
        val ready = file
        val player = platform.videoPlayer
        if (playing && ready != null && player != null) {
            player(ready, frame)
        } else {
            Box(
                if (url != null) frame.clickable(role = Role.Button, onClick = ::play) else frame,
                contentAlignment = Alignment.Center,
            ) {
                poster?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                if (url != null) {
                    Box(Modifier.background(Palette.Bg.copy(alpha = 0.7f), RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(if (status == "BAIXANDO") "BAIXANDO…" else "▶ ASSISTIR", color = Palette.Green, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }
            }
        }
        if (url != null && (platform.saveVideo != null || platform.shareVideo != null)) {
            Row {
                platform.saveVideo?.let { save -> MediaButton("SALVAR") { withFile { f -> save(f, videoName(url)) } } }
                platform.shareVideo?.let { share -> MediaButton("COMPARTILHAR") { withFile { f -> share(f, videoName(url), shareText(url)) } } }
            }
        }
        val note = when {
            status != null && status != "BAIXANDO" -> status
            url != null && ready == null -> "Vídeo do 0FF-GR1D. Baixa ao tocar."
            url == null && media.kind == Attachment.VIDEO -> "Seu vídeo: a 2B viu estes 4 quadros, sem som."
            else -> null
        }
        note?.let { Text(it, color = Palette.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun MediaButton(label: String, onClick: () -> Unit) =
    TextButton(onClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
        Text(label, color = Palette.Cyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
