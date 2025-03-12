package com.anthonymandra.rawdroid

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.text.Html
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.anthonymandra.image.ImageConfiguration
import com.anthonymandra.image.JpegConfiguration
import com.anthonymandra.image.TiffConfiguration
import com.anthonymandra.rawdroid.databinding.SaveDialogBinding
import com.anthonymandra.rawdroid.databinding.SaveJpgBinding
import com.anthonymandra.rawdroid.databinding.SaveTiffBinding
import com.google.android.material.snackbar.Snackbar

typealias SaveConfigurationListener = (ImageConfiguration) -> Unit
class SaveConfigDialog(activity: Activity) : Dialog(activity) {

    private var onSaveConfiguration: SaveConfigurationListener? = null
    private lateinit var binding: SaveDialogBinding
    private lateinit var jpgBinding: SaveJpgBinding
    private lateinit var  tiffBinding: SaveTiffBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SaveDialogBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val adapter = CustomPagerAdapter(binding.tabContainer)
        binding.tabContainer.adapter = adapter
        binding.tabLayout.setupWithViewPager(binding.tabContainer)

        binding.checkBoxSetDefault.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Snackbar.make(binding.dialog,
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

        binding.buttonSave.setOnClickListener {
            val formatConfig = when (binding.tabLayout.selectedTabPosition) {
                0 -> JpegConfiguration(jpgBinding.seekBarQuality.progress)
                1 -> TiffConfiguration(tiffBinding.switchCompress.isChecked)
                else -> JpegConfiguration()
            }
            onSaveConfiguration?.invoke(formatConfig)

            if (binding.checkBoxSetDefault.isChecked)
                formatConfig.savePreference(context)

            dismiss()
        }
        binding.buttonCancel.setOnClickListener { dismiss() }
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