package com.anthonymandra.widget

import android.content.Context
import android.util.AttributeSet
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.Label
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.android.synthetic.main.material_color_key.view.*
import java.util.*

typealias OnLabelSelectionChangedListener = (List<Label>) -> Unit
class XmpLabelGroup @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : MaterialButtonToggleGroup(context, attrs, defStyleAttr) {

    private var mListener: OnLabelSelectionChangedListener? = null

    val checked: List<Label>
        get() {
            val checked = ArrayList<Label>()
            checkedButtonIds.forEach {
                when(it) {
                    blueLabel.id -> checked.add(Label.Blue)
                    redLabel.id -> checked.add(Label.Red)
                    greenLabel.id  -> checked.add(Label.Green)
                    yellowLabel.id  -> checked.add(Label.Yellow)
                    purpleLabel.id  -> checked.add(Label.Purple)
                }
            }
            return checked
        }

    init {
        inflate(context, R.layout.material_color_key, this)
        addOnButtonCheckedListener { _, _, _ -> mListener?.invoke(checked) }
    }

    fun setChecked(toCheck: Label) {
        when (toCheck) {
            Label.Blue -> check(blueLabel.id)
            Label.Red -> check(redLabel.id)
            Label.Green -> check(greenLabel.id)
            Label.Yellow -> check(yellowLabel.id)
            Label.Purple -> check(purpleLabel.id)
        }
    }

    fun setOnLabelSelectionChangedListener(listener: OnLabelSelectionChangedListener) {
        mListener = listener
    }
}
