package br.offgr1d.companion.capture

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import br.offgr1d.companion.App
import br.offgr1d.companion.Notifications
import br.offgr1d.companion.PanelActivity
import br.offgr1d.companion.R
import br.offgr1d.companion.screen.ScreenContext
import java.io.ByteArrayOutputStream

object Images {
    private const val MAX_EDGE = 1024

    /** Reduz e comprime em memória; a captura nunca vai para o disco. */
    fun jpeg(source: Bitmap): ByteArray {
        // A captura do assistente vem em HARDWARE, que não deixa ler os pixels.
        val soft = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
        val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(soft.width, soft.height))
        val small = if (scale < 1f) Bitmap.createScaledBitmap(soft, (soft.width * scale).toInt(), (soft.height * scale).toInt(), true) else soft
        return ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }

    /** Apps protegidos devolvem tudo da mesma cor. */
    fun isBlank(b: Bitmap): Boolean {
        val first = b.getPixel(b.width / 2, b.height / 2)
        for (i in 1 until 20) for (j in 1 until 20) if (b.getPixel(b.width * i / 20, b.height * j / 20) != first) return false
        return true
    }
}

/** Só mostra o diálogo de autorização do Android. Cada captura pede de novo. */
class CaptureActivity : ComponentActivity() {
    private val ask = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) {
            startForegroundService(
                Intent(this, CaptureService::class.java).putExtra(CaptureService.DATA, data).putExtra(CaptureService.CODE, r.resultCode),
            )
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) ask.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }
}

/** Pega um único quadro e encerra. Não grava vídeo e não continua em segundo plano. */
class CaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var done = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifications.build(this, getString(R.string.capture_running))
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(ID, n)
        if (projection != null) return START_NOT_STICKY
        @Suppress("DEPRECATION")
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(DATA, Intent::class.java) else intent?.getParcelableExtra(DATA)
        try {
            start(intent?.getIntExtra(CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED, data ?: error("sem autorização"))
        } catch (e: Exception) {
            end(null)
        }
        return START_NOT_STICKY
    }

    private fun start(code: Int, data: Intent) {
        val p = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data) ?: return end(null)
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = end(null)
        }, handler)
        val dm = resources.displayMetrics
        val r = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2)
        reader = r
        display = p.createVirtualDisplay("2b", r.width, r.height, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler)
        handler.postDelayed({ grab(0) }, 700) // espera o diálogo do Android sumir
    }

    private fun grab(attempt: Int) {
        if (done) return
        val image = runCatching { reader?.acquireLatestImage() }.getOrNull()
        if (image == null) {
            if (attempt < 10) handler.postDelayed({ grab(attempt + 1) }, 200) else end(null)
            return
        }
        val jpeg = try {
            image.use {
                val plane = it.planes[0]
                val wide = Bitmap.createBitmap(plane.rowStride / plane.pixelStride, it.height, Bitmap.Config.ARGB_8888)
                wide.copyPixelsFromBuffer(plane.buffer)
                val b = Bitmap.createBitmap(wide, 0, 0, it.width, it.height)
                if (Images.isBlank(b)) null else Images.jpeg(b)
            }
        } catch (e: Exception) {
            null
        }
        end(jpeg)
    }

    private fun end(jpeg: ByteArray?) {
        if (done) return
        done = true
        handler.removeCallbacksAndMessages(null)
        display?.release()
        reader?.close()
        projection?.stop()
        (application as App).companion.attachScreen(ScreenContext(null, jpeg))
        runCatching { startActivity(PanelActivity.intent(this)) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        end(null)
        super.onDestroy()
    }

    companion object {
        const val DATA = "data"
        const val CODE = "code"
        private const val ID = 2
    }
}
