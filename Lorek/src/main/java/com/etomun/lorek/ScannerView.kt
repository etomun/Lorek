package com.etomun.lorek

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
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
    private val frameOffsetY = -(64.dp())
    private val frameStrokeW = 4.dp()
    private val scannerSpeed = 1500L
    private var frameCornerRad = 40f

    private var lineHeight = 0
    private var topAnim = 0
    private var isReversing = false

    private val lineRect: Rect by lazy { Rect() }
    private val _frameRect: Rect by lazy { Rect() }
    val frameRect get() = _frameRect

    private var scanMode: ScanMode
    private var lineDown: Bitmap
    private var lineUp: Bitmap
    private var paint: Paint
    private lateinit var valueAnimator: ValueAnimator

    init {
        val attr = context.obtainStyledAttributes(attrs, R.styleable.ScannerView)
        scanMode = ScanMode.getParams(attr.getInt(R.styleable.ScannerView_scanMode, 0))
        attr.recycle()

        frameCornerRad = if (scanMode is ScanMode.BarCode) 20f else 40f
        lineDown = BitmapFactory.decodeResource(resources, R.drawable.line_down)
        lineUp = BitmapFactory.decodeResource(resources, R.drawable.line_up)
        lineHeight = lineDown.height
        paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#99000000")
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val yOffset = if (scanMode is ScanMode.BarCode) frameH / 4 else frameH / 2
        _frameRect.set(
            (width / 2) - (frameW / 2) + frameOffsetX,
            (height / 2) - yOffset + frameOffsetY,
            (width / 2) + (frameW / 2) + frameOffsetX,
            (height / 2) + yOffset + frameOffsetY
        )

        drawFrame(canvas, _frameRect)
        drawShadow(canvas, _frameRect)
        drawLine(canvas, _frameRect)
        initAnim()
        startAnim()
    }

    private fun initAnim() {
        if (!::valueAnimator.isInitialized) {
            valueAnimator =
                ValueAnimator.ofInt(_frameRect.top - lineHeight, _frameRect.bottom).apply {
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    duration = scannerSpeed
                    interpolator = LinearInterpolator()
                    addUpdateListener { animation: ValueAnimator ->
                        (animation.animatedValue as Int).let {
                            isReversing = topAnim >= it
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
            moveTo(right, top + 120f)
            lineTo(right, top)
            lineTo(left, top)
            lineTo(left, top + 120f)

            moveTo(left, bottom - 120f)
            lineTo(left, bottom)
            lineTo(right, bottom)
            lineTo(right, bottom - 120f)
        }

    fun setMode(mode: ScanMode) {
        this.scanMode = mode
        postInvalidate()
    }

    private fun startAnim() {
        if (::valueAnimator.isInitialized && !valueAnimator.isRunning) {
            valueAnimator.start()
        }
    }

    fun resumeAnim() {
        if (::valueAnimator.isInitialized) {
            valueAnimator.resume()
        }
    }


    fun pauseAnim() {
        if (::valueAnimator.isInitialized) {
            valueAnimator.pause()
        }
    }

    fun stopAnim() {
        if (::valueAnimator.isInitialized) {
            valueAnimator.cancel()
        }
    }
}