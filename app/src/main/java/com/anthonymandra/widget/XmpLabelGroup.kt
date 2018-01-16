package com.anthonymandra.widget

import android.content.Context
import android.support.v7.widget.ToggleGroup
import android.util.AttributeSet

import com.anthonymandra.rawdroid.R
import kotlinx.android.synthetic.main.material_color_key.view.*

import java.util.ArrayList

typealias OnLabelSelectionChangedListener = (List<XmpLabelGroup.Labels>) -> Unit
class XmpLabelGroup @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ToggleGroup(context, attrs, defStyleAttr) {

    private var mListener: OnLabelSelectionChangedListener? = null

    val checked: List<Labels>
        get() {
            val checked = ArrayList<Labels>()
            if (blueLabel.isChecked)
                checked.add(Labels.Blue)
            if (redLabel.isChecked)
                checked.add(Labels.Red)
            if (greenLabel.isChecked)
                checked.add(Labels.Green)
            if (yellowLabel.isChecked)
                checked.add(Labels.Yellow)
            if (purpleLabel.isChecked)
                checked.add(Labels.Purple)
            return checked
        }

    enum class Labels {
        Blue,
        Red,
        Green,
        Yellow,
        Purple
    }

    init {
        inflate(context, R.layout.material_color_key, this)
        setOnCheckedChangeListener { _,_ -> mListener?.invoke(checked) }
    }

    fun setChecked(toCheck: Labels, checked: Boolean) {
        when (toCheck) {
            XmpLabelGroup.Labels.Blue -> blueLabel.isChecked = checked
            XmpLabelGroup.Labels.Red -> redLabel.isChecked = checked
            XmpLabelGroup.Labels.Green -> greenLabel.isChecked = checked
            XmpLabelGroup.Labels.Yellow -> yellowLabel.isChecked = checked
            XmpLabelGroup.Labels.Purple -> purpleLabel.isChecked = checked
        }
    }

    fun setOnLabelSelectionChangedListener(listener: OnLabelSelectionChangedListener) {
        mListener = listener
    }
}
