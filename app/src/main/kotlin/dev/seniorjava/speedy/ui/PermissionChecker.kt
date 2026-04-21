package dev.seniorjava.speedy.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.seniorjava.speedy.service.SpeedNotifications
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPostNotifications(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun isChannelEnabled(): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val system = context.getSystemService<NotificationManager>() ?: return true
        val channel = system.getNotificationChannel(SpeedNotifications.CHANNEL_ID) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val power = context.getSystemService<PowerManager>() ?: return true
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }
}
