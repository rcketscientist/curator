package com.anthonymandra.rawdroid

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.anthonymandra.rawdroid.data.Label
import com.anthonymandra.rawdroid.data.SubjectEntity
import kotlinx.android.synthetic.main.xmp_edit_landscape.*
import kotlinx.android.synthetic.main.xmp_subject_edit.*
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView
import java.util.*

typealias RatingChangedListener = (rating: Int?) -> Unit
typealias LabelChangedListener = (label: String?) -> Unit
typealias SubjectChangedListener = (subject: Collection<SubjectEntity>?) -> Unit
typealias MetaChangedListener = (xmp: XmpValues) -> Unit
class XmpEditFragment : XmpBaseFragment() {
    private var mRatingListener: RatingChangedListener? = null
    private var mLabelListener: LabelChangedListener? = null
    private var mSubjectListener: SubjectChangedListener? = null

    private var mXmpChangedListener: MetaChangedListener? = null

    /**
     * Convenience method for single select XmpLabelGroup
     */
    private val label: String?
			get() { return colorLabels.firstOrNull() }

    /**
     * Convenience method for single select XmpLabelGroup
     */
    private val rating: Int?
			get() { return ratings.firstOrNull() }

    fun setRatingListener(listener: RatingChangedListener) {
        mRatingListener = listener
    }

    fun setLabelListener(listener: LabelChangedListener) {
        mLabelListener = listener
    }

    fun setSubjectListener(listener: SubjectChangedListener) {
        mSubjectListener = listener
    }

    fun setListener(listener: MetaChangedListener) {
        mXmpChangedListener = listener
    }

    override fun onXmpChanged(xmp: XmpFilter) {
        recentXmp.rating = xmp.rating.firstOrNull()
        recentXmp.label = xmp.label.firstOrNull()
        recentXmp.subject = xmp.subject

				mXmpChangedListener?.invoke(recentXmp)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.xmp_edit_landscape, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setExclusive(true)
        setAllowUnselected(true)

        clearMetaButton.setOnClickListener { clear() }
        recentMetaButton.setOnClickListener { mXmpChangedListener?.invoke(recentXmp) }
        helpButton.setOnClickListener { startTutorial() }
    }

    /**
     * Silently set xmp without firing listeners
     */
    fun initXmp(rating: Int?, subject: List<SubjectEntity>, label: String?) {
        super.initXmp(
						if (rating == null) Collections.emptyList() else arrayListOf(rating),
						if (label == null) Collections.emptyList() else arrayListOf(label),
                subject)
    }

    override fun onKeywordsSelected(selectedKeywords: Collection<SubjectEntity>) {
        recentXmp.subject = subject
        mSubjectListener?.invoke(recentXmp.subject)
    }

    override fun onLabelSelectionChanged(checked: List<Label>) {
        val c = context
        if (c != null) {
            if (checked.isNotEmpty()) {
                when (checked[0]) {
                    Label.Blue -> recentLabel.imageTintList = ContextCompat.getColorStateList(c, R.color.colorKeyBlue)
                    Label.Red -> recentLabel.imageTintList = ContextCompat.getColorStateList(c, R.color.colorKeyRed)
                    Label.Green -> recentLabel.imageTintList = ContextCompat.getColorStateList(c, R.color.colorKeyGreen)
                    Label.Yellow -> recentLabel.imageTintList = ContextCompat.getColorStateList(c, R.color.colorKeyYellow)
                    Label.Purple -> recentLabel.imageTintList = ContextCompat.getColorStateList(c, R.color.colorKeyPurple)
                }
            } else {
                recentLabel.imageTintList = ContextCompat.getColorStateList(c, R.color.white)
            }
        }

        recentXmp.label = label
        mLabelListener?.invoke(recentXmp.label)
    }

    override fun onRatingSelectionChanged(checked: List<Int>) {
        recentXmp.rating = rating
        mRatingListener?.invoke(recentXmp.rating)

        if (recentXmp.rating == null) {
            recentRating.setImageResource(R.drawable.ic_star_border)
            return
        }

        when (recentXmp.rating) {
            5 -> recentRating.setImageResource(R.drawable.ic_star5)
            4 -> recentRating.setImageResource(R.drawable.ic_star4)
            3 -> recentRating.setImageResource(R.drawable.ic_star3)
            2 -> recentRating.setImageResource(R.drawable.ic_star2)
            1 -> recentRating.setImageResource(R.drawable.ic_star1)
            else -> recentRating.setImageResource(R.drawable.ic_star_border)
        }
    }

    private fun startTutorial() {
        val sequence = MaterialShowcaseSequence(activity)

        val root = view ?: return

        // Sort group
        sequence.addSequenceItem(getRectangularView(
                recentMetaButton,
                R.string.tutSetRecent))

        // Segregate
        sequence.addSequenceItem(getRectangularView(
                clearMetaButton,
                R.string.tutClearMeta))

        // rating
        sequence.addSequenceItem(getRectangularView(
                metaLabelRating,
                R.string.tutSetRatingLabel))

        // subject
        sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.keywordFragment),
                R.string.tutSetSubject))

        // Match
        sequence.addSequenceItem(getRectangularView(
						addKeyword,
                R.string.tutAddSubject))

        // Match
        sequence.addSequenceItem(getRectangularView(
                editKeyword,
                R.string.tutEditSubject))

        sequence.start()
    }

    private fun getRectangularView(target: View, @StringRes contentId: Int): MaterialShowcaseView {
        return getRectangularView(target,
                getString(R.string.editMetadata),
                getString(contentId),
                getString(R.string.ok))
    }

    private fun getRectangularView(target: View, title: String, content: String, dismiss: String): MaterialShowcaseView {
        return MaterialShowcaseView.Builder(activity)
                .setTarget(target)
                .setTitleText(title)
                .setContentText(content)
                .setDismissOnTouch(true)
                .setDismissText(dismiss)
                .withRectangleShape()
                .build()
    }

    companion object {
        private val recentXmp = XmpValues()
    }
}
