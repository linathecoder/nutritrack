package com.nutritrack.android.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.nutritrack.android.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Semi-circular gauge. [score] is 0..100. (Display only: the score itself is hard-coded for now.) */
class HealthGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val strokeW = 9 * density
    private val nodeR = 5 * density

    private val green = ContextCompat.getColor(context, R.color.health_green)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeW
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.slate_100)
    }
    private val fillPaint = Paint(trackPaint).apply { color = green }
    private val nodeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.white) }
    private val nodeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = green
    }
    private val oval = RectF()

    var score: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val inset = strokeW / 2 + nodeR
        val radius = minOf((width - 2 * inset) / 2, height - inset - nodeR)
        val cx = width / 2f
        val cy = height - inset
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        canvas.drawArc(oval, 180f, 180f, false, trackPaint)
        val sweep = 180f * score / 100f
        if (score > 0) canvas.drawArc(oval, 180f, sweep, false, fillPaint)

        // Marker at the end of the filled arc.
        val angle = (180.0 + sweep) * PI / 180.0
        val nx = cx + radius * cos(angle).toFloat()
        val ny = cy + radius * sin(angle).toFloat()
        canvas.drawCircle(nx, ny, nodeR - nodeStroke.strokeWidth / 2, nodeFill)
        canvas.drawCircle(nx, ny, nodeR - nodeStroke.strokeWidth / 2, nodeStroke)
    }
}
