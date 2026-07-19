# PELab

[English](README.md) | **简体中文** | [Français](README.fr.md)

PELab 全称为 Penguito Effect Lab，旨在从零开始构建一个完整的、具备图形渲染能力的 Android 应用。

## 当前开发进度

### 0.1

- 完成项目基础架构的搭建和模块划分。

#### 0.1.1

- 搭建 Android 工程并确定工具链版本；
- applicationId：`com.penguito.effectlab`；
- 最低系统版本：Android 8.0（API 26）。

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
