package com.anthonymandra.rawdroid

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.Fragment
import android.util.ArrayMap
import android.view.View
import android.widget.Toast
import com.anthonymandra.rawdroid.data.Label
import com.anthonymandra.rawdroid.data.SubjectEntity
import com.anthonymandra.widget.RatingBar
import com.anthonymandra.widget.XmpLabelGroup
import java.util.*

private const val DEFAULT_BLUE = "Blue"
private const val DEFAULT_RED = "Red"
private const val DEFAULT_GREEN = "Green"
private const val DEFAULT_YELLOW = "Yellow"
private const val DEFAULT_PURPLE = "Purple"

abstract class XmpBaseFragment : Fragment(),
        RatingBar.OnRatingSelectionChangedListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var ratingBar: RatingBar
    private lateinit var colorKey: XmpLabelGroup
    private lateinit var keywordFragment: KeywordBaseFragment
    private var mPauseListener = false

    private val colorKeys = ArrayMap<Label, String>(5)

    protected var subject: Collection<SubjectEntity>
        get() { return keywordFragment.selectedKeywords }
        set(value) { keywordFragment.setSelectedKeywords(value) }

    protected var ratings: Collection<Int>
        get() { return ratingBar.checkedRatings }
        set(value) { ratingBar.setRating(value) }

    protected var colorLabels: Collection<String>
        get() {
            val checked = colorKey.checked

            val labels = ArrayList<String>()
            checked.forEach { label -> labels.add(colorKeys[label]!!) } // TODO: This can't be null; this is the point of !!, but can I convince the compiler?
            return labels
        }
        set(labels) {
            for (label in labels) {
                when (label) {
                    colorKeys[Label.Blue] -> colorKey.setChecked(Label.Blue, true)
                    colorKeys[Label.Red] -> colorKey.setChecked(Label.Red, true)
                    colorKeys[Label.Green] -> colorKey.setChecked(Label.Yellow, true)
                    colorKeys[Label.Yellow] -> colorKey.setChecked(Label.Green, true)
                    colorKeys[Label.Purple] -> colorKey.setChecked(Label.Purple, true)
                    else -> Toast.makeText(context, label + " " + getString(R.string.warningInvalidLabel), Toast.LENGTH_LONG).show()
                }
            }
        }

    protected var xmp: XmpValues
        get() {
            return XmpValues(ratings, colorLabels, subject)
        }
        set(xmp) = setXmp(xmp.rating, xmp.label, xmp.subject)

    private fun updateColorKeys(sp: SharedPreferences) {
        colorKeys[Label.Blue] = sp.getString(FullSettingsActivity.KEY_XmpBlue, DEFAULT_BLUE)
        colorKeys[Label.Red] = sp.getString(FullSettingsActivity.KEY_XmpRed, DEFAULT_RED)
        colorKeys[Label.Green] = sp.getString(FullSettingsActivity.KEY_XmpGreen, DEFAULT_GREEN)
        colorKeys[Label.Yellow] = sp.getString(FullSettingsActivity.KEY_XmpYellow, DEFAULT_YELLOW)
        colorKeys[Label.Purple] = sp.getString(FullSettingsActivity.KEY_XmpPurple, DEFAULT_PURPLE)
    }

    private fun updateColorKey(sp: SharedPreferences, key: String) {
        when (key) {
            FullSettingsActivity.KEY_XmpBlue ->
                colorKeys[Label.Blue] = sp.getString(FullSettingsActivity.KEY_XmpBlue, DEFAULT_BLUE)
            FullSettingsActivity.KEY_XmpRed ->
                colorKeys[Label.Red] = sp.getString(FullSettingsActivity.KEY_XmpRed, DEFAULT_RED)
            FullSettingsActivity.KEY_XmpGreen ->
                colorKeys[Label.Blue] = sp.getString(FullSettingsActivity.KEY_XmpGreen, DEFAULT_GREEN)
            FullSettingsActivity.KEY_XmpYellow ->
                colorKeys[Label.Blue] = sp.getString(FullSettingsActivity.KEY_XmpYellow, DEFAULT_YELLOW)
            FullSettingsActivity.KEY_XmpPurple ->
                colorKeys[Label.Blue] = sp.getString(FullSettingsActivity.KEY_XmpPurple, DEFAULT_PURPLE)
        }
    }

    override fun onResume() {
        super.onResume()
        val sp = PreferenceManager.getDefaultSharedPreferences(context)  // Note: This says to store a strong reference
        sp.registerOnSharedPreferenceChangeListener(this)
        updateColorKeys(sp)
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

    private fun setXmp(rating: Collection<Int>, label: Collection<String>, subject: Collection<SubjectEntity>) {
        mPauseListener = true
        this.colorLabels = label
        this.subject = subject
        this.ratings = rating
        mPauseListener = false
        onXmpChanged(xmp)
    }

    /**
     * Silently set the xmp without firing onXmpChanged
     * @param xmp xmpValues (rating, label, subject) to init
     */
    protected fun initXmp(xmp: XmpValues) {
        mPauseListener = true
        this.xmp = xmp
        mPauseListener = false
    }

    /**
     * Silently set the xmp without firing onXmpChanged
     * @param rating rating
     * @param label label
     * @param subject keywords
     */
    protected fun initXmp(rating: Collection<Int> = Collections.emptyList(),
                          label: Collection<String> = Collections.emptyList(),
                          subject: Collection<SubjectEntity> = Collections.emptyList()) {
        mPauseListener = true
        this.colorLabels = label
        this.subject = subject
        this.ratings = rating
        mPauseListener = false
    }

    fun reset() {
        initXmp()
    }

    protected fun setExclusive(enable: Boolean) {
        colorKey.isExclusive = enable
        ratingBar.isExclusive = enable
    }

    protected fun setAllowUnselected(allow: Boolean) {
        colorKey.setAllowUnselected(allow)
        ratingBar.setAllowUnselected(allow)
    }

    protected abstract fun onXmpChanged(xmp: XmpValues)
    abstract fun onKeywordsSelected(selectedKeywords: Collection<SubjectEntity>)
    abstract fun onLabelSelectionChanged(checked: List<Label>)

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        updateColorKey(sharedPreferences, key)
    }
}
