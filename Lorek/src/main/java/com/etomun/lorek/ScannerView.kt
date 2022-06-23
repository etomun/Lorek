package com.etomun.lorek

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator

class ScannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {
    private val frameH = 280.dp()
    private val frameW = 280.dp()
    private val frameOffsetX = 0.dp()
    private val frameOffsetY = 0.dp()
    private val frameStrokeW = 3.dp()
    private val scannerSpeed = 1500L
    private var frameCornerRad = 32f

    private var lineHeight = 0
    private var topAnim = 0
    private var isReversing = false

    private val lineRect: Rect by lazy { Rect() }
    private val _frameRect: Rect by lazy { Rect() }
    private var customRect: Rect? = null
    val frameRect get() = customRect ?: _frameRect

    private var scanMode: ScanMode
    private var lineDown: Bitmap
    private var lineUp: Bitmap
    private var paint: Paint
    private var valueAnimator: ValueAnimator? = null

    init {
        val attr = context.obtainStyledAttributes(attrs, R.styleable.ScannerView)
        scanMode = ScanMode.getParams(attr.getInt(R.styleable.ScannerView_scanMode, 0))
        attr.recycle()

        lineDown = BitmapFactory.decodeResource(resources, R.drawable.line_down)
        lineUp = BitmapFactory.decodeResource(resources, R.drawable.line_up)
        lineHeight = lineDown.height
        paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#99000000")
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        frameCornerRad = if (scanMode is ScanMode.BarCode) 24f else 32f
        val yOffset = if (scanMode is ScanMode.BarCode) frameH / 4 else frameH / 2
        _frameRect.set(
            (width / 2) - (frameW / 2) + frameOffsetX,
            (height / 2) - yOffset + frameOffsetY,
            (width / 2) + (frameW / 2) + frameOffsetX,
            (height / 2) + yOffset + frameOffsetY
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawFrame(canvas, frameRect)
        drawShadow(canvas, frameRect)
        drawLine(canvas, frameRect)
        initAnim()
        startAnim()
    }

    /* Reset valueAnimator & animating line properties */
    private fun resetAnim() {
        stopAnim()
        valueAnimator?.removeAllListeners()
        valueAnimator = null
        topAnim = 0
        isReversing = false
    }

    /* Init valueAnimator */
    private fun initAnim() {
        if (valueAnimator == null && frameRect.height() > 0) {
            valueAnimator =
                ValueAnimator.ofInt(frameRect.top - lineHeight, frameRect.bottom).apply {
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    duration = scannerSpeed
                    interpolator = LinearInterpolator()
                    addUpdateListener { animation: ValueAnimator ->
                        (animation.animatedValue as Int).let {
                            isReversing = topAnim >= it
                            Log.e("Anim", "topAnim: $topAnim >= anim: $it ---> $isReversing")
                            topAnim = it
                        }
                        postInvalidate()
                    }
                }
        }
    }

    /* Animated Scan Line */
    private fun drawLine(canvas: Canvas, frame: Rect) {
        var topLine = topAnim
        var bottomLine = topAnim + lineHeight
        val minAnim = frame.top - lineHeight
        val maxAnim = frame.bottom

        if (maxAnim - topAnim <= lineHeight) {
            bottomLine = maxAnim
        }

        if (topAnim - minAnim <= lineHeight) {
            topLine = frame.top
        }

        lineRect.set(frame.left, topLine, frame.right, bottomLine)
        if (isReversing) {
            canvas.drawBitmap(lineUp, null, lineRect, paint)
        } else {
            canvas.drawBitmap(lineDown, null, lineRect, paint)
        }
    }

    /* Viewfinder Dark Surface */
    private fun drawShadow(canvas: Canvas, frame: Rect) {
        val rectF = RectF(
            frame.left.toFloat(),
            frame.top.toFloat(),
            frame.right.toFloat(),
            frame.bottom.toFloat()
        )

        Path().apply {
            addRoundRect(rectF, frameCornerRad, frameCornerRad, Path.Direction.CW)
            fillType = Path.FillType.INVERSE_WINDING
        }.let { canvas.drawPath(it, paint) }
    }

    /* Scan Inner Frame with Strokes */
    private fun drawFrame(canvas: Canvas, frame: Rect) {
        val left = (frame.left + (frameStrokeW / 2)).toFloat()
        val top = (frame.top + (frameStrokeW / 2)).toFloat()
        val right = (frame.right - (frameStrokeW / 2)).toFloat()
        val bottom = (frame.bottom - (frameStrokeW / 2)).toFloat()

        Paint(Paint.DITHER_FLAG).apply {
            color = Color.parseColor("#164396")
            style = Paint.Style.STROKE
            strokeWidth = frameStrokeW.toFloat()
            pathEffect = CornerPathEffect(frameCornerRad)
        }.let { canvas.drawPath(frameStrokes(left, top, right, bottom), it) }
    }

    private fun frameStrokes(left: Float, top: Float, right: Float, bottom: Float) =
        Path().apply {
            val offset = 0.1f * (bottom - top)
            moveTo(right, top + offset)
            lineTo(right, top)
            lineTo(left, top)
            lineTo(left, top + offset)

            moveTo(left, bottom - offset)
            lineTo(left, bottom)
            lineTo(right, bottom)
            lineTo(right, bottom - offset)
        }

    private fun startAnim() {
        valueAnimator?.let { if (!it.isRunning) it.start() }
    }

    fun resumeAnim() {
        valueAnimator?.resume()
    }


    fun pauseAnim() {
        valueAnimator?.pause()
    }

    fun stopAnim() {
        valueAnimator?.cancel()
    }


    fun setMode(mode: ScanMode) {
        this.scanMode = mode
        postInvalidate()
    }

    fun setCustomRect(rect: Rect) {
        val customRect = Rect()

        var rectLeft = rect.left
        var rectRight = rect.right
        var rectTop = rect.top
        var rectBottom = rect.bottom

        if (rect.bottom > bottom) {
            rectTop = bottom - rect.height()
            rectBottom = bottom
        } else if (rect.top < top) {
            rectTop = top
            rectBottom = top + rect.height()
        }

        if (rect.right > right) {
            rectLeft = right - rect.width()
            rectRight = right
        } else if (rect.left < left) {
            rect.left = left
            rectRight = left + rect.width()
        }

        if (scanMode is ScanMode.BarCode) {
            rectTop += rect.height() / 4
            rectBottom -= rect.height() / 4
        }

        customRect.set(
            rectLeft + frameOffsetX,
            rectTop + frameOffsetY,
            rectRight + frameOffsetX,
            rectBottom + frameOffsetY
        )
        this.customRect = customRect

        resetAnim()
    }

}