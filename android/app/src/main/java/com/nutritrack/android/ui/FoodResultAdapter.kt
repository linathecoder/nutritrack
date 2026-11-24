package com.nutritrack.android.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nutritrack.android.R
import com.nutritrack.android.data.food.FoodItem
import com.nutritrack.android.databinding.ItemFoodResultBinding
import java.util.Locale
import kotlin.math.roundToInt

class FoodResultAdapter(
    private val tintFor: (String) -> Int,
    private val onAdd: (FoodItem) -> Unit
) :
    ListAdapter<FoodItem, FoodResultAdapter.Holder>(Diff) {

    private var added: Set<String> = emptySet()

    fun setAdded(ids: Set<String>) {
        if (ids == added) return
        added = ids
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemFoodResultBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemFoodResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val food = getItem(position)
        val b = holder.binding
        b.tvFoodEmoji.text = food.icon
        b.tvFoodEmoji.backgroundTintList = ColorStateList.valueOf(tintFor(food.category))
        b.tvFoodName.text = food.name
        val n = food.per100g
        b.tvFoodMacros.text = "${n.kcal.roundToInt()} kcal  •  P ${g(n.protein)}g · C ${g(n.carbs)}g · F ${g(n.fat)}g"

        val alreadyAdded = food.id in added
        b.btnAdd.text = if (alreadyAdded) "+ More" else "+ Add"
        b.btnAdd.setBackgroundResource(if (alreadyAdded) R.drawable.bg_btn_more else R.drawable.bg_btn_add)
        b.btnAdd.contentDescription = "Add ${food.name}"
        b.btnAdd.setOnClickListener { onAdd(food) }
    }

    private fun g(v: Double) = String.format(Locale.US, "%.1f", v).removeSuffix(".0")

    private object Diff : DiffUtil.ItemCallback<FoodItem>() {
        override fun areItemsTheSame(a: FoodItem, b: FoodItem) = a.id == b.id
        override fun areContentsTheSame(a: FoodItem, b: FoodItem) = a == b
    }
}
