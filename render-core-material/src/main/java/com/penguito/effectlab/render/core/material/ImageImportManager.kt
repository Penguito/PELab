package com.penguito.effectlab.render.core.material

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

class ImageImportManager(context: Context) {
    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    fun importImage(
        imageUri: Uri,
        onCompleted: (String?) -> Unit,
    ) {
        Thread({
            val imagePath = try {
                importToCache(imageUri)
            } catch (error: IOException) {
                Log.e(LOG_TAG, "Image import failed", error)
                null
            }
            mainHandler.post { onCompleted(imagePath) }
        }, IMPORT_THREAD_NAME).start()
    }

    private fun importToCache(imageUri: Uri): String? {
        // read image orientation
        val orientationMatrix = readOrientationMatrix(imageUri)

        // decode and resize image
        var bitmap = loadBitmap(imageUri) ?: return null
        bitmap = resizeBitmap(bitmap)

        // apply image orientation
        bitmap = applyOrientation(bitmap, orientationMatrix)

        // save imported image to cache
        val imageFile = File(applicationContext.cacheDir, IMPORTED_IMAGE_FILE_NAME)
        return try {
            val saved = imageFile.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
            if (saved) imageFile.absolutePath else null
        } finally {
            bitmap.recycle()
        }
    }

    private fun readOrientationMatrix(imageUri: Uri): Matrix? {
        val exif = applicationContext.contentResolver.openInputStream(imageUri)?.use { input ->
            ExifInterface(input)
        } ?: return null
        if (exif.rotationDegrees == 0 && !exif.isFlipped) return null

        return Matrix().apply {
            setRotate(exif.rotationDegrees.toFloat())
            if (exif.isFlipped) {
                preScale(-1F, 1F)
            }
        }
    }

    private fun loadBitmap(imageUri: Uri): Bitmap? {
        // read image size without creating bitmap
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        applicationContext.contentResolver.openInputStream(imageUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        // decode image with sample size
        options.inJustDecodeBounds = false
        options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight)
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        return applicationContext.contentResolver.openInputStream(imageUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        val imageSize = maxOf(width, height)
        var sampleSize = 1
        while (imageSize / (sampleSize * 2) >= MAX_IMAGE_SIZE) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun resizeBitmap(bitmap: Bitmap): Bitmap {
        val imageSize = maxOf(bitmap.width, bitmap.height)
        if (imageSize <= MAX_IMAGE_SIZE) return bitmap

        val scale = MAX_IMAGE_SIZE.toFloat() / imageSize
        val resizedBitmap = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt(),
            (bitmap.height * scale).roundToInt(),
            true,
        )
        bitmap.recycle()
        return resizedBitmap
    }

    private fun applyOrientation(bitmap: Bitmap, matrix: Matrix?): Bitmap {
        if (matrix == null) return bitmap

        val orientedBitmap = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true,
        )
        bitmap.recycle()
        return orientedBitmap
    }

    private companion object {
        const val IMPORT_THREAD_NAME = "PELab-ImageImport"
        const val IMPORTED_IMAGE_FILE_NAME = "imported_image.jpg"
        const val MAX_IMAGE_SIZE = 2048
        const val JPEG_QUALITY = 95
        const val LOG_TAG = "PELabImageImport"
    }
}
