package com.penguito.effectlab.render.core.permission

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager

enum class CameraPermissionResult {
    GRANTED,
    DENIED,
}

class CameraPermissionGate(private val activity: Activity) {

    fun isGranted(): Boolean =
        activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun request() {
        if (!isGranted()) {
            activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CODE)
        }
    }

    fun resolveRequestResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ): CameraPermissionResult? {
        if (requestCode != REQUEST_CODE) return null

        val cameraPermissionIndex = permissions.indexOf(Manifest.permission.CAMERA)
        return if (
            grantResults.getOrNull(cameraPermissionIndex) == PackageManager.PERMISSION_GRANTED
        ) {
            CameraPermissionResult.GRANTED
        } else {
            CameraPermissionResult.DENIED
        }
    }

    private companion object {
        const val REQUEST_CODE = 1001
    }
}
