package com.anthonymandra.rawdroid;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

public class XmpEditFragment extends XmpBaseFragment
{
	private static final String TAG = XmpEditFragment.class.getSimpleName();
	public static final String FRAGMENT_TAG = "XmpEditFragment";

	private MetaChangedListener mListener;
	public interface MetaChangedListener
	{
		void onMetaChanged(Integer rating, String label, String[] subject);
	}

	public void setListener(MetaChangedListener listener)
	{
		mListener = listener;
	}

	@Override
	protected void onXmpChanged(XmpValues xmp)
	{
		super.onXmpChanged(xmp);

		recentXmp = new XmpEditValues();
		recentXmp.Subject = xmp.subject;
		recentXmp.Rating = formatRating(xmp.rating);
		recentXmp.Label = formatLabel(xmp.label);
		mListener.onMetaChanged(
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

	private void clearXmp()
	{
		setXmp((Integer) null, null, null);
		clear();
	}

	/**
	 * Convenience method for single select XmpLabelGroup
	 * @return
	 */
	private String getLabel()
	{
		String[] label = getColorLabels();
		return label != null ? label[0] : null;
	}

	/**
	 * Convenience method for single select XmpLabelGroup
	 * @return
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
	 * @param rating
	 * @param subject
	 * @param label
	 */
	public void initXmp(Integer rating, String[] subject, String label)
	{
		super.initXmp(formatRating(rating),
				formatLabel(label),
				subject);
	}

	public void setXmp(Integer rating, String[] subject, String label)
	{
		super.setXmp(formatRating(rating),
				formatLabel(label),
				subject);
	}

	private void attachButtons()
	{
		getActivity().findViewById(R.id.buttonReuseXmp).setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (recentXmp != null)
				{
					setXmp(recentXmp.Rating, recentXmp.Subject, recentXmp.Label);
				}
			}
		});
//TODO: Doesn't seem to be attaching!
		getActivity().findViewById(R.id.clearButton).setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				clearXmp();
			}
		});
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
