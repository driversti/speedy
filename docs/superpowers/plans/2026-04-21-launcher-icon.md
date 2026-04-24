# Launcher Icon Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder launcher icon with a speedometer + thermal-arc design (green → amber → red) on a dark radial-spotlight background, with a themed-icon (monochrome) variant for Android 13+.

**Architecture:** Android Adaptive Icon. Three Vector Drawable layers (`background`, `foreground`, `monochrome`) composed through `mipmap-anydpi-v26/ic_launcher{_round}.xml`. No bitmap fallbacks (`minSdk = 26` means every device supports `anydpi-v26`). All glow/halo is explicit geometry — Vector Drawable has no filter-effect support.

**Tech Stack:** Android Vector Drawable XML (+ `aapt:attr` for inline gradients, supported since API 24 / minSdk 26 here).

**Design spec:** `docs/superpowers/specs/2026-04-21-launcher-icon-design.md`.

**Commits:** This repo's conventions (from user's global CLAUDE.md) require asking before committing. Each task ends with a commit step — pause and confirm with the user before running the `git commit` command.

---

## File Structure

Resources only — no Kotlin changes. One module (`:app`), standard Android resource layout.

- `app/src/main/res/drawable/ic_launcher_background.xml` — **rewrite**. Radial gradient background layer.
- `app/src/main/res/drawable/ic_launcher_foreground.xml` — **rewrite**. Speedometer: halo + arc + needle + hub.
- `app/src/main/res/drawable/ic_launcher_monochrome.xml` — **new**. Themed-icon variant (Android 13+).
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — **modify**. Point `<monochrome>` at the new drawable.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` — **modify**. Same.

The existing adaptive-icon structure is correct; only the drawables it references need work.

---

### Task 1: Rewrite the background layer

A radial gradient that reads as a dark stage with a soft spotlight near the bottom-centre (under where the arc will sit).

**Files:**
- Modify: `app/src/main/res/drawable/ic_launcher_background.xml`

- [ ] **Step 1: Replace the file contents**

Overwrite `app/src/main/res/drawable/ic_launcher_background.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <path android:pathData="M0,0 L108,0 L108,108 L0,108 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="54"
                android:centerY="80"
                android:gradientRadius="90">
                <item android:offset="0.0" android:color="#FF2D3748" />
                <item android:offset="0.6" android:color="#FF1A202C" />
                <item android:offset="1.0" android:color="#FF0B0E14" />
            </gradient>
        </aapt:attr>
    </path>

</vector>
```

- [ ] **Step 2: Verify the XML parses**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. Any parse error in the vector drawable would fail the `processDebugResources` task.

- [ ] **Step 3: Commit (ask user first)**

```bash
git add app/src/main/res/drawable/ic_launcher_background.xml
git commit -m "feat(icon): radial-gradient background for adaptive launcher icon"
```

---

### Task 2: Rewrite the foreground layer (halo + arc + needle + hub)

**Files:**
- Modify: `app/src/main/res/drawable/ic_launcher_foreground.xml`

- [ ] **Step 1: Replace the file contents**

Overwrite `app/src/main/res/drawable/ic_launcher_foreground.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!--
        Speedometer geometry:
          Arc centre  (54, 70)
          Arc radius  26  → endpoints (28, 70) and (80, 70), apex (54, 44)
          Needle      hub (54, 70) → tip (72, 52)
          Hub         r = 5.5 outer, r = 2 inner
        All inside the 66dp (radius 33) safe zone of the 108dp adaptive-icon viewport.
    -->

    <!-- Halo: wider copy of the arc under the main arc (Vector Drawable has no blur filter) -->
    <path
        android:pathData="M 28,70 A 26,26 0 0,1 80,70"
        android:strokeWidth="14"
        android:strokeLineCap="round"
        android:strokeAlpha="0.25"
        android:fillColor="#00000000">
        <aapt:attr name="android:strokeColor">
            <gradient
                android:type="linear"
                android:startX="28" android:startY="44"
                android:endX="80" android:endY="44">
                <item android:offset="0.0" android:color="#FF4CAF50" />
                <item android:offset="0.5" android:color="#FFFFC107" />
                <item android:offset="1.0" android:color="#FFF44336" />
            </gradient>
        </aapt:attr>
    </path>

    <!-- Main thermal arc (green → amber → red) -->
    <path
        android:pathData="M 28,70 A 26,26 0 0,1 80,70"
        android:strokeWidth="7"
        android:strokeLineCap="round"
        android:fillColor="#00000000">
        <aapt:attr name="android:strokeColor">
            <gradient
                android:type="linear"
                android:startX="28" android:startY="44"
                android:endX="80" android:endY="44">
                <item android:offset="0.0" android:color="#FF4CAF50" />
                <item android:offset="0.5" android:color="#FFFFC107" />
                <item android:offset="1.0" android:color="#FFF44336" />
            </gradient>
        </aapt:attr>
    </path>

    <!-- Needle: hub centre → tip in the amber-to-red zone -->
    <path
        android:pathData="M 54,70 L 72,52"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="3.5"
        android:strokeLineCap="round" />

    <!-- Hub outer ring (white circle, r = 5.5). Drawn as two semicircles. -->
    <path
        android:pathData="M 48.5,70 A 5.5,5.5 0 1,1 59.5,70 A 5.5,5.5 0 1,1 48.5,70 Z"
        android:fillColor="#FFFFFFFF" />

    <!-- Hub inner dot (dark, r = 2) — ties back to the background mid-stop. -->
    <path
        android:pathData="M 52,70 A 2,2 0 1,1 56,70 A 2,2 0 1,1 52,70 Z"
        android:fillColor="#FF1A202C" />

