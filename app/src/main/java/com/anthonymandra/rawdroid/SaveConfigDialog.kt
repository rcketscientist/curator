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
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.save_dialog.*
import kotlinx.android.synthetic.main.save_jpg.*
import kotlinx.android.synthetic.main.save_tiff.*

typealias SaveConfigurationListener = (ImageConfiguration) -> Unit
class SaveConfigDialog(activity: Activity) : Dialog(activity) {

    private var onSaveConfiguration: SaveConfigurationListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.save_dialog)
        setTitle(R.string.saveAs)

        val adapter = CustomPagerAdapter(tabContainer)
        tabContainer.adapter = adapter
        tabLayout.setupWithViewPager(tabContainer)

        checkBoxSetDefault.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Snackbar.make(dialog,
                    Html.fromHtml(
                        context.getString(R.string.saveDefaultConfirm) +
                            "  <i>" + context.getString(R.string.settingsReset) + "</i>"),
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
            val formatConfig = when (tabLayout.selectedTabPosition) {
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
                formatConfig.savePreference(context)

            dismiss()
        }
        buttonCancel.setOnClickListener { dismiss() }
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