package com.anthonymandra.rawdroid;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import pl.polidea.treeview.AbstractTreeViewAdapter;
import pl.polidea.treeview.InMemoryTreeStateManager;
import pl.polidea.treeview.TreeBuilder;
import pl.polidea.treeview.TreeNodeInfo;
import pl.polidea.treeview.TreeStateManager;
import pl.polidea.treeview.TreeViewList;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.RatingBar.OnRatingBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.anthonymandra.framework.GalleryActivity;
import com.anthonymandra.framework.MetaObject;
import com.anthonymandra.widget.MultiSpinner;

public class XmpFragment extends SherlockFragment
{
	private static final String TAG = XmpFragment.class.getSimpleName();
	public static final String FRAGMENT_TAG = "XmpFragment";
	
	Set<String> selectedKeywords = new HashSet<String>();
	RatingBar mRatingBar;
	TreeAdapter mKeywordAdapter;
	MetaObject mMedia;
	RadioGroup colorKey;
	MultiSpinner customKeywords;

	double lastRating;
	String[] lastSubject;
	String lastLabel;
	/**
	 * Since RatingBar must have a value this defines no value
	 */
	private boolean hasWritten = false;
	boolean isRatingNull = true;
	boolean isCleared = false;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		// Inflate the layout for this fragment
		View view;
		boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
		isPortrait = isPortrait && getResources().getBoolean(R.bool.hasTwoPanes) || !isPortrait && !getResources().getBoolean(R.bool.hasTwoPanes);// Single pane devices use the opposite bar full screen
		
		if (isPortrait)	// Single pane devices use the opposite bar full screen
		{
			view = inflater.inflate(R.layout.xmp_fragment_portrait, container, false);
		}
		else
		{
			view = inflater.inflate(R.layout.xmp_fragment_landscape, container, false);
		}
		
		return view;
	}

	public static XmpFragment newInstance(MetaObject media)
	{
		XmpFragment fragment = new XmpFragment();
		fragment.initialize(media);
		return fragment;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		// setRetainInstance(true);

		mRatingBar = ((RatingBar) getActivity().findViewById(R.id.ratingBar));
		colorKey = (RadioGroup) getActivity().findViewById(R.id.radioGroupColorKey);
		customKeywords = (MultiSpinner) getActivity().findViewById(R.id.multiSpinnerKeywords);

		attachButtons();
		createTreeView();
		populateXmp();
	}

	@Override
	public void onStop()
	{
		super.onStop();
		writeCurrentXmp();
	}

	private void initialize(MetaObject media)
	{
		mMedia = media;
	}

	public void setMediaObject(MetaObject media)
	{
		writeCurrentXmp();
		clear();
		initialize(media);
		populateXmp();
	}

	private void clear()
	{
		// Not sure how these can be null, but it happens from time to time
		if (selectedKeywords != null)
			selectedKeywords.clear();
		if (mKeywordAdapter != null)
			mKeywordAdapter.refresh();
		// mKeywordAdapter.notifyDataSetChanged();
		if (colorKey != null)
			colorKey.clearCheck();
		if (mRatingBar != null)
			mRatingBar.setRating(0);
		if (customKeywords != null)
			customKeywords.clearSelected();
		isRatingNull = true;
	}

	private void writeCurrentXmp()
	{
		// Obviously don't write if media doesn't exist
		if (mMedia == null)
		{
			return;
		}

		// Avoid writing blank xmp on already empty xmp, but
		// Allow writing blank when cleared
		if (!hasModifications())
		{
//			Log.d(TAG, "No modifications: " + mMedia.getName());
			return;			
		}

		lastLabel = getColorLabel();
		lastRating = getRating();
		lastSubject = getSubject();
		hasWritten = true;

		setLastXmp();
//		Log.d(TAG, "Modifications: " + mMedia.getName());
		mMedia.writeXmp();
	}
	
	private String[] getSubject()
	{
		Set<String>	allKeywords = new HashSet<String>();
		allKeywords.addAll(selectedKeywords);
		allKeywords.addAll(customKeywords.getSelected());
		return allKeywords.toArray(new String[0]);
	}

	private void setLastXmp()
	{
		mMedia.setLabel(lastLabel);
		mMedia.setRating(lastRating);
		mMedia.setSubject(lastSubject);
	}

