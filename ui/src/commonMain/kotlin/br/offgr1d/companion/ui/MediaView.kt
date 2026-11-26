package br.offgr1d.companion.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import br.offgr1d.companion.screen.Attachment
import kotlinx.coroutines.launch

private fun decode(bytes: ByteArray): ImageBitmap? = runCatching { bytes.decodeToImageBitmap() }.getOrNull()

@Composable
fun Thumb(bytes: ByteArray, modifier: Modifier = Modifier) {
    val img = remember(bytes) { decode(bytes) } ?: return
    Image(img, null, modifier.clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
}

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

    fun play() {
        if (url == null || status == "BAIXANDO") return
        val ready = file
        if (ready != null) {
            if (platform.videoPlayer != null) playing = true else platform.openFile(ready)
            return
        }
        status = "BAIXANDO"
        scope.launch {
            runCatching { app.video(url) }
                .onSuccess { f -> file = f; status = null; if (platform.videoPlayer != null) playing = true else platform.openFile(f) }
                .onFailure { e -> status = "NÃO BAIXOU: ${e.message ?: "sem rede"}" }
        }
    }

    Column(modifier) {
        val shape = RoundedCornerShape(8.dp)
        val ratio = if (media.kind == Attachment.VIDEO && url != null) 9f / 16f else poster?.let { it.width.toFloat() / it.height } ?: 1f
        val frame = Modifier.widthIn(max = if (ratio < 1f) 240.dp else 320.dp).aspectRatio(ratio.coerceIn(0.4f, 2.5f))
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
        val note = when {
            status != null && status != "BAIXANDO" -> status
            url != null && ready == null -> "Vídeo do 0FF-GR1D. Baixa ao tocar."
            url == null && media.kind == Attachment.VIDEO -> "Seu vídeo: a 2B viu estes 4 quadros, sem som."
            else -> null
        }
        note?.let { Text(it, color = Palette.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}
