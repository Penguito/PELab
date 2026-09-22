package com.penguito.effectlab.render.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val maskPaint = Paint().apply { color = Color.BLACK }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.background_second)
        style = Paint.Style.STROKE
        strokeWidth = 1.5F * density
    }
    private val gridPaint = Paint(borderPaint).apply {
        alpha = 160
        strokeWidth = 1F * density
    }
    private val cornerPaint = Paint(borderPaint).apply {
        strokeWidth = 3F * density
    }
    private var cropRect = RectF()

    fun setCropRect(rect: RectF) {
        cropRect = RectF(rect)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cropRect.isEmpty) return

        drawMask(canvas)
        drawGrid(canvas)
        canvas.drawRect(cropRect, borderPaint)
        drawCorners(canvas)
    }

    private fun drawMask(canvas: Canvas) {
        canvas.drawRect(0F, 0F, width.toFloat(), cropRect.top, maskPaint)
        canvas.drawRect(0F, cropRect.top, cropRect.left, cropRect.bottom, maskPaint)
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, maskPaint)
        canvas.drawRect(0F, cropRect.bottom, width.toFloat(), height.toFloat(), maskPaint)
    }

    private fun drawGrid(canvas: Canvas) {
        val thirdWidth = cropRect.width() / 3F
        val thirdHeight = cropRect.height() / 3F
        for (index in 1..2) {
            val x = cropRect.left + thirdWidth * index
            val y = cropRect.top + thirdHeight * index
            canvas.drawLine(x, cropRect.top, x, cropRect.bottom, gridPaint)
            canvas.drawLine(cropRect.left, y, cropRect.right, y, gridPaint)
        }
    }

    private fun drawCorners(canvas: Canvas) {
        val left = cropRect.left
        val top = cropRect.top
        val right = cropRect.right
        val bottom = cropRect.bottom
        val length = minOf(24F * density, cropRect.width() / 3F, cropRect.height() / 3F)

        canvas.drawLine(left, top, left + length, top, cornerPaint)
        canvas.drawLine(left, top, left, top + length, cornerPaint)
        canvas.drawLine(right, top, right - length, top, cornerPaint)
        canvas.drawLine(right, top, right, top + length, cornerPaint)
        canvas.drawLine(left, bottom, left + length, bottom, cornerPaint)
        canvas.drawLine(left, bottom, left, bottom - length, cornerPaint)
        canvas.drawLine(right, bottom, right - length, bottom, cornerPaint)
        canvas.drawLine(right, bottom, right, bottom - length, cornerPaint)
    }
}
