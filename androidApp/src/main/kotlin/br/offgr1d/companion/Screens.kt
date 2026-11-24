package br.offgr1d.companion

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import br.offgr1d.companion.capture.CaptureActivity
import br.offgr1d.companion.chat.Mood
import br.offgr1d.companion.overlay.OverlayService
import br.offgr1d.companion.ui.CompanionApp
import br.offgr1d.companion.ui.DefaultAvatar
import br.offgr1d.companion.ui.Palette
import br.offgr1d.companion.ui.PlatformActions

/** Arte em assets/avatar/{idle,thinking,talking,alert}.png. Sem arquivo, usa o símbolo provisório. */
object AvatarArt {
    fun load(assets: AssetManager, mood: Mood): Bitmap? =
        runCatching { assets.open("avatar/${mood.name.lowercase()}.png").use(BitmapFactory::decodeStream) }.getOrNull()
}

abstract class BaseActivity : ComponentActivity() {
    protected val companion get() = (application as App).companion
    protected var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fechado por padrão até os ajustes serem lidos.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }

    @Composable
    protected fun SecureWindow() {
        val state by companion.state.collectAsState()
        val block = state.settings.blockScreenshots
        LaunchedEffect(block) {
            if (block) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    protected fun actions(extra: @Composable ColumnScope.() -> Unit = {}) = PlatformActions(
        openUrl = { url -> if (url.startsWith("https://")) open(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        captureScreen = ::captureScreen,
        extraSettings = extra,
        avatar = { mood, m -> Avatar(mood, m) },
    )

    /** Sai da frente antes de ler: o que interessa é o app de baixo. */
    private fun captureScreen() {
        startActivity(Intent(this, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        if (this is PanelActivity) finish() else moveTaskToBack(true)
    }

    protected fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            companion.notice("Nenhum app para abrir isso.")
        }
    }

    @Composable
    private fun Avatar(mood: Mood, modifier: Modifier) {
        val image = remember(mood) { (AvatarArt.load(assets, mood) ?: AvatarArt.load(assets, Mood.IDLE))?.asImageBitmap() }
        if (image != null) Image(image, "2B", modifier) else DefaultAvatar(mood, modifier)
    }
}

class MainActivity : BaseActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { startOverlay() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val platform = actions { SystemSettings() }
        setContent {
            SecureWindow()
            Box(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding()) { CompanionApp(companion, platform) }
        }
    }

    @Composable
    private fun SystemSettings() {
        val tick = refresh // relê as permissões ao voltar dos ajustes do Android
        val canOverlay = remember(tick) { Settings.canDrawOverlays(this) }
        val running = remember(tick) { OverlayService.running }
        Text("2B SOBRE OUTROS APPS", color = Palette.Green)
        TextButton(onClick = {
            when {
                !canOverlay -> open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                running -> {
                    stopService(Intent(this, OverlayService::class.java))
                    OverlayService.running = false
                    refresh++
                }
                Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> startOverlay()
            }
        }) {
            Text(if (!canOverlay) "PERMITIR A BOLHA" else if (running) "ESCONDER A BOLHA" else "MOSTRAR A BOLHA", color = Palette.Cyan)
        }
        Text("Toque na bolha para conversar; segure para ler a tela.", color = Palette.Dim)
        Text("Como assistente do Android, a 2B abre pelo botão ou gesto de assistente e recebe a tela sem pedir captura.", color = Palette.Dim)
        TextButton(onClick = { open(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }) { Text("ESCOLHER ASSISTENTE PADRÃO", color = Palette.Cyan) }
    }

    private fun startOverlay() {
        startForegroundService(Intent(this, OverlayService::class.java))
        OverlayService.running = true
        refresh++
    }
}

/** A mesma conversa, numa folha por cima do app aberto. */
class PanelActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val platform = actions()
        setContent {
            SecureWindow()
            Box(
                Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) { finish() }.safeDrawingPadding(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(0.75f).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                        .clickable(remember { MutableInteractionSource() }, indication = null) {},
                ) { CompanionApp(companion, platform, compact = true, onClose = ::finish) }
            }
        }
    }

    companion object {
        fun intent(c: Context): Intent =
            Intent(c, PanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
