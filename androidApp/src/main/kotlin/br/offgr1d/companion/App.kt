package br.offgr1d.companion

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Bundle
import br.offgr1d.companion.alerts.Alert
import br.offgr1d.companion.alerts.AlertJob
import br.offgr1d.companion.capture.CaptureService
import br.offgr1d.companion.chat.Companion2B
import br.offgr1d.companion.llm.LiteRtEngine
import br.offgr1d.companion.store.BlobStore
import br.offgr1d.companion.store.KeystoreCipher
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class App : Application(), Application.ActivityLifecycleCallbacks {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var visible = 0

    val companion: Companion2B by lazy {
        Companion2B(
            scope = scope,
            store = BlobStore(File(noBackupFilesDir, "data"), KeystoreCipher()),
            modelDir = File(noBackupFilesDir, "models"),
            cacheDir = cacheDir,
            engineFactory = { LiteRtEngine() },
        )
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        scope.launch(Dispatchers.Main) {
            companion.awaitReady() // antes disso os ajustes ainda são os padrões
            launch {
                companion.state.map { it.settings.anyAlert }.distinctUntilChanged().collect { on ->
                    AlertJob.schedule(this@App, on)
                    if (!on) Notifications.cancelAlerts(this@App)
                }
            }
            launch {
                companion.state.map { it.settings.autoScreen }.distinctUntilChanged().collect { on -> if (!on) CaptureService.stopLive() }
            }
        }
    }

    /** @return null se não deu para consultar */
    suspend fun checkAlerts(): List<Alert>? {
        val fresh = companion.checkAlerts() ?: return null
        fresh.forEach { Notifications.alert(this, it) }
        return fresh
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) companion.releaseMemory()
    }

    // A conversa está à vista quando uma das janelas dela está.
    override fun onActivityStarted(activity: Activity) {
        if (activity is BaseActivity && visible++ == 0) companion.onVisible()
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity is BaseActivity && --visible == 0) companion.onHidden()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
