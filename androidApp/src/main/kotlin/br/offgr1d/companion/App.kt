package br.offgr1d.companion

import android.app.Activity
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class App : Application(), Application.ActivityLifecycleCallbacks {
    /** Falha em segundo plano vira aviso na conversa: não pode fechar o app, muito menos a cada abertura. */
    private val onFailure = CoroutineExceptionHandler { _, e ->
        runCatching { companion.notice("Algo falhou no app: ${e.message ?: e.javaClass.simpleName}") }
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + onFailure)
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
                    runCatching { AlertJob.schedule(this@App, on) }
                        .onFailure { companion.notice("Não consegui agendar os avisos: ${it.message ?: it.javaClass.simpleName}") }
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
        fresh.forEach { a ->
            // A capa vai na notificação; o vídeo baixa em seguida, menos em dados móveis.
            val picture = a.poster?.let { url -> runCatching { companion.picture(url) }.getOrNull() }
                ?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size) }
            Notifications.alert(this, a, picture)
            if (a.video != null && !metered()) runCatching { companion.video(a.video!!) }
        }
        return fresh
    }

    private fun metered() = getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true

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
