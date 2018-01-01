package com.anthonymandra.rawdroid;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.anthonymandra.rawdroid.data.SubjectEntity;
import com.anthonymandra.widget.XmpLabelGroup;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence;
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView;

public class XmpEditFragment extends XmpBaseFragment
{
	private static final XmpEditValues recentXmp = new XmpEditValues();
	private ImageView recentRating;
	private ImageView recentLabel;

	public interface RatingChangedListener
	{
		void onRatingChanged(Integer rating);
	}

	public interface LabelChangedListener
	{
		void onLabelChanged(String label);
	}

	public interface SubjectChangedListener
	{
		void onSubjectChanged(SubjectEntity[] subject);
	}

	private RatingChangedListener mRatingListener;
	private LabelChangedListener mLabelListener;
	private SubjectChangedListener mSubjectListener;

	public void setRatingListener(RatingChangedListener listener)
	{
		mRatingListener = listener;
	}

	public void setLabelListener(LabelChangedListener listener)
	{
		mLabelListener = listener;
	}

	public void setSubjectListener(SubjectChangedListener listener)
	{
		mSubjectListener = listener;
	}

	private MetaChangedListener mXmpChangedListener;
	public interface MetaChangedListener
	{
		void onMetaChanged(Integer rating, String label, SubjectEntity[] subject);
	}

	public void setListener(MetaChangedListener listener)
	{
		mXmpChangedListener = listener;
	}

	@Override
	protected void onXmpChanged(XmpValues xmp)
	{
		recentXmp.Subject = xmp.subject;
		recentXmp.Rating = formatRating(xmp.rating);
		recentXmp.Label = formatLabel(xmp.label);

		fireMetaUpdate();
	}

