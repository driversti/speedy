package dev.seniorjava.speedy.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.seniorjava.speedy.data.SettingsRepository
import dev.seniorjava.speedy.data.SpeedStateHolder
import dev.seniorjava.speedy.domain.DisplayMode
import dev.seniorjava.speedy.domain.ServiceState
import dev.seniorjava.speedy.domain.SpeedSample
import dev.seniorjava.speedy.service.SpeedServiceController
import dev.seniorjava.speedy.ui.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val speedStateHolder: SpeedStateHolder,
    private val serviceController: SpeedServiceController,
    private val permissionChecker: PermissionChecker,
) : ViewModel() {

    private val permissions = MutableStateFlow(readPermissions())

    val state: StateFlow<DashboardUiState> = combine(
        settingsRepository.isEnabled,
        speedStateHolder.serviceState,
        speedStateHolder.speed,
        permissions,
        settingsRepository.displayMode,
    ) { enabled, serviceState, sample, perm, displayMode ->
        DashboardUiState(
            isEnabled = enabled,
            serviceState = serviceState,
            sample = sample,
            permissions = perm,
            displayMode = displayMode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(
            isEnabled = false,
            serviceState = ServiceState.STOPPED,
            sample = SpeedSample.ZERO,
            permissions = permissions.value,
            displayMode = DisplayMode.BOTH,
        ),
    )

    init {
        // Restart the service automatically if persisted state says "enabled"
        // and the prerequisite permissions are granted (covers process-death
        // reopen, not boot — BootReceiver handles boot).
        settingsRepository.isEnabled
            .onEach { enabled ->
                if (enabled && canRun()) serviceController.start()
            }
            .launchIn(viewModelScope)
    }

    fun onToggleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                if (!canRun()) {
                    // Refuse to persist "enabled" if we can't honor it —
                    // prevents a ghost "on" state without a running service.
                    refreshPermissions()
                    return@launch
                }
                settingsRepository.setEnabled(true)
                serviceController.start()
            } else {
                settingsRepository.setEnabled(false)
                serviceController.stop()
            }
        }
    }

    fun onDisplayModeChanged(mode: DisplayMode) {
        viewModelScope.launch {
            settingsRepository.setDisplayMode(mode)
        }
    }

    fun refreshPermissions() {
        permissions.value = readPermissions()
    }

    private fun canRun(): Boolean {
        val p = permissions.value
        return p.notificationsGranted && p.channelEnabled
    }

    private fun readPermissions(): PermissionsState = PermissionsState(
        notificationsGranted = permissionChecker.hasPostNotifications(),
        channelEnabled = permissionChecker.isChannelEnabled(),
        batteryOptimizationIgnored = permissionChecker.isIgnoringBatteryOptimizations(),
    )
}

data class DashboardUiState(
    val isEnabled: Boolean,
    val serviceState: ServiceState,
    val sample: SpeedSample,
    val permissions: PermissionsState,
    val displayMode: DisplayMode,
)

data class PermissionsState(
    val notificationsGranted: Boolean,
    val channelEnabled: Boolean,
    val batteryOptimizationIgnored: Boolean,
)
