package com.anthonymandra.rawdroid

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AlertDialog
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.SeekBar
import com.anthonymandra.framework.CoreActivity
import com.anthonymandra.image.ImageConfiguration
import com.anthonymandra.image.JpegConfiguration
import com.anthonymandra.image.TiffConfiguration
import kotlinx.android.synthetic.main.format_name.*
import kotlinx.android.synthetic.main.save_dialog.*
import kotlinx.android.synthetic.main.save_jpg.*
import kotlinx.android.synthetic.main.save_tiff.*

typealias RenameListener = (ImageConfiguration) -> Unit
class RenameDialog(
        activity: Activity,
        private val itemsToRename: Collection<Uri>) : AlertDialog(activity) {

    private var onSaveConfiguration: SaveConfigurationListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.format_name)
        setTitle(context.getString(R.string.renameImages))
        setCanceledOnTouchOutside(true)

        nameTextView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            @SuppressLint("SetTextI18n")
            override fun afterTextChanged(s: Editable) {
                updateExample()
            }
        })

        formatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            @SuppressLint("SetTextI18n")
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                updateExample()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // TODO: Add buttons
    }

    private fun updateExample() {
        exampleTextView.text = "Ex: " + formatRename(formatSpinner.selectedItemPosition,
                nameTextView.text.toString(),
                itemsToRename.size - 1,
                itemsToRename.size)
    }

    private fun numDigits(x: Int): Int {
        return when {
            x < 10 -> 1
            x < 100 -> 2
            x < 1000 -> 3
            x < 10000 -> 4
            x < 100000 -> 5
            x < 1000000 -> 6
            x < 10000000 -> 7
            x < 100000000 -> 8
            x < 1000000000 -> 9
            else -> 10
        }
    }

    private fun formatRename(format: Int, baseName: String, index: Int, total: Int): String? {
        val sequencer = "%0" + numDigits(total) + "d"

        var rename: String? = null
        when (format) {
            0 -> rename = baseName + "-" + String.format(sequencer, index)
            1 -> rename = baseName + " (" + String.format(sequencer, index) + " of " + total + ")"
        }

        return rename
    }
}