</vector>
```

- [ ] **Step 2: Verify the XML parses**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Eyeball the icon in Android Studio preview**

Open `ic_launcher_foreground.xml` in Android Studio → Design tab. Confirm:
- Arc is visible, gradient runs green (left) → amber (top) → red (right).
- Needle points toward the amber-to-red transition.
- Hub has a visible dark dot in the centre.
- A soft halo is visible behind the arc (look for slight spread around the stroke).

No code step — this is a visual smoke test before committing.

- [ ] **Step 4: Commit (ask user first)**

```bash
git add app/src/main/res/drawable/ic_launcher_foreground.xml
git commit -m "feat(icon): speedometer foreground with thermal arc and glow halo"
```

---

### Task 3: Create the monochrome (themed-icon) drawable

Same geometry as the foreground, minus the halo and gradient — a single-colour alpha shape the OS retints with the user's theme. The inner hub dot becomes a transparent cut-out via `android:fillType="evenOdd"` so the themed tint shows through.

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_monochrome.xml`

- [ ] **Step 1: Create the file**

Create `app/src/main/res/drawable/ic_launcher_monochrome.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Themed-icon drawable (Android 13+). The system replaces white pixels with
         the user's theme tint; transparent stays transparent. No halo (reads as
         smudge under a flat tint); hub dot is a transparent cut-out. -->

    <!-- Arc (white, stroked) -->
    <path
        android:pathData="M 28,70 A 26,26 0 0,1 80,70"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="7"
        android:strokeLineCap="round"
        android:fillColor="#00000000" />

    <!-- Needle -->
    <path
        android:pathData="M 54,70 L 72,52"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="3.5"
        android:strokeLineCap="round" />

    <!-- Hub ring with inner cut-out: outer circle + inner circle, filled with evenOdd -->
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="M 48.5,70 A 5.5,5.5 0 1,1 59.5,70 A 5.5,5.5 0 1,1 48.5,70 Z
                          M 52,70   A 2,2     0 1,1 56,70   A 2,2     0 1,1 52,70   Z" />

</vector>
```

- [ ] **Step 2: Verify the XML parses**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit (ask user first)**

```bash
git add app/src/main/res/drawable/ic_launcher_monochrome.xml
git commit -m "feat(icon): monochrome layer for Android 13+ themed launcher icons"
```

---

### Task 4: Wire the monochrome layer into both adaptive-icon definitions

The current `ic_launcher.xml` and `ic_launcher_round.xml` reuse the foreground drawable for monochrome — that's fine as a placeholder, but our new foreground has a coloured gradient that won't render correctly as a themed icon. Point them at the new dedicated monochrome drawable.

**Files:**
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

- [ ] **Step 1: Update `ic_launcher.xml`**

Replace the single `<monochrome>` line in `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

Before:
```xml
<monochrome android:drawable="@drawable/ic_launcher_foreground" />
```

After:
```xml
<monochrome android:drawable="@drawable/ic_launcher_monochrome" />
```

Full file after edit:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
```

- [ ] **Step 2: Update `ic_launcher_round.xml` the same way**

Apply the identical edit to `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`.

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit (ask user first)**

```bash
git add app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml \
        app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
git commit -m "feat(icon): wire dedicated monochrome drawable into adaptive icons"
```

---

### Task 5: Lint and full verification

Resource-only changes don't get caught by unit tests; the verification step is lint + a manual install + eyeball check across the three scenarios the spec calls out.

**Files:** none modified — this is a verification task.

- [ ] **Step 1: Run lint**

Run: `./gradlew :app:lintDebug`
Expected: `BUILD SUCCESSFUL`. No new `VectorPath`, `VectorRaster`, or `IconLauncherShape` warnings attributable to the new drawables.

- [ ] **Step 2: Install the debug APK on a connected device/emulator**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`, app installs as `dev.seniorjava.speedy.debug`.

If no device is connected, ask the user to start an emulator (Pixel 7 API 34 is a good default — has circular mask + themed-icon support) before running this step.

- [ ] **Step 3: Launcher-mask verification**

Open the app drawer / home screen and confirm the icon renders without clipping of the arc endpoints. If multiple launchers are available, ideally check:
- Pixel launcher (circular mask) — default on Pixel devices / API 34 emulator.
- Nova/OnePlus style (squircle mask) — optional.
- Samsung launcher (rounded-square mask) — optional, depends on test device.

Pass criteria: both arc endpoints, the hub, and the needle are fully visible in all available masks. The thermal gradient reads left→right: green → amber → red.

- [ ] **Step 4: Themed-icon verification (Android 13+)**

On the test device/emulator: Settings → Wallpaper & style → enable **Themed icons**. Return to home screen.

Pass criteria: the Speedy icon tints with the user's theme; arc + needle + hub outline + inner cut-out are all readable; no speckle or banding from the (now absent) halo.

- [ ] **Step 5: Small-size legibility**

Open Recent apps / multitasking view (icon renders at ~24dp). Long-press the home-screen icon to trigger the notification shortcut sheet (~32dp). Pass criteria: arc and needle still readable at both sizes.

- [ ] **Step 6: No commit**

This task has no code changes. If any verification step fails, go back to the relevant earlier task (adjust geometry/colours in the drawable, rebuild, recheck) rather than committing fixes on top.

---

## Rollback

If at any point the icon looks wrong and we need to ship with the old placeholder, `git revert` the commits from Tasks 1–4 in reverse order. The old two-arrow icon will come back exactly as it was.
