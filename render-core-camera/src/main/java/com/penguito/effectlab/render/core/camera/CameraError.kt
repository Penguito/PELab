package com.penguito.effectlab.render.core.camera

enum class CameraErrorCode {
    ACCESS_FAILED,
    PERMISSION_MISSING,
    CAMERA_NOT_FOUND,
    PREVIEW_CONFIGURATION_MISSING,
}

data class CameraError(
    val code: CameraErrorCode,
    val cameraId: String? = null,
    val lensFacing: LensFacing? = null,
    val cause: Throwable? = null,
)

fun interface CameraErrorListener {
    /**
     * Receive errors when detecting camera2.
     */
    fun onCameraError(error: CameraError)
}
