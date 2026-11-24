package br.offgr1d.companion.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import br.offgr1d.companion.AvatarArt
import br.offgr1d.companion.chat.Mood

/** A 2B desenhada na bolha: usa a arte dos assets ou o símbolo provisório. */
class AvatarView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val art = Mood.entries.associateWith { AvatarArt.load(context.assets, it) }
    private val box = RectF()

    var mood: Mood = Mood.IDLE
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bitmap = art[mood] ?: art[Mood.IDLE]
        if (bitmap != null) {
            val s = minOf(w / bitmap.width, h / bitmap.height)
            box.set((w - bitmap.width * s) / 2, (h - bitmap.height * s) / 2, (w + bitmap.width * s) / 2, (h + bitmap.height * s) / 2)
            canvas.drawBitmap(bitmap, null, box, paint)
            return
        }
        val color = when (mood) {
            Mood.IDLE -> 0xFF00FF84
            Mood.THINKING -> 0xFFFACC15
            Mood.TALKING -> 0xFF22D3EE
            Mood.ALERT -> 0xFFF43F5E
        }.toInt()
        val r = minOf(w, h) / 2
        paint.style = Paint.Style.FILL
        paint.color = 0xFF0E0D10.toInt()
        canvas.drawCircle(w / 2, h / 2, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r / 12
        paint.color = color
        canvas.drawCircle(w / 2, h / 2, r - paint.strokeWidth, paint)
        paint.style = Paint.Style.FILL
        val u = r / 6
        val bars = when (mood) {
            Mood.IDLE -> intArrayOf(2, 4, 2)
            Mood.THINKING -> intArrayOf(2, 2, 2)
            Mood.TALKING -> intArrayOf(4, 6, 4)
            Mood.ALERT -> intArrayOf(6, 6, 6)
        }
        bars.forEachIndexed { i, b ->
            val x = w / 2 + (i - 1) * u * 2 - u * 0.75f
            canvas.drawRect(x, h / 2 - u * b / 2, x + u * 1.5f, h / 2 + u * b / 2, paint)
        }
    }
}
