package dev.seniorjava.speedy.ui.dashboard

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.seniorjava.speedy.data.SettingsRepository
import dev.seniorjava.speedy.data.SpeedStateHolder
import dev.seniorjava.speedy.domain.DisplayMode
import dev.seniorjava.speedy.domain.ServiceState
import dev.seniorjava.speedy.domain.SpeedSample
import dev.seniorjava.speedy.service.SpeedServiceController
import dev.seniorjava.speedy.ui.PermissionChecker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val isEnabledFlow = MutableStateFlow(false)
    private val serviceStateFlow = MutableStateFlow(ServiceState.STOPPED)
    private val speedFlow = MutableStateFlow(SpeedSample.ZERO)
    private val displayModeFlow = MutableStateFlow(DisplayMode.BOTH)

    private val settingsRepository: SettingsRepository = mockk {
        every { isEnabled } returns isEnabledFlow
        every { displayMode } returns displayModeFlow
        coEvery { setDisplayMode(any()) } just runs
    }
    private val speedStateHolder: SpeedStateHolder = mockk {
        every { serviceState } returns serviceStateFlow
        every { speed } returns speedFlow
    }
    private val serviceController: SpeedServiceController = mockk(relaxed = true)
    private val permissionChecker: PermissionChecker = mockk {
        every { hasPostNotifications() } returns true
        every { isChannelEnabled() } returns true
        every { isIgnoringBatteryOptimizations() } returns false
    }

    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = DashboardViewModel(
            settingsRepository = settingsRepository,
            speedStateHolder = speedStateHolder,
            serviceController = serviceController,
            permissionChecker = permissionChecker,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has displayMode BOTH`() = runTest {
        viewModel.state.test {
            assertThat(awaitItem().displayMode).isEqualTo(DisplayMode.BOTH)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state updates when displayMode flow changes`() = runTest {
        viewModel.state.test {
            assertThat(awaitItem().displayMode).isEqualTo(DisplayMode.BOTH)
            displayModeFlow.value = DisplayMode.DOWNLOAD
            assertThat(awaitItem().displayMode).isEqualTo(DisplayMode.DOWNLOAD)
            displayModeFlow.value = DisplayMode.UPLOAD
            assertThat(awaitItem().displayMode).isEqualTo(DisplayMode.UPLOAD)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDisplayModeChanged persists the selected mode`() = runTest {
        viewModel.onDisplayModeChanged(DisplayMode.UPLOAD)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { settingsRepository.setDisplayMode(DisplayMode.UPLOAD) }
    }
}
