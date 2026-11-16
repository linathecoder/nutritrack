package com.nutritrack.android.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.nutritrack.android.R
import kotlin.math.min

class MacroDonutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val strokeW = 11 * density

    private val trackPaint = paint(ContextCompat.getColor(context, R.color.slate_100))
    private val proteinPaint = paint(ContextCompat.getColor(context, R.color.macro_protein))
    private val carbsPaint = paint(ContextCompat.getColor(context, R.color.macro_carbs))
    private val fatPaint = paint(ContextCompat.getColor(context, R.color.macro_fat))
    private val oval = RectF()

    private var protein = 0f
    private var carbs = 0f
    private var fat = 0f

    private fun paint(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeW
        strokeCap = Paint.Cap.ROUND
        color = c
    }

    fun setShares(protein: Float, carbs: Float, fat: Float) {
        this.protein = protein.coerceIn(0f, 1f)
        this.carbs = carbs.coerceIn(0f, 1f)
        this.fat = fat.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        val pad = strokeW / 2
        val left = (width - size) / 2 + pad
        val top = (height - size) / 2 + pad
        oval.set(left, top, left + size - strokeW, top + size - strokeW)

        canvas.drawArc(oval, 0f, 360f, false, trackPaint)

        val gap = 6f
        var start = -90f
        for ((share, p) in listOf(protein to proteinPaint, carbs to carbsPaint, fat to fatPaint)) {
            if (share <= 0f) continue
            val sweep = share * 360f
            val drawn = (sweep - gap).coerceAtLeast(0.5f)
            canvas.drawArc(oval, start + gap / 2, drawn, false, p)
            start += sweep
        }
    }
}
