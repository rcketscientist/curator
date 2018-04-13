package com.anthonymandra.rawdroid

import android.app.Dialog
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.DialogFragment
import android.text.Html
import android.view.View
import android.widget.SeekBar
import com.anthonymandra.image.ImageConfiguration
import com.anthonymandra.image.JpegConfiguration
import com.anthonymandra.image.TiffConfiguration
import kotlinx.android.synthetic.main.save_dialog.*
import kotlinx.android.synthetic.main.save_jpg.*
import kotlinx.android.synthetic.main.save_tiff.*

typealias SaveConfigurationListener = (ImageConfiguration) -> Unit
class SaveConfigDialogFragment : DialogFragment() {

    private var onSaveConfiguration: SaveConfigurationListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.save_dialog)
        dialog.setTitle(R.string.saveAs)

        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabHost.setup()

        val jpg = tabHost.newTabSpec("JPG")
        val tif = tabHost.newTabSpec("TIF")

        jpg.setContent(R.id.JPG)
        jpg.setIndicator("JPG")
        tabHost.addTab(jpg)

        tif.setContent(R.id.TIFF)
        tif.setIndicator("TIFF")
        tabHost.addTab(tif)

        checkBoxSetDefault.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Snackbar.make(dialog.currentFocus!!,
                    Html.fromHtml(
                        resources.getString(R.string.saveDefaultConfirm) + "  "
                            + "<i>" + resources.getString(R.string.settingsReset) + "</i>"),
                    Snackbar.LENGTH_LONG)
                    .show()
            }
        }

        seekBarQuality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                valueQuality.text = progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        buttonSave.setOnClickListener {
            val formatConfig = when (tabHost.currentTab) {
                0 /*JPG */ -> {
                    val c = JpegConfiguration()
                    c.quality = seekBarQuality.progress
                    c
                }
                1 /*TIF*/ -> {
                    val c = TiffConfiguration()
                    c.compress = switchCompress.isChecked
                    c
                }
                else -> JpegConfiguration()
            }
            onSaveConfiguration?.invoke(formatConfig)

            if (checkBoxSetDefault.isChecked)
                formatConfig.savePreference(activity)

            dismiss()
        }
        buttonCancel.setOnClickListener { dismiss() }
    }

    fun setSaveConfigurationListener(callback: SaveConfigurationListener) {
        onSaveConfiguration = callback
    }
}