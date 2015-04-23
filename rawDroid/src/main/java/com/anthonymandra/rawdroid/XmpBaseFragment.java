package com.anthonymandra.rawdroid;

import android.app.Activity;
import android.app.Fragment;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.RatingBar.OnRatingBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.anthonymandra.content.Meta;
import com.anthonymandra.framework.GalleryActivity;
import com.anthonymandra.framework.ImageUtils;
import com.anthonymandra.widget.MultiSpinner;
import com.anthonymandra.widget.XmpLabelGroup;
import com.drew.metadata.Metadata;
import com.drew.metadata.xmp.XmpDirectory;
import com.drew.metadata.xmp.XmpWriter;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pl.polidea.treeview.AbstractTreeViewAdapter;
import pl.polidea.treeview.InMemoryTreeStateManager;
import pl.polidea.treeview.TreeBuilder;
import pl.polidea.treeview.TreeNodeInfo;
import pl.polidea.treeview.TreeStateManager;
import pl.polidea.treeview.TreeViewList;

public abstract class XmpBaseFragment extends Fragment
{
	private static final String TAG = XmpBaseFragment.class.getSimpleName();
	public static final String FRAGMENT_TAG = "XmpEditFragment";

	private Set<String> selectedKeywords = new HashSet<>();
	private RatingBar mRatingBar;
	private TreeAdapter mKeywordAdapter;
	private XmpLabelGroup colorKey;
	private MultiSpinner customKeywords;

	/**
	 * Since RatingBar must have a value this defines no value
	 */
	boolean isRatingNull = true;

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		mRatingBar = ((RatingBar) getActivity().findViewById(R.id.ratingBar));
		colorKey = (XmpLabelGroup) getActivity().findViewById(R.id.colorKey);
		customKeywords = (MultiSpinner) getActivity().findViewById(R.id.multiSpinnerKeywords);

		attachButtons();
		createTreeView();
	}

	protected void clear()
	{
		// Not sure how these can be null, but it happens from time to time
		if (selectedKeywords != null)
			selectedKeywords.clear();
		if (mKeywordAdapter != null)
			mKeywordAdapter.refresh();
		if (colorKey != null)
			colorKey.clearCheck();
		if (mRatingBar != null)
			mRatingBar.setRating(0);
		if (customKeywords != null)
			customKeywords.clearSelected();
		isRatingNull = true;
	}

	protected String[] getSubject()
	{
		Set<String>	allKeywords = new HashSet<>();
		allKeywords.addAll(selectedKeywords);
		allKeywords.addAll(customKeywords.getSelected());
		return allKeywords.toArray(new String[allKeywords.size()]);
	}

	protected double getRating()
	{
		if (isRatingNull)
			return Double.NaN;
		else
			return mRatingBar.getRating();
	}

	protected String[] getColorLabel()
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
		xmp.label = getColorLabel();
		xmp.rating = getRating();
		xmp.subject = getSubject();
		return xmp;
	}

	protected void setXmp(XmpValues xmp)
	{
		setColorLabel(xmp.label);
		setSubject(xmp.subject);
		setRating((float)xmp.rating);
	}

	protected void setRating(float rating)
	{
		if (!Double.isNaN(rating))
			mRatingBar.setRating(rating);
		else
		{
			isRatingNull = true;
		}
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

	protected void setMultiselect(boolean enable)
	{
		colorKey.setMultiselect(enable);
	}

	protected void setSubject(String[] subject)
	{
		if (subject != null)
		{
			selectedKeywords.addAll(Arrays.asList(subject));
			mKeywordAdapter.notifyDataSetChanged();
			customKeywords.setSelected(Arrays.asList(subject));
		}
	}

	private void createTreeView()
	{
		TreeStateManager<ParentChild> manager = new InMemoryTreeStateManager<>();
		TreeBuilder<ParentChild> tree = new TreeBuilder<>(manager);
		int numberOfLevels = 0;
		BufferedReader readbuffer = null;
		try
		{
			File keywords = GalleryActivity.getKeywordFile(getActivity());
			if (keywords == null)
				return;
			readbuffer = new BufferedReader(new FileReader(keywords));
			String line;
			SparseArray<String> parents = new SparseArray<>();
			parents.put(-1, null);
			while ((line = readbuffer.readLine()) != null)
			{
				String tokens[] = line.split("\t");
				int level = tokens.length - 1;
				String node = tokens[level];
				parents.put(level, node);
				numberOfLevels = Math.max(numberOfLevels, level + 1);
				ParentChild pair = new ParentChild<>(parents.get(level - 1), node);
				tree.sequentiallyAddNextNode(pair, level);
			}
		}
		catch (Exception e)
		{
			Toast.makeText(getActivity(), R.string.errorKeywordRequired, Toast.LENGTH_SHORT).show();
		}
		finally
		{
            Utils.closeSilently(readbuffer);
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
				onXmpChanged(getXmp());
			}
		});
		colorKey.setOnLabelSelectionChangedListener(new XmpLabelGroup.OnLabelSelectionChangedListener()
		{
			@Override
			public void onLabelSelectionChanged(List<XmpLabelGroup.Labels> checked)
			{
				onXmpChanged(getXmp());
			}
		});

		customKeywords.setSelection(0, false);		// This stops onItemSelected firing on creation
		customKeywords.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
		{
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
			{
				onXmpChanged(getXmp());
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent)
			{
				onXmpChanged(getXmp());
			}
		});
	}

	class TreeAdapter extends AbstractTreeViewAdapter<ParentChild> implements CompoundButton.OnCheckedChangeListener
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
			onXmpChanged(getXmp());
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
			View view = getActivity().getLayoutInflater().inflate(R.layout.treeview_item, null, false);
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

	public class XmpValues
	{
		public double rating;
		public String[] label;
		public String[] subject;
	}
}
