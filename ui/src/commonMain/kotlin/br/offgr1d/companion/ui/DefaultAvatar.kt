package br.offgr1d.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import br.offgr1d.companion.chat.Mood

/** Símbolo provisório até a arte da personagem ficar pronta (veja docs/AVATAR.md). */
@Composable
fun DefaultAvatar(mood: Mood, modifier: Modifier = Modifier) {
    val color = mood.color()
    Box(modifier.background(Palette.Bg, CircleShape).border(2.dp, color, CircleShape)) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.minDimension / 12
            val lit = when (mood) {
                Mood.IDLE -> listOf(2, 4, 2)
                Mood.THINKING -> listOf(2, 2, 2)
                Mood.TALKING -> listOf(4, 6, 4)
                Mood.ALERT -> listOf(6, 6, 6)
            }
            lit.forEachIndexed { i, h ->
                drawRect(color, Offset(u * (3.25f + i * 2), size.height / 2 - u * h / 2), Size(u * 1.5f, u * h))
            }
        }
    }
}
