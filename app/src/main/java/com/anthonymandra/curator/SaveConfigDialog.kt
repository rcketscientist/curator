package com.anthonymandra.curator

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.text.Html
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.anthonymandra.curator.databinding.SaveDialogBinding
import com.anthonymandra.curator.databinding.SaveJpgBinding
import com.anthonymandra.curator.databinding.SaveTiffBinding
import com.anthonymandra.image.ImageConfiguration
import com.anthonymandra.image.JpegConfiguration
import com.anthonymandra.image.TiffConfiguration
import com.google.android.material.snackbar.Snackbar

typealias SaveConfigurationListener = (ImageConfiguration) -> Unit
class SaveConfigDialog(activity: Activity) : Dialog(activity) {
    private lateinit var dialogBinding: SaveDialogBinding
    private lateinit var jpgBinding: SaveJpgBinding
    private lateinit var tifBinding: SaveTiffBinding

    private var onSaveConfiguration: SaveConfigurationListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = dialogBinding.root
        setContentView(view)        
        setTitle(R.string.saveAs)

        val adapter = CustomPagerAdapter(dialogBinding.tabContainer)
        dialogBinding.tabContainer.adapter = adapter
        dialogBinding.tabLayout.setupWithViewPager(dialogBinding.tabContainer)

        dialogBinding.checkBoxSetDefault.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Snackbar.make(dialogBinding.dialog,
                    Html.fromHtml(
                        context.getString(R.string.saveDefaultConfirm) +
                            "  <i>" + context.getString(R.string.settingsReset) + "</i>"),
                    Snackbar.LENGTH_LONG)
                    .show()
            }
        }

        jpgBinding.seekBarQuality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                jpgBinding.valueQuality.text = progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        dialogBinding.buttonSave.setOnClickListener {
            val formatConfig = when (dialogBinding.tabLayout.selectedTabPosition) {
                0 -> JpegConfiguration(jpgBinding.seekBarQuality.progress)
                1 -> TiffConfiguration(tifBinding.switchCompress.isChecked)
                else -> JpegConfiguration()
            }
            onSaveConfiguration?.invoke(formatConfig)

            if (dialogBinding.checkBoxSetDefault.isChecked)
                formatConfig.savePreference(context)

            dismiss()
        }
        dialogBinding.buttonCancel.setOnClickListener { dismiss() }
    }

    fun setSaveConfigurationListener(callback: SaveConfigurationListener) {
        onSaveConfiguration = callback
    }

    inner class CustomPagerAdapter(private val host: ViewPager) : PagerAdapter() {

        override fun instantiateItem(collection: ViewGroup, position: Int): Any {
            return host.getChildAt(position)
        }

        override fun destroyItem(collection: ViewGroup, position: Int, view: Any) {
            collection.removeView(view as View?)
        }

        override fun getCount(): Int {
            return host.childCount
        }

        override fun isViewFromObject(view: View, toCompare: Any): Boolean {
            return view === toCompare
        }

        override fun getPageTitle(position: Int): CharSequence? {
            return when (position) {
                0 -> "JPG"
                1 -> "TIFF"
                else -> "JPG"
            }
        }
    }
}