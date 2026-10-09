# HyperOS Status Bar 12 Icons

[English](README.md) | [简体中文](README_CN.md)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![LSPosed Compatible](https://img.shields.io/badge/LSPosed-API%20101-brightgreen.svg)]()
[![Xiaomi HyperOS](https://img.shields.io/badge/HyperOS-Supported-orange.svg)]()
[![Android 14](https://img.shields.io/badge/Android-14-green.svg)]()

A lightweight, modern LSPosed module designed for **Xiaomi HyperOS** to expand the status bar system icon limit from **6 to 12** without clipping into the center camera cutout.

---

## 🔍 Background & The Problem

On devices running **Xiaomi HyperOS (Android 14+)**, the status bar enforces a rigid icon limit on the right-hand system status area:

* Even when multiple system features are active (such as **VPN, Network Speed, NFC, Bluetooth, Alarm, Hotspot, VoLTE/VoWiFi, Wi-Fi, Cellular, Battery**), HyperOS strictly limits the display to **at most 6 icons**.
* Extra icons are either ignored by hardcoded MIUI blocklists or hidden dynamically during layout calculation.
* Standard status bar tweaks often conflict with other modules or cause icon overlap and clipping across the center hole-punch camera.

---

## 🔬 Technical Root Cause

### 1. Hardcoded Blocklists in Framework
HyperOS SystemUI maintains static blocklists inside `com.android.systemui.statusbar.phone.MiuiIconManagerUtils`:
* `RIGHT_BLOCK_LIST` and `MINI_RIGHT_BLOCK_LIST` explicitly filter out critical slots (such as `network_speed` and `nfc`).
* `MiuiStatusIconContainer.setIgnoredSlots` is repeatedly invoked with ignored slot lists to drop icons before measurement.

### 2. Physical Layout & Usable Width Constraints
In modern punch-hole display phones, `MiuiPhoneStatusBarView` splits the status bar into three sections:
```
[ mStatusBarLeftContainer (1.0f) ] [ mCutoutSpace (Center) ] [ mSystemIconArea (1.0f) ]
```
The usable width of the right status icon area is restricted to:
$$\text{Usable Width} \approx \frac{\text{Screen Width} - \text{Cutout Width}}{2}$$
On a typical 1080p/1440p device, this leaves only approximately **400px** of usable space between the camera cutout and the battery icon.

### 3. Coordinate Overflow & Forced GONE State
At default icon spacing (~14px), 6 icons occupy ~300px. When 7 or more icons attempt to display:
* Required width exceeds the allocated container bounds.
* `xTranslation` calculates to a position smaller than `paddingStart` (off-screen or inside the cutout space).
* `MiuiStatusIconContainer.onLayout` detects this overflow and forcibly assigns:
  ```java
  iconState.visibleState = View.GONE;
  child.setVisibility(View.GONE);
  ```

### 4. Modern LSPosed Runtime Requirements (LibXposed API 101)
Modern Zygisk LSPosed operates on the **LibXposed (API 101)** standard:
* Modules declaring `META-INF/xposed/java_init.list` must extend `io.github.libxposed.api.XposedModule`. Legacy `IXposedHookLoadPackage` classes cause instant silent initialization crashes.
* Modules must compile to a **single `classes.dex`** (avoiding multidex and Kotlin runtime overhead) to prevent ART DexOpt verification failures.
* `META-INF/xposed/scope.list` must include `system` alongside target packages with `staticScope=true` for seamless daemon registration.

---

## 💡 The Solution

This module provides a pure Java, single-dex hook built directly on Modern LibXposed:

```
[SystemUI Launch / Icon Update]
           │
           ▼
[Stage 1: Blocklist Neutralization]
   ├── Clear MiuiIconManagerUtils.RIGHT_BLOCK_LIST & MINI_RIGHT_BLOCK_LIST
   └── Intercept MiuiStatusIconContainer.setIgnoredSlots -> pass empty list
           │
           ▼
[Stage 2: Dynamic Spacing & Dimension Expansion]
   ├── Overrides onMeasure: condense mIconSpacing from 14px to 3px when childCount > 6
   └── Expands widthMeasureSpec bounds to at least 700px AT_MOST
           │
           ▼
[Stage 3: Right-to-Left Layout Recalculation]
   ├── Iterates all visible status icon views
   ├── Calculates positive X-coordinates from containerWidth towards left
   └── Forces visibleState = VISIBLE and alpha = 1.0f for all up to 12 icons
           │
           ▼
[Stage 4: Container Weight Rebalancing]
   └── In updateCutoutLocation: adjusts LinearLayout weights (left=0.8, right=1.2)
```

---

## 🚀 Key Features

* **Expands Limit to 12 Icons**: Allows up to 12 status icons to display simultaneously on the right side.
* **Intelligent Spacing Condensation**: Automatically compresses icon spacing (`mIconSpacing = 3`) when icon count is high, ensuring icons never collide with the center hole-punch camera.
* **Unlocks Blocked Slots**: Unblocks slots such as network speed and NFC that are otherwise suppressed by HyperOS.
* **Ultra-Lightweight Single-DEX**: Zero third-party runtime bloat. Compiled APK is ~17 KB with a single `classes.dex`.
* **Modern LibXposed (API 101) Native**: Fully compliant with modern Zygisk LSPosed architecture.
* **Dual-Mode Fallback**: Includes legacy `assets/xposed_init` fallback for older Xposed frameworks.
* **Multi-Channel Diagnostic Logging**: Writes to Logcat (`tag: StatusBarExpand`), Xposed log, and persistent file at `/sdcard/statusbar_expand.log`.

---

## 📦 Project Structure

```
├── .github/
│   └── workflows/
│       └── build.yml                   # Automated CI build on GitHub Actions
├── app/
│   ├── libs/
│   │   └── libxposed-api-101.0.0.jar   # Modern LibXposed API compileOnly library
│   ├── src/
│   │   └── main/
│   │       ├── assets/
│   │       │   └── xposed_init         # Legacy Xposed fallback descriptor
│   │       ├── java/com/hyperos/statusbar/expand/
│   │       │   ├── Logger.java         # Multi-destination logging utility
│   │       │   ├── StatusBarExpandHook.java    # Legacy hook implementation
│   │       │   └── StatusBarExpandModule.java  # Modern LibXposed entry point
│   │       ├── resources/META-INF/xposed/
│   │       │   ├── java_init.list      # LibXposed entry point declaration
│   │       │   ├── module.prop         # Module metadata & static scope flag
│   │       │   └── scope.list          # Scoped packages (system + com.android.systemui)
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── LICENSE                             # GPLv3 License
├── README.md                           # English documentation
└── README_CN.md                        # Chinese documentation
```

---

## 🛠️ Building from Source

### Prerequisites
* JDK 17 (Eclipse Temurin or OpenJDK)
* Android SDK (compileSdk 34, build-tools 34.0.0)

### Build Steps
```bash
# Clone the repository
git clone https://github.com/badgayisme-blip/HyperOS-StatusBar-Expand.git
cd HyperOS-StatusBar-Expand

# Build release APK
./gradlew assembleRelease
```
The output APK will be generated at `app/build/outputs/apk/release/app-release.apk`.

---

## 📲 Installation & Usage

1. Download the latest `HyperOS_StatusBar_12Icons.apk` from [Releases](../../releases).
2. Install the APK on your device.
3. Open **LSPosed Manager**:
   * Enable the **HyperOS Status Bar 12 Icons** module.
   * Verify that **System UI** (`com.android.systemui`) is checked in the scope.
4. Restart System UI (or reboot device):
   ```bash
   su -c killall com.android.systemui
   ```
5. Check logs to verify operation:
   * File log: `/sdcard/statusbar_expand.log`
   * Logcat:
     ```bash
     adb logcat -d -s StatusBarExpand:V
     ```

---

## 📄 License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
