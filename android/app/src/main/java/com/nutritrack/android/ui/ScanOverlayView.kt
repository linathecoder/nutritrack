package com.nutritrack.android.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.nutritrack.android.R
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

class ScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val primary = ContextCompat.getColor(context, R.color.primary)

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.scan_dim)
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = 0x4DFFFFFF
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4 * density
        strokeCap = Paint.Cap.ROUND
        color = primary
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val frame = RectF()
    private val cornerPath = Path()
    private val clipPath = Path()
    private val radius = 24 * density
    private val cornerLen = 32 * density
    private var progress = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val frameW = min(288 * density, w - 48 * density)
        val frameH = min(240 * density, frameW)
        val cx = w / 2f
        val cy = h * 0.5f
        frame.set(cx - frameW / 2, cy - frameH / 2, cx + frameW / 2, cy + frameH / 2)
        linePaint.shader = LinearGradient(
            frame.left, 0f, frame.right, 0f,
            intArrayOf(0x0013EC49, primary, 0x0013EC49), null, Shader.TileMode.CLAMP
        )
        clipPath.reset()
        clipPath.addRoundRect(frame, radius, radius, Path.Direction.CW)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRoundRect(frame, radius, radius, clearPaint)
        canvas.drawRoundRect(frame, radius, radius, borderPaint)

        canvas.save()
        canvas.clipPath(clipPath)
        val y = frame.top + progress * frame.height()
        linePaint.alpha = (sin(PI * progress) * 255).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(frame.left, y - 2 * density, frame.right, y + 2 * density, 2 * density, 2 * density, linePaint)
        canvas.restore()

        drawCorners(canvas)
    }

    private fun drawCorners(canvas: Canvas) {
        val inset = cornerPaint.strokeWidth / 2
        val l = frame.left + inset
        val t = frame.top + inset
        val r = frame.right - inset
        val b = frame.bottom - inset
        val rad = radius - inset
        val len = cornerLen

        cornerPath.reset()
        cornerPath.moveTo(l, t + len); cornerPath.lineTo(l, t + rad)
        cornerPath.arcTo(l, t, l + 2 * rad, t + 2 * rad, 180f, 90f, false); cornerPath.lineTo(l + len, t)
        cornerPath.moveTo(r - len, t); cornerPath.lineTo(r - rad, t)
        cornerPath.arcTo(r - 2 * rad, t, r, t + 2 * rad, 270f, 90f, false); cornerPath.lineTo(r, t + len)
        cornerPath.moveTo(r, b - len); cornerPath.lineTo(r, b - rad)
        cornerPath.arcTo(r - 2 * rad, b - 2 * rad, r, b, 0f, 90f, false); cornerPath.lineTo(r - len, b)
        cornerPath.moveTo(l + len, b); cornerPath.lineTo(l + rad, b)
        cornerPath.arcTo(l, b - 2 * rad, l + 2 * rad, b, 90f, 90f, false); cornerPath.lineTo(l, b - len)
        canvas.drawPath(cornerPath, cornerPaint)
    }
}
