package com.anthonymandra.rawdroid

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.anthonymandra.rawdroid.data.Label
import com.anthonymandra.rawdroid.data.SubjectEntity
import com.anthonymandra.rawdroid.models.ColorKeys
import com.anthonymandra.rawdroid.settings.MetaSettingsFragment
import com.anthonymandra.widget.RatingBar
import com.anthonymandra.widget.XmpLabelGroup
import java.util.*

abstract class XmpBaseFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var ratingBar: RatingBar
    private lateinit var colorKey: XmpLabelGroup
    private lateinit var keywordFragment: KeywordBaseFragment
    private var mPauseListener = false

	@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
	private val colorKeys: ColorKeys by lazy {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.registerOnSharedPreferenceChangeListener(this)

		 ColorKeys().also {
            it.blue = sp.getString(MetaSettingsFragment.KEY_XmpBlue, it.blue)
            it.red = sp.getString(MetaSettingsFragment.KEY_XmpRed, it.red)
            it.green = sp.getString(MetaSettingsFragment.KEY_XmpGreen, it.green)
            it.yellow = sp.getString(MetaSettingsFragment.KEY_XmpYellow, it.yellow)
            it.purple = sp.getString(MetaSettingsFragment.KEY_XmpPurple, it.purple)
        }
    }

    protected var subject: List<SubjectEntity>
        get() { return keywordFragment.selectedKeywords.toList() }
        set(value) { keywordFragment.setSelectedKeywords(value) }

    protected var ratings: List<Int>
        get() { return ratingBar.checkedRatings }
        set(value) { ratingBar.setRating(value) }

    protected var colorLabels: List<String>
        get() {
            val checked = colorKey.checked

            val labels = ArrayList<String>()
            checked.forEach { label -> labels.add(colorKeys.customValue(label)) }
            return labels
        }
        set(labels) {
            for (label in labels) {
                when (colorKeys.label(label)) {
                    Label.Blue -> colorKey.setChecked(Label.Blue)
                    Label.Red -> colorKey.setChecked(Label.Red)
                    Label.Green -> colorKey.setChecked(Label.Yellow)
                    Label.Yellow -> colorKey.setChecked(Label.Green)
                    Label.Purple -> colorKey.setChecked(Label.Purple)
                    else -> Toast.makeText(context, label + " " + getString(R.string.warningInvalidLabel), Toast.LENGTH_LONG).show()
                }
            }
        }

    protected var xmp: XmpFilter
        get() {
            return XmpFilter(ratings, colorLabels, subject)
        }
        set(xmp) = setXmp(xmp.rating, xmp.label, xmp.subject)

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
	 private fun updateColorKey(sp: SharedPreferences, key: String) {
        when (key) {
            MetaSettingsFragment.KEY_XmpBlue ->
                colorKeys.blue = sp.getString(MetaSettingsFragment.KEY_XmpBlue, colorKeys.blue)
            MetaSettingsFragment.KEY_XmpRed ->
                colorKeys.red = sp.getString(MetaSettingsFragment.KEY_XmpRed, colorKeys.red)
            MetaSettingsFragment.KEY_XmpGreen ->
                colorKeys.green = sp.getString(MetaSettingsFragment.KEY_XmpGreen, colorKeys.green)
            MetaSettingsFragment.KEY_XmpYellow ->
                colorKeys.yellow = sp.getString(MetaSettingsFragment.KEY_XmpYellow, colorKeys.yellow)
            MetaSettingsFragment.KEY_XmpPurple ->
                colorKeys.purple = sp.getString(MetaSettingsFragment.KEY_XmpPurple, colorKeys.purple)
        }
    }

    override fun onPause() {
        super.onPause()
        PreferenceManager.getDefaultSharedPreferences(context)
			  .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ratingBar = view.findViewById(R.id.ratingBar)
        colorKey = view.findViewById(R.id.colorKey)
        keywordFragment = childFragmentManager.findFragmentById(R.id.keywordFragment) as KeywordBaseFragment

        ratingBar.setOnRatingSelectionChangedListener { checked ->
            if (!mPauseListener)
                this@XmpBaseFragment.onRatingSelectionChanged(checked)
        }

        colorKey.setOnLabelSelectionChangedListener { checked ->
            if (!mPauseListener)
                this@XmpBaseFragment.onLabelSelectionChanged(checked)
        }
        keywordFragment.setOnKeywordsSelectedListener { selectedKeywords ->
            if (!mPauseListener)
                this@XmpBaseFragment.onKeywordsSelected(selectedKeywords)
        }
    }

    protected fun clear() {
        keywordFragment.clearSelectedKeywords()
        colorKey.clearChecked()
        ratingBar.clearChecked()
        onXmpChanged(xmp)
    }

    private fun setXmp(rating: List<Int>, label: List<String>, subject: List<SubjectEntity>) {
        mPauseListener = true
        this.colorLabels = label
        this.subject = subject
        this.ratings = rating
        mPauseListener = false
        onXmpChanged(xmp)
    }

    /**
     * Silently set the xmp without firing onXmpChanged
     * @param rating rating
     * @param label label
     * @param subject keywords
     */
    protected fun initXmp(rating: List<Int> = Collections.emptyList(),
                          label: List<String> = Collections.emptyList(),
                          subject: List<SubjectEntity> = Collections.emptyList()) {
        mPauseListener = true
        this.colorLabels = label
        this.subject = subject
        this.ratings = rating
        mPauseListener = false
    }

    fun reset() {
        initXmp()
    }

    protected fun isSingleSelection(enable: Boolean) {
        colorKey.isSingleSelection = enable
        ratingBar.isSingleSelection = enable
    }

//    protected fun setAllowUnselected(allow: Boolean) {
//        colorKey.setAllowUnselected(allow)
//        ratingBar.setAllowUnselected(allow)
//    }

    protected abstract fun onXmpChanged(xmp: XmpFilter)
    abstract fun onKeywordsSelected(selectedKeywords: Collection<SubjectEntity>)
    abstract fun onRatingSelectionChanged(checked: List<Int>)
    abstract fun onLabelSelectionChanged(checked: List<Label>)

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        updateColorKey(sharedPreferences, key)
    }
}
