# PELab

<p align="center">
  <img src="docs/assets/pelab-icon.png" alt="PELab 图标" width="128">
</p>

[English](README.md) | **简体中文** | [Français](README.fr.md)

PELab 全称为 Penguito Effect Lab，旨在从零开始构建一个完整的、具备图形渲染能力的 Android 应用。

## 当前开发进度

### 0.2

- 跑通基础相机渲染链路。

#### 0.2.1

- 在 `render-core-permission` 中集中实现相机权限检查、请求和结果解析；
- 新增 `CaptureActivity`，验证相机权限门禁和基础页面生命周期。

### 0.1

- 完成项目基础架构的搭建和模块划分。

#### 0.1.1

- 搭建 Android 工程并确定工具链版本；
- applicationId：`com.penguito.effectlab`；
- 最低系统版本：Android 8.0（API 26）。

#### 0.1.2

- 完成 Android 工程的模块划分；
- 新增 `render-ui`、`render-core-camera`、`render-core-permission`、`render-core-material` 和 `render-sdk` Android Library 模块。

#### 0.1.3

- 验证从 Java → JNI → C++ 的最小调用链路。
- 在 `render-sdk` 中新增 `RenderEngine` Java SDK 入口，并加载 `libpelab_sdk.so`；

## 模块架构

```text
app
└── render-ui
    ├── render-core-camera
    ├── render-core-permission
    ├── render-core-material
    └── render-sdk
```

| 模块 | 职责 |
| --- | --- |
| `app` | 应用入口 |
| `render-ui` | 负责 UI 与页面生命周期的管理 |
| `render-core-camera` | 相机功能模块 —— 管理相机输入及其生命周期 |
| `render-core-permission` | 权限申请功能模块 —— 负责权限申请与结果处理 |
| `render-core-material` | 素材管理功能模块 —— 负责特效素材的存储与加载 |
| `render-sdk` | 渲染 SDK 模块 —— 提供 Java 接口并承载 Native 渲染实现 |

## 固定工具链

| 组件 | 版本 |
| --- | --- |
| JDK / JVM target | 17 |
| Android Gradle Plugin | 9.2.1 |
| Gradle Wrapper | 9.4.1 |
| Kotlin | AGP 9.2.1 内置 Kotlin 2.3.10 |
| compileSdk | Android 36.1 |
| targetSdk / minSdk | 36 / 26 |
| NDK | 29.0.14206865 |
| CMake | 3.22.1 |

## 构建

当前构建需要 JDK 17 和 Android SDK 36.1。

```bash
./gradlew :app:assembleDebug
```

Debug APK 的输出路径是 `app/build/outputs/apk/debug/app-debug.apk`。

Native Library 会针对 `arm64-v8a`、`armeabi-v7a` 和 `x86_64` 分别构建并自动打包进 APK。
