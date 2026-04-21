package dev.seniorjava.speedy.data

import dev.seniorjava.speedy.domain.ServiceState
import dev.seniorjava.speedy.domain.SpeedSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the live speed + service status.
 *
 * The foreground service writes here; the Dashboard ViewModel reads via the
 * exposed StateFlows. A Hilt `@Singleton` guarantees both sides see the same
 * instance.
 */
@Singleton
class SpeedStateHolder @Inject constructor() {

    private val _speed = MutableStateFlow(SpeedSample.ZERO)
    val speed: StateFlow<SpeedSample> = _speed.asStateFlow()

    private val _serviceState = MutableStateFlow(ServiceState.STOPPED)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    fun updateSpeed(sample: SpeedSample) {
        _speed.value = sample
    }

    fun updateServiceState(state: ServiceState) {
        _serviceState.value = state
        if (state == ServiceState.STOPPED) {
            _speed.value = SpeedSample.ZERO
        }
    }
}
