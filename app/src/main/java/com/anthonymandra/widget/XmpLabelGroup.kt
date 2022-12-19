package com.anthonymandra.widget

import android.content.Context
import android.util.AttributeSet
import com.anthonymandra.curator.databinding.MaterialColorKeyBinding
import com.anthonymandra.curator.R
import com.anthonymandra.curator.data.Label
import com.google.android.material.button.MaterialButtonToggleGroup
import java.util.*

typealias OnLabelSelectionChangedListener = (List<Label>) -> Unit
class XmpLabelGroup
    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
        : MaterialButtonToggleGroup(context, attrs, defStyleAttr) {
    private lateinit var ui: MaterialColorKeyBinding
    private var mListener: OnLabelSelectionChangedListener? = null

    val checked: List<Label>
        get() {
            val checked = ArrayList<Label>()
            checkedButtonIds.forEach {
                when(it) {
                    ui.blueLabel.id -> checked.add(Label.Blue)
                    ui.redLabel.id -> checked.add(Label.Red)
                    ui.greenLabel.id  -> checked.add(Label.Green)
                    ui.yellowLabel.id  -> checked.add(Label.Yellow)
                    ui.purpleLabel.id  -> checked.add(Label.Purple)
                }
            }
            return checked
        }

    init {
        // TODO: AJM: This probably doesn't work with ViewBinding...
        inflate(context, R.layout.material_color_key, this)
        addOnButtonCheckedListener { _, _, _ -> mListener?.invoke(checked) }
    }

    fun setChecked(toCheck: Label) {
        when (toCheck) {
            Label.Blue -> check(ui.blueLabel.id)
            Label.Red -> check(ui.redLabel.id)
            Label.Green -> check(ui.greenLabel.id)
            Label.Yellow -> check(ui.yellowLabel.id)
            Label.Purple -> check(ui.purpleLabel.id)
        }
    }

    fun setOnLabelSelectionChangedListener(listener: OnLabelSelectionChangedListener) {
        mListener = listener
    }
}
