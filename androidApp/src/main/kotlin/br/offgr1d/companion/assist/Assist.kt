package br.offgr1d.companion.assist

import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.View
import br.offgr1d.companion.App
import br.offgr1d.companion.PanelActivity
import br.offgr1d.companion.capture.Images
import br.offgr1d.companion.screen.ScreenContext

/** Assistente do Android: o sistema entrega a tela quando a pessoa chama a 2B. */
class AssistService : VoiceInteractionService()

class AssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = AssistSession(this)
}

class AssistSession(context: Context) : VoiceInteractionSession(context) {
    private val handler = Handler(Looper.getMainLooper())
    private var text: String? = null
    private var image: ByteArray? = null
    private var opened = false

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        setUiEnabled(false) // a conversa abre na nossa própria janela
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        text = null
        image = null
        opened = false
        // Se a pessoa desligou o envio da tela no Android, abre sem ela.
        handler.postDelayed({ openPanel() }, 900)
    }

    override fun onHandleAssist(state: AssistState) {
        super.onHandleAssist(state)
        val structure = state.assistStructure
        if (structure != null) text = runCatching { ScreenText.read(structure) }.getOrNull()
        if (image != null) openPanel()
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        image = screenshot?.let { b -> runCatching { Images.jpeg(b) }.getOrNull() }
        if (text != null && image != null) openPanel()
    }

    private fun openPanel() {
        if (opened) return
        opened = true
        handler.removeCallbacksAndMessages(null)
        val screen = ScreenContext(text, image)
        (context.applicationContext as App).companion.attachScreen(screen.takeUnless { it.isEmpty })
        text = null
        image = null
        runCatching { startAssistantActivity(PanelActivity.intent(context)) }
            .onFailure { runCatching { context.startActivity(PanelActivity.intent(context)) } }
        hide()
    }

    override fun onHide() {
        handler.removeCallbacksAndMessages(null)
        super.onHide()
    }
}

object ScreenText {
    private const val MAX = 6000

    fun read(s: AssistStructure): String {
        val out = LinkedHashSet<String>()
        for (i in 0 until s.windowNodeCount) visit(s.getWindowNodeAt(i).rootViewNode, out)
        return out.joinToString("\n").take(MAX)
    }

    private fun visit(n: AssistStructure.ViewNode, out: MutableSet<String>) {
        if (n.visibility != View.VISIBLE || n.isAssistBlocked || isPassword(n)) return
        val t = n.text?.toString()?.trim().orEmpty().ifEmpty { n.contentDescription?.toString()?.trim().orEmpty() }
        if (t.isNotEmpty()) out += t
        n.webDomain?.let { out += "site: $it" }
        for (i in 0 until n.childCount) visit(n.getChildAt(i), out)
    }

    private val PASSWORDS = setOf(
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
    )

    private fun isPassword(n: AssistStructure.ViewNode): Boolean =
        (n.inputType and (InputType.TYPE_MASK_CLASS or InputType.TYPE_MASK_VARIATION)) in PASSWORDS ||
            n.autofillHints?.any { "password" in it.lowercase() } == true
}

/** Exigido pelo Android para o app poder ser assistente. Nunca abre o microfone. */
class NoRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, callback: Callback?) {
        runCatching { callback?.error(SpeechRecognizer.ERROR_CLIENT) }
    }

    override fun onCancel(callback: Callback?) {}
    override fun onStopListening(callback: Callback?) {}
}
