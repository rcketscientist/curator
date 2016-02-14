package com.anthonymandra.framework;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DecodeUtils;
import com.android.gallery3d.data.ImageCacheRequest;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.anthonymandra.content.Meta;
import com.anthonymandra.rawprocessor.LibRaw;
import com.anthonymandra.rawprocessor.TiffDecoder;
import com.anthonymandra.util.FileUtil;
import com.anthonymandra.util.ImageUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class LocalImage extends MetaMedia {
	private static final String TAG = LocalImage.class.getSimpleName();

	public LocalImage(Context context, Uri image) {
		super(context, image);
	}

	public LocalImage(Context context, Cursor image) {
		this(context, Uri.parse(image.getString(Meta.URI_COLUMN)));
	}

    @Override
    public byte[] getThumbWithWatermark(byte[] watermark, int waterWidth,
                                        int waterHeight, LibRaw.Margins margins) {
	    if (ImageUtils.isAndroidImage(mContext, mUri))
	    {
		    return getImageBytes();
	    }

	    ParcelFileDescriptor pfd = null;
	    try
	    {
		    pfd = mContext.getContentResolver().openFileDescriptor(mUri, "r");
		    int fd = pfd.getFd();

		    return LibRaw.getThumbWithWatermark(fd, watermark, margins,
				    waterWidth, waterHeight);
	    }
	    catch (FileNotFoundException e)
	    {
			return null;
	    }
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
		if (ImageUtils.isAndroidImage(mType))
		{
			byte[] imageBytes = getImageBytes();

			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;

			// Decode dimensions
			BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, o);
			thumbWidth = o.outWidth;
			thumbHeight = o.outHeight;
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
			if (ImageUtils.isTiffMime(mType))
			{
				int[] dim = new int[2];
				int[] imageData = TiffDecoder.getImageFd(mName, fd, dim);
				width = dim[0];
				thumbWidth = width;
				height = dim[1];
				thumbHeight = height;

				// This is necessary since BitmapRegionDecoder only supports jpg and png
				Bitmap bmp = Bitmap.createBitmap(imageData, width, height, Bitmap.Config.ARGB_8888);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				bmp.compress(Bitmap.CompressFormat.JPEG, 100, baos);

				return baos.toByteArray();
			}

			// Raw images
			String[] exif = new String[12];
			byte[] imageData = LibRaw.getThumb(fd, exif);
			try {
				setThumbHeight(Integer.parseInt(exif[8]));
				setThumbWidth(Integer.parseInt(exif[9]));
				setHeight(Integer.parseInt(exif[10]));
				setWidth(Integer.parseInt(exif[11]));
			} catch (Exception e) {
				Log.d(TAG, "Dimensions exif parse failed:", e);
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

	private DocumentFile getXmpFile()
	{
		return getAssociatedFile("xmp");
	}

    private DocumentFile getJpgFile()
    {
        return getAssociatedFile("image/jpeg", "jpg");
    }

	private DocumentFile getAssociatedFile(String ext)
	{
		return getAssociatedFile("text/plain", ext);
	}

    private DocumentFile getAssociatedFile(String mimeType, String ext) {
	    String name = FileUtil.swapExtention(getName(), ext);

	    DocumentFile image = DocumentFile.fromSingleUri(mContext, mUri);
	    DocumentFile parent = image.getParentFile();

	    // The automatic creation of the file could cause issues, but findFile is slow
	    // and there's no way to exists() an arbitrary child...
	    return parent.createFile(mimeType, name);
    }

	@Override
	public Uri getSwapUri() {
		return SwapProvider.createSwapUri(getName(), getUri());
	}

	@Override
	public Job<Bitmap> requestImage(GalleryApp app, int type) {
		return new LocalImageRequest(app, mUri, type, this);
	}

	public static class LocalImageRequest extends ImageCacheRequest {
		private LocalImage mImage;

		LocalImageRequest(GalleryApp application, Uri uri, int type,
				LocalImage image) {
			super(application, uri, type, MetaMedia.getTargetSize(type));
			mImage = image;
		}

		@Override
		public Bitmap onDecodeOriginal(JobContext jc, final int type) {
			byte[] imageData = mImage.getThumb();

			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;
			int targetSize = MetaMedia.getTargetSize(type);

			// try to decode from JPEG EXIF
			if (type == MetaMedia.TYPE_MICROTHUMBNAIL) {
				Bitmap bitmap = DecodeUtils.decodeIfBigEnough(jc, imageData,
						options, targetSize);
				if (bitmap != null)
					return bitmap;
				// }
			}

			return DecodeUtils.decodeThumbnail(jc, imageData, options,
					targetSize, type);
		}
	}

	@Override
	public Job<BitmapRegionDecoder> requestLargeImage() {
		return new LocalLargeImageRequest(this);
	}

	public static class LocalLargeImageRequest implements
			Job<BitmapRegionDecoder> {
		LocalImage mImage;

		public LocalLargeImageRequest(LocalImage image) {
			mImage = image;
		}

		public BitmapRegionDecoder run(JobContext jc) {
			byte[] imageData = mImage.getThumb();
			BitmapRegionDecoder brd = DecodeUtils.createBitmapRegionDecoder(jc,
					imageData, 0, imageData.length, false);
			return brd;
		}
	}
}
