package br.offgr1d.companion

import android.app.Application
import android.content.ComponentCallbacks2
import br.offgr1d.companion.chat.Companion2B
import br.offgr1d.companion.llm.LiteRtEngine
import br.offgr1d.companion.store.BlobStore
import br.offgr1d.companion.store.KeystoreCipher
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    val companion: Companion2B by lazy {
        Companion2B(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            store = BlobStore(File(noBackupFilesDir, "data"), KeystoreCipher()),
            modelDir = File(noBackupFilesDir, "models"),
            cacheDir = cacheDir,
            engineFactory = { LiteRtEngine() },
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) companion.releaseMemory()
    }
}
