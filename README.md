# PELab

<p align="center">
  <img src="docs/assets/pelab-icon.png" alt="PELab icon" width="128">
</p>

**English** | [简体中文](README.zh-CN.md) | [Français](README.fr.md)

PELab, short for Penguito Effect Lab, aims to build a complete Android application with graphics rendering capabilities from scratch.

## Current Development Progress

### 0.2

- Establish the basic camera rendering path.

#### 0.2.1

- Centralized camera permission checks, requests, and result parsing in `render-core-permission`;
- Added `CaptureActivity` to verify the camera permission gate and basic page lifecycle.

#### 0.2.2

- Added Camera2 device detection before camera creation in `render-core-camera`;
- Added `CameraErrorListener` to report camera access, permission, device detection, and configuration errors to the business layer.

### 0.1

- Establish the project's foundational architecture and module structure.

#### 0.1.1

- Set up the Android project and define the toolchain versions;
- applicationId: `com.penguito.effectlab`;
- Minimum supported version: Android 8.0 (API 26).

#### 0.1.2

- Completed the Android project's module structure;
- Added the `render-ui`, `render-core-camera`, `render-core-permission`, `render-core-material`, and `render-sdk` Android Library modules.

#### 0.1.3

- Verified the minimal Java → JNI → C++ call path.
- Added the `RenderEngine` Java SDK entry point in `render-sdk` and loaded `libpelab_sdk.so`;

## Module Architecture

```text
app
└── render-ui
    ├── render-core-camera
    ├── render-core-permission
    ├── render-core-material
    └── render-sdk
```

| Module | Responsibility |
| --- | --- |
| `app` | Application entry point |
| `render-ui` | Manages the UI and page lifecycle |
| `render-core-camera` | Camera module — manages camera input and its lifecycle |
| `render-core-permission` | Permission module — handles permission requests and their results |
| `render-core-material` | Material management module — handles the storage and loading of effect materials |
| `render-sdk` | Rendering SDK module — provides Java APIs and contains the Native rendering implementation |

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

The Native Library is built separately for `arm64-v8a`, `armeabi-v7a`, and `x86_64`, then packaged automatically into the APK.
