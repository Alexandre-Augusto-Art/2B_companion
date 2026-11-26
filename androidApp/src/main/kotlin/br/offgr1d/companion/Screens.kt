package br.offgr1d.companion

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import br.offgr1d.companion.capture.CaptureActivity
import br.offgr1d.companion.capture.CaptureService
import br.offgr1d.companion.overlay.OverlayService
import br.offgr1d.companion.ui.AlertActions
import br.offgr1d.companion.ui.CompanionApp
import br.offgr1d.companion.ui.Palette
import br.offgr1d.companion.ui.PlatformActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class BaseActivity : ComponentActivity() {
    protected val app get() = application as App
    protected val companion get() = app.companion
    protected var refresh by mutableIntStateOf(0)

    // Seletor do Android: não pede permissão de galeria e só entrega o arquivo escolhido.
    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) app.scope.launch(Dispatchers.IO) {
            runCatching { MediaFiles.read(applicationContext, uri) }
                .onSuccess { companion.attachScreen(it) }
                .onFailure { companion.notice("Não consegui abrir esse arquivo.") }
        }
    }

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

    protected fun actions(alerts: AlertActions? = null, extra: @Composable ColumnScope.() -> Unit = {}) = PlatformActions(
        openUrl = { url -> if (url.startsWith("https://")) open(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        captureScreen = ::captureScreen,
        alerts = alerts,
        autoScreenNote = "Ao tocar na bolha. O Android pede autorização uma vez e mostra o aviso enquanto estiver ligado; ao bloquear " +
            "o celular, pode pedir de novo. A imagem fica só na memória e só é usada se você enviar.",
        extraSettings = extra,
        pickMedia = { runCatching { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) } },
        videoPlayer = { file, modifier -> VideoPlayer(file, modifier) },
        openFile = {},
    )

    /** Sai da frente antes de ler: o que interessa é o app de baixo. */
    private fun captureScreen() {
        if (this is PanelActivity) finish() else moveTaskToBack(true)
        // Com a sessão aberta não há diálogo: dá tempo de esta janela sumir.
        Handler(Looper.getMainLooper()).postDelayed({
            if (!CaptureService.grabLive()) {
                applicationContext.startActivity(Intent(applicationContext, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }, if (CaptureService.isLive) 600 else 0)
    }

    protected fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            companion.notice("Nenhum app para abrir isso.")
        }
    }
}

class MainActivity : BaseActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { startOverlay() }
    private val askAlerts = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val tick = refresh
            val platform = remember(tick) { actions(alertActions()) { SystemSettings() } }
            SecureWindow()
            Box(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding()) { CompanionApp(companion, platform) }
        }
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == OPEN_CHAT) companion.focusChat()
    }

    private fun alertActions() = AlertActions(
        enabled = {
            Notifications.createAlertChannels(this)
            if (needsPermission()) askAlerts.launch(Manifest.permission.POST_NOTIFICATIONS)
            // A primeira consulta só marca o que já existe; avisa do que vier depois.
            app.scope.launch { runCatching { app.checkAlerts() } }
        },
        test = {
            if (needsPermission()) askAlerts.launch(Manifest.permission.POST_NOTIFICATIONS)
            else Notifications.alert(this, companion.sampleAlert())
        },
        allowed = Notifications.allowed(this),
        openSettings = { open(Notifications.settingsIntent(this)) },
    )

    private fun needsPermission() =
        Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

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
                needsPermission() ->
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> startOverlay()
            }
        }) {
            Text(if (!canOverlay) "PERMITIR A BOLHA" else if (running) "ESCONDER A BOLHA" else "MOSTRAR A BOLHA", color = Palette.Cyan)
        }
        Text("Toque na bolha para conversar; segure para ler a tela.", color = Palette.Dim)
        Text("Como assistente do Android, a 2B abre pelo botão ou gesto de assistente e recebe a tela sem pedir captura.", color = Palette.Dim)
        TextButton(onClick = { open(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }) { Text("ESCOLHER ASSISTENTE PADRÃO", color = Palette.Cyan) }
        Text("Avisos atrasando? Deixe a 2B fora da economia de bateria.", color = Palette.Dim)
        TextButton(onClick = { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text("ECONOMIA DE BATERIA", color = Palette.Cyan) }
    }

    private fun startOverlay() {
        startForegroundService(Intent(this, OverlayService::class.java))
        OverlayService.running = true
        refresh++
    }

    companion object {
        private const val OPEN_CHAT = "br.offgr1d.companion.OPEN_CHAT"

        fun chatIntent(c: Context): Intent = Intent(c, MainActivity::class.java).setAction(OPEN_CHAT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

    override fun onStart() {
        super.onStart()
        visible = true
    }

    override fun onStop() {
        visible = false
        super.onStop()
    }

    override fun onPause() {
        // Antes de qualquer leitura nova: a próxima abertura da bolha lê a tela do momento.
        if (isFinishing && companion.state.value.settings.autoScreen) companion.attachScreen(null)
        super.onPause()
    }

    companion object {
        var visible = false
            private set

        fun intent(c: Context): Intent =
            Intent(c, PanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
