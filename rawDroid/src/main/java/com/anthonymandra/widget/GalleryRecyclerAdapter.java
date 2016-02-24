package com.anthonymandra.widget;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.gallery3d.data.MediaItem;
import com.anthonymandra.content.Meta;
import com.anthonymandra.framework.ImageDecoder;
import com.anthonymandra.framework.LocalImage;
import com.anthonymandra.rawdroid.R;
import com.anthonymandra.util.ImageUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class GalleryRecyclerAdapter extends CursorRecyclerAdapter<GalleryRecyclerAdapter.ViewHolder>
{
	private final int purple;
	private final int blue;
	private final int yellow;
	private final int green;
	private final int red;

	private final Context mContext;
	private Set<Uri> mSelectedItems = new HashSet<>();
	private Set<Long> mSelectedIds = new HashSet<>();
	private TreeSet<Integer> mSelectedPositions = new TreeSet<>();

	private ImageDecoder mImageDecoder;

	private OnSelectionUpdatedListener mSelectionListener;
	private OnItemClickListener mOnItemClickListener;
	private OnItemLongClickListener mOnItemLongClickListener;

	/**
	 * Interface definition for a callback to be invoked when an item in this
	 * AdapterView has been clicked.
	 */
	public interface OnItemClickListener {

		/**
		 * Callback method to be invoked when an item in this AdapterView has
		 * been clicked.
		 * <p>
		 * Implementers can call getItemAtPosition(position) if they need
		 * to access the data associated with the selected item.
		 *
		 * @param parent The RecyclerView adapter where the click happened.
		 * @param view The view within the AdapterView that was clicked (this
		 *            will be a view provided by the adapter)
		 * @param position The position of the view in the adapter.
		 * @param id The row id of the item that was clicked.
		 */
		void onItemClick(RecyclerView.Adapter<?> parent, View view, int position, long id);
	}

	/**
	 * Register a callback to be invoked when an item in this AdapterView has
	 * been clicked.
	 *
	 * @param listener The callback that will be invoked.
	 */
	public void setOnItemClickListener(OnItemClickListener listener) {
		mOnItemClickListener = listener;
	}

	/**
	 * @return The callback to be invoked with an item in this AdapterView has
	 *         been clicked, or null id no callback has been set.
	 */
	public final OnItemClickListener getOnItemClickListener() {
		return mOnItemClickListener;
	}

	/**
	 * Interface definition for a callback to be invoked when an item in this
	 * view has been clicked and held.
	 */
	public interface OnItemLongClickListener {
		/**
		 * Callback method to be invoked when an item in this view has been
		 * clicked and held.
		 *
		 * Implementers can call getItemAtPosition(position) if they need to access
		 * the data associated with the selected item.
		 *
		 * @param parent The RecyclerView adapter where the click happened
		 * @param view The view within the AbsListView that was clicked
		 * @param position The position of the view in the list
		 * @param id The row id of the item that was clicked
		 *
		 * @return true if the callback consumed the long click, false otherwise
		 */
		boolean onItemLongClick(RecyclerView.Adapter<?> parent, View view, int position, long id);
	}

	/**
	 * Register a callback to be invoked when an item in this AdapterView has
	 * been clicked and held
	 *
	 * @param listener The callback that will run
	 */
	public void setOnItemLongClickListener(OnItemLongClickListener listener) {
		mOnItemLongClickListener = listener;
	}

	/**
	 * @return The callback to be invoked with an item in this AdapterView has
	 *         been clicked and held, or null id no callback as been set.
	 */
	public final OnItemLongClickListener getOnItemLongClickListener() {
		return mOnItemLongClickListener;
	}

	public interface OnSelectionUpdatedListener
	{
		void onSelectionUpdated(Set<Uri> selectedUris);
	}

	public static class ViewHolder extends RecyclerView.ViewHolder
	{
		public LoadingImageView mImageView;
		public TextView mFileName;
		public View mLabel;
		public android.widget.RatingBar mRatingBar;
		public CheckableRelativeLayout mBaseView;
		public ImageView mXmpView;

		public ViewHolder(View view)
		{
			super(view);

			mImageView = (LoadingImageView) view.findViewById(R.id.webImageView);
			mImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
			mFileName = (TextView) view.findViewById(R.id.filenameView);
			mLabel = view.findViewById(R.id.label);
			mRatingBar= (android.widget.RatingBar) view.findViewById(R.id.galleryRatingBar);
			mBaseView = (CheckableRelativeLayout) view.findViewById(R.id.fileView2);
			mXmpView = (ImageView) view.findViewById(R.id.xmp);
		}
	}

	public static class GalleryItem
	{
		private String name;
		private int  rotation;
		private float rating;
		private String label;
		private Uri uri;
		private boolean hasSubject;

		public static GalleryItem fromCursor(Context c, Cursor cursor)
		{
			GalleryItem item = new GalleryItem();
			item.rotation = ImageUtils.getRotation(cursor.getInt(Meta.ORIENTATION_COLUMN));
			item.rating = cursor.getFloat(Meta.RATING_COLUMN);
			item.uri = Uri.parse(cursor.getString(Meta.URI_COLUMN));
			item.label = cursor.getString(Meta.LABEL_COLUMN);
			item.name = cursor.getString(Meta.NAME_COLUMN);
			item.hasSubject = cursor.getString(Meta.SUBJECT_COLUMN) != null;
			return item;
		}
	}

	public GalleryRecyclerAdapter(Context context, Cursor c, ImageDecoder imageDecoder)
	{
		super(c);
		mContext = context;

		mImageDecoder = imageDecoder;

		purple = mContext.getResources().getColor(R.color.startPurple);
		blue = mContext.getResources().getColor(R.color.startBlue);
		yellow = mContext.getResources().getColor(R.color.startYellow);
		green = mContext.getResources().getColor(R.color.startGreen);
		red = mContext.getResources().getColor(R.color.startRed);
	}

	@Override
	public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
	{
		View itemView = LayoutInflater.from(mContext).inflate(R.layout.fileview, parent, false);
		ViewHolder vh = new ViewHolder(itemView);
		return vh;
	}

	@Override
	public void onBindViewHolder(ViewHolder vh, final int position, Cursor cursor)
	{
		if (mOnItemClickListener != null)
		{
			vh.mBaseView.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					mOnItemClickListener.onItemClick(GalleryRecyclerAdapter.this, v, position, getItemId(position));
				}
			});
		}

		if (mOnItemLongClickListener != null)
		{
			vh.mBaseView.setOnLongClickListener(new View.OnLongClickListener()
			{
				@Override
				public boolean onLongClick(View v)
				{
					return mOnItemLongClickListener.onItemLongClick(GalleryRecyclerAdapter.this, v, position, getItemId(position));
				}
			});
		}

		GalleryItem galleryItem = GalleryItem.fromCursor(mContext, cursor);
		vh.mImageView.setRotation(galleryItem.rotation);
		vh.mRatingBar.setRating(galleryItem.rating);

		if (galleryItem.label != null)
		{
			switch (galleryItem.label.toLowerCase())
			{
				case "purple":
					vh.mLabel.setVisibility(View.VISIBLE);
					vh.mLabel.setBackgroundColor(purple);
					break;
				case "blue":
					vh.mLabel.setVisibility(View.VISIBLE);
					vh.mLabel.setBackgroundColor(blue);
					break;
				case "yellow":
					vh.mLabel.setVisibility(View.VISIBLE);
					vh.mLabel.setBackgroundColor(yellow);
					break;
				case "green":
					vh.mLabel.setVisibility(View.VISIBLE);
					vh.mLabel.setBackgroundColor(green);
					break;
				case "red":
					vh.mLabel.setVisibility(View.VISIBLE);
					vh.mLabel.setBackgroundColor(red);
					break;
				default:
					vh.mLabel.setVisibility(View.GONE);
					break;
			}
		}
		else
		{
			vh.mLabel.setVisibility(View.GONE);
		}
		vh.mFileName.setText(galleryItem.name);
		vh.mXmpView.setVisibility(galleryItem.hasSubject ? View.VISIBLE : View.GONE);
		mImageDecoder.loadImage(new LocalImage(mContext, galleryItem.uri), vh.mImageView);
		vh.mBaseView.setChecked(mSelectedItems.contains(galleryItem.uri));
	}

	public MediaItem getImage(int position)
	{
		return new LocalImage(mContext, getUri(position));
	}

	public Uri getUri(int position)
	{
		Cursor c = (Cursor)getItem(position);
		return Uri.parse(c.getString((c.getColumnIndex(Meta.Data.URI))));
	}


	public int getCount()
	{
		//TODO: This wrapper just makes it easier to swap old gallery adapter, remove if recycler works
		return getItemCount();
	}

	public List<Uri> getSelectedItems()
	{
		return new ArrayList<Uri>(mSelectedItems);
	}

	public int getSelectedItemCount()
	{
		return mSelectedItems.size();
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