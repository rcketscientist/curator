package com.anthonymandra.widget

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.Label
import com.anthonymandra.rawdroid.databinding.RatingBarBinding
import com.google.android.material.button.MaterialButtonToggleGroup

import java.util.ArrayList

typealias OnRatingSelectionChangedListener = (List<Int>) -> Unit
class RatingBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : MaterialButtonToggleGroup(context, attrs, defStyleAttr) {

    private var mListener: OnRatingSelectionChangedListener? = null
    private val binding: RatingBarBinding =
        RatingBarBinding.inflate(LayoutInflater.from(context), this)

    init {
        attachButtons()
        setDrawable()
    }

    val checkedRatings: List<Int>
        get() {
            val checked = ArrayList<Int>()
            if (binding.rating5 == null) return checked // TODO: Need to address the different logic in material toggle, get rid of all these null handles
            if (binding.rating5.isChecked)
                checked.add(5)
            if (binding.rating4.isChecked)
                checked.add(4)
            if (binding.rating3.isChecked)
                checked.add(3)
            if (binding.rating2.isChecked)
                checked.add(2)
            if (binding.rating1.isChecked)
                checked.add(1)
            return checked
        }

    val rating: Int?
        get() {
            val ratings = checkedRatings
            return if (ratings.isNotEmpty()) ratings[0] else null
        }

    private fun attachButtons() {
        addOnButtonCheckedListener { group, checkedId, isChecked ->
            // In exclusive mode this will behave like a factory rating bar
            if (isSingleSelection) {
                // If there's no checked button, clear
                if (checkedButtonId == View.NO_ID) {
                    binding.rating1?.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                    binding.rating2?.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                    binding.rating3?.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                    binding.rating4?.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                    binding.rating5?.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                }
                else if (isChecked) {
                    when (checkedId) {
                        // Cascade selected stars down like a typical ratingbar
                        R.id.rating5 -> {
                            binding.rating5.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            binding.rating4.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            binding.rating3.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            binding.rating2.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            binding.rating1.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                        }
                        R.id.rating4 -> {
                            binding.rating5.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            binding.rating4.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            binding.rating3.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            binding.rating2.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            binding.rating1.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                        }
                        R.id.rating3 -> {
                            binding.rating5.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            binding.rating4.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            binding.rating3.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            binding.rating2.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            binding.rating1.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                        }
                        R.id.rating2 -> {
                            binding.rating5.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            binding.rating4.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            binding.rating3.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            binding.rating2.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            binding.rating1.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                        }
                        R.id.rating1 -> {
                            binding.rating5.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            binding.rating4.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            binding.rating3.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            binding.rating2.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            binding.rating1.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                        }
                    }
                }
            }

            mListener?.invoke(checkedRatings)
        }
    }

    override fun setSingleSelection(singleSelection: Boolean) {
        super.setSingleSelection(singleSelection)
        setDrawable()
    }

    private fun setDrawable() {
        val drawableId = if (isSingleSelection) R.drawable.ic_star_border else R.drawable.multi_select_star
        binding.rating1?.icon = resources.getDrawable(drawableId, context.theme)
        binding.rating2?.icon = resources.getDrawable(drawableId, context.theme)
        binding.rating3?.icon = resources.getDrawable(drawableId, context.theme)
        binding.rating4?.icon = resources.getDrawable(drawableId, context.theme)
        binding.rating5?.icon = resources.getDrawable(drawableId, context.theme)
    }

    /**
     * Sets the given ratings, if null is passed all ratings will be cleared.
     * @param ratings ratings to check
     */
    fun setRating(ratings: Collection<Int>?) {
        if (ratings == null)
            clearChecked()
        else {
            for (rating in ratings) {
                setRating(rating)
            }
        }
    }

    fun setRating(rating: Int?) {
        when (rating) {
            5 -> binding.rating5.isChecked = true
            4 -> binding.rating4.isChecked = true
            3 -> binding.rating3.isChecked = true
            2 -> binding.rating2.isChecked = true
            1 -> binding.rating1.isChecked = true
            else -> {
            }
        }
    }

    fun setOnRatingSelectionChangedListener(listener: OnRatingSelectionChangedListener) {
        mListener = listener
    }
}
