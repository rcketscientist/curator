package com.anthonymandra.rawdroid

import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.Fragment
import android.view.View
import android.widget.Toast
import com.anthonymandra.rawdroid.data.SubjectEntity
import com.anthonymandra.widget.RatingBar
import com.anthonymandra.widget.XmpLabelGroup
import java.util.*

abstract class XmpBaseFragment : Fragment(),
        RatingBar.OnRatingSelectionChangedListener,
        XmpLabelGroup.OnLabelSelectionChangedListener,
        KeywordBaseFragment.OnKeywordsSelectedListener {

    lateinit var mRatingBar: RatingBar
    lateinit var colorKey: XmpLabelGroup
    lateinit var mKeywordFragment: KeywordBaseFragment
    private var mPauseListener = false

    protected var subject: Collection<SubjectEntity>
        get() {
            return mKeywordFragment.getSelectedKeywords()
        }
        set(subject) {
            mKeywordFragment.setSelectedKeywords(subject)
        }

    protected var ratings: Collection<Int>
        get() {
            return mRatingBar.checkedRatings
        }
        set(ratings) {
            mRatingBar.setRating(ratings)
        }

    protected var colorLabels: Collection<String>
        get() {
            val c = context ?: return Collections.emptyList()

            val sp = PreferenceManager.getDefaultSharedPreferences(c)
            val checked = colorKey.checkedLabels

            if (checked.size == 0)
                return Collections.emptyList()

            val labels = ArrayList<String>()
            for (check in checked) {
                when (check) {
                    XmpLabelGroup.Labels.blue -> labels.add(sp.getString(FullSettingsActivity.KEY_XmpBlue, "Blue"))
                    XmpLabelGroup.Labels.red -> labels.add(sp.getString(FullSettingsActivity.KEY_XmpRed, "Red"))
                    XmpLabelGroup.Labels.green -> labels.add(sp.getString(FullSettingsActivity.KEY_XmpGreen, "Green"))
                    XmpLabelGroup.Labels.yellow -> labels.add(sp.getString(FullSettingsActivity.KEY_XmpYellow, "Yellow"))
                    XmpLabelGroup.Labels.purple -> labels.add(sp.getString(FullSettingsActivity.KEY_XmpPurple, "Purple"))
                }
            }
            return labels
        }
        set(labels) {
            val c = context ?: return

            val sp = PreferenceManager.getDefaultSharedPreferences(c)
            val red = sp.getString(FullSettingsActivity.KEY_XmpRed, "Red")
            val blue = sp.getString(FullSettingsActivity.KEY_XmpBlue, "Blue")
            val green = sp.getString(FullSettingsActivity.KEY_XmpGreen, "Green")
            val yellow = sp.getString(FullSettingsActivity.KEY_XmpYellow, "Yellow")
            val purple = sp.getString(FullSettingsActivity.KEY_XmpPurple, "Purple")

            for (label in labels) {
                when (label) {
                    blue -> colorKey.setChecked(XmpLabelGroup.Labels.blue, true)
                    red -> colorKey.setChecked(XmpLabelGroup.Labels.red, true)
                    yellow -> colorKey.setChecked(XmpLabelGroup.Labels.yellow, true)
                    green -> colorKey.setChecked(XmpLabelGroup.Labels.green, true)
                    purple -> colorKey.setChecked(XmpLabelGroup.Labels.purple, true)
                    else -> Toast.makeText(c, label + " " + getString(R.string.warningInvalidLabel), Toast.LENGTH_LONG).show()
                }
            }
        }

    protected var xmp: XmpValues
        get() {
            val xmp = XmpValues()
            xmp.label = colorLabels
            xmp.rating = ratings
            xmp.subject = subject
            return xmp
        }
        set(xmp) = setXmp(xmp.rating, xmp.label, xmp.subject)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mRatingBar = view.findViewById(R.id.ratingBar)
        colorKey = view.findViewById(R.id.colorKey)
        mKeywordFragment = childFragmentManager.findFragmentById(R.id.keywordFragment) as KeywordBaseFragment
        attachButtons()
    }

    protected fun clear() {
        mKeywordFragment.clearSelectedKeywords()
        colorKey.clearChecked()
        mRatingBar.clearChecked()
        onXmpChanged(xmp)
    }

    protected abstract fun onXmpChanged(xmp: XmpValues)

    protected fun setXmp(rating: Collection<Int>, label: Collection<String>, subject: Collection<SubjectEntity>) {
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
        mRatingBar.isExclusive = enable
    }

    protected fun setAllowUnselected(allow: Boolean) {
        colorKey.setAllowUnselected(allow)
        mRatingBar.setAllowUnselected(allow)
    }

    private fun attachButtons() {
        // Ratings
        mRatingBar.setOnRatingSelectionChangedListener { checked ->
            if (!mPauseListener)
                this@XmpBaseFragment.onRatingSelectionChanged(checked)
        }
        colorKey.setOnLabelSelectionChangedListener { checked ->
            if (!mPauseListener)
                this@XmpBaseFragment.onLabelSelectionChanged(checked)
        }
        mKeywordFragment.setOnKeywordsSelectedListener({ selectedKeywords ->
            if (!mPauseListener)
                this@XmpBaseFragment.onKeywordsSelected(selectedKeywords)
        })
    }
}
