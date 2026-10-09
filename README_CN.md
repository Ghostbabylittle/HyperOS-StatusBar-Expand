# HyperOS 状态栏 12 图标扩展模块 (Status Bar 12 Icons)

[English](README.md) | [简体中文](README_CN.md)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![LSPosed Compatible](https://img.shields.io/badge/LSPosed-API%20101-brightgreen.svg)]()
[![Xiaomi HyperOS](https://img.shields.io/badge/HyperOS-%E6%94%AF%E6%8C%81-orange.svg)]()
[![Android 14](https://img.shields.io/badge/Android-14-green.svg)]()

专为 **小米 HyperOS (澎湃 OS / Android 14+)** 设计的高性能、超轻量 LSPosed 模块。突破系统限制，将状态栏右侧系统状态图标数量上限从 **6 个扩展至 12 个**，并完美解决与中置挖孔前摄的重叠与防遮挡问题。

---

## 🔍 背景与核心痛点

在搭载 **Xiaomi HyperOS (Android 14+)** 的设备上，系统状态栏右侧区域对状态图标施加了极为严苛的数量限制：

* 即使开启了多项系统服务（如 **VPN、实时网速、NFC、蓝牙、闹钟、热点、VoLTE/VoWiFi、Wi-Fi、蜂窝移动网络、电池** 等），HyperOS 默认最多仅允许展示 **6 个图标**。
* 超出数量的图标会被 MIUI 底层硬编码的黑名单拦截，或在布局排版计算时被强制隐藏。
* 传统的全局状态栏修改往往引起图标与中置挖孔摄像头的碰撞、重叠，或与现存的 SystemUI 模块（如截屏限制破解等）产生兼容性冲突。

---

## 🔬 技术根因深度解析

### 1. 框架内部硬编码黑名单拦截
HyperOS 的 SystemUI 在 `com.android.systemui.statusbar.phone.MiuiIconManagerUtils` 中定义了静态过滤列表：
* `RIGHT_BLOCK_LIST` 与 `MINI_RIGHT_BLOCK_LIST` 强制过滤了例如 `network_speed`（实时网速）和 `nfc` 等关键槽位。
* 容器的 `MiuiStatusIconContainer.setIgnoredSlots` 会在测量前不断传入被忽略的插槽列表，导致图标无法进入排版队列。

### 2. 中置挖孔屏幕的物理可用宽度受限
在采用前置中置单孔屏的机型中，`MiuiPhoneStatusBarView` 将状态栏平分为三部分：
```
[ mStatusBarLeftContainer (左侧通知区, 权重 1.0) ] [ mCutoutSpace (居中挖孔) ] [ mSystemIconArea (右侧状态区, 权重 1.0) ]
```
右侧系统状态栏图标区域的实际水平空间受限：
$$\text{可用宽度} \approx \frac{\text{屏幕总宽度} - \text{挖孔宽度}}{2}$$
在主流 1080p/1440p 分辨率机型上，挖孔右边缘至电池图标之间的可用像素通常仅有 **400px** 左右。

### 3. 坐标溢出与强制 GONE 隐藏机制
在默认 ~14px 的图标间距下，6 个图标已占据约 300px。当展示 7 个及以上图标时：
* 所需总宽度超过了容器测量宽度。
* 计算出的图标 X 轴偏移量小于 `paddingStart`（即进入屏幕外或跨入挖孔区域）。
* `MiuiStatusIconContainer.onLayout` 监测到该溢出状态后，会直接执行：
  ```java
  iconState.visibleState = View.GONE;
  child.setVisibility(View.GONE);
  ```
  从而导致超出 6 个的图标被底层直接掐断显示。

### 4. 现代 LSPosed (LibXposed API 101) 架构约束
在现代 Zygisk LSPosed 环境下：
* 模块配置了 `META-INF/xposed/java_init.list` 时，LSPosed 会强制以 **LibXposed (API 101)** 原生模式加载，彻底跳过传统的 `assets/xposed_init`。
* `java_init.list` 中指定的入口类**必须继承 `io.github.libxposed.api.XposedModule`**。若放入旧版 `IXposedHookLoadPackage` 类，将在实例化阶段抛出隐式 `ClassCastException` 导致模块直接静默崩溃。
* 必须保持**单 DEX (`classes.dex`)** 架构，去除 Kotlin 运行时及 AndroidX 冗余依赖，避免引起 ART DexOpt 校验异常 (`Fail to get art odex vdex`)。
* `META-INF/xposed/scope.list` 必须包含 `system`，并在 `module.prop` 中声明 `staticScope=true`，以实现守护进程对系统作用域的免手动勾选自动注册。

---

## 💡 解决方案

本模块采用纯 Java 现代 LibXposed 原生实现，构建了全方位的无冲突注入链路：

```
[SystemUI 启动 / 状态栏图标刷新]
           │
           ▼
[第一阶段：黑名单彻底解除]
   ├── 清空 MiuiIconManagerUtils.RIGHT_BLOCK_LIST 与 MINI_RIGHT_BLOCK_LIST
   └── 拦截 MiuiStatusIconContainer.setIgnoredSlots，强制替换为空列表
           │
           ▼
[第二阶段：动态间距自适应与尺寸扩容]
   ├── 拦截 onMeasure：当子视图数量 > 6 时，动态将 mIconSpacing 压缩至 3px
   └── 拓展 widthMeasureSpec 宽度上限至至少 700px AT_MOST
           │
           ▼
[第三阶段：自右向左精确坐标重排]
   ├── 遍历所有未被屏蔽的有效状态图标
   ├── 依据容器宽度自右侧向左逆向计算坐标，确保全部获得有效正向坐标
   └── 强制将前 12 个有效图标的 visibleState 设为 VISIBLE，alpha 设为 1.0f
           │
           ▼
[第四阶段：容器布局权重微调]
   └── 在 updateCutoutLocation 中动态重平衡 LinearLayout 权重（左侧 0.8，右侧 1.2）
```

---

## 🚀 核心特性

* **完整扩展至 12 个图标**：右侧系统状态栏最多可同时清晰显示 12 个图标。
* **智能间距自适应算法**：图标较多时自动紧凑化间距（`mIconSpacing = 3`），确保绝不与中央挖孔前摄产生遮挡碰撞。
* **解锁受限图标**：实时网速、NFC 等被 HyperOS 屏蔽的图标恢复正常显示。
* **极致精简单 DEX**：无任何第三方运行时冗余，生成的发布版 APK 仅约 **17 KB**，内存占用微乎其微。
* **现代 LibXposed (API 101) 原生兼容**：无缝运行于现代 Zygisk LSPosed。
* **双模兼容机制**：保留 legacy `assets/xposed_init` 降级支持，兼容不同框架环境。
* **多通道全链路日志记录**：同时输出至 Logcat（标签：`StatusBarExpand`）、LSPosed 日志及本地文件 `/sdcard/statusbar_expand.log`。

---

## 📦 项目目录结构

```
├── .github/
│   └── workflows/
│       └── build.yml                   # GitHub Actions 自动化持续集成构建
├── app/
│   ├── libs/
│   │   └── libxposed-api-101.0.0.jar   # Modern LibXposed API 编译期依赖库
│   ├── src/
│   │   └── main/
│   │       ├── assets/
│   │       │   └── xposed_init         # 旧版 Xposed 降级入口
│   │       ├── java/com/hyperos/statusbar/expand/
│   │       │   ├── Logger.java         # 多通道日志记录器
│   │       │   ├── StatusBarExpandHook.java    # 旧版 Hook 实现
│   │       │   └── StatusBarExpandModule.java  # 现代 LibXposed 核心入口
│   │       ├── resources/META-INF/xposed/
│   │       │   ├── java_init.list      # LibXposed 入口声明
│   │       │   ├── module.prop         # 模块元数据与静态作用域标记
│   │       │   └── scope.list          # 作用域包名 (system 与 com.android.systemui)
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

### 环境准备
* JDK 17 (Eclipse Temurin 或 OpenJDK)
* Android SDK (compileSdk 34, build-tools 34.0.0)

### 编译指令
```bash
# 克隆仓库
git clone https://github.com/badgayisme-blip/HyperOS-StatusBar-Expand.git
cd HyperOS-StatusBar-Expand

# 编译 Release APK
./gradlew assembleRelease
```
编译产物位于 `app/build/outputs/apk/release/app-release.apk`。

---

## 📲 安装与启用

1. 在 [Releases](../../releases) 页面下载最新版 `HyperOS_StatusBar_12Icons.apk`。
2. 安装 APK 至手机。
3. 打开 **LSPosed 管理器**：
   * 勾选启用 **HyperOS Status Bar 12 Icons** 模块。
   * 确认作用域中已自动包含 **系统界面 (System UI / `com.android.systemui`)**。
4. 重启 SystemUI（或重启手机）：
   ```bash
   su -c killall com.android.systemui
   ```
5. 验证运行状态与日志：
   * 本地日志文件：`/sdcard/statusbar_expand.log`
   * Logcat 查看：
     ```bash
     adb logcat -d -s StatusBarExpand:V
     ```

---

## 📄 开源许可证

本项目基于 [GNU General Public License v3.0](LICENSE) 协议开源。
