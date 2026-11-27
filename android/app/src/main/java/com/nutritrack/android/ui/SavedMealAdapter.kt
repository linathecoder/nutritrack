package com.nutritrack.android.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.nutritrack.android.R
import com.nutritrack.android.data.model.SavedMealDto
import com.nutritrack.android.databinding.ItemSavedMealBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** What one card of the My Meals list shows. */
data class SavedMealUi(
    val meal: SavedMealDto,
    val ingredientsText: String,
    val score: Int?,
    val favorite: Boolean
)

class SavedMealAdapter(
    private val onLog: (SavedMealUi) -> Unit,
    private val onMore: (SavedMealUi, View) -> Unit,
    private val onFavorite: (SavedMealUi) -> Unit
) : ListAdapter<SavedMealUi, SavedMealAdapter.Holder>(Diff) {

    class Holder(val binding: ItemSavedMealBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemSavedMealBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val b = holder.binding
        val ctx = b.root.context
        val meal = item.meal
        val totals = meal.totals

        b.tvMealName.text = meal.name?.takeIf { it.isNotBlank() } ?: "Meal"
        b.tvIngredients.text = item.ingredientsText.ifBlank { "No ingredient details" }

        // Health score badge: green from 75, amber below (same threshold as the design)
        val score = item.score
        if (score == null) {
            b.tvScoreBadge.visibility = View.GONE
        } else {
            b.tvScoreBadge.visibility = View.VISIBLE
            b.tvScoreBadge.text = "Score $score"
            val watch = score < 75
            b.tvScoreBadge.setBackgroundResource(if (watch) R.drawable.bg_pill_amber else R.drawable.bg_pill_green)
            b.tvScoreBadge.setTextColor(
                ContextCompat.getColor(ctx, if (watch) R.color.amber_text else R.color.nav_active)
            )
        }

        b.tvKcalPill.text = "${(totals?.calories ?: 0.0).roundToInt()} kcal"
        b.tvProteinPill.text = "${(totals?.proteinG ?: 0.0).roundToInt()}g P"
        b.tvCarbsPill.text = "${(totals?.carbsG ?: 0.0).roundToInt()}g C"
        b.tvFatPill.text = "${(totals?.fatG ?: 0.0).roundToInt()}g F"

        val weight = meal.quantityG.roundToInt()
        val last = lastLoggedLabel(meal.lastLoggedAt)
        b.tvMealMeta.text = if (last != null) "$weight g \u2022 Last logged $last" else "$weight g \u2022 Saved meal"

        val url = meal.imageUrl
        if (!url.isNullOrBlank()) {
            b.ivThumb.setPadding(0, 0, 0, 0)
            b.ivThumb.load(url) {
                placeholder(R.drawable.ic_meal_bowl)
                error(R.drawable.ic_meal_bowl)
            }
        } else {
            val pad = (14 * ctx.resources.displayMetrics.density).roundToInt()
            b.ivThumb.setPadding(pad, pad, pad, pad)
            b.ivThumb.setImageResource(R.drawable.ic_meal_bowl)
        }

        b.btnFavorite.setImageResource(if (item.favorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
        b.btnFavorite.setOnClickListener { onFavorite(item) }
        b.btnLog.setOnClickListener { onLog(item) }
        b.btnMore.setOnClickListener { onMore(item, it) }
        b.root.setOnClickListener { onLog(item) }
    }

    private fun lastLoggedLabel(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        return runCatching {
            Instant.parse(iso).atZone(ZoneId.of("Africa/Tunis")).toLocalDate().format(LABEL_FORMAT)
        }.getOrNull()
    }

    private object Diff : DiffUtil.ItemCallback<SavedMealUi>() {
        override fun areItemsTheSame(a: SavedMealUi, b: SavedMealUi) = a.meal.barcode == b.meal.barcode
        override fun areContentsTheSame(a: SavedMealUi, b: SavedMealUi) = a == b
    }

    companion object {
        private val LABEL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    }
}
