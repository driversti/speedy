package dev.seniorjava.speedy.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.seniorjava.speedy.R
import dev.seniorjava.speedy.domain.DisplayMode
import dev.seniorjava.speedy.domain.SpeedFormatter
import dev.seniorjava.speedy.domain.SpeedSample
import dev.seniorjava.speedy.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeedNotificationFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val iconRenderer: SpeedIconRenderer,
    private val formatter: SpeedFormatter,
) {
    private val contentIntent: PendingIntent by lazy {
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun build(sample: SpeedSample, mode: DisplayMode): Notification {
        val bitmap = iconRenderer.render(sample, mode)
        val text = when (mode) {
            DisplayMode.BOTH -> context.getString(
                R.string.notification_text,
                formatter.formatFull(sample.downloadBps),
                formatter.formatFull(sample.uploadBps),
            )
            DisplayMode.DOWNLOAD -> context.getString(
                R.string.notification_text_download,
                formatter.formatFull(sample.downloadBps),
            )
            DisplayMode.UPLOAD -> context.getString(
                R.string.notification_text_upload,
                formatter.formatFull(sample.uploadBps),
            )
        }
        return NotificationCompat.Builder(context, SpeedNotifications.CHANNEL_ID)
            .setSmallIcon(IconCompat.createWithBitmap(bitmap))
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(contentIntent)
            .build()
    }
}
