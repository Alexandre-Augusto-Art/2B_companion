package br.offgr1d.companion.capture

import android.app.Activity
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionConfig
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

/** Só mostra o diálogo de autorização do Android. */
class CaptureActivity : ComponentActivity() {
    private val ask = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) {
            startForegroundService(
                Intent(this, CaptureService::class.java).putExtra(CaptureService.DATA, data).putExtra(CaptureService.CODE, r.resultCode),
            )
        } else if ((application as App).companion.state.value.settings.autoScreen) {
            startActivity(PanelActivity.intent(this)) // recusou a leitura, mas queria conversar
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val mpm = getSystemService(MediaProjectionManager::class.java)
        ask.launch(
            if (Build.VERSION.SDK_INT >= 34) mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            else mpm.createScreenCaptureIntent(),
        )
    }
}

/**
 * Lê a tela em um único quadro por vez. Normalmente encerra depois do primeiro. Com "Ler a tela sozinha", mantém a
 * autorização do Android aberta, mas sem receber imagens: só liga a tela virtual na hora em que a pessoa chama a 2B.
 */
class CaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var grabbing = false
    private var ended = false
    private val keepAlive get() = (application as App).companion.state.value.settings.autoScreen

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            end()
            return START_NOT_STICKY
        }
        val stop = PendingIntent.getService(this, 4, Intent(this, CaptureService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val n = if (keepAlive) Notifications.build(this, getString(R.string.capture_live), stop, R.string.capture_stop)
        else Notifications.build(this, getString(R.string.capture_running))
        try {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            if (projection != null) {
                grab()
                return START_NOT_STICKY
            }
            @Suppress("DEPRECATION")
            val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(DATA, Intent::class.java) else intent?.getParcelableExtra(DATA)
            start(intent?.getIntExtra(CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED, data ?: error("sem autorização"))
        } catch (e: Exception) {
            deliver(null)
            end()
        }
        return START_NOT_STICKY
    }

    private fun start(code: Int, data: Intent) {
        val p = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data) ?: error("sem autorização")
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = end() // a pessoa parou pelo Android, ou a tela foi bloqueada
        }, handler)
        if (keepAlive) live = this
        grabbing = true
        handler.postDelayed({ read() }, 500) // espera o diálogo do Android sumir
    }

    private fun grab() {
        if (grabbing || ended) return
        grabbing = true
        read()
    }

    private fun read() {
        val p = projection
        if (ended || p == null) return finishGrab(null)
        try {
            val dm = resources.displayMetrics
            val r = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2)
            reader = r
            val d = display
            // O Android 14 só aceita uma tela virtual por autorização: a mesma é religada.
            if (d == null) {
                display = p.createVirtualDisplay("2b", r.width, r.height, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler)
            } else {
                d.resize(r.width, r.height, dm.densityDpi)
                d.surface = r.surface
            }
            handler.postDelayed({ take(0) }, 250)
        } catch (e: Exception) {
            finishGrab(null)
        }
    }

    private fun take(attempt: Int) {
        if (ended) return
        val image = runCatching { reader?.acquireLatestImage() }.getOrNull()
        if (image == null) {
            if (attempt < 10) handler.postDelayed({ take(attempt + 1) }, 200) else finishGrab(null)
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
        finishGrab(jpeg)
    }

    private fun finishGrab(jpeg: ByteArray?) {
        if (ended || !grabbing) return
        grabbing = false
        runCatching { display?.surface = null } // entre uma leitura e outra, nada é recebido
        reader?.close()
        reader = null
        deliver(jpeg)
        if (!keepAlive) end()
    }

    private fun deliver(jpeg: ByteArray?) {
        (application as App).companion.attachScreen(ScreenContext(null, jpeg))
        runCatching { startActivity(PanelActivity.intent(this)) }
    }

    private fun end() {
        if (ended) return
        ended = true
        if (live === this) live = null
        handler.removeCallbacksAndMessages(null)
        if (grabbing) deliver(null)
        grabbing = false
        display?.release()
        reader?.close()
        projection?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        end()
        super.onDestroy()
    }

    companion object {
        const val DATA = "data"
        const val CODE = "code"
        private const val STOP = "stop"
        private const val ID = 2

        private var live: CaptureService? = null

        val isLive get() = live != null

        /** Só na thread principal. @return false se não há sessão aberta: é preciso pedir autorização. */
        fun grabLive(): Boolean = live?.also { it.grab() } != null

        fun stopLive() { live?.end() }
    }
}
