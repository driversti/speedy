# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Speedy is an Android app (single module `:app`, package `dev.seniorjava.speedy`) that displays live up/down network speed in the status bar via a foreground service. Kotlin + Jetpack Compose + Hilt. `minSdk 26`, `targetSdk 35`, `compileSdk 36`.

The canonical product spec is in `SPEC.md` (English) and `SPEC-uk.md` (Ukrainian original) — consult it when behavior is ambiguous. Section numbers in `SPEC.md` are the authoritative reference.

## Build & test

Gradle is **pinned to JDK 21** via `gradle.properties` (`org.gradle.java.home=…/21.0.10-tem`). AGP 8.8 does not support JDK 25 — if the path doesn't exist on your machine, adjust or remove that line before building.

```sh
./gradlew :app:assembleDebug           # build debug APK
./gradlew :app:testDebugUnitTest       # run JVM unit tests (domain layer)
./gradlew :app:connectedDebugAndroidTest   # instrumented tests (needs device/emulator)
./gradlew :app:lintDebug               # Android lint
./gradlew :app:assembleRelease         # release build (minify + shrink on)
```

Run a single unit test:

```sh
./gradlew :app:testDebugUnitTest --tests "dev.seniorjava.speedy.domain.SpeedFormatterTest"
./gradlew :app:testDebugUnitTest --tests "dev.seniorjava.speedy.domain.SpeedCalculatorTest.first tick returns zero"
```

Debug builds append `.debug` to the applicationId and `-debug` to the version name, so a debug and release build can coexist on the same device.

## Architecture — the one thing that matters

Four packages inside `app/src/main/kotlin/dev/seniorjava/speedy/`:

- `domain/` — pure Kotlin, no Android deps. `SpeedCalculator` (drift-corrected delta), `SpeedFormatter` (bps→Kbps/Mbps/Gbps thresholds), `SpeedSample`, `ServiceState`.
- `data/` — `SettingsRepository` (DataStore preferences, the `isEnabled` flag survives reboot), `SpeedStateHolder`.
- `service/` — `SpeedMonitorService` (FGS, `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`), `SpeedIconRenderer`, `SpeedNotificationFactory`, `SpeedNotifications` (constants), `SpeedServiceController`, `BootReceiver`.
- `ui/` — `MainActivity`, `dashboard/DashboardScreen` + `DashboardViewModel`, `PermissionChecker`, `theme/`.

**The critical load-bearing abstraction is `SpeedStateHolder` (`data/SpeedStateHolder.kt`).** It's a Hilt `@Singleton` that holds two `StateFlow`s (`speed` and `serviceState`). The service is the only writer; the `DashboardViewModel` is the reader. Hilt's singleton scope guarantees both sides see the same instance — this is what lets the dashboard mirror exactly what the service is publishing to the notification. Do not introduce a second state source; extend `SpeedStateHolder` instead.

Service lifecycle logic lives in `SpeedMonitorService.kt` and has to be reasoned about together:
- The tick loop only runs when both `screenOn` AND `networkAvailable` are true. `maybeStartTicking()` is the single gate.
- `ACTION_SCREEN_OFF` → stop tick (battery). `ACTION_SCREEN_ON` → resume.
- `NetworkCallback.onLost` with no remaining transport → cancel the notification (icon disappears), set state to `WAITING_FOR_NETWORK`. `onAvailable` → resume.
- `SpeedCalculator.reset()` is called whenever ticking stops so the next tick after resume is treated as a fresh baseline (otherwise the delta across the gap would produce a garbage reading).

`SpeedCalculator` is stateful. First tick, `currentBytes < previousBytes` (counter reset/overflow), and `elapsedMs <= 0` all return `SpeedSample.ZERO` — see tests in `app/src/test/kotlin/.../domain/SpeedCalculatorTest.kt`.

`SpeedIconRenderer` reuses **one** `Bitmap` + `Canvas` across ticks (cleared with `PorterDuff.Mode.CLEAR` each time). Don't allocate per-tick. The bitmap is `ALPHA_8` at **96×96** — the spec says 48×48, but Pixel's system resizer drops the smaller asset; see the note in `SpeedIconRenderer.kt`.

## Platform rules you'll trip on

- **Android 14+ foreground service type:** `SpeedMonitorService` declares `android:foregroundServiceType="specialUse"` and the manifest `<property>` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`. `startForeground` passes `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on `UPSIDE_DOWN_CAKE+`. Don't change the service type without updating both the manifest and the `startForeground` call.
- **Boot receiver is NOT `directBootAware`.** DataStore preferences live in credential-protected storage; reading `isEnabled` before first unlock throws `IOException`. Keep `android:directBootAware="false"` on `BootReceiver`.
- **`POST_NOTIFICATIONS`** (Android 13+) is required for the status-bar icon to render at all. `PermissionChecker` / the dashboard handle the prompt. If the user denies it, the service simply cannot start.
- **`PendingIntent.FLAG_IMMUTABLE`** is mandatory when the notification's content intent opens `MainActivity` (Android 12+).

## Dependency injection

All DI is Hilt + KSP. `AppModule` (`di/AppModule.kt`) provides the singleton `DataStore<Preferences>` (file name `speedy_settings`) and an `@AppScope`-qualified application-wide `CoroutineScope`. `SpeedyApp` is `@HiltAndroidApp`. Service and `BootReceiver` are `@AndroidEntryPoint`.

## Testing conventions

Unit tests use **JUnit 4 + Google Truth + MockK + Turbine + kotlinx-coroutines-test** (see `app/build.gradle.kts`). Tests live under `app/src/test/kotlin/dev/seniorjava/speedy/...` and currently cover the two pure-domain classes. New logic should go into `domain/` when possible to keep it testable on the JVM.

Instrumented tests (`app/src/androidTest/...`) use Espresso + Compose test + Turbine. Use them for Compose UI and things that require a real Android framework (the service lifecycle, DataStore behavior on real storage).

## Conventions worth knowing

- **Kotlin official style** (`kotlin.code.style=official` in `gradle.properties`).
- **Version catalog:** add/bump dependencies in `gradle/libs.versions.toml`, not inline in `build.gradle.kts`.
- **Coroutines scopes:** the service uses its own `CoroutineScope(Dispatchers.Default + SupervisorJob())` tied to its lifecycle. The app-wide `@AppScope` in `AppModule` is for work that must outlive a single component (e.g. `BootReceiver.goAsync()` continuation).
- **Localization:** `res/values/strings.xml` is the default (**English**); `res/values-uk/strings.xml` is the Ukrainian override. Note: `SPEC.md` §1 claims `uk` is default and `en` fallback — the code is the other way around. Trust the resource layout.
- **No third-party analytics / crash reporting.** This is a deliberate product constraint, not an oversight — do not add Firebase, Sentry, etc.
