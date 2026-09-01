package com.penguito.effectlab.render.ui

import android.content.Intent

internal data class ImageIntentData(
    val imageSource: ImageSource,
    val imagePath: String,
) {

    fun writeTo(intent: Intent): Intent = intent
        .putExtra(EXTRA_IMAGE_SOURCE, imageSource.value)
        .putExtra(EXTRA_IMAGE_PATH, imagePath)

    companion object {
        private const val EXTRA_IMAGE_SOURCE = "com.penguito.effectlab.extra.IMAGE_SOURCE"
        private const val EXTRA_IMAGE_PATH = "com.penguito.effectlab.extra.IMAGE_PATH"

        fun fromIntent(intent: Intent): ImageIntentData? {
            val imageSource = ImageSource.fromValue(intent.getStringExtra(EXTRA_IMAGE_SOURCE)) ?: return null
            val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
            if (imagePath.isNullOrBlank()) return null

            return ImageIntentData(
                imageSource = imageSource,
                imagePath = imagePath,
            )
        }
    }
}
