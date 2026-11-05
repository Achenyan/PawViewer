package com.pawviewer.ui.widget

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/**
 * 阅读器单页：先按 FIT_CENTER 铺满，再支持双指缩放 / 双击放大 / 放大后拖动。
 * 缩放基准是「适配后的比例」，所以任何尺寸的图都不会被拉成 1:1 顶在左上角。
 */
class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageView(context, attrs) {

    private val matrix = Matrix()
    private var baseScale = 1f
    private var currentScale = 1f
    private var lastX = 0f
    private var lastY = 0f

    var onTap: ((Float) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val target = (currentScale * detector.scaleFactor)
                    .coerceIn(baseScale, baseScale * MAX_FACTOR)
                val applied = target / currentScale
                currentScale = target
                matrix.postScale(applied, applied, detector.focusX, detector.focusY)
                clamp()
                imageMatrix = matrix
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentScale > baseScale * 1.05f) {
                    applyFit()
                } else {
                    val target = baseScale * 2.5f
                    val applied = target / currentScale
                    currentScale = target
                    matrix.postScale(applied, applied, e.x, e.y)
                    clamp()
                    imageMatrix = matrix
                }
                return true
            }

            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                onTap?.invoke(e.x)
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (currentScale <= baseScale * 1.01f) return false
                matrix.postTranslate(-distanceX, -distanceY)
                clamp()
                imageMatrix = matrix
                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        applyFit()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyFit()
    }

    fun resetZoom() = applyFit()

    private fun applyFit() {
        val d = drawable ?: return
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val drawW = d.intrinsicWidth.toFloat()
        val drawH = d.intrinsicHeight.toFloat()
        if (viewW <= 0f || viewH <= 0f || drawW <= 0f || drawH <= 0f) return

        baseScale = min(viewW / drawW, viewH / drawH)
        currentScale = baseScale
        matrix.reset()
        matrix.setScale(baseScale, baseScale)
        matrix.postTranslate((viewW - drawW * baseScale) / 2f, (viewH - drawH * baseScale) / 2f)
        imageMatrix = matrix
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (currentScale > baseScale * 1.01f) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (currentScale > baseScale * 1.01f && !scaleDetector.isInProgress) {
                    matrix.postTranslate(event.x - lastX, event.y - lastY)
                    clamp()
                    imageMatrix = matrix
                    lastX = event.x
                    lastY = event.y
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun clamp() {
        val drawable = drawable ?: return
        if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) return

        val values = FloatArray(9)
        matrix.getValues(values)
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]
        val scaleX = values[Matrix.MSCALE_X]
        val scaleY = values[Matrix.MSCALE_Y]

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val contentW = drawable.intrinsicWidth * scaleX
        val contentH = drawable.intrinsicHeight * scaleY

        val fixedX = if (contentW <= viewW) {
            (viewW - contentW) / 2f
        } else {
            transX.coerceIn(viewW - contentW, 0f)
        }
        val fixedY = if (contentH <= viewH) {
            (viewH - contentH) / 2f
        } else {
            transY.coerceIn(viewH - contentH, 0f)
        }
        if (fixedX != transX || fixedY != transY) {
            matrix.postTranslate(fixedX - transX, fixedY - transY)
        }
    }

    companion object {
        private const val MAX_FACTOR = 6f
    }
}
