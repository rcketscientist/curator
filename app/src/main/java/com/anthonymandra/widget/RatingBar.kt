package com.anthonymandra.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View

import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.Label
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.android.synthetic.main.rating_bar.view.*

import java.util.ArrayList

typealias OnRatingSelectionChangedListener = (List<Int>) -> Unit
class RatingBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : MaterialButtonToggleGroup(context, attrs, defStyleAttr) {

    private var mListener: OnRatingSelectionChangedListener? = null

    val checkedRatings: List<Int>
        get() {
            val checked = ArrayList<Int>()
            if (rating5 == null) return checked // TODO: Need to address the different logic in material toggle, get rid of all these null handles
            if (rating5.isChecked)
                checked.add(5)
            if (rating4.isChecked)
                checked.add(4)
            if (rating3.isChecked)
                checked.add(3)
            if (rating2.isChecked)
                checked.add(2)
            if (rating1.isChecked)
                checked.add(1)
            return checked
        }

    val rating: Int?
        get() {
            val ratings = checkedRatings
            return if (ratings.isNotEmpty()) ratings[0] else null
        }

    init {
        View.inflate(context, R.layout.rating_bar, this)
        attachButtons()
        setDrawable()
    }

    private fun attachButtons() {
        addOnButtonCheckedListener { group, checkedId, isChecked ->
            // In exclusive mode this will behave like a factory rating bar
            if (isSingleSelection) {
                // If there's no checked button, clear
                if (checkedButtonId == View.NO_ID) {
                    rating1?.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                    rating2?.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                    rating3?.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                    rating4?.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                    rating5?.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                }
                else if (isChecked) {
                    when (checkedId) {
                        // Cascade selected stars down like a typical ratingbar
                        R.id.rating5 -> {
                            rating5.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            rating4.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            rating3.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            rating2.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            rating1.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                        }
                        R.id.rating4 -> {
                            rating5.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            rating4.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            rating3.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            rating2.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            rating1.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                        }
                        R.id.rating3 -> {
                            rating5.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            rating4.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            rating3.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            rating2.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            rating1.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                        }
                        R.id.rating2 -> {
                            rating5.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            rating4.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            rating3.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            rating2.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                            rating1.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
                        }
                        R.id.rating1 -> {
                            rating5.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            rating4.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            rating3.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            rating2.icon = resources.getDrawable(R.drawable.ic_star_border, context.theme)
                            rating1.icon = resources.getDrawable(R.drawable.ic_star, context.theme)
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
        rating1?.icon = resources.getDrawable(drawableId, context.theme)
        rating2?.icon = resources.getDrawable(drawableId, context.theme)
        rating3?.icon = resources.getDrawable(drawableId, context.theme)
        rating4?.icon = resources.getDrawable(drawableId, context.theme)
        rating5?.icon = resources.getDrawable(drawableId, context.theme)
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
            5 -> rating5.isChecked = true
            4 -> rating4.isChecked = true
            3 -> rating3.isChecked = true
            2 -> rating2.isChecked = true
            1 -> rating1.isChecked = true
            else -> {
            }
        }
    }

    fun setOnRatingSelectionChangedListener(listener: OnRatingSelectionChangedListener) {
        mListener = listener
    }
}
