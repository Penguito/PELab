# PELab

<p align="center">
  <img src="docs/assets/pelab-icon.png" alt="PELab icon" width="128">
</p>

**English** | [简体中文](README.zh-CN.md) | [Français](README.fr.md)

PELab, short for Penguito Effect Lab, aims to build a complete Android application with graphics rendering capabilities from scratch.

## Current Development Progress

### 0.5

- Add support for capturing, importing, and saving images to the gallery.

#### 0.5.1

- Added support for reading the current render result and encoding it as JPEG in `render-sdk`.

#### 0.5.2

- Added photo capture support in `render-ui` and enabled preview by navigating to the editor page.

#### 0.5.3

- Added system single-image selection support in `render-ui`.

### 0.4

- Add support for setting LUT filters.

#### 0.4.1

- Added the filter material model and material loading and copying methods to `render-core-material`.

#### 0.4.2

- Added the `setFilter()` method to `render-sdk`.

#### 0.4.3

- Completed LUT Bitmap decoding and GLES 2D Texture upload in `render-sdk`.

#### 0.4.4

- Implemented the Filter Pass rendering pipeline in `render-sdk`.

#### 0.4.5

- Refactored the Native Renderer by splitting image adjustment and filter features into independent passes.
- Completed testing of the LUT filter feature pipeline in the recorder UI.

### 0.3

- Add basic image adjustment support and implement a 3-pass rendering pipeline.

#### 0.3.1

- Added `ImageParams` to `render-sdk`.

#### 0.3.2

- Enabled the business layer to configure and update image params through `render-sdk`.

#### 0.3.3

- Completed the 3-pass rendering pipeline in `render-sdk`: OES -> normalized buffer -> image adjustment buffer -> `SurfaceView`.

#### 0.3.4

- Implemented brightness adjustment in the `render-sdk` Adjustment Shader.

#### 0.3.5

- Implemented basic warmth adjustment in the `render-sdk` Adjustment Shader.

#### 0.3.6

- Added live brightness and warmth adjustment controls to the Capture page.

### 0.2

- Establish the basic camera rendering path.

#### 0.2.1

- Centralized camera permission checks, requests, and result parsing in `render-core-permission`;
- Added `CaptureActivity` to verify the camera permission gate and basic page lifecycle.

#### 0.2.2

- Added Camera2 device detection before camera creation in `render-core-camera`;
- ~~Added `CameraErrorListener` to report camera access, permission, device detection, and configuration errors to the business layer.~~

#### 0.2.3

- Added `Camera2Manager` to `render-core-camera` to create and manage the lifecycle of `CameraDevice` and `CaptureSession`.

#### 0.2.4

- Added an initialization method to `RenderEngine` in `render-sdk` to create and release the GL environment.

#### 0.2.5

- Added GL rendering in `render-sdk` to complete the preview path from `Camera2` to `SurfaceView`.

#### 0.2.6

- Added front and rear camera switching with frame-time and current FPS display to the Capture page.

#### 0.2.7

- Fixed the selectable preview resolutions to 1280×720 and 1920×1080.

#### 0.2.8

- Added RGBA textures and framebuffers for the fixed portrait specifications in `render-sdk`.

#### 0.2.9

- Completed the OES -> RGBA framebuffer -> `SurfaceView` two-pass rendering path in `render-sdk`.

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
