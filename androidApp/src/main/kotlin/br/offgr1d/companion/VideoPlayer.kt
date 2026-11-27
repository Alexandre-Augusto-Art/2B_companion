package br.offgr1d.companion

import android.media.MediaPlayer
import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import br.offgr1d.companion.ui.Palette
import java.io.File
import kotlinx.coroutines.delay

private const val STEP_MS = 10_000

/** Vídeo já baixado, tocando dentro da conversa, com voltar, pausar, adiantar, barra de tempo e som. */
@Composable
fun VideoPlayer(file: File, modifier: Modifier) {
    var view by remember { mutableStateOf<VideoView?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var pos by remember { mutableIntStateOf(0) }
    var dur by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var muted by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(view) {
        val v = view ?: return@LaunchedEffect
        while (true) {
            runCatching {
                if (dragging == null) pos = v.currentPosition
                if (v.duration > 0) dur = v.duration
                playing = v.isPlaying
            }
            delay(250)
        }
    }

    fun seek(by: Int) {
        val v = view ?: return
        val to = (v.currentPosition + by).coerceIn(0, maxOf(0, dur))
        v.seekTo(to)
        pos = to
    }

    Column(Modifier.fillMaxWidth()) {
        AndroidView(
            factory = { c ->
                VideoView(c).apply {
                    setVideoPath(file.path)
                    setOnPreparedListener { mp ->
                        player = mp
                        if (muted) mp.setVolume(0f, 0f)
                        start()
                    }
                    setOnCompletionListener { seekTo(0); playing = false }
                    setOnClickListener { if (isPlaying) pause() else start() }
                    view = this
                }
            },
            modifier = modifier,
            onRelease = { it.stopPlayback(); view = null; player = null },
        )
        Slider(
            value = (dragging ?: pos.toFloat()).coerceIn(0f, maxOf(1, dur).toFloat()),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { view?.seekTo(it.toInt()); pos = it.toInt() }; dragging = null },
            valueRange = 0f..maxOf(1, dur).toFloat(),
            colors = SliderDefaults.colors(thumbColor = Palette.Green, activeTrackColor = Palette.Green, inactiveTrackColor = Palette.Line),
            modifier = Modifier.fillMaxWidth().height(28.dp).semantics { contentDescription = "Posição do vídeo" },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Control("−10s", "Voltar 10 segundos") { seek(-STEP_MS) }
            Control(if (playing) "PAUSAR" else "TOCAR", if (playing) "Pausar" else "Tocar") {
                view?.let { if (it.isPlaying) it.pause() else it.start(); playing = it.isPlaying }
            }
            Control("+10s", "Adiantar 10 segundos") { seek(STEP_MS) }
            Control(if (muted) "SEM SOM" else "SOM", if (muted) "Ligar o som" else "Tirar o som") {
                muted = !muted
                runCatching { player?.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f) }
            }
        }
        Text("${clock(dragging?.toInt() ?: pos)} / ${clock(dur)}", color = Palette.Dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}

@Composable
private fun Control(label: String, description: String, onClick: () -> Unit) =
    TextButton(onClick, Modifier.semantics { contentDescription = description }, contentPadding = PaddingValues(horizontal = 6.dp)) {
        Text(label, color = Palette.Green, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }

private fun clock(ms: Int): String {
    val s = maxOf(0, ms) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
