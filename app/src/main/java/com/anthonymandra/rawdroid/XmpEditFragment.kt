package com.anthonymandra.rawdroid

import android.os.Bundle
import android.support.annotation.StringRes
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.anthonymandra.rawdroid.data.SubjectEntity
import com.anthonymandra.widget.XmpLabelGroup
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView
import java.util.*

class XmpEditFragment : XmpBaseFragment() {
    private lateinit var recentRating: ImageView
    private lateinit var recentLabel: ImageView

    private var mRatingListener: RatingChangedListener? = null
    private var mLabelListener: LabelChangedListener? = null
    private var mSubjectListener: SubjectChangedListener? = null

    private var mXmpChangedListener: MetaChangedListener? = null

    /**
     * Convenience method for single select XmpLabelGroup
     */
    private val label: String?
        get() {
            val label = colorLabels
            return if (label.isEmpty()) null else label.iterator().next()
        }

    /**
     * Convenience method for single select XmpLabelGroup
     */
    private val rating: Int?
        get() {
            val ratings = ratings
            return if (ratings.isEmpty()) null else ratings.iterator().next()
        }

    interface RatingChangedListener {
        fun onRatingChanged(rating: Int?)
    }

    interface LabelChangedListener {
        fun onLabelChanged(label: String?)
    }

    interface SubjectChangedListener {
        fun onSubjectChanged(subject: Collection<SubjectEntity>?)
    }

    fun setRatingListener(listener: RatingChangedListener) {
        mRatingListener = listener
    }

    fun setLabelListener(listener: LabelChangedListener) {
        mLabelListener = listener
    }

    fun setSubjectListener(listener: SubjectChangedListener) {
        mSubjectListener = listener
    }

    interface MetaChangedListener {
        fun onMetaChanged(rating: Int?, label: String?, subject: Collection<SubjectEntity>?)
    }

    fun setListener(listener: MetaChangedListener) {
        mXmpChangedListener = listener
    }

    override fun onXmpChanged(xmp: XmpValues) {
        recentXmp.Subject = xmp.subject
        recentXmp.Rating = formatRating(xmp.rating)
        recentXmp.Label = formatLabel(xmp.label)

        fireMetaUpdate()
    }

    private fun fireMetaUpdate() {
        mXmpChangedListener?.onMetaChanged(
                recentXmp.Rating,
                recentXmp.Label,
                recentXmp.Subject)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.xmp_edit_landscape, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setExclusive(true)
        setAllowUnselected(true)

        view.findViewById<View>(R.id.clearMetaButton).setOnClickListener { clear() }
        view.findViewById<View>(R.id.recentMetaButton).setOnClickListener { fireMetaUpdate() }
        view.findViewById<View>(R.id.helpButton).setOnClickListener { startTutorial() }

        recentLabel = view.findViewById(R.id.recentLabel)
        recentRating = view.findViewById(R.id.recentRating)
    }

    private fun formatRating(ratings: Collection<Int>?): Int? {
        return ratings?.iterator()?.next()
    }

    private fun formatRating(rating: Int?): Collection<Int> {
        return if (rating == null) Collections.emptyList() else arrayListOf(rating)
    }

    private fun formatLabel(labels: Collection<String>?): String? {
        return labels?.iterator()?.next()
    }

    private fun formatLabel(label: String?): Collection<String> {
        return if (label == null) Collections.emptyList() else arrayListOf(label)
    }

    /**
     * Silently set xmp without firing listeners
     */
    fun initXmp(rating: Int?, subject: Collection<SubjectEntity>, label: String) {
        super.initXmp(formatRating(rating),
                formatLabel(label),
                subject)
    }

    override fun onKeywordsSelected(selectedKeywords: Collection<SubjectEntity>) {
        recentXmp.Subject = subject
        mSubjectListener?.onSubjectChanged(recentXmp.Subject)
    }

    override fun onLabelSelectionChanged(checked: List<XmpLabelGroup.Labels>) {
        val c = context
        if (c != null) {
            if (checked.isNotEmpty()) {
                when (checked[0]) {
                    XmpLabelGroup.Labels.blue -> recentLabel.imageTintList = ContextCompat.getColorStateList(c, R.color.colorKeyBlue)
                    XmpLabelGroup.Labels.red -> recentLabel.imageTintList = ContextCompat.getColorStateList(c, R.color.colorKeyRed)
                    XmpLabelGroup.Labels.green -> recentLabel.imageTintList = ContextCompat.getColorStateList(c, R.color.colorKeyGreen)
                    XmpLabelGroup.Labels.yellow -> recentLabel.imageTintList = ContextCompat.getColorStateList(c, R.color.colorKeyYellow)
                    XmpLabelGroup.Labels.purple -> recentLabel.imageTintList = ContextCompat.getColorStateList(c, R.color.colorKeyPurple)
                }
            } else {
                recentLabel.imageTintList = ContextCompat.getColorStateList(c, R.color.white)
            }
        }

        recentXmp.Label = label
        mLabelListener?.onLabelChanged(recentXmp.Label)
    }

    override fun onRatingSelectionChanged(checked: List<Int>) {
        recentXmp.Rating = rating
        mRatingListener?.onRatingChanged(recentXmp.Rating)

        if (recentXmp.Rating == null) {
            recentRating.setImageResource(R.drawable.ic_star_border)
            return
        }

        when (recentXmp.Rating) {
            5 -> recentRating.setImageResource(R.drawable.ic_star5)
            4 -> recentRating.setImageResource(R.drawable.ic_star4)
            3 -> recentRating.setImageResource(R.drawable.ic_star3)
            2 -> recentRating.setImageResource(R.drawable.ic_star2)
            1 -> recentRating.setImageResource(R.drawable.ic_star1)
            else -> recentRating.setImageResource(R.drawable.ic_star_border)
        }
    }

    /**
     * Default values indicate no xmp
     */
    class XmpEditValues {
        var Rating: Int? = null
        var Subject: Collection<SubjectEntity>? = null
        var Label: String? = null
    }

    private fun startTutorial() {
        val sequence = MaterialShowcaseSequence(activity)

        val root = view ?: return

        // Sort group
        sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.recentMetaButton),
                R.string.tutSetRecent))

        // Segregate
        sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.clearMetaButton),
                R.string.tutClearMeta))

        // Rating
        sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.metaLabelRating),
                R.string.tutSetRatingLabel))

        // Subject
        sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.keywordFragment),
                R.string.tutSetSubject))

        // Match
        sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.addKeyword),
                R.string.tutAddSubject))

        // Match
        sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.editKeyword),
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
        private val recentXmp = XmpEditValues()
    }
}
