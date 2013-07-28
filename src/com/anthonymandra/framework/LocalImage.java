package com.anthonymandra.framework;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;
import android.util.Log;

import com.android.gallery3d.data.DecodeUtils;
import com.android.gallery3d.data.ImageCacheRequest;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.anthonymandra.dcraw.LibRaw;
import com.drew.metadata.xmp.XmpDirectory;

public class LocalImage extends MetaMedia
{
	private static final String TAG = LocalImage.class.getSimpleName();
	private long mDataVersion = 0;
	File mImage;
	File mXmp;

	public LocalImage(File image)
	{
		mImage = image;
		mDataVersion = ++sVersion;
	}

	// TEMPORARY to avoid full rewrite during testing
	public File getFile()
	{
		return mImage;
	}

	@Override
	public byte[] getImage()
	{
		BufferedInputStream bis = null;
		try
		{
			bis = getImageInputStream();
			if (bis == null)
				return null;
			byte[] imageData = new byte[bis.available()];
			bis.read(imageData);
			return imageData;
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return null;
		}
		finally
		{
			if (bis != null)
			{
				try
				{
					bis.close();
				}
				catch (IOException e)
				{
				}
			}
		}
	}

	@Override
	public boolean delete()
	{
		if (hasXmp())
			getXmpFile().delete();
		return mImage.delete();
	}

	@Override
	public String getName()
	{
		return mImage.getName();
	}

	@Override
	public String getPath()
	{
		return mImage.getPath();
	}

	@Override
	public boolean canDecode()
	{
//		Log.d(TAG, mImage.getName());
//		Log.d(TAG, "canExecute: " + String.valueOf(mImage.canExecute()));
//		Log.d(TAG, "canRead: " + String.valueOf(mImage.canRead()));
//		Log.d(TAG, "canWrite: " + String.valueOf(mImage.canWrite()));
//		Log.d(TAG, "isAbsolute: " + String.valueOf(mImage.isAbsolute()));
//		Log.d(TAG, "isDirectory: " + String.valueOf(mImage.isDirectory()));
//		Log.d(TAG, "isFile: " + String.valueOf(mImage.isFile()));
//		Log.d(TAG, "isHidden: " + String.valueOf(mImage.isHidden()));
		if (!mImage.isFile())
			return false;
		return LibRaw.canDecode(mImage);
	}

	@SuppressLint("SimpleDateFormat")
	@Override
	public byte[] getThumb()
	{
		if (Util.isNativeImage(this))
		{
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(mImage.getPath(), o);
			thumbWidth = o.outWidth;
			thumbHeight = o.outHeight;
			width = o.outWidth;
			height = o.outHeight;
			
			return getImage();
		}

		// byte[] image = getImage();
		// if (image == null)
		// return image;

		String[] exif = new String[12];
		byte[] image = LibRaw.getThumb(mImage, exif);
//		ExifIFD0Directory exifDir = mMetadata.getOrCreateDirectory(ExifIFD0Directory.class);
//		ExifSubIFDDirectory exifSubDir = mMetadata.getOrCreateDirectory(ExifSubIFDDirectory.class);
		try
		{
			setThumbHeight(Integer.parseInt(exif[8]));
			setThumbWidth(Integer.parseInt(exif[9]));
			setHeight(Integer.parseInt(exif[10]));
			setWidth(Integer.parseInt(exif[11]));
		}
		catch (Exception e)
		{
			Log.d(TAG, "Dimensions exif parse failed:", e);
		}
		
		makeLegacy = exif[0];
		modelLegacy = exif[1];
		apertureLegacy = exif[2];
		focalLegacy = exif[3];
		isoLegacy = exif[4];
		shutterLegacy = exif[5];
		
		try
		{
			SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM d hh:mm:ss yyyy");
			dateLegacy = sdf.parse(exif[6].trim());
		}
		catch (Exception e)
		{
			Log.d(TAG, "Date exif parse failed:", e);
		}
		
		try
		{
			orientLegacy = Integer.parseInt(exif[7]);
		}
		catch (Exception e)
		{
			Log.d(TAG, "Orientation exif parse failed:", e);
		}
		
		return image;
	}

	@Override
	public BufferedInputStream getThumbInputStream()
	{
		byte[] thumb = getThumb();
		if (thumb == null)
			return null;
		return new BufferedInputStream(new ByteArrayInputStream(thumb));
	}

	@Override
	public BufferedOutputStream getXmpOutputStream()
	{
		try
		{
			return new BufferedOutputStream(new FileOutputStream(getXmpFile()));
		}
		catch (FileNotFoundException e)
		{
			return null;
		}
	}

	@Override
	public BufferedInputStream getImageInputStream()
	{
		try
		{
			return new BufferedInputStream(new FileInputStream(mImage));
		}
		catch (FileNotFoundException e)
		{
			return null;
		}
	}

	@Override
	public long getFileSize()
	{
		return mImage.length();
	}

