package br.offgr1d.companion

import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/** Vídeo já baixado, tocando dentro da conversa. Toque pausa e continua. */
@Composable
fun VideoPlayer(file: File, modifier: Modifier) = AndroidView(
    factory = { c ->
        VideoView(c).apply {
            setVideoPath(file.path)
            setOnPreparedListener { start() }
            setOnCompletionListener { seekTo(0) }
            setOnClickListener { if (isPlaying) pause() else start() }
        }
    },
    modifier = modifier,
    onRelease = { it.stopPlayback() },
)
