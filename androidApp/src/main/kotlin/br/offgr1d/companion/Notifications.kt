package br.offgr1d.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.content.Intent
import android.provider.Settings
import br.offgr1d.companion.alerts.Alert
import br.offgr1d.companion.alerts.AlertFeed

object Notifications {
    private const val CHANNEL = "overlay"
    private const val ALERTS = "alert-"

    private fun manager(c: Context) = c.getSystemService(NotificationManager::class.java)

    /** Notificação fixa enquanto a bolha ou a leitura de tela estão ligadas. */
    fun build(c: Context, text: String, stop: PendingIntent? = null, stopLabel: Int = R.string.overlay_stop): Notification {
        manager(c).createNotificationChannel(
            NotificationChannel(CHANNEL, c.getString(R.string.channel_overlay), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(c, 1, PanelActivity.intent(c), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_2b)
            .setContentTitle(text)
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setOngoing(true)
            .apply { stop?.let { addAction(Notification.Action.Builder(null, c.getString(stopLabel), it).build()) } }
            .build()
    }

    // ---------- avisos do 0FF-GR1D ----------

    private fun channelId(feed: AlertFeed) = ALERTS + feed.name.lowercase()

    /** Um canal por assunto: a pessoa muda som e bloqueio de cada um no Android. */
    fun createAlertChannels(c: Context) = AlertFeed.entries.forEach {
        manager(c).createNotificationChannel(NotificationChannel(channelId(it), it.label, NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun allowed(c: Context) = manager(c).areNotificationsEnabled()

    fun alert(c: Context, a: Alert, picture: Bitmap? = null) {
        createAlertChannels(c)
        val open = PendingIntent.getActivity(
            c, 3, MainActivity.chatIntent(c), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Com o conteúdo escondido na tela bloqueada, não revela o assunto.
        val locked = Notification.Builder(c, channelId(a.feed)).setSmallIcon(R.drawable.ic_2b).setContentTitle(c.getString(R.string.alert_locked)).build()
        val n = Notification.Builder(c, channelId(a.feed))
            .setSmallIcon(R.drawable.ic_2b)
            .setColor(0xFF00FF84.toInt())
            .setContentTitle(a.title)
            .setContentText(a.text)
            .setStyle(
                if (picture != null) Notification.BigPictureStyle().bigPicture(picture).setSummaryText("2B: ${a.voice} Toque para ver o vídeo.")
                else Notification.BigTextStyle().bigText("${a.text}\n\n2B: ${a.voice}"),
            )
            .apply { if (picture != null) setLargeIcon(picture) }
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(locked)
            .build()
        runCatching { manager(c).notify(channelId(a.feed) + a.id, 1, n) } // sem permissão, a mensagem ainda está na conversa
    }

    fun cancelAlerts(c: Context) =
        manager(c).activeNotifications.filter { it.tag?.startsWith(ALERTS) == true }.forEach { manager(c).cancel(it.tag, it.id) }

    fun settingsIntent(c: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
}
