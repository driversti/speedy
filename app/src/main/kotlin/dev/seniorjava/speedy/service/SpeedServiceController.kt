package dev.seniorjava.speedy.service

import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper used by the Dashboard (and BootReceiver) to start / stop the
 * foreground service. Keeps Intent plumbing out of the ViewModel.
 */
@Singleton
class SpeedServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun start() {
        val intent = Intent(context, SpeedMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop() {
        context.stopService(Intent(context, SpeedMonitorService::class.java))
    }
}
