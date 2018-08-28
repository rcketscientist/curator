package com.anthonymandra.widget

import android.content.Context
import androidx.appcompat.widget.ToggleGroup
import android.util.AttributeSet

import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.Label

import java.util.ArrayList

typealias OnLabelSelectionChangedListener = (List<Label>) -> Unit
class XmpLabelGroup @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ToggleGroup(context, attrs, defStyleAttr) {

    private var mListener: OnLabelSelectionChangedListener? = null

    val checked: List<Label>
        get() {
            val checked = ArrayList<Label>()
            if (blueLabel.isChecked)
                checked.add(Label.Blue)
            if (redLabel.isChecked)
                checked.add(Label.Red)
            if (greenLabel.isChecked)
                checked.add(Label.Green)
            if (yellowLabel.isChecked)
                checked.add(Label.Yellow)
            if (purpleLabel.isChecked)
                checked.add(Label.Purple)
            return checked
        }



    init {
        inflate(context, R.layout.material_color_key, this)
        setOnCheckedChangeListener { _,_ -> mListener?.invoke(checked) }
    }

    fun setChecked(toCheck: Label, checked: Boolean) {
        when (toCheck) {
            Label.Blue -> blueLabel.isChecked = checked
            Label.Red -> redLabel.isChecked = checked
            Label.Green -> greenLabel.isChecked = checked
            Label.Yellow -> yellowLabel.isChecked = checked
            Label.Purple -> purpleLabel.isChecked = checked
        }
    }

    fun setOnLabelSelectionChangedListener(listener: OnLabelSelectionChangedListener) {
        mListener = listener
    }
}
