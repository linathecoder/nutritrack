package com.nutritrack.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nutritrack.android.R
import com.nutritrack.android.data.food.HealthResult
import com.nutritrack.android.data.food.HealthTier
import com.nutritrack.android.data.food.Insight
import com.nutritrack.android.databinding.ItemInsightBinding
import kotlin.math.roundToInt

class HealthCardRenderer(
    private val context: Context,
    private val gauge: HealthGaugeView,
    private val tvScore: TextView,
    private val tvRating: TextView,
    private val tvExplanation: TextView,
    private val bars: Map<HealthTier, View>,
    private val labels: Map<HealthTier, TextView>
) {
    private fun color(res: Int) = ContextCompat.getColor(context, res)
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()

    fun render(h: HealthResult) {
        gauge.score = h.score
        tvScore.text = if (h.hasScore) h.score.toString() else "—"
        tvRating.text = h.label
        tvExplanation.text = h.explanation

        val (accent, bg, border) = when (h.tier) {
            HealthTier.EXCELLENT -> Triple(color(R.color.health_green), color(R.color.health_green_bg), color(R.color.health_green_border))
            HealthTier.GOOD -> Triple(Color.parseColor("#65A30D"), Color.parseColor("#F7FEE7"), Color.parseColor("#D9F99D"))
            HealthTier.MODERATE -> Triple(color(R.color.amber_text), color(R.color.amber_bg), color(R.color.amber_border))
            HealthTier.POOR -> Triple(color(R.color.error_text), color(R.color.error_bg), Color.parseColor("#FECACA"))
            HealthTier.NONE -> Triple(color(R.color.text_slate_400), color(R.color.slate_100), color(R.color.slate_200))
        }
        gauge.accentColor = accent
        tvRating.setTextColor(accent)
        (tvRating.background.mutate() as? GradientDrawable)?.let {
            it.setColor(bg)
            it.setStroke(dp(1), border)
        }

        styleTier(HealthTier.POOR, "#DC2626", 0x33, h.tier)
        styleTier(HealthTier.MODERATE, "#D97706", 0x40, h.tier)
        styleTier(HealthTier.GOOD, "#65A30D", 0x4D, h.tier)
        styleTier(HealthTier.EXCELLENT, "#10B981", 0x4D, h.tier)
    }

    private fun styleTier(tier: HealthTier, base: String, fadedAlpha: Int, current: HealthTier) {
        val active = tier == current
        val c = Color.parseColor(base)
        bars[tier]?.backgroundTintList = ColorStateList.valueOf(if (active) c else (c and 0x00FFFFFF) or (fadedAlpha shl 24))
        labels[tier]?.setTextColor(if (active) c else color(R.color.text_slate_400))
    }
}

object InsightRenderer {
    fun add(inflater: LayoutInflater, container: LinearLayout, insight: Insight, isGood: Boolean) {
        val ctx = container.context
        val item = ItemInsightBinding.inflate(inflater, container, false)
        item.insightRoot.setBackgroundResource(if (isGood) R.drawable.bg_insight_good else R.drawable.bg_insight_watch)
        when {
            insight.neutral -> {
                item.tvInsightIcon.text = "i"
                item.tvInsightIcon.setTextColor(ContextCompat.getColor(ctx, R.color.text_slate_500))
            }
            isGood -> {
                item.tvInsightIcon.text = "✓"
                item.tvInsightIcon.setTextColor(ContextCompat.getColor(ctx, R.color.health_green))
            }
            else -> {
                item.tvInsightIcon.text = "!"
                item.tvInsightIcon.setTextColor(ContextCompat.getColor(ctx, R.color.amber_text))
            }
        }
        item.tvInsightTitle.text = insight.title
        item.tvInsightText.text = insight.text
        container.addView(item.root)
    }
}