//	private boolean hasExistingXmp()
//	{
//		if (mMedia.getLabel() != null)
//			return true;
//		if (mMedia.getSubject() != null)
//			return true;
//		if (Double.isNaN(mMedia.getRating()))
//			return true;
//		return false;
//	}

	private boolean hasModifications()
	{
		double widgetRating = getRating();
		String widgetLabel = getColorLabel();
		String[] widgetSubject = getSubject();
		
		double metaRating = mMedia.getRating();
		String metaLabel = mMedia.getLabel();
		String[] metaSubject = mMedia.getSubject();
		
		boolean bothLabelNull = widgetLabel == null && metaLabel == null;
		boolean bothRatingNaN = Double.isNaN(widgetRating) && Double.isNaN(widgetRating);
		boolean bothSubjectEmpty = widgetSubject.length == 0 && metaSubject == null;
		
		if (!bothRatingNaN && widgetRating != metaRating)
			return true;
		if (!bothLabelNull)
		{
			if (widgetLabel != null)
			{
				if (!widgetLabel.equals(metaLabel))
				{
					return true;
				}
			}
			else
			{
				return true;
			}
		}
		if (!bothSubjectEmpty && !Arrays.equals(widgetSubject, metaSubject))
			return true;
		return false;
	}

	private double getRating()
	{
		if (isRatingNull)
			return Double.NaN;
		else
			return mRatingBar.getRating();
	}

	private String getColorLabel()
	{
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
		int checkedId = colorKey.getCheckedRadioButtonId();
		switch (checkedId)
		{
			case R.id.radioBlue:
				return sp.getString(FullSettingsActivity.KEY_XmpBlue, "Blue");
			case R.id.radioRed:
				return sp.getString(FullSettingsActivity.KEY_XmpRed, "Red");
			case R.id.radioGreen:
				return sp.getString(FullSettingsActivity.KEY_XmpGreen, "Green");
			case R.id.radioYellow:
				return sp.getString(FullSettingsActivity.KEY_XmpYellow, "Yellow");
			case R.id.radioPurple:
				return sp.getString(FullSettingsActivity.KEY_XmpPurple, "Purple");
			default:
				return null;
		}
	}

	private void populateXmp()
	{
		if (mMedia == null)
		{
			onDestroy();
			return;
		}
		String label = mMedia.getLabel();
		if (label != null)
		{
			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
			String red = sp.getString(FullSettingsActivity.KEY_XmpRed, "Red");
			String blue = sp.getString(FullSettingsActivity.KEY_XmpBlue, "Blue");
			String green = sp.getString(FullSettingsActivity.KEY_XmpGreen, "Green");
			String yellow = sp.getString(FullSettingsActivity.KEY_XmpYellow, "Yellow");
			String purple = sp.getString(FullSettingsActivity.KEY_XmpPurple, "Purple");
			// String color = label.toLowerCase(Locale.US);
			if (label.equals(blue))
			{
				colorKey.check(R.id.radioBlue);
			}
			else if (label.equals(red))
			{
				colorKey.check(R.id.radioRed);
			}
			else if (label.equals(yellow))
			{
				colorKey.check(R.id.radioYellow);
			}
			else if (label.equals(green))
			{
				colorKey.check(R.id.radioGreen);
			}
			else if (label.equals(purple))
			{
				colorKey.check(R.id.radioPurple);
			}
			else
			{
				Toast.makeText(this.getActivity(), label + " " + getString(R.string.warningInvalidLabel), Toast.LENGTH_LONG).show();
			}
		}

		String[] subject = mMedia.getSubject();
		if (subject != null)
		{
			selectedKeywords.addAll(Arrays.asList(subject));
			mKeywordAdapter.notifyDataSetChanged();
			customKeywords.setSelected(Arrays.asList(subject));
		}

		double rating = mMedia.getRating();
		if (!Double.isNaN(rating))
			mRatingBar.setRating((float) rating);
		else
		{
			isRatingNull = true;
		}
	}

	private void createTreeView()
	{
		TreeStateManager<ParentChild> manager = new InMemoryTreeStateManager<ParentChild>();
		TreeBuilder<ParentChild> tree = new TreeBuilder<ParentChild>(manager);
		int numberOfLevels = 0;
		BufferedReader readbuffer = null;
		try
		{
			File keywords = GalleryActivity.getKeywordFile(getActivity());
			if (keywords == null)
				return;
			readbuffer = new BufferedReader(new FileReader(keywords));
			String line;
			HashMap<Integer, String> parents = new HashMap<Integer, String>();
			parents.put(-1, null);
			while ((line = readbuffer.readLine()) != null)
			{
				String tokens[] = line.split("\t");
				int level = tokens.length - 1;
				String node = tokens[level];
				parents.put(level, node);
				numberOfLevels = Math.max(numberOfLevels, level + 1);
				ParentChild pair = new ParentChild<String>(parents.get(level - 1), node);
				tree.sequentiallyAddNextNode(pair, level);
			}
		}
		catch (Exception e)
		{
			Toast.makeText(getActivity(), R.string.errorKeywordRequired, Toast.LENGTH_SHORT).show();
		}
		finally
		{
			try
			{
				if (readbuffer != null)
					readbuffer.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		manager.collapseChildren(null);

		TreeViewList treeView = (TreeViewList) getActivity().findViewById(R.id.keywordTreeView);
		mKeywordAdapter = new TreeAdapter(getActivity(), manager, numberOfLevels);

		if (numberOfLevels > 0)
		{
			treeView.setAdapter(mKeywordAdapter);
			treeView.setCollapsedDrawable(getResources().getDrawable(R.drawable.plus_white));
			treeView.setExpandedDrawable(getResources().getDrawable(R.drawable.minus_white));
		}
	}

	private void attachButtons()
	{
		// Ratings
		mRatingBar.setOnRatingBarChangeListener(new OnRatingBarChangeListener()
		{
			@Override
			public void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser)
			{
				isRatingNull = false;
			}
		});

		getActivity().findViewById(R.id.buttonReuseXmp).setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (hasWritten)
				{
					clear();
					setLastXmp();
					populateXmp();
				}
			}
		});

		getActivity().findViewById(R.id.buttonClear).setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				mMedia.clearXmp();
				clear();
			}
		});
	}

	class TreeAdapter extends AbstractTreeViewAdapter<ParentChild> implements android.widget.CompoundButton.OnCheckedChangeListener
	{
		protected int iconWidth = 0;

		public TreeAdapter(final Activity host, final TreeStateManager<ParentChild> treeStateManager, final int numberOfLevels)
		{
			super(host, treeStateManager, numberOfLevels);
		}

		@Override
		public void setCollapsedDrawable(Drawable collapsedDrawable)
		{
			iconWidth = Math.max(iconWidth, collapsedDrawable.getIntrinsicWidth());
			super.setCollapsedDrawable(collapsedDrawable);
		}

		@Override
		public void setExpandedDrawable(Drawable expandedDrawable)
		{
			iconWidth = Math.max(iconWidth, expandedDrawable.getIntrinsicWidth());
			super.setExpandedDrawable(expandedDrawable);
		}

		@Override
		protected int calculateIndentation(TreeNodeInfo<ParentChild> nodeInfo)
		{
			return getIndentWidth() * nodeInfo.getLevel() + iconWidth;
		}

		@Override
		protected void calculateIndentWidth()
		{
			// No need to calculate
		}

		@Override
		public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked)
		{
			final String id = (String) buttonView.getTag();
			changeSelected(isChecked, id);
		}

		private void changeSelected(final boolean isChecked, final String id)
		{
			if (isChecked)
			{
				selectedKeywords.add(id);
			}
			else
			{
				selectedKeywords.remove(id);
			}
		}

		@Override
		public long getItemId(int position)
		{
			return 0;
		}

		@Override
		public View getNewChildView(TreeNodeInfo treeNodeInfo)
		{
			View view = getActivity().getLayoutInflater().inflate(R.layout.treeview_item, null);
			return updateView(view, treeNodeInfo);
		}

		@Override
		public View updateView(View view, TreeNodeInfo treeNodeInfo)
		{
			final TextView descriptionView = (TextView) view.findViewById(R.id.textViewTreeViewDescription);

			String name = (String) ((ParentChild) treeNodeInfo.getId()).getChild();
			boolean selected = selectedKeywords.contains(name);
			descriptionView.setText(name);
			final CheckBox box = (CheckBox) view.findViewById(R.id.checkBoxTreeView);
			box.setTag(name);
			box.setOnCheckedChangeListener(null); // We don't want view updates to loop check change
			box.setChecked(selected);
			box.setOnCheckedChangeListener(this);
			return view;
		}

		@Override
		public void handleItemClick(final View view, final Object id)
		{
			final ParentChild pairId = (ParentChild) id;
			final TreeNodeInfo<ParentChild> info = getManager().getNodeInfo(pairId);
			if (info.isWithChildren())
			{
				super.handleItemClick(view, id);
			}
			else
			{
				final ViewGroup vg = (ViewGroup) view;
				final CheckBox cb = (CheckBox) vg.findViewById(R.id.checkBoxTreeView);
				cb.performClick();
			}
		}
	}
}
