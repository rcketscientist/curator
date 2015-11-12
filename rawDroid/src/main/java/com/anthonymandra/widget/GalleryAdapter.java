package com.anthonymandra.widget;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.gallery3d.data.MediaItem;
import com.anthonymandra.content.Meta;
import com.anthonymandra.framework.ImageDecoder;
import com.anthonymandra.framework.ImageUtils;
import com.anthonymandra.framework.LocalImage;
import com.anthonymandra.rawdroid.R;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class GalleryAdapter extends CursorAdapter
{
	private final int purple;
	private final int blue;
	private final int yellow;
	private final int green;
	private final int red;

	private final Context mContext;
	private final LayoutInflater mInflater;
	protected int mItemHeight = 0;
	protected int mNumColumns = 0;
	protected GridView.LayoutParams mImageViewLayoutParams;
	private Set<Uri> mSelectedItems = new HashSet<>();
	private Set<Long> mSelectedIds = new HashSet<>();
	private TreeSet<Integer> mSelectedPositions = new TreeSet<>();

	private ImageDecoder mImageDecoder;
	private OnSelectionUpdatedListener mSelectionListener;

	public interface OnSelectionUpdatedListener
	{
		void onSelectionUpdated(Set<Uri> selectedUris);
	}

	public GalleryAdapter(Context context, Cursor c, ImageDecoder imageDecoder)
	{
		super(context, c, 0);
		mContext = context;
		mInflater = LayoutInflater.from(context);

		mImageDecoder = imageDecoder;

		purple = mContext.getResources().getColor(R.color.startPurple);
		blue = mContext.getResources().getColor(R.color.startBlue);
		yellow = mContext.getResources().getColor(R.color.startYellow);
		green = mContext.getResources().getColor(R.color.startGreen);
		red = mContext.getResources().getColor(R.color.startRed);

		mImageViewLayoutParams = new GridView.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);

		if (getNumColumns() == 0)
		{
			final int displayWidth = mContext.getResources().getDisplayMetrics().widthPixels;
			final int thumbSize = mContext.getResources().getDimensionPixelSize(R.dimen.image_thumbnail_size);
			final int thumbSpacing = mContext.getResources().getDimensionPixelSize(R.dimen.image_thumbnail_spacing);
			final int numColumns = (int) Math.floor(displayWidth / (thumbSize + thumbSpacing));
			if (numColumns > 0)
			{
				final int columnWidth = (displayWidth / numColumns) - thumbSpacing;
				setNumColumns(numColumns);
				setItemHeight(columnWidth);
			}
		}
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		final View view = mInflater.inflate(R.layout.fileview, parent, false);
		view.setLayoutParams(mImageViewLayoutParams);

		LoadingImageView image = (LoadingImageView) view.findViewById(R.id.webImageView);
		image.setScaleType(ImageView.ScaleType.CENTER_CROP);

		view.setTag(R.id.webImageView, image);
		view.setTag(R.id.filenameView, view.findViewById(R.id.filenameView));
		view.setTag(R.id.galleryRatingBar, view.findViewById(R.id.galleryRatingBar));
		view.setTag(R.id.label, view.findViewById(R.id.label));
		return view;
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor)
	{
		LoadingImageView imageView = (LoadingImageView) view.getTag(R.id.webImageView);
		TextView fileName = (TextView) view.getTag(R.id.filenameView);
		View label = (View) view.getTag(R.id.label);
		android.widget.RatingBar ratingBar= (android.widget.RatingBar) view.getTag(R.id.galleryRatingBar);

		final int rotation = ImageUtils.getRotation(cursor.getInt(Meta.ORIENTATION_COLUMN));
		final Float rating = Float.valueOf(cursor.getFloat(Meta.RATING_COLUMN));
		final Uri uri = Uri.parse(cursor.getString(Meta.URI_COLUMN));
		final String labelString = cursor.getString(Meta.LABEL_COLUMN);

		label.setTag(labelString);
		ratingBar.setTag(rating);
		fileName.setTag(uri);

		imageView.setRotation(rotation);
		ratingBar.setRating(cursor.getFloat(cursor.getColumnIndex(Meta.Data.RATING)));

		if (labelString != null)
		{
			switch (labelString.toLowerCase())
			{
				case "purple":
					view.setBackgroundColor(purple);
					label.setVisibility(View.VISIBLE);
					label.setBackgroundColor(purple);
					break;
				case "blue":
					view.setBackgroundColor(blue);
					label.setVisibility(View.VISIBLE);
					label.setBackgroundColor(blue);
					break;
				case "yellow":
					view.setBackgroundColor(yellow);
					label.setVisibility(View.VISIBLE);
					label.setBackgroundColor(yellow);
					break;
				case "green":
					view.setBackgroundColor(green);
					label.setVisibility(View.VISIBLE);
					label.setBackgroundColor(green);
					break;
				case "red":
					view.setBackgroundColor(red);
					label.setVisibility(View.VISIBLE);
					label.setBackgroundColor(red);
					break;
				default:
					view.setBackgroundColor(0);
					label.setVisibility(View.GONE);
					break;
			}
		}
		else
		{
			view.setBackgroundColor(0);
			label.setVisibility(View.GONE);
		}
		File image = new File(uri.getPath());
		fileName.setText(cursor.getString(cursor.getColumnIndex(Meta.Data.NAME)));
		mImageDecoder.loadImage(new LocalImage(mContext, image), imageView);
		((Checkable) view).setChecked(mSelectedItems.contains(uri));
	}

	public MediaItem getImage(int position)
	{
		return new LocalImage(mContext, new File(getUri(position).getPath()));
	}

	public Uri getUri(int position)
	{
		Cursor c = (Cursor)getItem(position);
		return Uri.parse(c.getString((c.getColumnIndex(Meta.Data.URI))));
	}

	public List<Uri> getSelectedItems()
	{
		return new ArrayList<Uri>(mSelectedItems);
	}

	public int getSelectedItemCount()
	{
		return mSelectedItems.size();
	}

	public void setItemHeight(int height)
	{
		if (height == mItemHeight)
		{
			return;
		}
		mItemHeight = height;
		mImageViewLayoutParams = new GridView.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, mItemHeight);

		if (mImageDecoder != null)
			mImageDecoder.setImageSize(height);

		notifyDataSetChanged();
	}

	public void setNumColumns(int numColumns)
	{
		mNumColumns = numColumns;
	}

	public int getNumColumns()
	{
		return mNumColumns;
	}

	public void addBetween(int start, int end)
	{
		for (int i = start; i <= end; i++)
		{
			if (getCursor().moveToPosition(i))
			{
				addSelection(getUri(i), i);
			}
		}
		updateSelection();
		notifyDataSetChanged();
	}

	public void addBetweenSelection(int position)
	{
		if (mSelectedPositions.size() > 0)
		{
			int first = mSelectedPositions.first();
			int last = mSelectedPositions.last();
			if (position > last)
			{
				addBetween(last, position);
			}
			else if (position < first)
			{
				addBetween(position, first);
			}
		}
		else
		{
			addBetween(0, position);
		}
	}

	/**
	 * Add a selection and update the view
	 * @param uri of selection
	 * @param position of selection
	 */
	private void addSelection(View view, Uri uri, int position)
	{
		addSelection(uri, position);
		((Checkable)view).setChecked(true);
	}

	/**
	 * Add a selection without updating the view
	 * This will generally require a call to notifyDataSetChanged()
	 * @param uri of selection
	 * @param position of selection
	 */
	private void addSelection(Uri uri, int position)
	{
		mSelectedItems.add(uri);
		mSelectedPositions.add(position);
		mSelectedIds.add(getItemId(position));
	}

	private void removeSelection(View view, Uri uri, int position)
	{
		mSelectedItems.remove(uri);
		mSelectedPositions.remove(position);
		mSelectedIds.remove(getItemId(position));
		((Checkable)view).setChecked(false);
	}

	public void clearSelection()
	{
		mSelectedItems.clear();
		mSelectedPositions.clear();
		updateSelection();
		notifyDataSetChanged();
	}

	public void toggleSelection(View v, int position)
	{
		Uri uri = getUri(position);
		if (mSelectedItems.contains(uri))
		{
			removeSelection(v, uri, position);
		}
		else
		{
			addSelection(v, uri, position);
		}
		updateSelection();
	}

	public void updateSelection()
	{
		if (mSelectionListener != null)
			mSelectionListener.onSelectionUpdated(mSelectedItems);
	}

	public void selectAll()
	{
		if (getCursor().moveToFirst())
		{
			do
			{
				mSelectedItems.add(getUri(getCursor().getPosition()));
			} while (getCursor().moveToNext());
		}

		updateSelection();
		notifyDataSetChanged();
	}

	public void setOnSelectionListener(OnSelectionUpdatedListener listener)
	{
		mSelectionListener = listener;
	}
}