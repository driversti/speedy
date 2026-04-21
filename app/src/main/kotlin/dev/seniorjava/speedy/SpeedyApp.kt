package dev.seniorjava.speedy

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.getSystemService
import dagger.hilt.android.HiltAndroidApp
import dev.seniorjava.speedy.service.SpeedNotifications

@HiltAndroidApp
class SpeedyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        registerNotificationChannel()
    }

    private fun registerNotificationChannel() {
        val channel = NotificationChannel(
            SpeedNotifications.CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }
}
