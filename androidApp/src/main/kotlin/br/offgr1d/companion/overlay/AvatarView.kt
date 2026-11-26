package br.offgr1d.companion.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import androidx.compose.ui.graphics.toArgb
import br.offgr1d.companion.chat.Mood
import br.offgr1d.companion.ui.FaceArt
import br.offgr1d.companion.ui.FaceFrame
import br.offgr1d.companion.ui.FaceMotion
import br.offgr1d.companion.ui.Palette

/** A 2B na bolha: o mesmo rosto do app, num disco escuro para aparecer sobre qualquer tela. */
class AvatarView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paths = FaceFrame.entries.associateWith { f ->
        f.shapes.map { s ->
            Path().apply {
                fillType = Path.FillType.EVEN_ODD
                s.rings.forEach { r ->
                    moveTo(r[0], r[1])
                    for (i in 2 until r.size step 2) lineTo(r[i], r[i + 1])
                    close()
                }
            }
        }
    }
    private var frame = FaceFrame.NEUTRAL
    private var blinking = false
    private var mouthOpen = false
    private var pulseUp = false
    private var faceAlpha = 255

    var mood: Mood = Mood.IDLE
        set(value) {
            if (field == value) return
            field = value
            removeCallbacks(mouth)
            removeCallbacks(pulse)
            mouthOpen = false
            faceAlpha = 255
            if (value == Mood.TALKING) postDelayed(mouth, FaceMotion.nextMouthMs())
            if (value == Mood.THINKING) post(pulse)
            update()
        }

    private val blink: Runnable = Runnable {
        blinking = !blinking
        update()
        postDelayed(blink, if (blinking) FaceMotion.BLINK_MS else FaceMotion.nextBlinkMs())
    }
    private val mouth: Runnable = Runnable {
        mouthOpen = !mouthOpen
        update()
        postDelayed(mouth, FaceMotion.nextMouthMs())
    }
    private val pulse: Runnable = Runnable {
        faceAlpha += if (pulseUp) 20 else -20
        if (faceAlpha >= 255) { faceAlpha = 255; pulseUp = false }
        if (faceAlpha <= 115) { faceAlpha = 115; pulseUp = true }
        invalidate()
        postDelayed(pulse, 50)
    }

    private fun update() {
        frame = when {
            blinking -> FaceFrame.BLINK
            mouthOpen -> FaceFrame.TALK
            else -> FaceFrame.NEUTRAL
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(blink, FaceMotion.nextBlinkMs())
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blink)
        removeCallbacks(mouth)
        removeCallbacks(pulse)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = minOf(w, h) / 2
        paint.color = Palette.Bg.toArgb()
        canvas.drawCircle(w / 2, h / 2, r, paint)
        val side = r * 1.5f // o rosto cabe no disco com folga
        val k = minOf(side / FaceArt.WIDTH, side / FaceArt.HEIGHT)
        canvas.save()
        canvas.translate((w - FaceArt.WIDTH * k) / 2, (h - FaceArt.HEIGHT * k) / 2)
        canvas.scale(k, k)
        paint.color = FaceMotion.tint(mood, Palette.Face).toArgb()
        paint.alpha = faceAlpha
        paths.getValue(frame).forEach { canvas.drawPath(it, paint) }
        canvas.restore()
    }
}
