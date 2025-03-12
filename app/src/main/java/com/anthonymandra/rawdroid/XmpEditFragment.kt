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
import com.anthonymandra.rawdroid.databinding.XmpCoreBinding
import com.anthonymandra.rawdroid.databinding.XmpEditLandscapeBinding
import com.anthonymandra.rawdroid.databinding.XmpSubjectEditBinding
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

    private var _binding: XmpEditLandscapeBinding? = null
    private val binding get() = _binding!!

    private var _xmpBinding: XmpCoreBinding? = null
    private val xmpBinding get() = _xmpBinding!!

    private var _xmpEditBinding: XmpSubjectEditBinding? = null
    private val xmpEditBinding get() = _xmpEditBinding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = XmpEditLandscapeBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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

    fun setListener(listener: MetaChangedListener) {
        mXmpChangedListener = listener
    }

    override fun onXmpChanged(xmp: XmpFilter) {
        recentXmp.rating = xmp.rating.firstOrNull()
        recentXmp.label = xmp.label.firstOrNull()
        recentXmp.subject = xmp.subject

				mXmpChangedListener?.invoke(recentXmp)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isSingleSelection(true)
//        setAllowUnselected(true)

        binding.clearMetaButton.setOnClickListener { clear() }
        binding.recentMetaButton.setOnClickListener { mXmpChangedListener?.invoke(recentXmp) }
        binding.helpButton.setOnClickListener { startTutorial() }
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
                    Label.Blue -> binding.recentLabel.imageTintList = ContextCompat.getColorStateList(c, R.color.colorKeyBlue)
                    Label.Red -> binding.recentLabel.imageTintList = ContextCompat.getColorStateList(c, R.color.colorKeyRed)
                    Label.Green -> binding.recentLabel.imageTintList = ContextCompat.getColorStateList(c, R.color.colorKeyGreen)
                    Label.Yellow -> binding.recentLabel.imageTintList = ContextCompat.getColorStateList(c, R.color.colorKeyYellow)
                    Label.Purple -> binding.recentLabel.imageTintList = ContextCompat.getColorStateList(c, R.color.colorKeyPurple)
                }
            } else {
                binding.recentLabel.imageTintList = ContextCompat.getColorStateList(c, R.color.white)
            }
        }

        recentXmp.label = label
        mLabelListener?.invoke(recentXmp.label)
    }

    override fun onRatingSelectionChanged(checked: List<Int>) {
        recentXmp.rating = rating
        mRatingListener?.invoke(recentXmp.rating)

        if (recentXmp.rating == null) {
            binding.recentRating.setImageResource(R.drawable.ic_star_border)
            return
        }

        when (recentXmp.rating) {
            5 -> binding.recentRating.setImageResource(R.drawable.ic_star5)
            4 -> binding.recentRating.setImageResource(R.drawable.ic_star4)
            3 -> binding.recentRating.setImageResource(R.drawable.ic_star3)
            2 -> binding.recentRating.setImageResource(R.drawable.ic_star2)
            1 -> binding.recentRating.setImageResource(R.drawable.ic_star1)
            else -> binding.recentRating.setImageResource(R.drawable.ic_star_border)
        }
    }

    private fun startTutorial() {
        val sequence = MaterialShowcaseSequence(activity)

        val root = view ?: return

        // Sort group
        sequence.addSequenceItem(getRectangularView(
            binding.recentMetaButton,
                R.string.tutSetRecent))

        // Segregate
        sequence.addSequenceItem(getRectangularView(
            binding.clearMetaButton,
                R.string.tutClearMeta))

        // rating
        sequence.addSequenceItem(getRectangularView(
            xmpBinding.ratingBar,
                R.string.tutSetRatingLabel))

        // subject
        sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.keywordFragment),
                R.string.tutSetSubject))

        // Match
        sequence.addSequenceItem(getRectangularView(
            xmpEditBinding.addKeyword,
                R.string.tutAddSubject))

        // Match
        sequence.addSequenceItem(getRectangularView(
            xmpEditBinding.editKeyword,
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
