package com.penguito.effectlab.render.core.permission

import android.Manifest
import android.app.Activity

class CameraPermissionGate(activity: Activity) : PermissionGate(
    activity = activity,
    permission = Manifest.permission.CAMERA,
    requestCode = REQUEST_CODE,
) {

    private companion object {
        const val REQUEST_CODE = 1001
    }
}
