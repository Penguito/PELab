package com.penguito.effectlab.render.ui.capture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.penguito.effectlab.render.ui.R
import kotlin.math.abs

class CaptureGestureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private var scaleListener: ((Float) -> Unit)? = null
    private var focusListener: ((Float, Float) -> Unit)? = null
    private var exposureListener: ((Float) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val focusRingRadius = 28F * density
    private val exposureBarHeight = 104F * density
    private val exposureBarGap = 20F * density
    private val exposureTouchRadius = 20F * density
    private val edgePadding = 16F * density
    private val exposureIconRadius = 10F * density
    private val exposureIconGap = 4F * density
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.background_second)
        style = Paint.Style.STROKE
        strokeWidth = 1.5F * density
    }
    private val exposureIcon = context.getDrawable(R.drawable.icon_editor_brightness)

    private var focusX = 0F
    private var focusY = 0F
    private var exposureBarX = 0F
    private var exposureBarTop = 0F
    private var exposureBarBottom = 0F
    private var exposureCompensation = 0F
    private var exposureDragStartY = 0F
    private var exposureDragStartValue = 0F
    private var isControlVisible = false
    private var isExposureDragging = false
    private var isExposureBarVisible = false

    private val hideControlRunnable = Runnable(::hideControls)

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleListener?.invoke(detector.scaleFactor)
                return true
            }
        },
    )
    private val tapGestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onSingleTapUp(event: MotionEvent): Boolean {
                if (width == 0 || height == 0) return false

                performClick()
                if (exposureCompensation != 0F) {
                    exposureCompensation = 0F
                    exposureListener?.invoke(exposureCompensation)
                }
                showControl(event.x, event.y)
                focusListener?.invoke(
                    event.x / width,
                    event.y / height,
                )
                return true
            }
        },
    )

    fun setOnScaleListener(listener: (Float) -> Unit) {
        scaleListener = listener
    }

    fun setOnFocusListener(listener: (Float, Float) -> Unit) {
        focusListener = listener
    }

    fun setOnExposureChangedListener(listener: (Float) -> Unit) {
        exposureListener = listener
    }

    fun reset() {
        exposureCompensation = 0F
        hideControls()
    }

    fun hideControls() {
        isControlVisible = false
        isExposureDragging = false
        isExposureBarVisible = false
        removeCallbacks(hideControlRunnable)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && isControlVisible) {
            removeCallbacks(hideControlRunnable)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isExposureControlTouched(event.x, event.y)) {
                    isExposureDragging = true
                    exposureDragStartY = event.y
                    exposureDragStartValue = exposureCompensation
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isExposureDragging) {
                    isExposureBarVisible = true
                    updateExposure(event.y)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isExposureDragging) {
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        updateExposure(event.y)
                    }
                    isExposureDragging = false
                    isExposureBarVisible = false
                    invalidate()
                    scheduleControlHide()
                    return true
                }
            }
        }

        scaleGestureDetector.onTouchEvent(event)
        tapGestureDetector.onTouchEvent(event)
        if (isControlVisible && (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)) {
            scheduleControlHide()
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isControlVisible) return

        canvas.drawCircle(focusX, focusY, focusRingRadius, strokePaint)
        if (isExposureBarVisible) drawExposureBar(canvas)
        drawExposureSun(canvas)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hideControlRunnable)
        super.onDetachedFromWindow()
    }

    private fun showControl(x: Float, y: Float) {
        focusX = x
        focusY = y

        val preferredBarX = focusX + focusRingRadius + exposureBarGap
        exposureBarX = if (preferredBarX + exposureTouchRadius <= width) {
            preferredBarX
        } else {
            focusX - focusRingRadius - exposureBarGap
        }.coerceIn(edgePadding, width - edgePadding)

        val halfBarHeight = exposureBarHeight / 2F
        val barCenterY = focusY.coerceIn(
            edgePadding + halfBarHeight,
            height - edgePadding - halfBarHeight,
        )
        exposureBarTop = barCenterY - halfBarHeight
        exposureBarBottom = barCenterY + halfBarHeight
        isControlVisible = true
        invalidate()
        scheduleControlHide()
    }

    private fun isExposureControlTouched(x: Float, y: Float): Boolean {
        return isControlVisible &&
            abs(x - exposureBarX) <= exposureTouchRadius &&
            abs(y - getExposureIconY()) <= exposureTouchRadius
    }

    private fun updateExposure(y: Float) {
        exposureCompensation = (
            exposureDragStartValue + (exposureDragStartY - y) / (exposureBarHeight / 2F)
        ).coerceIn(-1F, 1F)
        exposureListener?.invoke(exposureCompensation)
        invalidate()
    }

    private fun drawExposureBar(canvas: Canvas) {
        val sunY = getExposureIconY()
        val upperEnd = (sunY - exposureIconRadius - exposureIconGap).coerceAtLeast(exposureBarTop)
        val lowerStart = (sunY + exposureIconRadius + exposureIconGap).coerceAtMost(exposureBarBottom)
        canvas.drawLine(exposureBarX, exposureBarTop, exposureBarX, upperEnd, strokePaint)
        canvas.drawLine(exposureBarX, lowerStart, exposureBarX, exposureBarBottom, strokePaint)
    }

    private fun drawExposureSun(canvas: Canvas) {
        val sunY = getExposureIconY()
        exposureIcon?.setBounds(
            (exposureBarX - exposureIconRadius).toInt(),
            (sunY - exposureIconRadius).toInt(),
            (exposureBarX + exposureIconRadius).toInt(),
            (sunY + exposureIconRadius).toInt(),
        )
        exposureIcon?.draw(canvas)
    }

    private fun getExposureIconY(): Float {
        val barCenterY = (exposureBarTop + exposureBarBottom) / 2F
        return barCenterY - exposureCompensation * exposureBarHeight / 2F
    }

    private fun scheduleControlHide() {
        removeCallbacks(hideControlRunnable)
        postDelayed(hideControlRunnable, CONTROL_HIDE_DELAY_MILLIS)
    }

    private companion object {
        const val CONTROL_HIDE_DELAY_MILLIS = 3_000L
    }
}