	private void fireMetaUpdate()
	{
		mXmpChangedListener.onMetaChanged(
				recentXmp.Rating,
				recentXmp.Label,
				recentXmp.Subject);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.xmp_edit_landscape, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);
		setExclusive(true);
		setAllowUnselected(true);
		attachButtons(view);
	}

	/**
	 * Convenience method for single select XmpLabelGroup
	 */
	private String getLabel()
	{
		String[] label = getColorLabels();
		return label != null ? label[0] : null;
	}

	/**
	 * Convenience method for single select XmpLabelGroup
	 */
	private Integer getRating()
	{
		Integer[] ratings = getRatings();
		return ratings != null ? ratings[0] : null;
	}

	protected Integer formatRating(Integer[] ratings)
	{
		if (ratings == null)
			return null;
		else
			return ratings[0];
	}

	protected Integer[] formatRating(Integer rating)
	{
		if (rating == null)
			return null;
		else
			return new Integer[]{rating};
	}

	protected String formatLabel(String[] labels)
	{
		if (labels == null)
			return null;
		else
			return labels[0];
	}

	protected String[] formatLabel(String label)
	{
		if (label == null)
			return null;
		else
			return new String[]{label};
	}

	/**
	 * Silently set xmp without firing listeners
	 */
	public void initXmp(Integer rating, SubjectEntity[] subject, String label)
	{
		super.initXmp(formatRating(rating),
				formatLabel(label),
				subject);
	}

	private void attachButtons(View parent)
	{
		parent.findViewById(R.id.clearMetaButton).setOnClickListener(v -> clear());
		parent.findViewById(R.id.recentMetaButton).setOnClickListener(v -> fireMetaUpdate());
		parent.findViewById(R.id.helpButton).setOnClickListener(v -> startTutorial());

		recentLabel = parent.findViewById(R.id.recentLabel);
		recentRating = parent.findViewById(R.id.recentRating);
	}

	@Override
	public void onKeywordsSelected(@NotNull Collection<? extends SubjectEntity> selectedKeywords) {
		recentXmp.Subject = getSubject();
		if (mSubjectListener != null)
			mSubjectListener.onSubjectChanged(recentXmp.Subject);
	}

	@Override
	public void onLabelSelectionChanged(List<XmpLabelGroup.Labels> checked)
	{
		if (checked.size() > 0)
		{
			switch (checked.get(0))
			{
				case blue:
					recentLabel.setImageTintList(ContextCompat.getColorStateList(getContext(), R.color.colorKeyBlue));
					break;
				case red:
					recentLabel.setImageTintList(ContextCompat.getColorStateList(getContext(), R.color.colorKeyRed));
					break;
				case green:
					recentLabel.setImageTintList(ContextCompat.getColorStateList(getContext(), R.color.colorKeyGreen));
					break;
				case yellow:
					recentLabel.setImageTintList(ContextCompat.getColorStateList(getContext(), R.color.colorKeyYellow));
					break;
				case purple:
					recentLabel.setImageTintList(ContextCompat.getColorStateList(getContext(), R.color.colorKeyPurple));
					break;
			}
		}
		else
		{
			recentLabel.setImageTintList(ContextCompat.getColorStateList(getContext(), R.color.white));
		}
		recentXmp.Label = getLabel();
		if (mLabelListener != null)
			mLabelListener.onLabelChanged(recentXmp.Label);
	}

	@Override
	public void onRatingSelectionChanged(List<Integer> checked)
	{
		recentXmp.Rating = getRating();
		if (mRatingListener != null)
			mRatingListener.onRatingChanged(recentXmp.Rating);

		if (recentXmp.Rating == null)
		{
			recentRating.setImageResource(R.drawable.ic_star_border);
			return;
		}

		switch (recentXmp.Rating)
		{
			case 5:
				recentRating.setImageResource(R.drawable.ic_star5);
				break;
			case 4:
				recentRating.setImageResource(R.drawable.ic_star4);
				break;
			case 3:
				recentRating.setImageResource(R.drawable.ic_star3);
				break;
			case 2:
				recentRating.setImageResource(R.drawable.ic_star2);
				break;
			case 1:
				recentRating.setImageResource(R.drawable.ic_star1);
				break;
			default:
				recentRating.setImageResource(R.drawable.ic_star_border);
				break;
		}
	}

	/**
	 * Default values indicate no xmp
	 */
	public static class XmpEditValues
	{
		public Integer Rating = null;
		public SubjectEntity[] Subject = null;
		public String Label = null;
	}

	private void startTutorial()
	{
		MaterialShowcaseSequence sequence = new MaterialShowcaseSequence(getActivity());

		View root = getView();
		if (root == null)
			return;

		// Sort group
		sequence.addSequenceItem(getRectangularView(
				root.findViewById(R.id.recentMetaButton),
				R.string.tutSetRecent));

		// Segregate
		sequence.addSequenceItem(getRectangularView(
				root.findViewById(R.id.clearMetaButton),
				R.string.tutClearMeta));

		// Rating
		sequence.addSequenceItem(getRectangularView(
				root.findViewById(R.id.metaLabelRating),
				R.string.tutSetRatingLabel));

		// Subject
		sequence.addSequenceItem(getRectangularView(
				root.findViewById(R.id.keywordFragment),
				R.string.tutSetSubject));

		// Match
		sequence.addSequenceItem(getRectangularView(
				root.findViewById(R.id.addKeyword),
				R.string.tutAddSubject));

		// Match
		sequence.addSequenceItem(getRectangularView(
				root.findViewById(R.id.editKeyword),
				R.string.tutEditSubject));

		sequence.start();
	}

	private MaterialShowcaseView getRectangularView(View target, @StringRes int contentId)
	{
		return getRectangularView(target,
				getString(R.string.editMetadata),
				getString(contentId),
				getString(R.string.ok));
	}

	private MaterialShowcaseView getRectangularView(View target, String title, String content, String dismiss)
	{
		return new MaterialShowcaseView.Builder(getActivity())
				.setTarget(target)
				.setTitleText(title)
				.setContentText(content)
				.setDismissOnTouch(true)
				.setDismissText(dismiss)
				.withRectangleShape()
				.build();
	}
}
