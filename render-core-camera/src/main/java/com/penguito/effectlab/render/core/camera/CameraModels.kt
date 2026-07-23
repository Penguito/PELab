package com.penguito.effectlab.render.core.camera

/** camera lens directions **/
enum class LensFacing {
    FRONT,
    BACK,
}

/** preview buffer size */
data class PreviewSize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0 && height > 0) {
            "Preview size must be greater than zero"
        }
    }

    internal val area: Long
        get() = width.toLong() * height

    internal val aspectRatio: Double
        get() = width.toDouble() / height
}

/** camera metadata */
data class CameraDeviceInfo(
    val cameraId: String,
    val lensFacing: LensFacing,
    val supportedPreviewSizes: List<PreviewSize>,
) {
    init {
        require(cameraId.isNotBlank())
        require(supportedPreviewSizes.isNotEmpty())
    }
}

/** camera configuration */
data class CameraConfiguration(
    val cameraId: String,
    val lensFacing: LensFacing,
    val previewSize: PreviewSize,
)
