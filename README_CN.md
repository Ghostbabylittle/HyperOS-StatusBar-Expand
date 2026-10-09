# HyperOS 状态栏 12 图标扩展模块 (Status Bar 12 Icons)

[English](README.md) | [简体中文](README_CN.md)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![LSPosed](https://img.shields.io/badge/LSPosed-%E6%A8%A1%E5%9D%97-brightgreen.svg)]()
[![Xiaomi HyperOS](https://img.shields.io/badge/HyperOS-%E6%94%AF%E6%8C%81-orange.svg)]()
[![Android 14](https://img.shields.io/badge/Android-14-green.svg)]()

专为 **小米 HyperOS (澎湃 OS / Android 14+)** 设计的 LSPosed 模块。解除系统限制，将状态栏右侧系统状态图标数量上限从 **6 个扩展至 12 个**。

---

## 🔍 背景与问题

在搭载 **Xiaomi HyperOS (Android 14+)** 的设备上，系统状态栏右侧区域对状态图标施加了数量限制：

* 即使开启了多项系统功能（如 **VPN、实时网速、NFC、蓝牙、闹钟、热点、VoLTE/VoWiFi、Wi-Fi、蜂窝网络、电池** 等），HyperOS 默认最多仅允许展示 **6 个图标**。
* 超出数量的图标会被底层黑名单拦截，或在排版计算时被强制隐藏。

---

## 🔬 技术根因

### 1. 框架内部硬编码黑名单拦截
HyperOS 的 SystemUI 在 `com.android.systemui.statusbar.phone.MiuiIconManagerUtils` 中定义了静态过滤列表：
* `RIGHT_BLOCK_LIST` 与 `MINI_RIGHT_BLOCK_LIST` 强制过滤了例如 `network_speed`（实时网速）和 `nfc` 等槽位。
* 容器的 `MiuiStatusIconContainer.setIgnoredSlots` 会在测量前不断传入被忽略的插槽列表，导致图标无法进入排版队列。

### 2. 坐标溢出与强制 GONE 隐藏机制
* `MiuiStatusIconContainer.onMeasure` 限制了测量宽度，且默认间距较大（约 14px）。
* 当展示超过 6 个图标时，计算出的水平坐标超出限制，触发 `MiuiStatusIconContainer.onLayout` 的溢出截断逻辑：
  ```java
  iconState.visibleState = View.GONE;
  child.setVisibility(View.GONE);
  ```
  导致超出 6 个的图标被直接隐藏。

---

## 💡 解决方案

本模块针对 SystemUI 进行精确 Hook，解除图标数量与显示限制：

```
[SystemUI 启动 / 状态栏刷新]
           │
           ▼
[第一阶段：解除黑名单]
   ├── 清空 MiuiIconManagerUtils.RIGHT_BLOCK_LIST 与 MINI_RIGHT_BLOCK_LIST
   └── 拦截 MiuiStatusIconContainer.setIgnoredSlots，传入空列表
           │
           ▼
[第二阶段：间距紧凑化与尺寸扩容]
   ├── 拦截 onMeasure：当图标数量较多时紧凑化间距 (mIconSpacing = 3px)
   └── 拓展 widthMeasureSpec 测量宽度
           │
           ▼
[第三阶段：坐标排版重算]
   ├── 遍历所有有效状态图标
   ├── 自右向左重新排布有效水平坐标
   └── 强制将前 12 个图标设为 VISIBLE
```

---

## 🚀 核心特性

* **扩展至 12 个图标**：右侧系统状态栏最多可同时展示 12 个图标。
* **紧凑化间距**：图标较多时自动缩减间距（`mIconSpacing = 3`），容纳更多图标正常排布。
* **解锁受限图标**：恢复实时网速、NFC 等被 HyperOS 屏蔽的图标正常显示。
* **极致轻量**：无第三方多余依赖，APK 大小仅约 17 KB。
* **多通道日志**：支持 Logcat（标签：`StatusBarExpand`）及本地文件 `/sdcard/statusbar_expand.log` 诊断。

---

## 📦 项目目录结构

```
├── .github/
│   └── workflows/
│       └── build.yml                   # GitHub Actions 自动化 CI 构建
├── app/
│   ├── libs/
│   │   └── libxposed-api-101.0.0.jar   # LibXposed 编译期依赖
│   ├── src/
│   │   └── main/
│   │       ├── assets/
│   │       │   └── xposed_init
│   │       ├── java/com/hyperos/statusbar/expand/
│   │       │   ├── Logger.java         # 日志记录工具
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
├── LICENSE                             # GPLv3 开源协议
├── README.md                           # 英文文档
└── README_CN.md                        # 中文文档
```

---

## 🛠️ 从源码编译

### 环境需求
* JDK 17
* Android SDK (compileSdk 34)

### 编译指令
```bash
git clone https://github.com/Ghostbabylittle/HyperOS-StatusBar-Expand.git
cd HyperOS-StatusBar-Expand
./gradlew assembleRelease
```
编译产物位于 `app/build/outputs/apk/release/app-release.apk`。

---

## 📲 安装与使用

1. 在 [Releases](../../releases) 页面下载 `HyperOS_StatusBar_12Icons.apk`。
2. 安装 APK 至手机。
3. 打开 **LSPosed 管理器**：
   * 勾选启用 **HyperOS Status Bar 12 Icons** 模块。
   * 确认作用域包含 **系统界面 (System UI / `com.android.systemui`)**。
4. 重启 SystemUI（或重启手机）：
   ```bash
   su -c killall com.android.systemui
   ```

---

## 📄 开源许可证

本项目基于 [GNU General Public License v3.0](LICENSE) 协议开源。
