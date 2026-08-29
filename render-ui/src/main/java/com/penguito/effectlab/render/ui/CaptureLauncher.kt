package com.penguito.effectlab.render.ui

import android.app.Activity
import com.penguito.effectlab.render.core.permission.CameraPermissionGate
import com.penguito.effectlab.render.core.permission.PermissionResult

class CaptureLauncher(
    private val activity: Activity,
    private val onPermissionDenied: () -> Unit,
) {
    private val permissionGate = CameraPermissionGate(activity)

    fun launch() {
        if (permissionGate.isGranted()) {
            openCapture()
        } else {
            permissionGate.request()
        }
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ): Boolean = when (
        permissionGate.resolveRequestResult(requestCode, permissions, grantResults)
    ) {
        PermissionResult.GRANTED -> {
            openCapture()
            true
        }

        PermissionResult.DENIED -> {
            onPermissionDenied()
            true
        }

        null -> false
    }

    private fun openCapture() {
        activity.startActivity(CaptureActivity.createIntent(activity))
    }
}
