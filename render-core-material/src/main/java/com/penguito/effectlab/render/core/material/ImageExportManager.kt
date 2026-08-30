package com.penguito.effectlab.render.core.material

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.IOException

class ImageExportManager(context: Context) {
    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    fun exportImage(
        jpegData: ByteArray,
        onCompleted: (Boolean) -> Unit,
    ) {
        Thread({
            val imageName = "$IMAGE_NAME_PREFIX${System.currentTimeMillis()}.jpg"
            val saved = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveImage(jpegData, imageName)
                } else {
                    saveImageBeforeQ(jpegData, imageName)
                }
            } catch (error: IOException) {
                Log.e(LOG_TAG, "Image export failed", error)
                false
            }
            mainHandler.post { onCompleted(saved) }
        }, EXPORT_THREAD_NAME).start()
    }

    private fun saveImage(jpegData: ByteArray, imageName: String): Boolean {
        val resolver = applicationContext.contentResolver
        val imageValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, imageName)
            put(MediaStore.Images.Media.MIME_TYPE, IMAGE_MIME_TYPE)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val imageUri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageValues,
        ) ?: return false

        return try {
            val output = resolver.openOutputStream(imageUri)
            if (output == null) {
                resolver.delete(imageUri, null, null)
                return false
            }
            output.use { it.write(jpegData) }

            imageValues.clear()
            imageValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(imageUri, imageValues, null, null)
            true
        } catch (error: IOException) {
            resolver.delete(imageUri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun saveImageBeforeQ(jpegData: ByteArray, imageName: String): Boolean {
        val albumRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val albumDirectory = File(albumRoot, ALBUM_NAME)
        if (!albumDirectory.exists() && !albumDirectory.mkdirs()) return false

        val imageFile = File(albumDirectory, imageName)
        imageFile.outputStream().use { it.write(jpegData) }
        MediaScannerConnection.scanFile(
            applicationContext,
            arrayOf(imageFile.absolutePath),
            arrayOf(IMAGE_MIME_TYPE),
            null,
        )
        return true
    }

    private companion object {
        const val EXPORT_THREAD_NAME = "PELab-ImageExport"
        const val IMAGE_NAME_PREFIX = "PELab_"
        const val IMAGE_MIME_TYPE = "image/jpeg"
        const val ALBUM_NAME = "PELab"
        const val LOG_TAG = "PELabImageExport"
    }
}
