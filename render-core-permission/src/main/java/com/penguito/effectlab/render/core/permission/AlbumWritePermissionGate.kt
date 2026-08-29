package com.penguito.effectlab.render.core.permission

import android.Manifest
import android.app.Activity
import android.os.Build

class AlbumWritePermissionGate(activity: Activity) : PermissionGate(
    activity = activity,
    permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
    requestCode = REQUEST_CODE,
) {

    override fun isPermissionRequired(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    private companion object {
        const val REQUEST_CODE = 1003
    }
}
