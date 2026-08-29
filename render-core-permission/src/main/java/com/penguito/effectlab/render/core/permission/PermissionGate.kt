package com.penguito.effectlab.render.core.permission

import android.app.Activity
import android.content.pm.PackageManager

enum class PermissionResult {
    GRANTED,
    DENIED,
}

abstract class PermissionGate(
    private val activity: Activity,
    private val permission: String,
    private val requestCode: Int,
) {

    fun isGranted(): Boolean = !isPermissionRequired() || activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun request() {
        if (!isGranted()) {
            activity.requestPermissions(arrayOf(permission), requestCode)
        }
    }

    fun resolveRequestResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ): PermissionResult? {
        if (requestCode != this.requestCode) return null

        val permissionIndex = permissions.indexOf(permission)
        return if (grantResults.getOrNull(permissionIndex) == PackageManager.PERMISSION_GRANTED) {
            PermissionResult.GRANTED
        } else {
            PermissionResult.DENIED
        }
    }

    protected open fun isPermissionRequired(): Boolean = true
}
