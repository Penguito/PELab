package com.penguito.effectlab.render.ui.editor

import android.graphics.RectF
import com.penguito.effectlab.render.ui.R

internal enum class CropRatio(
    val labelResId: Int,
    val iconResId: Int,
    private val width: Int,
    private val height: Int,
) {
    ORIGINAL(R.string.editor_crop_original, R.drawable.icon_editor_crop, 0, 0),
    SQUARE(R.string.editor_crop_square, R.drawable.icon_crop_1_1, 1, 1),
    LANDSCAPE_4_3(R.string.editor_crop_4_3, R.drawable.icon_crop_4_3, 4, 3),
    PORTRAIT_3_4(R.string.editor_crop_3_4, R.drawable.icon_crop_3_4, 3, 4),
    LANDSCAPE_16_9(R.string.editor_crop_16_9, R.drawable.icon_crop_16_9, 16, 9),
    PORTRAIT_9_16(R.string.editor_crop_9_16, R.drawable.icon_crop_9_16, 9, 16),
    ;

    fun createFrame(bounds: RectF): RectF {
        if (this == ORIGINAL) return RectF(bounds)
        return fitFrame(bounds, width.toFloat() / height)
    }

    companion object {
        fun fitFrame(bounds: RectF, ratio: Float): RectF {
            val frameWidth = minOf(bounds.width(), bounds.height() * ratio)
            val frameHeight = frameWidth / ratio
            val left = bounds.centerX() - frameWidth / 2F
            val top = bounds.centerY() - frameHeight / 2F
            return RectF(left, top, left + frameWidth, top + frameHeight)
        }
    }
}
