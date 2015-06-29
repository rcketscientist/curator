package com.anthonymandra.rawdroid;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.Toast;

import com.anthonymandra.widget.RatingBar;
import com.anthonymandra.widget.XmpLabelGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class XmpBaseFragment extends Fragment
{
	private static final String TAG = XmpBaseFragment.class.getSimpleName();

	private RatingBar mRatingBar;
	private XmpLabelGroup colorKey;
	private KeywordBaseFragment mKeywordFragment;
	private boolean mPauseListener = false;

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);
		mRatingBar = ((RatingBar) view.findViewById(R.id.ratingBar));
		colorKey = (XmpLabelGroup) view.findViewById(R.id.colorKey);
		mKeywordFragment = (KeywordBaseFragment) getChildFragmentManager().findFragmentById(R.id.keywordFragment);
		attachButtons();
	}

	protected void clear()
	{
		mKeywordFragment.clearSelectedKeywords();
		if (colorKey != null)
			colorKey.clearCheck();
		if (mRatingBar != null)
			mRatingBar.clearCheck();
	}

	protected String[] getSubject()
	{
		Collection<String> selectedKeywords = mKeywordFragment.getSelectedKeywords();
		if (selectedKeywords.size() == 0)
			return null;
		return selectedKeywords.toArray(new String[selectedKeywords.size()]);
	}

	protected Integer[] getRatings()
	{
		List<Integer> ratings = mRatingBar.getCheckedRatings();
		if (ratings.size() == 0)
			return null;
		return ratings.toArray(new Integer[ratings.size()]);
	}

	protected String[] getColorLabels()
	{
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
		List<XmpLabelGroup.Labels> checked = colorKey.getCheckedLabels();

		if (checked.size() == 0)
			return null;

		List<String> labels = new ArrayList<>();
		for (XmpLabelGroup.Labels check : checked)
		{
			switch (check)
			{
				case blue:
					labels.add(sp.getString(FullSettingsActivity.KEY_XmpBlue, "Blue"));
					break;
				case red:
					labels.add(sp.getString(FullSettingsActivity.KEY_XmpRed, "Red"));
					break;
				case green:
					labels.add(sp.getString(FullSettingsActivity.KEY_XmpGreen, "Green"));
					break;
				case yellow:
					labels.add(sp.getString(FullSettingsActivity.KEY_XmpYellow, "Yellow"));
					break;
				case purple:
					labels.add(sp.getString(FullSettingsActivity.KEY_XmpPurple, "Purple"));
					break;
			}
		}
		return labels.toArray(new String[labels.size()]);
	}

	protected XmpValues getXmp()
	{
		XmpValues xmp = new XmpValues();
		xmp.label = getColorLabels();
		xmp.rating = getRatings();
		xmp.subject = getSubject();
		return xmp;
	}

	protected void onXmpChanged(XmpValues xmp) { }

	protected void setColorLabel(String[] labels)
	{
		if (labels != null)
		{
			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
			String red = sp.getString(FullSettingsActivity.KEY_XmpRed, "Red");
			String blue = sp.getString(FullSettingsActivity.KEY_XmpBlue, "Blue");
			String green = sp.getString(FullSettingsActivity.KEY_XmpGreen, "Green");
			String yellow = sp.getString(FullSettingsActivity.KEY_XmpYellow, "Yellow");
			String purple = sp.getString(FullSettingsActivity.KEY_XmpPurple, "Purple");

			for (String label : labels)
			{
				if (label.equals(blue))
				{
					colorKey.setChecked(XmpLabelGroup.Labels.blue, true);
				} else if (label.equals(red))
				{
					colorKey.setChecked(XmpLabelGroup.Labels.red, true);
				} else if (label.equals(yellow))
				{
					colorKey.setChecked(XmpLabelGroup.Labels.yellow, true);
				} else if (label.equals(green))
				{
					colorKey.setChecked(XmpLabelGroup.Labels.green, true);
				} else if (label.equals(purple))
				{
					colorKey.setChecked(XmpLabelGroup.Labels.purple, true);
				} else
				{
					Toast.makeText(this.getActivity(), label + " " + getString(R.string.warningInvalidLabel), Toast.LENGTH_LONG).show();
				}
			}
		}
	}

	protected void setXmp(XmpValues xmp)
	{
		setXmp(xmp.rating, xmp.label, xmp.subject);
	}

	protected void setXmp(Integer[] rating, String[] label, String[] subject)
	{
		mPauseListener = true;
		setColorLabel(label);
		setSubject(subject);
		setRating(rating);
		mPauseListener = false;
		dispatchChanges();
	}

	/**
	 * Silently set the xmp without firing onXmpChanged
	 * @param xmp
	 */
	protected void initXmp(XmpValues xmp)
	{
		mPauseListener = true;
		setXmp(xmp);
		mPauseListener = false;
	}

	/**
	 * Silently set the xmp without firing onXmpChanged
	 * @param rating
	 * @param label
	 * @param subject
	 */
	protected void initXmp(Integer[] rating, String[] label, String[] subject)
	{
		mPauseListener = true;
		setColorLabel(label);
		setSubject(subject);
		setRating(rating);
		mPauseListener = false;
	}

	protected void setRating(Integer[] ratings)
	{
		if (ratings != null)
			mRatingBar.setRating(ratings);
		else
			mRatingBar.clearCheck();
	}

	protected void setMultiselect(boolean enable)
	{
		colorKey.setMultiselect(enable);
		mRatingBar.setMultiselect(enable);
	}

	protected void setSubject(String[] subject)
	{
		if (subject != null)
		{
			mKeywordFragment.setSelectedKeywords(Arrays.asList(subject));
		}
		else
		{
			mKeywordFragment.clearSelectedKeywords();
		}
	}

	private void attachButtons()
	{
		// Ratings
		mRatingBar.setOnRatingSelectionChangedListener(new RatingBar.OnRatingSelectionChangedListener()
		{
			@Override
			public void onRatingSelectionChanged(List<Integer> checked)
			{
				dispatchChanges();
			}
		});
		colorKey.setOnLabelSelectionChangedListener(new XmpLabelGroup.OnLabelSelectionChangedListener()
		{
			@Override
			public void onLabelSelectionChanged(List<XmpLabelGroup.Labels> checked)
			{
				dispatchChanges();
			}
		});
		mKeywordFragment.setOnKeywordsSelectedListener(new KeywordEditFragment.OnKeywordsSelectedListener()
		{
			@Override
			public void onKeywordsSelected(Collection<String> selectedKeywords)
			{
				dispatchChanges();
			}
		});
	}

	private void dispatchChanges()
	{
		if (!mPauseListener)
			onXmpChanged(getXmp());
	}

	public static class XmpValues
	{
		public Integer[] rating;
		public String[] label;
		public String[] subject;
	}
}
