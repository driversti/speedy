# Launcher icon redesign — Speedy

**Date:** 2026-04-21
**Status:** Approved — ready for implementation plan

## Goal

Replace the placeholder launcher icon (two plain Material-coloured arrows on a light background) with a distinctive, atmospheric "speedometer" icon that matches the app's identity as a live network speed monitor.

This affects the app launcher icon only. The dynamically rendered status-bar icon (`SpeedIconRenderer`) is out of scope — it has its own constraints (48×48/96×96 ALPHA_8 bitmap, redrawn every tick) and must remain a text-based up/down readout.

## Concept

**Speedometer with a thermal arc** (green → amber → red) on a dark, radially-lit background. White needle resting at ~75% of the scale (in the amber-to-red transition), white hub with a dark centre.

Chosen over alternatives — lightning bolt, dual waveform, motion chevrons — because the speedometer is the most immediately readable metaphor for "speed", and the thermal arc adds a second layer of meaning (colour = speed zone) without sacrificing clarity at small sizes.

The launcher icon intentionally does not differentiate upload vs. download. That split is already carried by the status-bar icon (↑/↓ arrows with per-direction values). The launcher icon's job is to be recognisable as "Speedy" at a glance.

## Visual specification

### Geometry (108 × 108 dp viewport)

All foreground geometry fits inside the 66dp safe zone — a circle of radius 33dp centred on `(54, 54)` — so nothing is clipped by the device's adaptive-icon mask (circle / squircle / rounded square).

- **Arc centre:** `(54, 54)` — geometric centre of the viewport. Android's circular launcher mask typically shows y≈18..90 (a 72dp diameter visible area), so the hub lands exactly at the visible centre of the icon on circular masks (and still reads as centred on squircle / rounded-square masks).
- **Arc radius:** 26 (endpoints at `(28, 54)` and `(80, 54)`; apex at `(54, 28)`). Endpoint distance from the icon centre is exactly 26, safely inside the 33dp keyline.
- **Needle:** from the hub `(54, 54)` to `(72, 36)` — 135° of arc sweep from the start (visually landing in the amber-to-red transition, about 75% of the way along the dial).
- **Hub:** white circle `r = 5.5` at `(54, 54)`, with an inner dark dot `r = 2` (colour `#1A202C`, matching the mid-stop of the background gradient).
- **Halo** (glow substitute): a wider copy of the arc, `stroke-width = 10`, `opacity = 0.25`, rendered **under** the main arc. Vector Drawable does not support `feGaussianBlur`, so the halo is an explicit geometry layer with the same radius, centre, and gradient as the main arc. The 3dp overhang on each side of the main arc reads as a glow without the round-cap tips spilling past typical circular-mask launchers (cap tip lands ~35dp from icon centre — inside the 36dp "effects zone" but outside the stricter 33dp keyline where important content lives).
- **Main arc:** `stroke-width = 7`, `stroke-linecap = round`.
- **Needle:** `stroke-width = 3.5`, `stroke-linecap = round`.

### Colours

**Background — radial gradient** (centre biased toward bottom-centre, "spotlight" effect):

- Gradient type: radial
- Centre: `(54, 80)`, radius `90`
- Stops:
  - `0%` → `#2D3748`
  - `60%` → `#1A202C`
  - `100%` → `#0B0E14`

**Thermal arc — horizontal linear gradient** (`startX = 28`, `startY = 28`, `endX = 80`, `endY = 28` — a straight left-to-right sweep; Y is irrelevant for a purely horizontal gradient but set to the arc apex for readability):

- `0%` → `#4CAF50` (green)
- `50%` → `#FFC107` (amber)
- `100%` → `#F44336` (red)

**Halo:** same linear gradient as the arc, rendered at 25% opacity.

**Needle and hub ring:** pure white `#FFFFFF`.

**Hub inner dot:** `#1A202C` (ties the needle back to the background's mid-stop).

### Monochrome / themed variant

Android 13+ themed icons tint a single-channel drawable. The monochrome layer keeps the arc + needle + hub geometry only — **no halo** (it reads as smudge/banding once the OS fills it with a flat tint).

- Arc, needle, hub ring, inner dot: all `#FFFFFF` (the system re-tints to the user's theme).
- Same geometry and stroke widths as the foreground layer, so the themed version reads identically.

## Files affected

- `app/src/main/res/drawable/ic_launcher_background.xml` — rewrite (current file is a placeholder).
- `app/src/main/res/drawable/ic_launcher_foreground.xml` — rewrite (current file is two flat arrows).
- `app/src/main/res/drawable/ic_launcher_monochrome.xml` — **new file**.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — update `<monochrome>` reference to the new drawable.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` — same update.

No PNG mipmaps (`mipmap-hdpi/`, `mipmap-xxhdpi/`, etc.) are added. `minSdk = 26` means every target device supports `mipmap-anydpi-v26/` adaptive icons; legacy bitmap fallbacks are unnecessary.

## Constraints / non-goals

- **No runtime theming beyond the themed-icon spec.** We are not adding a Material You dynamic-colour launcher icon variant — the static palette is the brand palette.
- **No changes to the status-bar icon.** `SpeedIconRenderer` stays as-is.
- **No app-name or typography inside the icon.** The "S" monogram variant was considered and rejected in favour of legibility at small sizes (36×36 dp on the launcher home screen).
- **No filter effects** (`feGaussianBlur`, `feDropShadow`, etc.) — Android Vector Drawable does not support SVG filter primitives. All glow / halo is geometry.

## Verification

Visual check after implementation:

1. `./gradlew :app:assembleDebug` builds without Vector Drawable parse errors.
2. Install the APK; verify the icon renders correctly in:
   - Pixel launcher (circular mask)
   - A launcher that uses a squircle mask (e.g. Nova / stock OnePlus)
   - A launcher that uses a rounded square mask (stock Samsung)
3. Enable **Themed icons** in Android 13+ settings; verify the monochrome layer renders cleanly (no speckle, arc + needle readable).
4. Check the icon at the three render sizes the system requests: launcher (48dp), recents (24dp), notification long-press (32dp). All three must stay legible.

## Open questions

None. Proceeding to the implementation plan.
