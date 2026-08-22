package com.penguito.effectlab.render.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

class PhotoPickerLauncher(
    private val activity: Activity,
    private val onImageSelected: (Uri) -> Unit,
) {

    fun launch() {
        activity.startActivityForResult(createPickerIntent(), REQUEST_CODE)
    }

    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ): Boolean {
        if (requestCode != REQUEST_CODE) return false

        if (resultCode == Activity.RESULT_OK) {
            data?.data?.let(onImageSelected)
        }
        return true
    }

    private fun createPickerIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                type = "image/*"
            }
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
        }
    }

    private companion object {
        const val REQUEST_CODE = 1002
    }
}
