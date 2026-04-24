# Display Mode Setting — Design

**Date:** 2026-04-24  
**Status:** Approved

## Summary

Add a user-facing setting that controls which speed direction is shown in the status-bar icon and notification: download only, upload only, or both (default).

## Decisions

- **Icon layout (single mode):** One value centered vertically in the 96×96 bitmap.
- **UI location:** New "Display mode" card on the dashboard, placed after the "Live speed" card.
- **UI control:** Three radio buttons (Material3 `RadioButton + Text` rows).
- **Default value:** `BOTH` — matches current behavior on first install.
- **Architecture approach:** Pass `DisplayMode` as an explicit parameter through the call chain; the service collects the setting flow and keeps a local variable.

## Domain Layer

### `domain/DisplayMode.kt` (new file)

```kotlin
enum class DisplayMode { BOTH, DOWNLOAD, UPLOAD }
```

Pure Kotlin, no Android dependencies.

## Settings Layer

### `data/SettingsRepository.kt` (modified)

New additions:
- `stringPreferencesKey("display_mode")`
- `displayMode: Flow<DisplayMode>` — reads the stored string, maps to enum; unknown value → `BOTH`
- `suspend fun setDisplayMode(mode: DisplayMode)` — persists `mode.name`

## Service Layer

### `service/SpeedIconRenderer.kt` (modified)

Signature change: `render(sample: SpeedSample, mode: DisplayMode): Bitmap`

| Mode       | Rendered lines                                      |
|------------|-----------------------------------------------------|
| `BOTH`     | ↑upload at `UP_BASELINE_PX = 44f`, ↓download at `DOWN_BASELINE_PX = 94f` (unchanged) |
| `DOWNLOAD` | ↓download centered at `CENTER_BASELINE_PX ≈ 60f`  |
| `UPLOAD`   | ↑upload centered at `CENTER_BASELINE_PX ≈ 60f`    |

`CENTER_BASELINE_PX` to be verified visually during implementation.

### `service/SpeedNotificationFactory.kt` (modified)

Signature change: `build(sample: SpeedSample, mode: DisplayMode): Notification`

| Mode       | Notification body text    |
|------------|---------------------------|
| `BOTH`     | `↓ 48 Mbps \| ↑ 12 Mbps` |
| `DOWNLOAD` | `↓ 48 Mbps`               |
| `UPLOAD`   | `↑ 12 Mbps`               |

### `service/SpeedMonitorService.kt` (modified)

- In `onCreate`, launch a coroutine that collects `settingsRepository.displayMode` → stores in `var currentDisplayMode: DisplayMode = DisplayMode.BOTH`
- `tickLoop()` passes `currentDisplayMode` to `notificationFactory.build()` and indirectly to `iconRenderer.render()`
- Setting changes are picked up on the next tick (≤1 second delay)

## UI Layer

### `ui/dashboard/DashboardViewModel.kt` (modified)

- `combine()` extended to include `settingsRepository.displayMode` as a fifth flow
- `DashboardUiState` gains field `displayMode: DisplayMode`
- New method: `fun onDisplayModeChanged(mode: DisplayMode)` → calls `settingsRepository.setDisplayMode(mode)`

### `ui/dashboard/DashboardScreen.kt` (modified)

- `DashboardScreen` gains parameter `onDisplayModeChanged: (DisplayMode) -> Unit`
- New private composable `DisplayModeCard`:
  - Title: "Display mode"
  - Three `Row(RadioButton + Text)` items:
    - `BOTH` → "Both (download + upload)"
    - `DOWNLOAD` → "Download only"
    - `UPLOAD` → "Upload only"
  - Placed after `LiveSpeedCard` in the main `Column`

### `res/values/strings.xml` (modified)

```xml
<string name="dashboard_display_mode_title">Display mode</string>
<string name="dashboard_display_mode_both">Both (download + upload)</string>
<string name="dashboard_display_mode_download">Download only</string>
<string name="dashboard_display_mode_upload">Upload only</string>
```

## Files to Create / Modify

| File | Action |
|------|--------|
| `domain/DisplayMode.kt` | Create |
| `data/SettingsRepository.kt` | Modify |
| `service/SpeedIconRenderer.kt` | Modify |
| `service/SpeedNotificationFactory.kt` | Modify |
| `service/SpeedMonitorService.kt` | Modify |
| `ui/dashboard/DashboardViewModel.kt` | Modify |
| `ui/dashboard/DashboardScreen.kt` | Modify |
| `res/values/strings.xml` | Modify |
| `res/values-uk/strings.xml` | Modify (Ukrainian translations) |
