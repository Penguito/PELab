package com.penguito.effectlab.render.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import com.penguito.effectlab.render.core.material.ImageImportManager

class PhotoPickerLauncher(
    private val activity: Activity,
    private val onImageImported: (String) -> Unit,
    private val onImageImportFailed: () -> Unit,
) {
    private val imageImportManager = ImageImportManager(activity)

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
            data?.data?.let { imageUri ->
                imageImportManager.importImage(imageUri) { imagePath ->
                    if (imagePath == null) {
                        onImageImportFailed()
                    } else {
                        onImageImported(imagePath)
                    }
                }
            }
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
