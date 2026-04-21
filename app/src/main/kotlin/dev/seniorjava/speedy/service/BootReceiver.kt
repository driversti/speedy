package dev.seniorjava.speedy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.seniorjava.speedy.data.SettingsRepository
import dev.seniorjava.speedy.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-launches the monitor service after a reboot when the user had it enabled.
 *
 * NOT `directBootAware` — the DataStore preferences file lives in
 * credential-protected storage and is inaccessible before the first unlock.
 * Without this guard, the coroutine that reads `SettingsRepository.isEnabled`
 * would crash with IOException on boot-completed of an encrypted device.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var serviceController: SpeedServiceController

    @Inject
    @field:AppScope
    lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        appScope.launch {
            try {
                if (settingsRepository.isEnabled.first()) {
                    serviceController.start()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
