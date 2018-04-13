package com.anthonymandra.rawdroid

import android.app.Dialog
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.design.widget.TabLayout
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
import com.android.gallery3d.util.Profile.commit
import android.R.attr.fragment
import android.content.Context
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import android.view.ViewGroup
import android.view.LayoutInflater
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager


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

        val adapter = CustomPagerAdapter(tabContainer)
        tabContainer.adapter = adapter
        tabLayout.setupWithViewPager(tabContainer)

//        tabLayout.addOnTabSelectedListener(object: TabLayout.OnTabSelectedListener {
//            override fun onTabReselected(tab: TabLayout.Tab?) {}
//
//            override fun onTabUnselected(tab: TabLayout.Tab?) {}
//
//            override fun onTabSelected(tab: TabLayout.Tab?) {
//
//            }
//        })
//
//        tabHost.setup()
//
//        val jpg = tabHost.newTabSpec("JPG")
//        val tif = tabHost.newTabSpec("TIF")
//
//        jpg.setContent(R.id.JPG)
//        jpg.setIndicator("JPG")
//        tabHost.addTab(jpg)
//
//        tif.setContent(R.id.TIFF)
//        tif.setIndicator("TIFF")
//        tabHost.addTab(tif)

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
                formatConfig.savePreference(activity)

            dismiss()
        }
        buttonCancel.setOnClickListener { dismiss() }
    }

    fun setSaveConfigurationListener(callback: SaveConfigurationListener) {
        onSaveConfiguration = callback
    }

//    fun setCurrentTab(String tab) {
//        when (tab) {
//            JPG -> replaceFragment
//        }
//    }
//
//    fun replaceFragment(fragment: Fragment) {
//        fragmentManager?.let {
//            val ft = it.beginTransaction()
//            ft.replace(R.id.tabContainer, fragment)
//            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
//            ft.commit()
//        }
//    }

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
    }
}