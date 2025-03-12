package com.anthonymandra.widget

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.Label
import com.anthonymandra.rawdroid.databinding.MaterialColorKeyBinding
import com.anthonymandra.rawdroid.databinding.RatingBarBinding
import com.google.android.material.button.MaterialButtonToggleGroup
import java.util.*

typealias OnLabelSelectionChangedListener = (List<Label>) -> Unit
class XmpLabelGroup
    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
        : MaterialButtonToggleGroup(context, attrs, defStyleAttr) {

    private var mListener: OnLabelSelectionChangedListener? = null
    private val binding: MaterialColorKeyBinding =
        MaterialColorKeyBinding.inflate(LayoutInflater.from(context), this)

    init {
        inflate(context, R.layout.material_color_key, this)
        addOnButtonCheckedListener { _, _, _ -> mListener?.invoke(checked) }
    }

    val checked: List<Label> get() {
        val checked = ArrayList<Label>()
        checkedButtonIds.forEach {
            when(it) {
                binding.blueLabel.id -> checked.add(Label.Blue)
                binding.redLabel.id -> checked.add(Label.Red)
                binding.greenLabel.id  -> checked.add(Label.Green)
                binding.yellowLabel.id  -> checked.add(Label.Yellow)
                binding.purpleLabel.id  -> checked.add(Label.Purple)
            }
        }
        return checked
    }

    fun setChecked(toCheck: Label) {
        when (toCheck) {
            Label.Blue -> check(binding.blueLabel.id)
            Label.Red -> check(binding.redLabel.id)
            Label.Green -> check(binding.greenLabel.id)
            Label.Yellow -> check(binding.yellowLabel.id)
            Label.Purple -> check(binding.purpleLabel.id)
        }
    }

    fun setOnLabelSelectionChangedListener(listener: OnLabelSelectionChangedListener) {
        mListener = listener
    }
}
