package com.nutritrack.android.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.nutritrack.android.data.local.WeightEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Minimal line chart of the weight history with an optional dashed target line. */
class WeightChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var points: List<Pair<Float, Double>> = emptyList()
    private var target: Double? = null
    private var firstLabel = ""
    private var lastLabel = ""

    private val density = resources.displayMetrics.density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0B7A28")
        strokeWidth = 3f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0B7A28")
        style = Paint.Style.FILL
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D97706")
        strokeWidth = 1.5f * density
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 6f * density), 0f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#64748B")
        textSize = 11f * density
    }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#E2E8F0")
        strokeWidth = 1f * density
    }

    fun setData(entries: List<WeightEntity>, targetKg: Double?) {
        val sorted = entries.sortedBy { it.date }
        target = targetKg
        if (sorted.isEmpty()) {
            points = emptyList()
        } else {
            val start = runCatching { LocalDate.parse(sorted.first().date) }.getOrNull()
            val span = runCatching {
                ChronoUnit.DAYS.between(start, LocalDate.parse(sorted.last().date)).coerceAtLeast(1)
            }.getOrDefault(1L)
            points = sorted.mapIndexed { index, e ->
                val x = if (sorted.size == 1) 0.5f else {
                    val day = runCatching { ChronoUnit.DAYS.between(start, LocalDate.parse(e.date)).toFloat() }
                        .getOrDefault(index.toFloat())
                    day / span.toFloat()
                }
                x to e.weightKg
            }
            firstLabel = sorted.first().date
            lastLabel = sorted.last().date
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val padL = 16f * density
        val padR = 16f * density
        val padT = 20f * density
        val padB = 28f * density
        val w = width - padL - padR
        val h = height - padT - padB

        if (points.isEmpty()) {
            val msg = "No data yet"
            canvas.drawText(msg, (width - textPaint.measureText(msg)) / 2f, height / 2f, textPaint)
            return
        }

        val values = points.map { it.second } + listOfNotNull(target)
        var min = values.min()
        var max = values.max()
        if (max - min < 1.0) {
            min -= 0.5
            max += 0.5
        }
        val margin = (max - min) * 0.15
        min -= margin
        max += margin

        fun yOf(v: Double) = padT + h - ((v - min) / (max - min)).toFloat() * h
        fun xOf(f: Float) = padL + f * w

        for (i in 0..2) {
            val y = padT + h * i / 2f
            canvas.drawLine(padL, y, padL + w, y, gridPaint)
        }
        canvas.drawText(String.format(Locale.US, "%.1f", max), padL, padT - 6f * density, textPaint)
        canvas.drawText(String.format(Locale.US, "%.1f", min), padL, padT + h + 14f * density, textPaint)

        target?.let {
            val y = yOf(it)
            canvas.drawLine(padL, y, padL + w, y, targetPaint)
            canvas.drawText("Goal ${String.format(Locale.US, "%.1f", it)}", padL + w - 70f * density, y - 5f * density, textPaint)
        }

        val path = Path()
        points.forEachIndexed { i, p ->
            val x = xOf(p.first)
            val y = yOf(p.second)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
        points.forEach { canvas.drawCircle(xOf(it.first), yOf(it.second), 4f * density, dotPaint) }

        canvas.drawText(firstLabel, padL, height - 8f * density, textPaint)
        canvas.drawText(lastLabel, padL + w - textPaint.measureText(lastLabel), height - 8f * density, textPaint)
    }
}
