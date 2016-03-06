package com.anthonymandra.rawdroid;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.anthonymandra.widget.XmpLabelGroup;

import java.util.Collection;
import java.util.List;

public class XmpEditFragment extends XmpBaseFragment
{
	private static final String TAG = XmpEditFragment.class.getSimpleName();
	public static final String FRAGMENT_TAG = "XmpEditFragment";

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
		void onSubjectChanged(String[] subject);
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
		void onMetaChanged(Integer rating, String label, String[] subject);
	}

	public void setListener(MetaChangedListener listener)
	{
		mXmpChangedListener = listener;
	}

	@Override
	protected void onXmpChanged(XmpValues xmp)
	{
		recentXmp = new XmpEditValues();
		recentXmp.Subject = xmp.subject;
		recentXmp.Rating = formatRating(xmp.rating);
		recentXmp.Label = formatLabel(xmp.label);
		mXmpChangedListener.onMetaChanged(
				recentXmp.Rating,
				recentXmp.Label,
				recentXmp.Subject);
	}

	XmpEditValues recentXmp;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.xmp_edit_landscape, container, false);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		attachButtons();
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

	public void setRating(int rating)
	{
		super.setRating(formatRating(rating));
	}

	public void setLabel(String label)
	{
		super.setColorLabel(formatLabel(label));
	}

	public void setSubject(String[] subject)
	{
		super.setSubject(subject);
	}

	/**
	 * Silently set xmp without firing listeners
	 */
	public void initXmp(Integer rating, String[] subject, String label)
	{
		super.initXmp(formatRating(rating),
				formatLabel(label),
				subject);
	}

	private void attachButtons()
	{
		getActivity().findViewById(R.id.clearButton).setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				clear();
			}
		});
	}

	@Override
	public void onKeywordsSelected(Collection<String> selectedKeywords)
	{
		if (mSubjectListener != null)
			mSubjectListener.onSubjectChanged(getSubject());
	}

	@Override
	public void onLabelSelectionChanged(List<XmpLabelGroup.Labels> checked)
	{
		if (mLabelListener != null)
			mLabelListener.onLabelChanged(getLabel());
	}

	@Override
	public void onRatingSelectionChanged(List<Integer> checked)
	{
		if (mRatingListener != null)
			mRatingListener.onRatingChanged(getRating());
	}

	/**
	 * Default values indicate no xmp
	 */
	public static class XmpEditValues
	{
		public Integer Rating = null;
		public String[] Subject = null;
		public String Label = null;
	}
}
