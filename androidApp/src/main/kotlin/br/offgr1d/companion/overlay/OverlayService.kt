package br.offgr1d.companion.overlay

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import br.offgr1d.companion.App
import br.offgr1d.companion.Notifications
import br.offgr1d.companion.PanelActivity
import br.offgr1d.companion.R
import br.offgr1d.companion.capture.CaptureActivity
import br.offgr1d.companion.capture.CaptureService
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Bolha da 2B: arraste para mover, toque para conversar, segure para ler a tela. */
class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var view: AvatarView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val stop = PendingIntent.getService(this, 2, Intent(this, OverlayService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val n = Notifications.build(this, getString(R.string.overlay_running), stop)
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(ID, n)
        wm = getSystemService(WindowManager::class.java)
        if (Settings.canDrawOverlays(this)) show() else stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) stopSelf()
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun show() {
        val size = (64 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = resources.displayMetrics.heightPixels / 3
        }
        val v = AvatarView(this)
        v.contentDescription = getString(R.string.open_2b)
        v.setOnClickListener {
            val auto = (application as App).companion.state.value.settings.autoScreen
            when {
                !auto || PanelActivity.visible -> startActivity(PanelActivity.intent(this))
                !CaptureService.grabLive() -> askCapture()
            }
        }
        v.setOnLongClickListener {
            if (!CaptureService.grabLive()) askCapture()
            true
        }
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        v.setOnTouchListener { touched, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && hypot(e.rawX - downX, e.rawY - downY) > slop) {
                        dragging = true
                        touched.cancelLongPress()
                        touched.isPressed = false
                    }
                    if (dragging) {
                        params.x = startX + (e.rawX - downX).toInt()
                        params.y = startY + (e.rawY - downY).toInt()
                        wm.updateViewLayout(touched, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (dragging) {
                    val dm = resources.displayMetrics
                    params.x = if (params.x + size / 2 < dm.widthPixels / 2) 0 else dm.widthPixels - size
                    params.y = params.y.coerceIn(0, (dm.heightPixels - size).coerceAtLeast(0))
                    wm.updateViewLayout(touched, params)
                }
            }
            dragging
        }
        wm.addView(v, params)
        view = v
        running = true
        scope.launch {
            (application as App).companion.state.map { it.mood }.distinctUntilChanged().collect { v.mood = it }
        }
    }

    private fun askCapture() = startActivity(Intent(this, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    override fun onDestroy() {
        scope.cancel()
        view?.let { wm.removeView(it) }
        view = null
        running = false
        super.onDestroy()
    }

    companion object {
        private const val ID = 1
        private const val STOP = "stop"

        @Volatile var running = false
    }
}
