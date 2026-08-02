package com.penguito.effectlab.render.core.camera

enum class CameraErrorCode {
    ACCESS_FAILED,
    PERMISSION_MISSING,
    CAMERA_NOT_FOUND,
    PREVIEW_CONFIGURATION_MISSING,
    CAMERA_DISCONNECTED,
    CAMERA_DEVICE_FAILED,
    CAPTURE_SESSION_FAILED,
}

data class CameraError(
    val code: CameraErrorCode,
    val cameraId: String? = null,
    val lensFacing: LensFacing? = null,
    val cause: Throwable? = null,
)

/** Receives Camera2 start, stop, and error events. */
interface Camera2Listener {
    fun onCameraStarted(configuration: CameraConfiguration)

    fun onCameraStopped()

    fun onCameraError(error: CameraError)
}
