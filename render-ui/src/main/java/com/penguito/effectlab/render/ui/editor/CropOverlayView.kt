package com.penguito.effectlab.render.ui.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.penguito.effectlab.render.ui.R
import kotlin.math.abs

class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val cornerTouchRadius = 28F * density
    private val minimumCropSize = 64F * density
    private val maskPaint = Paint().apply {
        color = Color.BLACK
        alpha = 220
    }
    private val maskPath = Path()
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
    private var displayRect = RectF()
    private var cropRect = RectF()
    private var dragMode = DragMode.NONE
    private var touchStartX = 0F
    private var touchStartY = 0F
    private var dragStartRect = RectF()
    private var isEditing = false
    private var cropRectChangedListener: ((RectF) -> Unit)? = null

    fun setCropRects(displayRect: RectF, cropRect: RectF) {
        this.displayRect = RectF(displayRect)
        this.cropRect = RectF(cropRect)
        updateSystemGestureExclusionRects()
        invalidate()
    }

    fun setOnCropRectChangedListener(listener: (RectF) -> Unit) {
        cropRectChangedListener = listener
    }

    fun setEditing(editing: Boolean) {
        isEditing = editing
        updateSystemGestureExclusionRects()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cropRect.isEmpty) return

        drawMask(canvas)
        if (!isEditing) return

        drawGrid(canvas)
        canvas.drawRect(cropRect, borderPaint)
        drawCorners(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEditing) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragMode = findDragMode(event.x, event.y)
                if (dragMode == DragMode.NONE) return false

                touchStartX = event.x
                touchStartY = event.y
                dragStartRect = RectF(cropRect)
                parent.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                when (dragMode) {
                    DragMode.MOVE -> moveCropRect(event.x, event.y)
                    DragMode.NONE -> return false
                    else -> resizeCropRect(event.x, event.y)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragMode == DragMode.NONE) return false
                dragMode = DragMode.NONE
                parent.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateSystemGestureExclusionRects()
    }

    private fun drawMask(canvas: Canvas) {
        maskPath.reset()
        maskPath.fillType = Path.FillType.EVEN_ODD
        maskPath.addRect(0F, 0F, width.toFloat(), height.toFloat(), Path.Direction.CW)
        maskPath.addRect(cropRect, Path.Direction.CW)
        canvas.drawPath(maskPath, maskPaint)
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

    private fun findDragMode(x: Float, y: Float): DragMode {
        return when {
            isNearCorner(x, y, cropRect.left, cropRect.top) -> DragMode.LEFT_TOP
            isNearCorner(x, y, cropRect.right, cropRect.top) -> DragMode.RIGHT_TOP
            isNearCorner(x, y, cropRect.left, cropRect.bottom) -> DragMode.LEFT_BOTTOM
            isNearCorner(x, y, cropRect.right, cropRect.bottom) -> DragMode.RIGHT_BOTTOM
            cropRect.contains(x, y) -> DragMode.MOVE
            else -> DragMode.NONE
        }
    }

    private fun isNearCorner(x: Float, y: Float, cornerX: Float, cornerY: Float): Boolean {
        return abs(x - cornerX) <= cornerTouchRadius && abs(y - cornerY) <= cornerTouchRadius
    }

    private fun moveCropRect(x: Float, y: Float) {
        val left = (dragStartRect.left + x - touchStartX)
            .coerceIn(displayRect.left, displayRect.right - dragStartRect.width())
        val top = (dragStartRect.top + y - touchStartY)
            .coerceIn(displayRect.top, displayRect.bottom - dragStartRect.height())
        cropRect = RectF(left, top, left + dragStartRect.width(), top + dragStartRect.height())
        notifyCropRectChanged()
    }

    private fun resizeCropRect(x: Float, y: Float) {
        val ratio = dragStartRect.width() / dragStartRect.height()

        // calculate changes horizontal and vertical
        val horizontalDirection = if (dragMode.isLeft) -1F else 1F
        val verticalDirection = if (dragMode.isTop) -1F else 1F
        val horizontalChange = (x - touchStartX) * horizontalDirection
        val verticalChange = (y - touchStartY) * verticalDirection * ratio
        val requestedWidth = dragStartRect.width() + (horizontalChange + verticalChange) / 2F

        // calculate the new size to the image area
        val anchorX = if (dragMode.isLeft) dragStartRect.right else dragStartRect.left
        val anchorY = if (dragMode.isTop) dragStartRect.bottom else dragStartRect.top
        val availableWidth = if (dragMode.isLeft) {
            anchorX - displayRect.left
        } else {
            displayRect.right - anchorX
        }
        val availableHeight = if (dragMode.isTop) {
            anchorY - displayRect.top
        } else {
            displayRect.bottom - anchorY
        }
        val maximumWidth = minOf(availableWidth, availableHeight * ratio)
        val preferredMinimumWidth = maxOf(minimumCropSize, minimumCropSize * ratio)
        val width = requestedWidth.coerceIn(minOf(preferredMinimumWidth, maximumWidth), maximumWidth)
        val height = width / ratio

        // rebuild the crop
        cropRect = RectF(
            if (dragMode.isLeft) anchorX - width else anchorX,
            if (dragMode.isTop) anchorY - height else anchorY,
            if (dragMode.isLeft) anchorX else anchorX + width,
            if (dragMode.isTop) anchorY else anchorY + height,
        )
        notifyCropRectChanged()
    }

    private fun notifyCropRectChanged() {
        updateSystemGestureExclusionRects()
        invalidate()
        cropRectChangedListener?.invoke(RectF(cropRect))
    }

    private fun updateSystemGestureExclusionRects() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (!isEditing || cropRect.isEmpty) {
            systemGestureExclusionRects = emptyList()
            return
        }

        systemGestureExclusionRects = listOf(
            createCornerTouchRect(cropRect.left, cropRect.top),
            createCornerTouchRect(cropRect.right, cropRect.top),
            createCornerTouchRect(cropRect.left, cropRect.bottom),
            createCornerTouchRect(cropRect.right, cropRect.bottom),
        )
    }

    private fun createCornerTouchRect(x: Float, y: Float): Rect {
        return Rect(
            (x - cornerTouchRadius).toInt().coerceAtLeast(0),
            (y - cornerTouchRadius).toInt().coerceAtLeast(0),
            (x + cornerTouchRadius).toInt().coerceAtMost(width),
            (y + cornerTouchRadius).toInt().coerceAtMost(height),
        )
    }

    private enum class DragMode(
        val isLeft: Boolean = false,
        val isTop: Boolean = false,
    ) {
        NONE,
        MOVE,
        LEFT_TOP(isLeft = true, isTop = true),
        RIGHT_TOP(isTop = true),
        LEFT_BOTTOM(isLeft = true),
        RIGHT_BOTTOM,
    }
}
