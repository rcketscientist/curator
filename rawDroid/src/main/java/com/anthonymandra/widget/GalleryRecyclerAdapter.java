//package com.anthonymandra.widget;
//
//import android.content.Context;
//import android.database.Cursor;
//import android.net.Uri;
//import android.support.v7.widget.RecyclerView;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.Checkable;
//import android.widget.GridView;
//import android.widget.ImageView;
//import android.widget.RelativeLayout;
//import android.widget.TextView;
//
//import com.android.gallery3d.data.MediaItem;
//import com.anthonymandra.content.Meta;
//import com.anthonymandra.framework.ImageDecoder;
//import com.anthonymandra.framework.ImageUtils;
//import com.anthonymandra.framework.LocalImage;
//import com.anthonymandra.framework.SwapProvider;
//import com.anthonymandra.rawdroid.R;
//
//import java.io.File;
//import java.util.ArrayList;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Set;
//import java.util.TreeSet;
//
//public class GalleryRecyclerAdapter extends CursorRecyclerViewAdapter<GalleryRecyclerAdapter.ViewHolder>
//{
//	private final int purple;
//	private final int blue;
//	private final int yellow;
//	private final int green;
//	private final int red;
//
//	private final Context mContext;
//	private final LayoutInflater mInflater;
//	protected int mItemHeight = 0;
//	protected int mNumColumns = 0;
//	protected RecyclerView.LayoutParams mImageViewLayoutParams;
//	private Set<Uri> mSelectedItems = new HashSet<>();
//	private Set<Long> mSelectedIds = new HashSet<>();
//	private TreeSet<Integer> mSelectedPositions = new TreeSet<>();
//
//	private ImageDecoder mImageDecoder;
//
//	public static class ViewHolder extends RecyclerView.ViewHolder
//	{
//		public LoadingImageView mImageView;
//		public TextView mFileName;
//		public View mLabel;
//		public android.widget.RatingBar mRatingBar;
//		public CheckableRelativeLayout mBaseView;
//
//		public ViewHolder(View view)
//		{
//			super(view);
//			mImageView = (LoadingImageView) view.findViewById(R.id.webImageView);
//			mFileName = (TextView) view.findViewById(R.id.filenameView);
//			mLabel = view.findViewById(R.id.label);
//			mRatingBar= (android.widget.RatingBar) view.findViewById(R.id.galleryRatingBar);
//			mBaseView = (CheckableRelativeLayout) view.findViewById(R.id.fileView2);
//		}
//	}
//
//	public static class GalleryItem
//	{
//		private String name;
//		private int  rotation;
//		private float rating;
//		private String label;
//		private Uri uri;
//
//		public static GalleryItem fromCursor(Cursor cursor)
//		{
//			GalleryItem item = new GalleryItem();
//			item.rotation = ImageUtils.getRotation(cursor.getInt(Meta.ORIENTATION_COLUMN));
//			item.rating = cursor.getFloat(Meta.RATING_COLUMN);
//			item.uri = Uri.parse(cursor.getString(Meta.URI_COLUMN));
//			item.label = cursor.getString(Meta.LABEL_COLUMN);
//			item.name = cursor.getString(cursor.getColumnIndex(Meta.Data.NAME));
//			return item;
//		}
//	}
//
//	public GalleryRecyclerAdapter(Context context, Cursor c, ImageDecoder imageDecoder)
//	{
//		super(context, c);
//		mContext = context;
//		mInflater = LayoutInflater.from(context);
//
//		mImageDecoder = imageDecoder;
//
//		purple = mContext.getResources().getColor(R.color.startPurple);
//		blue = mContext.getResources().getColor(R.color.startBlue);
//		yellow = mContext.getResources().getColor(R.color.startYellow);
//		green = mContext.getResources().getColor(R.color.startGreen);
//		red = mContext.getResources().getColor(R.color.startRed);
//
//		mImageViewLayoutParams = new RecyclerView.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
//
////		if (getNumColumns() == 0)
////		{
////			final int displayWidth = mContext.getResources().getDisplayMetrics().widthPixels;
////			final int thumbSize = mContext.getResources().getDimensionPixelSize(R.dimen.image_thumbnail_size);
////			final int thumbSpacing = mContext.getResources().getDimensionPixelSize(R.dimen.image_thumbnail_spacing);
////			final int numColumns = (int) Math.floor(displayWidth / (thumbSize + thumbSpacing));
////			if (numColumns > 0)
////			{
////				final int columnWidth = (displayWidth / numColumns) - thumbSpacing;
////				setNumColumns(numColumns);
////				setItemHeight(columnWidth);
////			}
////		}
//	}
//
//	@Override
//	public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
//	{
//		View itemView = LayoutInflater.from(parent.getContext())
//				.inflate(R.layout.fileview, parent, false);
////		itemView.setLayoutParams(mImageViewLayoutParams);
//		ViewHolder vh = new ViewHolder(itemView);
//		vh.mImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
//		return vh;
//	}
//
//	@Override
//	public void onBindViewHolder(ViewHolder vh, Cursor cursor)
//	{
//		GalleryItem galleryItem = GalleryItem.fromCursor(cursor);
//		vh.mImageView.setRotation(galleryItem.rotation);
//		vh.mRatingBar.setRating(galleryItem.rating);
//
//		if (galleryItem.label != null)
//		{
//			switch (galleryItem.label.toLowerCase())
//			{
//				case "purple":
//					vh.mBaseView.setBackgroundColor(purple);
//					vh.mLabel.setVisibility(View.VISIBLE);
//					vh.mLabel.setBackgroundColor(purple);
//					break;
//				case "blue":
//					vh.mBaseView.setBackgroundColor(blue);
//					vh.mLabel.setVisibility(View.VISIBLE);
//					vh.mLabel.setBackgroundColor(blue);
//					break;
//				case "yellow":
//					vh.mBaseView.setBackgroundColor(yellow);
//					vh.mLabel.setVisibility(View.VISIBLE);
//					vh.mLabel.setBackgroundColor(yellow);
//					break;
//				case "green":
//					vh.mBaseView.setBackgroundColor(green);
//					vh.mLabel.setVisibility(View.VISIBLE);
//					vh.mLabel.setBackgroundColor(green);
//					break;
//				case "red":
//					vh.mBaseView.setBackgroundColor(red);
//					vh.mLabel.setVisibility(View.VISIBLE);
//					vh.mLabel.setBackgroundColor(red);
//					break;
//				default:
//					vh.mBaseView.setBackgroundColor(0);
//					vh.mLabel.setVisibility(View.GONE);
//					break;
//			}
//		}
//		else
//		{
//			vh.mBaseView.setBackgroundColor(0);
//			vh.mLabel.setVisibility(View.GONE);
//		}
//		vh.mFileName.setText(galleryItem.name);
//		File image = new File(galleryItem.uri.getPath());
//		mImageDecoder.loadImage(new LocalImage(mContext, image), vh.mImageView);
//		vh.mBaseView.setChecked(mSelectedItems.contains(galleryItem.uri));
//	}
//
//	public MediaItem getImage(int position)
//	{
//		return new LocalImage(mContext, new File(getUri(position).getPath()));
//	}
//
//	public Uri getUri(int position)
//	{
//		Cursor c = (Cursor)getItem(position);
//		return Uri.parse(c.getString((c.getColumnIndex(Meta.Data.URI))));
//	}
//
//	public int getCount()
//	{
//		// Wrap the new method name so adapters can be easily swapped with prior versions
//		return getItemCount();
//	}
//
//	public List<Uri> getSelectedItems()
//	{
//		return new ArrayList<Uri>(mSelectedItems);
//	}
//
//	public int getSelectedItemCount()
//	{
//		return mSelectedItems.size();
//	}
//
//	public void setItemHeight(int height)
//	{
//		if (height == mItemHeight)
//		{
//			return;
//		}
//		mItemHeight = height;
//		mImageViewLayoutParams = new RecyclerView.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, mItemHeight);
//
//		if (mImageDecoder != null)
//			mImageDecoder.setImageSize(height);
//
//		notifyDataSetChanged();
//	}
//
//	public void setNumColumns(int numColumns)
//	{
//		mNumColumns = numColumns;
//	}
//
//	public int getNumColumns()
//	{
//		return mNumColumns;
//	}
//
//	public void addBetween(int start, int end)
//	{
//		for (int i = start; i <= end; i++)
//		{
//			if (getCursor().moveToPosition(i))
//			{
//				addSelection(getUri(i), i);
//			}
//		}
//		updateSelection();
//		notifyDataSetChanged();
//	}
//
//	public void addBetweenSelection(int position)
//	{
//		if (mSelectedPositions.size() > 0)
//		{
//			int first = mSelectedPositions.first();
//			int last = mSelectedPositions.last();
//			if (position > last)
//			{
//				addBetween(last, position);
//			}
//			else if (position < first)
//			{
//				addBetween(position, first);
//			}
//		}
//		else
//		{
//			addBetween(0, position);
//		}
//	}
//
//	/**
//	 * Add a selection and update the view
//	 * @param uri of selection
//	 * @param position of selection
//	 */
//	private void addSelection(View view, Uri uri, int position)
//	{
//		addSelection(uri, position);
//		((Checkable)view).setChecked(true);
//	}
//
//	/**
//	 * Add a selection without updating the view
//	 * This will generally require a call to notifyDataSetChanged()
//	 * @param uri of selection
//	 * @param position of selection
//	 */
//	private void addSelection(Uri uri, int position)
//	{
//		mSelectedItems.add(uri);
//		mSelectedPositions.add(position);
//		mSelectedIds.add(getItemId(position));
//	}
//
//	private void removeSelection(View view, Uri uri, int position)
//	{
//		mSelectedItems.remove(uri);
//		mSelectedPositions.remove(position);
//		mSelectedIds.remove(getItemId(position));
//		((Checkable)view).setChecked(false);
//	}
//
//	public void clearSelection()
//	{
//		mSelectedItems.clear();
//		mSelectedPositions.clear();
//		updateSelection();
//		notifyDataSetChanged();
//	}
//
//	public void toggleSelection(View v, int position)
//	{
//		Uri uri = getUri(position);
//		if (mSelectedItems.contains(uri))
//		{
//			removeSelection(v, uri, position);
//		}
//		else
//		{
//			addSelection(v, uri, position);
//		}
//		updateSelection();
//	}
//
//	public void updateSelection()
//	{
//		ArrayList<Uri> arrayUri = new ArrayList<>();
//		for (Uri selection : mSelectedItems)
//		{
//			arrayUri.add(SwapProvider.getSwapUri(new File(selection.getPath())));
//		}
//	}
//
//	public void selectAll()
//	{
//		if (getCursor().moveToFirst())
//		{
//			do
//			{
//				mSelectedItems.add(getUri(getCursor().getPosition()));
//			} while (getCursor().moveToNext());
//		}
//
//		updateSelection();
//		notifyDataSetChanged();
//	}
//}