package com.anthonymandra.widget;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.TextView;

import com.anthonymandra.content.Meta;
import com.anthonymandra.rawdroid.R;
import com.anthonymandra.util.ImageUtils;
import com.bumptech.glide.Glide;

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
	private final Set<Uri> mSelectedItems = new HashSet<>();
	private final TreeSet<Integer> mSelectedPositions = new TreeSet<>();

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
	@SuppressWarnings("unused")
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
	@SuppressWarnings("unused")
	public final OnItemLongClickListener getOnItemLongClickListener() {
		return mOnItemLongClickListener;
	}

	public interface OnSelectionUpdatedListener
	{
		void onSelectionUpdated(Set<Uri> selectedUris);
	}

	public static class ViewHolder extends RecyclerView.ViewHolder
	{
		public final ImageView mImageView;
		public final TextView mFileName;
		public final View mLabel;
		public final android.widget.RatingBar mRatingBar;
		public final CheckableRelativeLayout mBaseView;
		public final ImageView mXmpView;

		public ViewHolder(View view)
		{
			super(view);

			mImageView = (ImageView) view.findViewById(R.id.webImageView);
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

		public static GalleryItem fromCursor(Cursor cursor)
		{
			GalleryItem item = new GalleryItem();
			item.rotation = ImageUtils.getRotation(cursor.getInt(cursor.getColumnIndex(Meta.ORIENTATION)));
			item.rating = cursor.getFloat(cursor.getColumnIndex(Meta.RATING));
			final String u = cursor.getString(cursor.getColumnIndex(Meta.URI));
			item.uri = u != null ? Uri.parse(u) : null;
			item.label = cursor.getString(cursor.getColumnIndex(Meta.LABEL));
			item.name = cursor.getString(cursor.getColumnIndex(Meta.NAME));
			item.hasSubject = cursor.getString(cursor.getColumnIndex(Meta.SUBJECT)) != null;
			return item;
		}

		public static GalleryItem fromViewHolder(ViewHolder vh)
		{
			GalleryItem item = new GalleryItem();
			item.rotation = (int)vh.mImageView.getRotation();
			item.rating = vh.mRatingBar.getRating();
			item.uri = (Uri) vh.mBaseView.getTag();
			item.label = (String) vh.mLabel.getTag();
			item.name = (String) vh.mFileName.getText();
			item.hasSubject = vh.mXmpView.getVisibility() == View.VISIBLE;
			return item;
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof GalleryItem)) {
				return false;
			}

			GalleryItem compare = (GalleryItem) o;
			boolean sameRotation = rotation == compare.rotation;
			boolean sameRating = rating == compare.rating;
			boolean sameSubject = hasSubject == compare.hasSubject;
			boolean sameUri = uri == null ? compare.uri == null : uri.equals(compare.uri);
			boolean sameName = name == null ? compare.name == null : name.equals(compare.name);
			boolean sameLabel = label == null ? compare.label == null : label.equals(compare.label);

			return sameLabel && sameName && sameRating && sameRotation && sameSubject && sameUri;
		}
	}

	@SuppressWarnings("deprecation")
	public GalleryRecyclerAdapter(Context context, Cursor c)
	{
		super(c);
		mContext = context;

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
		return new ViewHolder(itemView);
	}

	@Override
	public void onBindViewHolder(final ViewHolder vh, final int position, Cursor cursor)
	{
		GalleryItem galleryItem = GalleryItem.fromCursor(cursor);
		GalleryItem former = GalleryItem.fromViewHolder(vh);

		vh.mBaseView.setChecked(mSelectedItems.contains(galleryItem.uri));

		// If nothing has changed avoid refreshing.
		// The reason for this is that loaderManagers replace cursors meaning every change
		// will refresh the entire data source causing flickering
		if (former.equals(galleryItem))
			return;

		if (mOnItemClickListener != null)
		{
			vh.mBaseView.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					int currentPos = vh.getAdapterPosition();
					mOnItemClickListener.onItemClick(GalleryRecyclerAdapter.this, v, currentPos, getItemId(currentPos));
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
					int currentPos = vh.getAdapterPosition();
					return mOnItemLongClickListener.onItemLongClick(GalleryRecyclerAdapter.this, v, currentPos, getItemId(currentPos));
				}
			});
		}

		vh.mImageView.setRotation(galleryItem.rotation);
		vh.mRatingBar.setRating(galleryItem.rating);

		vh.mLabel.setTag(galleryItem.label);    // store tag for compare
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
		vh.mBaseView.setTag(galleryItem.uri);   // store tag for compare

		Glide.with(mContext)
				.using(new RawModelLoader(mContext))
				.load(galleryItem.uri)
				.centerCrop()
				.into(vh.mImageView);
	}

	@Nullable
	public Uri getUri(int position)
	{
		Cursor c = (Cursor)getItem(position);   // This is the adapter cursor, don't close
		if (c == null)
			return null;
		int index = c.getColumnIndex(Meta.URI);
		if (index < 0)
			return null; //TODO: Fabric #55 https://fabric.io/anthony-mandras-projects/android/apps/com.anthonymandra.rawdroid/issues/57569a4cffcdc04250f29fb7
		final String uriString = c.getString(index);
		if (uriString == null)
			return null;
		return Uri.parse(uriString);
	}

	@Nullable
	public List<String> getUris()
	{
		if (mCursor == null)
			return null;
		int index = mCursor.getColumnIndex(Meta.URI);
		if (index < 0)
			return null;

		List<String> uris = new ArrayList<>();
		while (mCursor.moveToNext()) {
			String uri = mCursor.getString(index);
			if (uri == null)
				continue;
			uris.add(uri);
		}
		return uris;
	}

	public List<Uri> getSelectedItems()
	{
		return new ArrayList<>(mSelectedItems);
	}

	public int getSelectedItemCount()
	{
		return mSelectedItems.size();
	}

	private void addBetween(int start, int end)
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
		if (uri == null)
			return;

		mSelectedItems.add(uri);
		mSelectedPositions.add(position);
	}

	private void removeSelection(View view, Uri uri, int position)
	{
		mSelectedItems.remove(uri);
		mSelectedPositions.remove(position);
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
		if (uri == null)
			return;

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

	private void updateSelection()
	{
		if (mSelectionListener != null)
			mSelectionListener.onSelectionUpdated(mSelectedItems);
	}

	public void selectAll()
	{
		Cursor c = getCursor();
		if (c != null && c.moveToFirst())
		{
			do
			{
				mSelectedItems.add(getUri(c.getPosition()));
			} while (c.moveToNext());

			updateSelection();
			notifyDataSetChanged();
		}
	}

	public void setOnSelectionListener(OnSelectionUpdatedListener listener)
	{
		mSelectionListener = listener;
	}
}