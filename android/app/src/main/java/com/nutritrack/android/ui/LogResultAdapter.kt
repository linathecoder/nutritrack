package com.nutritrack.android.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.nutritrack.android.R
import com.nutritrack.android.data.food.FoodItem
import com.nutritrack.android.data.food.FoodRepository
import com.nutritrack.android.data.model.SavedMealDto
import com.nutritrack.android.data.openfoodfacts.ScannedProduct
import com.nutritrack.android.databinding.ItemLogHeaderBinding
import com.nutritrack.android.databinding.ItemLogResultBinding
import java.util.Locale
import kotlin.math.roundToInt

/** One line of the Log Meal results list. */
sealed interface LogRow {
    val key: String

    data class Header(val title: String) : LogRow {
        override val key get() = "h:$title"
    }

    /** Something the user already logged: own meal, scanned product or food. */
    data class Past(val meal: SavedMealDto, val ingredients: String) : LogRow {
        override val key get() = "p:${meal.barcode}"
    }

    /** Food from the app's built-in list. */
    data class Food(val food: FoodItem) : LogRow {
        override val key get() = "f:${food.id}"
    }

    /** Packaged product found in Open Food Facts by name. */
    data class Packaged(val product: ScannedProduct) : LogRow {
        override val key get() = "o:${product.barcode}"
    }
}

class LogResultAdapter(
    private val onPast: (SavedMealDto) -> Unit,
    private val onFood: (FoodItem) -> Unit,
    private val onPackaged: (ScannedProduct) -> Unit
) : ListAdapter<LogRow, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int) =
        if (getItem(position) is LogRow.Header) TYPE_HEADER else TYPE_ROW

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemLogHeaderBinding.inflate(inflater, parent, false))
        } else {
            RowHolder(ItemLogResultBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is LogRow.Header -> (holder as HeaderHolder).binding.tvLogHeader.text = row.title
            is LogRow.Past -> bindPast((holder as RowHolder).binding, row)
            is LogRow.Food -> bindFood((holder as RowHolder).binding, row.food)
            is LogRow.Packaged -> bindPackaged((holder as RowHolder).binding, row.product)
        }
    }

    // ---- Past: own meals, scanned products, foods logged before -------------------------

    private fun bindPast(b: ItemLogResultBinding, row: LogRow.Past) {
        val meal = row.meal
        val barcode = meal.barcode.orEmpty()
        val ownMeal = barcode.startsWith("meal-")
        val scanned = barcode.isNotEmpty() && barcode.all { it.isDigit() }

        showThumb(b, meal.imageUrl)
        b.tvResultName.text = meal.name?.takeIf { it.isNotBlank() } ?: "Meal"
        setTag(b, when {
            ownMeal -> "MY MEAL"
            scanned -> "SCANNED"
            else -> "LOGGED BEFORE"
        }, ownMeal)
        setIngredients(b, row.ingredients)
        val n = meal.per100g
        b.tvResultMacros.text = macros(n?.calories, n?.proteinG, n?.carbsG, n?.fatG) + " per 100 g"
        b.btnResultAction.text = "+ Add"
        b.btnResultAction.setBackgroundResource(R.drawable.bg_btn_add)
        b.btnResultAction.setOnClickListener { onPast(meal) }
        b.root.setOnClickListener { onPast(meal) }
    }

    // ---- Built-in foods ---------------------------------------------------------------

    private fun bindFood(b: ItemLogResultBinding, food: FoodItem) {
        b.ivResultThumb.visibility = View.GONE
        b.tvResultEmoji.visibility = View.VISIBLE
        b.tvResultEmoji.text = food.icon
        b.tvResultEmoji.backgroundTintList = ColorStateList.valueOf(FoodRepository.tintFor(food.category))
        b.tvResultName.text = food.name
        b.tvResultTag.visibility = View.GONE
        setIngredients(b, food.category)
        val n = food.per100g
        b.tvResultMacros.text = macros(n.kcal, n.protein, n.carbs, n.fat) + " per 100 g"
        b.btnResultAction.text = "+ Add"
        b.btnResultAction.setBackgroundResource(R.drawable.bg_btn_add)
        b.btnResultAction.setOnClickListener { onFood(food) }
        b.root.setOnClickListener { onFood(food) }
    }

    // ---- Open Food Facts product found by name ---------------------------------------

    private fun bindPackaged(b: ItemLogResultBinding, p: ScannedProduct) {
        showThumb(b, p.imageUrl)
        b.tvResultName.text = listOfNotNull(p.name, p.brand?.let { "($it)" }).joinToString(" ")
        setTag(b, "OPEN FOOD FACTS", false)
        setIngredients(b, p.ingredients.joinToString(", "))
        val n = p.per100g
        b.tvResultMacros.text = macros(n.kcal, n.proteinG, n.carbsG, n.fatG) + " per 100 g"
        b.btnResultAction.text = "View"
        b.btnResultAction.setBackgroundResource(R.drawable.bg_btn_more)
        b.btnResultAction.setOnClickListener { onPackaged(p) }
        b.root.setOnClickListener { onPackaged(p) }
    }

    // ---- helpers ----------------------------------------------------------------------

    private fun showThumb(b: ItemLogResultBinding, url: String?) {
        b.tvResultEmoji.visibility = View.GONE
        b.ivResultThumb.visibility = View.VISIBLE
        if (!url.isNullOrBlank()) {
            b.ivResultThumb.setPadding(0, 0, 0, 0)
            b.ivResultThumb.load(url) {
                placeholder(R.drawable.ic_meal_bowl)
                error(R.drawable.ic_meal_bowl)
            }
        } else {
            val pad = (7 * b.root.resources.displayMetrics.density).roundToInt()
            b.ivResultThumb.setPadding(pad, pad, pad, pad)
            b.ivResultThumb.setImageResource(R.drawable.ic_meal_bowl)
        }
    }

    private fun setTag(b: ItemLogResultBinding, text: String, highlight: Boolean) {
        b.tvResultTag.visibility = View.VISIBLE
        b.tvResultTag.text = text
        b.tvResultTag.setBackgroundResource(if (highlight) R.drawable.bg_pill_green else R.drawable.bg_pill_slate)
    }

    /** "Made with: ..." line, hidden when there is nothing to say. */
    private fun setIngredients(b: ItemLogResultBinding, text: String?) {
        val clean = text?.trim().orEmpty()
        if (clean.isEmpty()) {
            b.tvResultIngredients.visibility = View.GONE
        } else {
            b.tvResultIngredients.visibility = View.VISIBLE
            b.tvResultIngredients.text = "Made with: $clean"
        }
    }

    private fun macros(kcal: Double?, p: Double?, c: Double?, f: Double?): String {
        fun v(x: Double?) = if (x == null) "\u2013" else String.format(Locale.US, "%.1f", x).removeSuffix(".0")
        val k = kcal?.roundToInt()?.toString() ?: "\u2013"
        return "$k kcal \u2022 P ${v(p)}g \u00B7 C ${v(c)}g \u00B7 F ${v(f)}g"
    }

    class HeaderHolder(val binding: ItemLogHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    class RowHolder(val binding: ItemLogResultBinding) : RecyclerView.ViewHolder(binding.root)

    private object Diff : DiffUtil.ItemCallback<LogRow>() {
        override fun areItemsTheSame(a: LogRow, b: LogRow) = a.key == b.key
        override fun areContentsTheSame(a: LogRow, b: LogRow) = a == b
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ROW = 1
    }
}
