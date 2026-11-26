package br.offgr1d.companion.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import br.offgr1d.companion.chat.Mood
import kotlin.random.Random
import kotlinx.coroutines.delay

/** Quadro do rosto: parado, boca aberta ou olhos fechados. */
enum class FaceFrame(val shapes: List<FaceArt.Shape>) { NEUTRAL(FaceArt.NEUTRAL), TALK(FaceArt.TALK), BLINK(FaceArt.BLINK) }

/** Os tempos do rosto, iguais no app, na bolha e no Windows. */
object FaceMotion {
    fun nextBlinkMs() = Random.nextLong(2_500, 6_000)
    const val BLINK_MS = 130L
    fun nextMouthMs() = Random.nextLong(90, 190)

    /** Alerta pinta de vermelho; o resto usa a cor da 2B. */
    fun tint(mood: Mood, color: Color) = if (mood == Mood.ALERT) Palette.Red else color
}

private val paths = FaceFrame.entries.associateWith { f ->
    f.shapes.map { s ->
        s.accent to Path().apply {
            fillType = PathFillType.EvenOdd
            s.rings.forEach { r ->
                moveTo(r[0], r[1])
                for (i in 2 until r.size step 2) lineTo(r[i], r[i + 1])
                close()
            }
        }
    }
}

/** O rosto da 2B: pisca sozinho, mexe a boca enquanto responde e pulsa enquanto pensa. */
@Composable
fun Face(mood: Mood, modifier: Modifier = Modifier, color: Color = Palette.Face, accent: Color = color) {
    var blink by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(FaceMotion.nextBlinkMs())
            blink = true
            delay(FaceMotion.BLINK_MS)
            blink = false
        }
    }
    LaunchedEffect(mood) {
        open = false
        if (mood == Mood.TALKING) while (true) {
            delay(FaceMotion.nextMouthMs())
            open = !open
        }
    }
    val pulse by rememberInfiniteTransition().animateFloat(0.45f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse))
    val alpha = if (mood == Mood.THINKING) pulse else 1f
    val frame = when {
        blink -> FaceFrame.BLINK
        open -> FaceFrame.TALK
        else -> FaceFrame.NEUTRAL
    }
    val main = FaceMotion.tint(mood, color)
    val second = if (mood == Mood.ALERT) main else accent
    Canvas(modifier) {
        val k = minOf(size.width / FaceArt.WIDTH, size.height / FaceArt.HEIGHT)
        translate((size.width - FaceArt.WIDTH * k) / 2, (size.height - FaceArt.HEIGHT * k) / 2) {
            scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                paths.getValue(frame).forEach { (isAccent, p) -> drawPath(p, if (isAccent) second else main, alpha) }
            }
        }
    }
}
