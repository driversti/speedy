package dev.seniorjava.speedy.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.AndroidEntryPoint
import dev.seniorjava.speedy.data.SettingsRepository
import dev.seniorjava.speedy.data.SpeedStateHolder
import dev.seniorjava.speedy.domain.DisplayMode
import dev.seniorjava.speedy.domain.ServiceState
import dev.seniorjava.speedy.domain.SpeedCalculator
import dev.seniorjava.speedy.domain.SpeedSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground Service that samples [TrafficStats] once per second and publishes
 * the resulting [SpeedSample] to:
 *   1. [SpeedStateHolder] (shared with the Dashboard UI)
 *   2. The ongoing notification (status-bar icon + shade text)
 *
 * Lifecycle concerns covered:
 *   - Android 14+ requires [ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE].
 *   - Screen OFF → pause the tick loop (battery). Screen ON → resume + redraw
 *     immediately.
 *   - Network loss (airplane mode, no transport) → pause + hide icon via
 *     [NotificationManagerCompat.cancel], and transition to WAITING_FOR_NETWORK.
 *     A [ConnectivityManager.NetworkCallback] resumes when transport reappears.
 *   - [DisplayMode] is collected from [SettingsRepository] and applied on each tick.
 */
@AndroidEntryPoint
class SpeedMonitorService : Service() {

    @Inject lateinit var calculator: SpeedCalculator
    @Inject lateinit var notificationFactory: SpeedNotificationFactory
    @Inject lateinit var stateHolder: SpeedStateHolder
    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickJob: Job? = null

    private var screenOn: Boolean = true
    private var networkAvailable: Boolean = false
    @Volatile private var currentDisplayMode: DisplayMode = DisplayMode.BOTH

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    stopTicking()
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    maybeStartTicking()
                }
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            networkAvailable = true
            maybeStartTicking()
        }

        override fun onLost(network: Network) {
            networkAvailable = hasAnyNetwork()
            if (!networkAvailable) {
                stopTicking()
                stateHolder.updateServiceState(ServiceState.WAITING_FOR_NETWORK)
                stateHolder.updateSpeed(SpeedSample.ZERO)
                NotificationManagerCompat.from(this@SpeedMonitorService)
                    .cancel(SpeedNotifications.NOTIFICATION_ID)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        networkAvailable = hasAnyNetwork()

        scope.launch {
            settingsRepository.displayMode.collect { mode ->
                currentDisplayMode = mode
            }
        }

        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val connectivityManager = getSystemService<ConnectivityManager>()
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager?.registerNetworkCallback(request, networkCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithPlaceholder()
        if (networkAvailable) {
            stateHolder.updateServiceState(ServiceState.ACTIVE)
            maybeStartTicking()
        } else {
            stateHolder.updateServiceState(ServiceState.WAITING_FOR_NETWORK)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        tickJob?.cancel()
        scope.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching {
            getSystemService<ConnectivityManager>()?.unregisterNetworkCallback(networkCallback)
        }
        calculator.reset()
        stateHolder.updateServiceState(ServiceState.STOPPED)
        super.onDestroy()
    }

    private fun startForegroundWithPlaceholder() {
        val notification = notificationFactory.build(SpeedSample.ZERO, currentDisplayMode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                SpeedNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(SpeedNotifications.NOTIFICATION_ID, notification)
        }
    }

    private fun maybeStartTicking() {
        if (tickJob?.isActive == true) return
        if (!screenOn || !networkAvailable) return
        stateHolder.updateServiceState(ServiceState.ACTIVE)
        tickJob = scope.launch { tickLoop() }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
        calculator.reset()
    }

    private suspend fun tickLoop() {
        val manager = NotificationManagerCompat.from(this)
        while (scope.isActive) {
            val nowMs = SystemClock.elapsedRealtime()
            val sample = calculator.sample(
                rxBytes = TrafficStats.getTotalRxBytes(),
                txBytes = TrafficStats.getTotalTxBytes(),
                nowMs = nowMs,
            )
            stateHolder.updateSpeed(sample)
            val notification = notificationFactory.build(sample, currentDisplayMode)
            runCatching {
                manager.notify(SpeedNotifications.NOTIFICATION_ID, notification)
            }
            delay(TICK_INTERVAL_MS)
        }
    }

    private fun hasAnyNetwork(): Boolean {
        val cm = getSystemService<ConnectivityManager>() ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val TICK_INTERVAL_MS = 1_000L
    }
}