	@Override
	public boolean rename(String baseName)
	{
		Boolean imageSuccess = true;
		Boolean xmpSuccess = true;

		String filename = getName();
		String ext = filename.substring(filename.lastIndexOf("."), filename.length());

		String rename = baseName + ext;
		File renameFile = new File(mImage.getParent() + File.separator + rename);
		mImage.renameTo(renameFile);

		if (hasXmpFile())
		{
			rename = baseName + ".xmp";
			File renameXmp = new File(mImage.getParent(), rename);
			xmpSuccess = getXmpFile().renameTo(renameXmp);
		}
		return imageSuccess && xmpSuccess;
	}

	public boolean hasXmp()
	{
		return hasXmpFile() || mMetadata.containsDirectory(XmpDirectory.class);
	}

	private boolean hasXmpFile()
	{
		return getXmpFile().exists();
	}

	private File getXmpFile()
	{
		if (mXmp != null)
			return mXmp;

		String name = mImage.getName();
		int pos = name.lastIndexOf(".");
		if (pos > 0)
		{
			name = name.substring(0, pos);
		}
		name += ".xmp";

		mXmp = new File(mImage.getParent(), name);
		return mXmp;
	}

	public void writeXmp()
	{
		OutputStream os = getXmpOutputStream();
		writeXmp(os);
		try
		{
			if (os != null)
			{
				os.close();
			}
		}
		catch (IOException e1)
		{
			e1.printStackTrace();
		}

	}

	@Override
	protected BufferedInputStream getXmpInputStream()
	{
		if (!hasXmpFile())
			return null;

		try
		{
			return new BufferedInputStream(new FileInputStream(getXmpFile()));
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public boolean isDirectory()
	{
		return mImage.isDirectory();
	}

	@Override
	public boolean copy(File destination)
	{
		if (mXmp != null)
		{
			File xmpDest = new File(destination, mXmp.getName());
			BufferedInputStream xmpStream = getXmpInputStream();
			if (xmpStream != null)
			{
				Util.copy(xmpStream, xmpDest);
				try
				{
					xmpStream.close();
				}
				catch (IOException e)
				{
				}
			}
		}

		if (mImage == null)
		{
			return false;
		}

		File imageDest = new File(destination, mImage.getName());
		BufferedInputStream imageStream = getImageInputStream();
		if (imageStream == null)
			return false;

		Util.copy(imageStream, imageDest);
		try
		{
			imageStream.close();
		}
		catch (IOException e)
		{
			return false;
		}

		return true;
	}

	@Override
	public boolean copyThumb(File destination)
	{
		BufferedOutputStream thumbStream = null;
		try
		{
			File thumbDest = new File(destination, Util.swapExtention(getName(), ".jpg"));
			thumbStream = new BufferedOutputStream(new FileOutputStream(thumbDest));
			byte[] thumb = getThumb();
			if (thumb == null)
				return false;
			thumbStream.write(getThumb()); // Assumes thumb is already in an image format (jpg at time of coding)
		}
		catch (IOException e)
		{
			return false;
		}
		finally
		{
			try
			{
				if (thumbStream != null)
				{
					thumbStream.close();
				}
			}
			catch (IOException e)
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public Job<Bitmap> requestImage(com.android.gallery3d.app.GalleryActivity app, int type)
	{
		return new LocalImageRequest(app, Uri.fromFile(mImage), type, getThumb());
	}

	public static class LocalImageRequest extends ImageCacheRequest
	{
		private byte[] mImageData;

		LocalImageRequest(com.android.gallery3d.app.GalleryActivity application, Uri uri, int type, byte[] imageData)
		{
			super(application, uri, type, MetaMedia.getTargetSize(type));
			mImageData = imageData;
		}

		@Override
		public Bitmap onDecodeOriginal(JobContext jc, final int type)
		{
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;
			int targetSize = MetaMedia.getTargetSize(type);

			// try to decode from JPEG EXIF
			if (type == MetaMedia.TYPE_MICROTHUMBNAIL)
			{
				// ExifInterface exif = null;
				// byte [] thumbData = null;
				// try {
				// exif = new ExifInterface(mLocalFilePath);
				// if (exif != null) {
				// thumbData = exif.getThumbnail();
				// }
				// } catch (Throwable t) {
				// Log.w(TAG, "fail to get exif thumb", t);
				// }
				// if (thumbData != null) {
				Bitmap bitmap = DecodeUtils.decodeIfBigEnough(jc, mImageData, options, targetSize);
				if (bitmap != null)
					return bitmap;
				// }
			}

			return DecodeUtils.decodeThumbnail(jc, mImageData, options, targetSize, type);
		}
	}

	@Override
	public Job<BitmapRegionDecoder> requestLargeImage()
	{
		return new LocalLargeImageRequest(getThumb());
	}

	public static class LocalLargeImageRequest implements Job<BitmapRegionDecoder>
	{
		byte[] mImageData;

		public LocalLargeImageRequest(byte[] imageData)
		{
			mImageData = imageData;
		}

		public BitmapRegionDecoder run(JobContext jc)
		{
			return DecodeUtils.createBitmapRegionDecoder(jc, mImageData, 0, mImageData.length, false);
		}
	}

	@Override
	public Uri getUri()
	{
		return Uri.fromFile(mImage);
	}

	@Override
	public long getDataVersion()
	{
		return mDataVersion;
	}

}
