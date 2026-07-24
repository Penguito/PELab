package com.penguito.effectlab.render.core.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlin.math.abs

/**
 * Detect Camera2 devices and creates a basic configuration for Camera2.
 */
class Camera2ConfigurationProvider(
    context: Context,
    private val onError: (CameraError) -> Unit,
) {
    private val cameraManager = context.applicationContext
        .getSystemService(CameraManager::class.java)

    fun getAvailableCameras(): List<CameraDeviceInfo> {
        return detectCameras().cameras
    }

    fun createConfiguration(
        lensFacing: LensFacing,
        targetPreviewSize: PreviewSize,
    ): CameraConfiguration? {
        val cameraResult = detectCameras()
        if (!cameraResult.completed) return null

        val camera = cameraResult.cameras.firstOrNull { it.lensFacing == lensFacing }
        if (camera == null) {
            reportError(
                code = CameraErrorCode.CAMERA_NOT_FOUND,
                lensFacing = lensFacing,
            )
            return null
        }

        return CameraConfiguration(
            cameraId = camera.cameraId,
            lensFacing = camera.lensFacing,
            previewSize = choosePreviewSize(
                supportedSizes = camera.supportedPreviewSizes,
                targetSize = targetPreviewSize,
            ),
        )
    }

    private fun detectCameras(): CameraResult {
        val cameraIds = try {
            cameraManager.cameraIdList
        } catch (error: CameraAccessException) {
            reportError(
                code = CameraErrorCode.ACCESS_FAILED,
                cause = error,
            )
            return CameraResult(completed = false)
        } catch (error: SecurityException) {
            reportError(
                code = CameraErrorCode.PERMISSION_MISSING,
                cause = error,
            )
            return CameraResult(completed = false)
        }

        val cameras = cameraIds
            .mapNotNull(::readCameraInfo)
            .sortedBy(CameraDeviceInfo::cameraId)
        return CameraResult(
            cameras = cameras,
            completed = true,
        )
    }

    private fun readCameraInfo(cameraId: String): CameraDeviceInfo? {
        val characteristics = try {
            cameraManager.getCameraCharacteristics(cameraId)
        } catch (error: CameraAccessException) {
            reportError(
                code = CameraErrorCode.ACCESS_FAILED,
                cameraId = cameraId,
                cause = error,
            )
            return null
        } catch (error: SecurityException) {
            reportError(
                code = CameraErrorCode.PERMISSION_MISSING,
                cameraId = cameraId,
                cause = error,
            )
            return null
        }

        val lensFacing = characteristics.toLensFacing() ?: return null
        val streamConfigurationMap =
            characteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]
        val previewSizes = streamConfigurationMap
            ?.getOutputSizes(SurfaceTexture::class.java)
            .orEmpty()
            .asSequence()
            .filter { it.width > 0 && it.height > 0 }
            .map { PreviewSize(width = it.width, height = it.height) }
            .distinct()
            .sortedByDescending(PreviewSize::area)
            .toList()

        if (previewSizes.isEmpty()) {
            reportError(
                code = CameraErrorCode.PREVIEW_CONFIGURATION_MISSING,
                cameraId = cameraId,
                lensFacing = lensFacing,
            )
            return null
        }

        return CameraDeviceInfo(
            cameraId = cameraId,
            lensFacing = lensFacing,
            supportedPreviewSizes = previewSizes,
        )
    }

    private fun choosePreviewSize(
        supportedSizes: List<PreviewSize>,
        targetSize: PreviewSize,
    ): PreviewSize {
        supportedSizes.firstOrNull { it == targetSize }?.let { return it }

        val boundedSizes = supportedSizes
            .filter { it.width <= MAX_PREVIEW_WIDTH && it.height <= MAX_PREVIEW_HEIGHT }
            .ifEmpty { supportedSizes }

        return boundedSizes.minWith(
            compareBy<PreviewSize>(
                { abs(it.aspectRatio - targetSize.aspectRatio) },
                { abs(it.area - targetSize.area) },
            ),
        )
    }

    private fun CameraCharacteristics.toLensFacing(): LensFacing? {
        return when (this[CameraCharacteristics.LENS_FACING]) {
            CameraCharacteristics.LENS_FACING_FRONT -> LensFacing.FRONT
            CameraCharacteristics.LENS_FACING_BACK -> LensFacing.BACK
            else -> null
        }
    }

    private fun reportError(
        code: CameraErrorCode,
        cameraId: String? = null,
        lensFacing: LensFacing? = null,
        cause: Throwable? = null,
    ) {
        onError(
            CameraError(
                code = code,
                cameraId = cameraId,
                lensFacing = lensFacing,
                cause = cause,
            ),
        )
    }

    private data class CameraResult(
        val cameras: List<CameraDeviceInfo> = emptyList(),
        val completed: Boolean,
    )

    private companion object {
        const val MAX_PREVIEW_WIDTH = 1920
        const val MAX_PREVIEW_HEIGHT = 1080
    }
}
