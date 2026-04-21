# Network Speed Monitor — Technical Specification

This document describes the technical details and architecture of the Android
app that displays internet connection speed in the status bar.

> The Ukrainian original is preserved at [`SPEC-uk.md`](SPEC-uk.md).

## 1. Overview and Technical Stack
- **Language:** Kotlin.
- **UI framework:** Jetpack Compose (Material 3).
- **Concurrency:** Kotlin Coroutines & `StateFlow`.
- **Dependency Injection:** Dagger Hilt.
- **Settings storage:** Jetpack DataStore (Preferences) — used to persist
  configuration (e.g. the `isEnabled: Boolean` flag, so the app knows whether
  to restart after device reboot).
- **SDK:** `minSdk = 26` (Android 8.0), `targetSdk = 35`, `compileSdk = 35`.
- **Localization:** `uk` (default), `en` (fallback).
- **Analytics & crash reporting:** deliberately **none** — no third-party
  trackers, for maximum energy efficiency and privacy.

## 2. Functional requirements and algorithms
- **Status bar display:** persistent notification (Foreground Service). The
  icon is generated dynamically as a `Bitmap` with upload and download speed
  text.
- **Refresh interval:** target is 1000 ms (one tick per second), implemented
  via `delay(1000)` inside a Coroutine that watches state.
- **Speed calculation (drift-corrected formula):**
  - The delta uses the real elapsed time between ticks to avoid timer error
    (e.g. 1050 ms instead of 1000 ms):
  ```kotlin
  bitsPerSecond = (currentBytes - previousBytes) * 8 * 1000 / elapsedMs
  ```
- **Scaling (formatter) and thresholds:**
  Round to **integers**, except in the Gbps range where **one decimal place**
  is allowed for precision and readability (e.g. `1.2G`). Ranges:
  - `< 1 000 bps` → show in **bps**
  - `1 000 – 999 999 bps` → show in **Kbps**
  - `1 000 000 – 999 999 999 bps` → show in **Mbps**
  - `>= 1 000 000 000 bps` → show in **Gbps** (or `G`)
- **Expanded notification (drawer):**
  - Title: `Network speed`.
  - Body: `↓ 14 Mbps | ↑ 2 Mbps` (updated dynamically).
  - **Tap action:** opens the Dashboard via `PendingIntent.getActivity`.
    The `PendingIntent.FLAG_IMMUTABLE` flag is mandatory (Android 12+
    requirement).

## 3. UI/UX and accessibility
- **Status-bar icon layout:**
  Text is stacked vertically — upload on top, download at the bottom:
  `↑ 2M`
  `↓ 14M`
  *Shortening rule:* values above 999 are truncated to at most 4 characters
  (including the decimal point in the Gbps range).
- **Home screen (Dashboard):**
  - **Switch** to enable/disable monitoring.
  - **Service status:** textual indicator — "Active / Waiting for network /
    Stopped".
  - **Live speed:** shows the same speed stream the service uses
    (communicated via a shared Hilt-`@Singleton` `StateFlow`). When the
    Switch is off (service stopped), render dashes (`— / —`).
  - **Permissions:** a permission management block covering
    `POST_NOTIFICATIONS` and Battery Optimization.
- **Accessibility:** all interactive elements (Switch, buttons) must have
  proper `contentDescription` for TalkBack support.
- **Dark/Light theme:** dynamic system theme support (Compose Material 3
  Theme).

## 4. Testing strategy
- **Unit tests:** required coverage for the calculator and the formatter
  (`SpeedFormatter`) — verify zero-delta handling, overflow (negative
  values), and correct rounding of thresholds to whole integers / hundreds
  of Mbps.
- **Instrumented / UI tests:** exercise the Compose Dashboard and verify
  `StateFlow` state rendering.

## 5. Non-functional requirements and memory management
- **Traffic reading:** use `TrafficStats.getTotalRxBytes()` /
  `getTotalTxBytes()`. The data includes all system traffic, which
  guarantees high performance.
- **Bitmap management and monochrome rendering (CRITICAL):**
  - The `smallIcon` in the status bar is monochrome. A `Bitmap` of config
    `Bitmap.Config.ALPHA_8` must be used, sized `48x48 px`. This cuts memory
    use by 4×.
  - `Paint.color` is set to `Color.WHITE` (the system uses only the alpha
    channel for drawing).
  - Only **one** `Bitmap` + `Canvas` instance is created at startup. On
    every tick the object is reused: the `Canvas` is cleared with
    `drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)`, the updated text
    is drawn, and the bitmap is handed to `Icon.createWithBitmap()`.

## 6. Edge cases
- **No network (airplane mode):** monitoring is paused, the icon is
  completely hidden, and `ConnectivityManager.NetworkCallback` waits for a
  network to resume.
- **`POST_NOTIFICATIONS` denied (Android 13+):** the Foreground Service is
  **not** allowed to start. The Dashboard shows a red alert with a button
  to open settings. When the permission is granted via the
  `ActivityResultLauncher.registerForActivityResult(RequestPermission())`
  callback, the service starts.
- **Notification constants:**
  - `const val CHANNEL_ID = "speed_monitor_channel"`
  - `const val NOTIFICATION_ID = 1`
  If the user disables the channel's notifications in OS settings, the
  Dashboard prompts them to re-enable the switch.
- **Doze / battery optimization:** an `Intent.ACTION_SCREEN_OFF` broadcast
  stops the tick timer; `ACTION_SCREEN_ON` resumes the service and
  immediately redraws the bitmap.
- **Calculation anomalies:**
  - *First tick:* delta = 0, the current bytes are stored as the baseline,
    output is `0 bps`.
  - *Overflow / negative delta:* on counter reset or OS counter overflow
    (`currentBytes < previousBytes`), this is treated as a "first tick" —
    delta = 0.
- **Auto-start after reboot:**
  A `BroadcastReceiver` listens strictly for
  `android.intent.action.BOOT_COMPLETED` (without `directBootAware`). The
  service starts only after the device has been unlocked for the first
  time. This is critical, because the DataStore / SharedPreferences
  settings file lives in Credential Protected Storage and is unavailable
  until first unlock. Reading the flag earlier would throw IOException
  (crash).

## 7. Android 14/15 FGS and required permissions
For stable operation, the following `<uses-permission>` entries must be
declared:
- `FOREGROUND_SERVICE`: base requirement for FGS across old and new APIs.
- `FOREGROUND_SERVICE_SPECIAL_USE`: service type required on Android 14+.
  The Service tag **must** include the following property:
  `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
  android:value="Continuous network speed status bar overlay" />`.
  The type is also passed during startup:
  ```kotlin
  startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
  ```
- `ACCESS_NETWORK_STATE`: needed to observe connectivity via
  `NetworkCallback`.
- `POST_NOTIFICATIONS`: required to show the `smallIcon` in the status bar.
- `RECEIVE_BOOT_COMPLETED`: read by the `BroadcastReceiver`.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: requested from the Dashboard
  settings.

**Notification channel setup:**
- **Priority / importance:** `NotificationManager.IMPORTANCE_LOW`
  (no sound, no vibration, but the icon is still visible in the status bar).
- **Visibility:** `NotificationCompat.VISIBILITY_PUBLIC` — speed is shown
  directly on the lock screen.
