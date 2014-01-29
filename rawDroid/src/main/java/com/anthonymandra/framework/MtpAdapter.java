//package com.anthonymandra.framework;
//
//import java.util.ArrayList;
//import java.util.List;
//
//import org.openintents.filemanager.R;
//
//import android.annotation.TargetApi;
//import android.content.Context;
//import android.mtp.MtpConstants;
//import android.mtp.MtpDevice;
//import android.mtp.MtpObjectInfo;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.view.ViewGroup.LayoutParams;
//import android.widget.GridView;
//import android.widget.ImageView;
//import android.widget.TextView;
//
//import com.anthonymandra.widget.LoadingImageView;
//
//@TargetApi(12)
//public class MtpAdapter extends CacheAdapter
//{
//	private Context mContext;
//
//	static class ViewHolder
//	{
//		public TextView filename;
//		public LoadingImageView image;
//	}
//
//	public MtpAdapter(Context c)
//	{
//		mContext = c;
//	}
//
//	public MtpAdapter(Context c, ImageDecoder cache, MtpDevice device)
//	{
//		super();
//		mContext = c;
////		mDevice = device;
//		mImageDecoder = cache;
//		mImageViewLayoutParams = new GridView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
//		
//		// Don't really need two adapters anymore, just need activity to create list
//		mImages = new ArrayList<MediaObject>();
//		for (int storageId : device.getStorageIds())
//		{
//			for (int objectId : device.getObjectHandles(storageId, 0, 0))
//			{
//				MtpObjectInfo info = device.getObjectInfo(objectId);
//				if (info.getAssociationType() != MtpConstants.ASSOCIATION_TYPE_GENERIC_FOLDER)
//				{
//					mImages.add(new MtpImage(device, objectId));
//				}
//			}
//		}
//	}
//
//	@Override
//	public int getCount()
//	{
//		return mImages.size();
//	}
//
//	@Override
//	public Object getItem(int position)
//	{
//		return 0;	// not supported for now 
//	}
//
//	@Override
//	public long getItemId(int position)
//	{
//		return position;
//	}
//	
//	@Override
//	public View getView(int position, View convertView, ViewGroup parent)
//	{
//		View view;
//		ViewHolder viewHolder;
//		
//		if (convertView == null)
//		{			
//			LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//			// view = inflater.inflate(R.layout.fileview, root)
//			view = inflater.inflate(R.layout.fileview, parent, false);
//			viewHolder = new ViewHolder();
//			viewHolder.filename = (TextView) view.findViewById(R.id.filenameView);
//			viewHolder.image = (LoadingImageView) view.findViewById(R.id.webImageView);
//			
//			viewHolder.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
//			view.setLayoutParams(mImageViewLayoutParams);
//			view.setTag(viewHolder);
//		}
//		else
//		{
//			// Otherwise re-use the converted view
//			view = convertView;
//			viewHolder = (ViewHolder) view.getTag();
////			viewHolder.image.setLoadingSpinner();
//		}
//			
////		int objectHandle = imageHandles.get(position);
////		
////		MtpObjectInfo info = mDevice.getObjectInfo(objectHandle);
////		// We'll check again if we can't process this object (zero size)
////		if (info.getCompressedSize() == 0)
////		{
////			return view;
////		}
//		
//		//TODO: This is gonna burn me...file access on gui thread.
//		MediaObject media = mImages.get(position);
//		viewHolder.filename.setText(media.getName());
//		mImageDecoder.loadImage(media, viewHolder.image);
//		
////		viewHolder.filename.setText(info.getName());
////		byte[] thumb = LibRaw.getThumbFromBuffer(mDevice.getObject(objectHandle, info.getCompressedSize()));
////		BitmapFactory.Options o = new BitmapFactory.Options();
////		o.inSampleSize = 8;
////		Bitmap image = BitmapFactory.decodeByteArray(thumb, 0, thumb.length, o);
//////		File imported = new File(FileManager.getStoragePath() + File.separator + info.getName());
//////		boolean success = mDevice.importFile(objectHandle, imported.getFilePath());
////		viewHolder.image.setImageBitmap(image);
////		mImageDecoder.loadImage(imported, viewHolder.image);
////		imported.delete();
//
//        // Check the height matches our calculated column width
//		if (view.getLayoutParams().height != mItemHeight)
//		{
//			view.setLayoutParams(mImageViewLayoutParams);
//		}
//
//		return view;
//	}
// }