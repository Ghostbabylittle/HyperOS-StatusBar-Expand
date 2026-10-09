# HyperOS Status Bar 12 Icons

[English](README.md) | [简体中文](README_CN.md)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![LSPosed](https://img.shields.io/badge/LSPosed-Module-brightgreen.svg)]()
[![Xiaomi HyperOS](https://img.shields.io/badge/HyperOS-Supported-orange.svg)]()
[![Android 14](https://img.shields.io/badge/Android-14-green.svg)]()

A lightweight LSPosed module for **Xiaomi HyperOS** that expands the right-side system status bar icon limit from **6 to 12**.

---

## 🔍 Background & The Problem

On devices running **Xiaomi HyperOS (Android 14+)**, SystemUI enforces a strict limit on the number of system status icons displayed on the right side of the status bar:

* Even when multiple system features are active (such as **VPN, Network Speed, NFC, Bluetooth, Alarm, Hotspot, VoLTE/VoWiFi, Wi-Fi, Cellular, Battery**), HyperOS caps the display at **6 icons**.
* Any icons beyond 6 are either blocked by internal MIUI blocklists or hidden during layout calculation.

---

## 🔬 Technical Root Cause

### 1. Hardcoded Blocklists in Framework
HyperOS SystemUI maintains static blocklists inside `com.android.systemui.statusbar.phone.MiuiIconManagerUtils`:
* `RIGHT_BLOCK_LIST` and `MINI_RIGHT_BLOCK_LIST` explicitly filter out slots like `network_speed` and `nfc`.
* `MiuiStatusIconContainer.setIgnoredSlots` is repeatedly invoked with ignored slot lists to drop icons before measurement.

### 2. Coordinate Overflow & Forced GONE State
* `MiuiStatusIconContainer.onMeasure` restricts horizontal bounds, and `onLayout` computes coordinates with default spacing (~14px).
* When more than 6 icons are present, the calculated X-offset overflows the container bounds, causing `MiuiStatusIconContainer.onLayout` to forcibly hide excess icons:
  ```java
  iconState.visibleState = View.GONE;
  child.setVisibility(View.GONE);
  ```

---

## 💡 The Solution

This module hooks SystemUI to eliminate the icon restriction:

```
[SystemUI Launch / Icon Update]
           │
           ▼
[Stage 1: Blocklist Neutralization]
   ├── Clear MiuiIconManagerUtils.RIGHT_BLOCK_LIST & MINI_RIGHT_BLOCK_LIST
   └── Intercept MiuiStatusIconContainer.setIgnoredSlots -> pass empty list
           │
           ▼
[Stage 2: Spacing & Dimension Expansion]
   ├── Intercept onMeasure: condense mIconSpacing (3px) when childCount > 6
   └── Expand widthMeasureSpec bounds
           │
           ▼
[Stage 3: Layout Recalculation]
   ├── Iterates all eligible status icon views
   ├── Recalculates positive X-coordinates from container width right-to-left
   └── Forces visibleState = VISIBLE and alpha = 1.0f for up to 12 icons
```

---

## 🚀 Key Features

* **Expands Limit to 12 Icons**: Allows up to 12 status icons to display simultaneously on the right side.
* **Condensed Spacing**: Dynamically reduces icon spacing (`mIconSpacing = 3`) when icon count is high so more icons fit on screen.
* **Unlocks Blocked Slots**: Unblocks slots such as network speed and NFC that are otherwise suppressed by HyperOS.
* **Ultra-Lightweight**: Zero third-party runtime dependencies (~17 KB APK).
* **Multi-Channel Logging**: Diagnostic logs output to Logcat (`tag: StatusBarExpand`) and persistent file at `/sdcard/statusbar_expand.log`.

---

## 📦 Project Structure

```
├── .github/
│   └── workflows/
│       └── build.yml                   # Automated CI build on GitHub Actions
├── app/
│   ├── libs/
│   │   └── libxposed-api-101.0.0.jar   # LibXposed compileOnly dependency
│   ├── src/
│   │   └── main/
│   │       ├── assets/
│   │       │   └── xposed_init
│   │       ├── java/com/hyperos/statusbar/expand/
│   │       │   ├── Logger.java         # Logging utility
│   │       │   ├── StatusBarExpandHook.java
│   │       │   └── StatusBarExpandModule.java
│   │       ├── resources/META-INF/xposed/
│   │       │   ├── java_init.list
│   │       │   ├── module.prop
│   │       │   └── scope.list
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
* JDK 17
* Android SDK (compileSdk 34)

### Build Steps
```bash
git clone https://github.com/Ghostbabylittle/HyperOS-StatusBar-Expand.git
cd HyperOS-StatusBar-Expand
./gradlew assembleRelease
```
The output APK is generated at `app/build/outputs/apk/release/app-release.apk`.

---

## 📲 Installation & Usage

1. Download `HyperOS_StatusBar_12Icons.apk` from [Releases](../../releases).
2. Install the APK on your device.
3. Open **LSPosed Manager**:
   * Enable the **HyperOS Status Bar 12 Icons** module.
   * Verify that **System UI** (`com.android.systemui`) is checked in scope.
4. Restart System UI (or reboot device):
   ```bash
   su -c killall com.android.systemui
   ```

---

## 📄 License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
