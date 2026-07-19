# PELab

**English** | [简体中文](README.zh-CN.md) | [Français](README.fr.md)

PELab, short for Penguito Effect Lab, aims to build a complete Android application with graphics rendering capabilities from scratch.

## Current Development Progress

### 0.1

- Establish the project's foundational architecture and module structure.

#### 0.1.1

- Set up the Android project and define the toolchain versions;
- applicationId: `com.penguito.effectlab`;
- Minimum supported version: Android 8.0 (API 26).

## Fixed Toolchain

| Component | Version |
| --- | --- |
| JDK / JVM target | 17 |
| Android Gradle Plugin | 9.2.1 |
| Gradle Wrapper | 9.4.1 |
| Kotlin | Kotlin 2.3.10 built into AGP 9.2.1 |
| compileSdk | Android 36.1 |
| targetSdk / minSdk | 36 / 26 |
| NDK | 29.0.14206865 |
| CMake | 3.22.1 |

## Build

The current build requires JDK 17 and Android SDK 36.1.

```bash
./gradlew :app:assembleDebug
```

The Debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
