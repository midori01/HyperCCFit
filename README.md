# HyperCCFit

[![Release](https://img.shields.io/badge/version-v1.0.0-blue.svg)](https://github.com/midori01/HyperCCFit/releases)
[![Target OS](https://img.shields.io/badge/HyperOS-1.0%20--%203.0-orange.svg)](#)
[![Android](https://img.shields.io/badge/Android-9%20--%2017-green.svg)](#)
[![Framework](https://img.shields.io/badge/Framework-LSPosed-purple.svg)](https://github.com/mywalkb/LSPosed_mod)
[![Mathematical Verification](https://img.shields.io/badge/Tests-934%20Passed%20(100%25)-brightgreen.svg)](https://github.com/midori01/HyperCCFit/blob/main/tests/)
[![License](https://img.shields.io/badge/License-Apache--2.0-yellow.svg)](https://github.com/midori01/HyperCCFit/blob/main/LICENSE)

**HyperCCFit** is a high-performance LSPosed module designed for **Xiaomi HyperOS** (MIUISystemUI / MIUISystemUIPlugin). It adapts and optimizes the HyperOS Control Center layout for minimal display scaling and high-DPI configurations, expanding panel width, aligning the media player, introducing a triple slender slider row with a dedicated ringer volume slider, and expanding Quick Settings tiles to 5 columns with single-line loop marquee titles.

---

## 1. Overview & Motivation

On modern Xiaomi devices running HyperOS, enthusiasts and power users often adjust display scaling or Android minimal width (e.g., setting screen density to 468 dp or higher for maximum information density). 

However, the native HyperOS Control Center hardcodes fixed dimension thresholds:
- **Excessive Margins**: The control center panel remains rigidly clamped, leaving massive empty margins on both sides of the screen.
- **Misaligned Media Player**: The media playback card occupies an awkward fixed fraction, leaving an asymmetrical gap beneath the Wi-Fi tile.
- **Limited Volume Controls**: The second row only provides Brightness and Media Volume, omitting a quick slider for Ringtone and Notification volume.
- **4-Column Tile Clutter**: Quick Settings tiles remain confined to 4 columns, where labels often wrap awkwardly or truncate into ellipses.

**HyperCCFit solves all of these issues** through non-invasive runtime bytecode interception and dimension synthesis.

---

## 2. Key Features

### 1) Unified 30-Span Grid Architecture
Replaces the native 4 / 20 / 3 span layout with a unified **30-span layout grid**:
- **Row 1 (30 spans)**: Wi-Fi card (15 spans, 50%) + Cellular Data card (15 spans, 50%).
- **Row 2 (30 spans)**: Expanded Media Player (15 spans, 50%) + Triple Slender Sliders (15 spans, 50%).
- **Row 3 (30 spans)**: Device Center / Smart Hub (30 spans, 100%).
- **Row 4+ (30 spans)**: Quick Setting Tiles (6 spans each &rarr; **5 columns per row**).

### 2) Expanded Media Player with Pixel-Perfect Alignment
- Expands the media player panel to `MATCH_PARENT` within its 15-span boundary.
- The right edge of the media player card aligns with the exact boundary of the Wi-Fi card above it, creating visual harmony.

### 3) Triple Slender Sliders with Dedicated Ringer Volume Slider
- Reduces individual slider width while maintaining comfortable touch targets.
- Dynamically injects an additional **Ringer & Notification Volume Slider** into the control center panel via runtime reflection proxying of `MainPanelContent`:
  - **Live Synchronization**: Actively syncs with `AudioManager.STREAM_RING` and listens for system volume broadcasts (`VOLUME_CHANGED_ACTION`, `RINGER_MODE_CHANGED_ACTION`).
  - **Quick Mute / Restore**: Tap the bell icon to toggle instant mute and restore previous volume levels.
  - **Native Visuals**: Automatically resolves native HyperOS bell icons (`ic_miui_volume_ringer`, `ic_miui_volume_ringer_mute`) and applies official blur blend tokens (`MiuiColorBlendToken`).

### 4) 5-Column Quick Settings Tiles with Marquee Titles
- Increases circular Quick Setting tiles from 4 to **5 tiles per row** (6 spans each).
- Configures tile labels as **single-line looping marquees** (`TextUtils.TruncateAt.MARQUEE`):
  - Eliminates awkward two-line text wrapping.
  - Focus and selection hooks ensure marquee scrolling never pauses or gets stuck.
  - Preserves native vertical spacing between icons and titles with zero overlap.

### 5) Dynamic Resolution & DPI Adaptation (`CCFitCalculator`)
- Dynamically intercepts `Resources.getDimensionPixelSize` and `getDimension`:
  - `control_center_panel_width`
  - `control_center_universal_4_rows_with_margin_size`
  - `control_center_universal_4_rows_size`
  - `control_center_universal_3_rows_with_margin_size`
  - `control_center_universal_2_rows_size`
  - `control_center_universal_1_row_size`
  - `control_center_secondary_panel_content_width`
- Adjusts panel width to screen width minus 16 dp (8 dp margin on each side).
- Fine-tunes Row 2 height by factor `0.78` for ideal proportion on high-density displays.
- Automatically bypasses modifications on narrow screens (&le; 380 dp) or in landscape orientation.

### 6) Companion App & System Diagnostics
- Embedded lightweight companion app to verify active module state.
- Real-time diagnostic readouts:
  - Screen Width (dp and px)
  - Screen Density (dpi and scale factor)
  - Calculated Panel Width and Screen Coverage Percentage
- One-tap SystemUI restart action via root shell for instant hot-reload.

---

## 3. Layout Grid Specification Matrix

| Row | Component | Span Count | Ratio | Alignment Target |
| :--- | :--- | :--- | :--- | :--- |
| **Row 1** | Wi-Fi Big Tile | **15 spans** | 50.0% | Left half |
| | Cellular Data Big Tile | **15 spans** | 50.0% | Right half |
| **Row 2** | Expanded Media Player | **15 spans** | 50.0% | Aligned under Wi-Fi |
| | Brightness Slider | **5 spans** | 16.67% | Slider 1 |
| | Media Volume Slider | **5 spans** | 16.67% | Slider 2 |
| | **Ringer Volume Slider** | **5 spans** | 16.67% | Slider 3 |
| **Row 3** | Smart Hub / Device Center | **30 spans** | 100.0% | Full width |
| **Row 4+** | Quick Setting Tiles (x5) | **6 spans &times; 5** | 100.0% | **5 Columns** |

---

## 4. Mathematical Verification

HyperCCFit includes an automated verification engine (`tests/test_calculator.py`) verifying dimension formulas and span invariants:

```sh
sh run_tests.sh
```

- **Scope**: 934 continuous density simulations from 381.0 dp to 800.0 dp (step 0.5 dp).
- **Checks**:
  - Small-screen bypass (&le; 380 dp)
  - Landscape orientation bypass
  - Total screen margin invariants (16 dp total, 8 dp each side)
  - Row 1, Row 2, Row 3, and Tile row span balance (30 spans each)
- **Result**: **100.00% PASS (0 Mismatches)**.

---

## 5. Installation & Usage

1. Download the latest `HyperCCFit.apk` from [GitHub Releases](https://github.com/midori01/HyperCCFit/releases).
2. Install the APK on your device.
3. Open **LSPosed Manager** and enable the **HyperCCFit** module.
4. Target scope is automatically declared:
   - `com.android.systemui` (System UI)
5. Restart SystemUI (or reboot the device).
6. Open the **HyperCCFit** companion app from your launcher to view your display metrics and confirm activation.

---

## 6. Building from Source

### Standalone On-Device Build (Termux / Linux)
You can build and sign the complete APK directly on Android using Termux without Gradle:

```sh
chmod +x build.sh
./build.sh
```

The script compiles resources with `aapt2`, compiles Java sources with `javac`, converts bytecode with `d8`, injects Xposed descriptors, 4-byte zip-aligns, and signs with `apksigner`.

### Standard Gradle Build (Android Studio / Desktop / CI)
Standard Android Studio and Gradle builds are fully supported:

```sh
./gradlew assembleRelease
```

---

## 7. License

This project is licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for details.
