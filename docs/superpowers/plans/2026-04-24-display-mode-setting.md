# Display Mode Setting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a user-facing setting that controls which speed direction is shown in the status-bar icon: download only, upload only, or both (default).

**Architecture:** A new `DisplayMode` enum lives in `domain/`. `SettingsRepository` persists it via DataStore. `SpeedMonitorService` collects the flow and passes the current value as a parameter to `SpeedNotificationFactory.build()` and `SpeedIconRenderer.render()`. `DashboardViewModel` exposes it in `DashboardUiState` and the `DashboardScreen` renders a new radio-button card.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, DataStore Preferences, kotlinx-coroutines, MockK, Google Truth, Turbine

---

## File Map

| File | Action |
|------|--------|
| `app/src/main/kotlin/dev/seniorjava/speedy/domain/DisplayMode.kt` | **Create** |
| `app/src/main/kotlin/dev/seniorjava/speedy/data/SettingsRepository.kt` | **Modify** |
| `app/src/main/kotlin/dev/seniorjava/speedy/service/SpeedIconRenderer.kt` | **Modify** |
| `app/src/main/kotlin/dev/seniorjava/speedy/service/SpeedNotificationFactory.kt` | **Modify** |
| `app/src/main/kotlin/dev/seniorjava/speedy/service/SpeedMonitorService.kt` | **Modify** |
| `app/src/main/kotlin/dev/seniorjava/speedy/ui/dashboard/DashboardViewModel.kt` | **Modify** |
| `app/src/main/kotlin/dev/seniorjava/speedy/ui/dashboard/DashboardScreen.kt` | **Modify** |
| `app/src/main/kotlin/dev/seniorjava/speedy/ui/MainActivity.kt` | **Modify** |
| `app/src/main/res/values/strings.xml` | **Modify** |
| `app/src/main/res/values-uk/strings.xml` | **Modify** |
| `app/src/test/kotlin/dev/seniorjava/speedy/ui/dashboard/DashboardViewModelTest.kt` | **Create** |

---

### Task 1: Add `DisplayMode` enum

**Files:**
- Create: `app/src/main/kotlin/dev/seniorjava/speedy/domain/DisplayMode.kt`

- [ ] **Step 1: Create the file**

```kotlin
package dev.seniorjava.speedy.domain

enum class DisplayMode { BOTH, DOWNLOAD, UPLOAD }
```

