package br.offgr1d.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context

object Notifications {
    private const val CHANNEL = "overlay"

    fun build(c: Context, text: String, stop: PendingIntent? = null): Notification {
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, c.getString(R.string.channel_overlay), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(c, 1, PanelActivity.intent(c), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_2b)
            .setContentTitle(text)
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setOngoing(true)
            .apply { stop?.let { addAction(Notification.Action.Builder(null, c.getString(R.string.overlay_stop), it).build()) } }
            .build()
    }
}
