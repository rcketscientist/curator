package com.anthonymandra.framework;

import android.annotation.SuppressLint;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DecodeUtils;
import com.android.gallery3d.data.ImageCacheRequest;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.anthonymandra.content.Meta;
import com.anthonymandra.imageprocessor.Exif;
import com.anthonymandra.imageprocessor.ImageProcessor;
import com.anthonymandra.util.ImageUtil;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.text.SimpleDateFormat;

public class LocalImage extends MetaMedia {
	private static final String TAG = LocalImage.class.getSimpleName();
	private static SimpleDateFormat mLibrawFormatter = new SimpleDateFormat("EEE MMM d hh:mm:ss yyyy");

	public LocalImage(Context context, Uri image) {
		super(context, image);
	}

	public LocalImage(Context context, Cursor image) {
		super(context, image);
	}

	private byte[] getImageBytes()
	{
		InputStream is = null;
		try
		{
			is = mContext.getContentResolver().openInputStream(mUri);
			return Util.toByteArray(is);
		} catch (Exception e)
		{
			return null;
		}
		finally
		{
			Utils.closeSilently(is);
		}
	}

	@SuppressLint("SimpleDateFormat")
	@Override
	public byte[] getThumb() {
		if (ImageUtil.isAndroidImage(mType))
		{
			byte[] imageBytes = getImageBytes();
			if (imageBytes == null)
				return null;

			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;

			// Decode dimensions
			BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, o);
//			thumbWidth = o.outWidth;
//			thumbHeight = o.outHeight;
			width = o.outWidth;
			height = o.outHeight;

			return imageBytes;
		}

		// Get a file descriptor to pass to native methods
		int fd;
		ParcelFileDescriptor pfd = null;

		try
		{
			pfd = mContext.getContentResolver().openFileDescriptor(mUri, "r");
			fd = pfd.getFd();
			if (ImageUtil.isTiffMime(mType))
			{
				int[] dim = new int[2];
				int[] imageData = ImageProcessor.getTiff(mName, fd, dim);
				width = dim[0];
//				thumbWidth = width;
				height = dim[1];
//				thumbHeight = height;

				// This is necessary since BitmapRegionDecoder only supports jpg and png
				Bitmap bmp = Bitmap.createBitmap(imageData, width, height, Bitmap.Config.ARGB_8888);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				bmp.compress(Bitmap.CompressFormat.JPEG, 100, baos);

				return baos.toByteArray();
			}

			// Raw images
			String[] exif = new String[12];
			byte[] imageData = ImageProcessor.getThumb(fd, exif);

			ContentProviderClient cpc = null;
			Cursor c = null;
			try
			{
				cpc = mContext.getContentResolver().acquireContentProviderClient(Meta.AUTHORITY);
				if (cpc != null)
				{
					c = cpc.query(Meta.CONTENT_URI, new String[] { Meta.PROCESSED },
							Meta.URI_SELECTION, new String[] {mUri.toString()}, null, null);
					if (c != null)
					{
						c.moveToFirst();
						if (c.getInt(0) == 0)   // If it hasn't been processed yet, insert basics
						{
							ContentValues cv = new ContentValues();
							try
							{
								cv.put(Meta.TIMESTAMP, mLibrawFormatter.parse(exif[Exif.TIMESTAMP]).getTime());
							}
							catch (Exception e)
							{
								e.printStackTrace();
							}
							cv.put(Meta.APERTURE, exif[Exif.APERTURE]);
							cv.put(Meta.MAKE, exif[Exif.MAKE]);
							cv.put(Meta.MODEL, exif[Exif.MODEL]);
							cv.put(Meta.FOCAL_LENGTH, exif[Exif.FOCAL]);
							cv.put(Meta.APERTURE, exif[Exif.HEIGHT]);
							cv.put(Meta.ISO, exif[Exif.ISO]);
							cv.put(Meta.ORIENTATION, exif[Exif.ORIENTATION]);
							cv.put(Meta.EXPOSURE, exif[Exif.SHUTTER]);
							cv.put(Meta.HEIGHT, exif[Exif.HEIGHT]);
							cv.put(Meta.WIDTH, exif[Exif.WIDTH]);
							//TODO: Placing thumb dimensions since we aren't decoding raw atm.
							cv.put(Meta.HEIGHT, exif[Exif.THUMB_HEIGHT]);
							cv.put(Meta.WIDTH, exif[Exif.THUMB_WIDTH]);
							// Are the thumb dimensions useful in database?

							cpc.update(Meta.CONTENT_URI, cv, Meta.URI_SELECTION, new String[] {mUri.toString()});
						}
					}
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			finally
			{
				if (cpc != null)
					cpc.release();
				Utils.closeSilently(c);
			}

			return imageData;
		}
		catch (FileNotFoundException e)
		{
			return null;
		}

		finally
		{
			Utils.closeSilently(pfd);
		}
	}
}