- [ ] **Step 2: Build to verify it compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/seniorjava/speedy/domain/DisplayMode.kt
git commit -m "feat(domain): add DisplayMode enum"
```

---

### Task 2: Extend `SettingsRepository` with display mode

**Files:**
- Modify: `app/src/main/kotlin/dev/seniorjava/speedy/data/SettingsRepository.kt`

- [ ] **Step 1: Update `SettingsRepository.kt`**

Replace the entire file with:

```kotlin
package dev.seniorjava.speedy.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.seniorjava.speedy.domain.DisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted user preferences.
 *
 * Reads return sensible defaults on first run (service disabled, display mode BOTH).
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val isEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.IS_ENABLED] ?: false }

    val displayMode: Flow<DisplayMode> = dataStore.data.map { prefs ->
        prefs[Keys.DISPLAY_MODE]
            ?.let { runCatching { DisplayMode.valueOf(it) }.getOrNull() }
            ?: DisplayMode.BOTH
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.IS_ENABLED] = enabled }
    }

    suspend fun setDisplayMode(mode: DisplayMode) {
        dataStore.edit { it[Keys.DISPLAY_MODE] = mode.name }
    }

    private object Keys {
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/seniorjava/speedy/data/SettingsRepository.kt
git commit -m "feat(data): add displayMode preference to SettingsRepository"
```

---

### Task 3: Update `SpeedIconRenderer` to accept `DisplayMode`

**Files:**
- Modify: `app/src/main/kotlin/dev/seniorjava/speedy/service/SpeedIconRenderer.kt`

> Note: `SpeedIconRenderer` uses Android `Bitmap`/`Canvas` so it cannot be unit-tested on the JVM. Visual correctness of `CENTER_BASELINE_PX` is verified manually on device.

- [ ] **Step 1: Update `SpeedIconRenderer.kt`**

Replace the entire file with:

```kotlin
package dev.seniorjava.speedy.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import dev.seniorjava.speedy.domain.DisplayMode
import dev.seniorjava.speedy.domain.SpeedFormatter
import dev.seniorjava.speedy.domain.SpeedSample
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.LazyThreadSafetyMode.NONE

/**
 * Renders the monochromatic status-bar icon. Per spec (section 5):
 *   - Bitmap is ALPHA_8, 96×96 px (matches `status_bar_icon_size` at xxxhdpi;
 *     smaller assets fall back to a blank small icon on modern Pixels).
 *   - A single Bitmap + Canvas is reused across ticks — we CLEAR it, redraw
 *     the new text, and hand the same instance to `Icon.createWithBitmap()`.
 *   - Paint.color = WHITE — the system uses only the alpha channel.
 *   - Text is drawn at [TEXT_SIZE_PX] when it fits, and auto-scaled down
 *     per-line when it doesn't, so 5-character readings like `↑999M` still
 *     fit the unit suffix without the system cropping it.
 */
@Singleton
class SpeedIconRenderer @Inject constructor(
    private val formatter: SpeedFormatter,
) {
    private val bitmap: Bitmap by lazy(NONE) {
        Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ALPHA_8)
    }
    private val canvas: Canvas by lazy(NONE) { Canvas(bitmap) }

    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    /**
     * Redraws the reused bitmap according to [mode]:
     *   - BOTH: upload top, download bottom (two lines)
     *   - DOWNLOAD / UPLOAD: single value centered vertically
     */
    fun render(sample: SpeedSample, mode: DisplayMode): Bitmap {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        when (mode) {
            DisplayMode.BOTH -> {
                drawLine("↑" + formatter.formatCompact(sample.uploadBps), UP_BASELINE_PX)
                drawLine("↓" + formatter.formatCompact(sample.downloadBps), DOWN_BASELINE_PX)
            }
            DisplayMode.DOWNLOAD -> {
                drawLine("↓" + formatter.formatCompact(sample.downloadBps), CENTER_BASELINE_PX)
            }
            DisplayMode.UPLOAD -> {
                drawLine("↑" + formatter.formatCompact(sample.uploadBps), CENTER_BASELINE_PX)
            }
        }
        return bitmap
    }

    private fun drawLine(text: String, baseline: Float) {
        paint.textSize = TEXT_SIZE_PX
        val width = paint.measureText(text)
        if (width > MAX_TEXT_WIDTH_PX) {
            paint.textSize = TEXT_SIZE_PX * MAX_TEXT_WIDTH_PX / width
        }
        canvas.drawText(text, SIZE_PX / 2f, baseline, paint)
    }

    private companion object {
        const val SIZE_PX = 96
        const val TEXT_SIZE_PX = 48f
        const val UP_BASELINE_PX = 44f
        const val DOWN_BASELINE_PX = 94f
        // Vertical center of the 96px bitmap, accounting for text cap-height (~40px at 48sp).
        const val CENTER_BASELINE_PX = 60f
        // Leave 4px breathing room on each side before the system clips.
        const val MAX_TEXT_WIDTH_PX = 88f
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/seniorjava/speedy/service/SpeedIconRenderer.kt
git commit -m "feat(service): support DisplayMode in SpeedIconRenderer"
```

---

### Task 4: Update `SpeedNotificationFactory` to accept `DisplayMode`

**Files:**
- Modify: `app/src/main/kotlin/dev/seniorjava/speedy/service/SpeedNotificationFactory.kt`

- [ ] **Step 1: Update `SpeedNotificationFactory.kt`**

Replace the entire file with:

```kotlin
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
            DisplayMode.DOWNLOAD -> "↓ ${formatter.formatFull(sample.downloadBps)}"
            DisplayMode.UPLOAD -> "↑ ${formatter.formatFull(sample.uploadBps)}"
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
```

- [ ] **Step 2: Build to verify it compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/seniorjava/speedy/service/SpeedNotificationFactory.kt
git commit -m "feat(service): support DisplayMode in SpeedNotificationFactory"
```

---

### Task 5: Update `SpeedMonitorService` to collect and use `DisplayMode`

**Files:**
- Modify: `app/src/main/kotlin/dev/seniorjava/speedy/service/SpeedMonitorService.kt`

- [ ] **Step 1: Update `SpeedMonitorService.kt`**

Replace the entire file with:

```kotlin
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
    private var currentDisplayMode: DisplayMode = DisplayMode.BOTH

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
        val notification = notificationFactory.build(SpeedSample.ZERO, DisplayMode.BOTH)
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
```

- [ ] **Step 2: Build to verify it compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/seniorjava/speedy/service/SpeedMonitorService.kt
git commit -m "feat(service): collect DisplayMode from settings and apply per tick"
```

---

### Task 6: Update `DashboardViewModel` and write unit tests

**Files:**
- Modify: `app/src/main/kotlin/dev/seniorjava/speedy/ui/dashboard/DashboardViewModel.kt`
- Create: `app/src/test/kotlin/dev/seniorjava/speedy/ui/dashboard/DashboardViewModelTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/dev/seniorjava/speedy/ui/dashboard/DashboardViewModelTest.kt`:

```kotlin
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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "dev.seniorjava.speedy.ui.dashboard.DashboardViewModelTest"
```

Expected: FAIL — `DashboardUiState` has no `displayMode` field, `DashboardViewModel` has no `onDisplayModeChanged` method.

- [ ] **Step 3: Update `DashboardViewModel.kt`**

Replace the entire file with:

```kotlin
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "dev.seniorjava.speedy.ui.dashboard.DashboardViewModelTest"
```

Expected: `BUILD SUCCESSFUL`, all 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/dev/seniorjava/speedy/ui/dashboard/DashboardViewModel.kt \
        app/src/test/kotlin/dev/seniorjava/speedy/ui/dashboard/DashboardViewModelTest.kt
git commit -m "feat(ui): add displayMode to DashboardViewModel state and unit tests"
```

---

### Task 7: Add string resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`

- [ ] **Step 1: Add English strings to `res/values/strings.xml`**

Add inside `<resources>`, after the `dashboard_speed_placeholder` line:

```xml
    <string name="dashboard_display_mode_title">Display mode</string>
    <string name="dashboard_display_mode_both">Both (download + upload)</string>
    <string name="dashboard_display_mode_download">Download only</string>
    <string name="dashboard_display_mode_upload">Upload only</string>
```

- [ ] **Step 2: Add Ukrainian strings to `res/values-uk/strings.xml`**

Open the file and add the same keys with Ukrainian translations:

```xml
    <string name="dashboard_display_mode_title">Режим відображення</string>
    <string name="dashboard_display_mode_both">Обидва (↓ завантаження + ↑ вивантаження)</string>
    <string name="dashboard_display_mode_download">Тільки завантаження (↓)</string>
    <string name="dashboard_display_mode_upload">Тільки вивантаження (↑)</string>
```

- [ ] **Step 3: Build to verify resources compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-uk/strings.xml
git commit -m "feat(res): add display mode string resources (en + uk)"
```

---

### Task 8: Add `DisplayModeCard` to `DashboardScreen` and wire up `MainActivity`

**Files:**
- Modify: `app/src/main/kotlin/dev/seniorjava/speedy/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/kotlin/dev/seniorjava/speedy/ui/MainActivity.kt`

- [ ] **Step 1: Update `DashboardScreen.kt`**

Add the import block additions and the new composable. Specifically:

1. Add these imports:
```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.material3.RadioButton
import dev.seniorjava.speedy.domain.DisplayMode
```

2. Add `onDisplayModeChanged: (DisplayMode) -> Unit` parameter to `DashboardScreen`:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onToggleEnabled: (Boolean) -> Unit,
    onRefreshPermissions: () -> Unit,
    onDisplayModeChanged: (DisplayMode) -> Unit,
) {
```

3. Add `DisplayModeCard` call in the main `Column`, after `LiveSpeedCard`:
```kotlin
DisplayModeCard(
    displayMode = state.displayMode,
    onDisplayModeChanged = onDisplayModeChanged,
)
```

4. Add the new private composable at the end of the file, before `PermissionsSection`:
```kotlin
@Composable
private fun DisplayModeCard(
    displayMode: DisplayMode,
    onDisplayModeChanged: (DisplayMode) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.dashboard_display_mode_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            DisplayMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDisplayModeChanged(mode) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = displayMode == mode,
                        onClick = { onDisplayModeChanged(mode) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(displayModeLabel(mode)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

private fun displayModeLabel(mode: DisplayMode): Int = when (mode) {
    DisplayMode.BOTH -> R.string.dashboard_display_mode_both
    DisplayMode.DOWNLOAD -> R.string.dashboard_display_mode_download
    DisplayMode.UPLOAD -> R.string.dashboard_display_mode_upload
}
```

- [ ] **Step 2: Update `MainActivity.kt`** — add `onDisplayModeChanged` to the `DashboardScreen` call:

```kotlin
DashboardScreen(
    state = uiState,
    onToggleEnabled = viewModel::onToggleEnabled,
    onRefreshPermissions = viewModel::refreshPermissions,
    onDisplayModeChanged = viewModel::onDisplayModeChanged,
)
```

- [ ] **Step 3: Build debug APK to verify everything compiles**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Run all unit tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/dev/seniorjava/speedy/ui/dashboard/DashboardScreen.kt \
        app/src/main/kotlin/dev/seniorjava/speedy/ui/MainActivity.kt
git commit -m "feat(ui): add DisplayModeCard to DashboardScreen"
```
