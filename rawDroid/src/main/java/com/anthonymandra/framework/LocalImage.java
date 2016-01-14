package com.anthonymandra.framework;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
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
import com.crashlytics.android.Crashlytics;
import com.drew.metadata.xmp.XmpDirectory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class LocalImage extends MetaMedia {
	private static final String TAG = LocalImage.class.getSimpleName();
	File mImage;
	File mXmp;

//	public LocalImage(Context context, File image) {
//		super(context, Uri.fromFile(image));
//		mImage = image;
//	}

	public LocalImage(Context context, Uri image) {
		super(context, image);
	}

	public LocalImage(Context context, Cursor image) {
		this(context, Uri.parse(image.getString(Meta.URI_COLUMN)));
	}

	// TEMPORARY to avoid full rewrite during testing
	public File getFile() {
		return mImage;
	}

	@Override
	public byte[] getImage() {
        return getImageBytes();
	}

    private byte[] getImageBytes()
    {
        byte[] dst = new byte[(int) mImage.length()];
        DataInputStream dis = null;
        try {
            dis = new DataInputStream(new FileInputStream(mImage));
            dis.readFully(dst);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            Utils.closeSilently(dis);
        }
        return dst;
    }

    @Override
    public byte[] getThumbWithWatermark(byte[] watermark, int waterWidth,
                                        int waterHeight, LibRaw.Margins margins) {
        if (Util.isNative(getFile())) {
            return getImageBytes();
        }

        return LibRaw.getThumbWithWatermark(mImage, watermark, margins,
				waterWidth, waterHeight);
    }

	@Override
	public String getFilePath() {
		return mImage.getPath();
	}

    @Deprecated
	@Override
	public boolean canDecode() {
		// Log.d(TAG, mImage.getName());
		// Log.d(TAG, "canExecute: " + String.valueOf(mImage.canExecute()));
		// Log.d(TAG, "canRead: " + String.valueOf(mImage.canRead()));
		// Log.d(TAG, "canWrite: " + String.valueOf(mImage.canWrite()));
		// Log.d(TAG, "isAbsolute: " + String.valueOf(mImage.isAbsolute()));
		// Log.d(TAG, "isDirectory: " + String.valueOf(mImage.isDirectory()));
		// Log.d(TAG, "isFile: " + String.valueOf(mImage.isFile()));
		// Log.d(TAG, "isHidden: " + String.valueOf(mImage.isHidden()));
        return mImage.isFile() && LibRaw.canDecode(mImage);
    }

	@SuppressLint("SimpleDateFormat")
	@Override
	public byte[] getThumb() {
		if (ImageUtils.isAndroidImage(mType))
		{
			InputStream is = null;
			byte[] imageBytes;
			try
			{
				is = mContext.getContentResolver().openInputStream(mUri);
				imageBytes = Util.getBytes(is);
			} catch (Exception e)
			{
				return null;
			}
			finally
			{
				Utils.closeSilently(is);
			}

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
			if (ImageUtils.isTiffImage(mType))
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

	public boolean hasXmp() {
		return hasXmpFile() || mMetadata.containsDirectoryOfType(XmpDirectory.class);
	}

	private boolean hasXmpFile() {
		return getXmpFile().exists();
	}
    private boolean hasJpgFile() {
        return getJpgFile().exists();
    }

	private File getXmpFile() {
		if (mXmp == null)
			mXmp = getAssociatedFile("xmp");

		return mXmp;
	}

    private File getJpgFile() {
        return getAssociatedFile("jpg");
    }

    private File getAssociatedFile(String ext) {
        String name = mImage.getName();
        int pos = name.lastIndexOf(".");
        if (pos > 0) {
            name = name.substring(0, pos);
        }
        name += "." + ext;

        return new File(mImage.getParent(), name);
    }

	@Override
	protected BufferedInputStream getXmpInputStream() {
		if (!hasXmpFile())
			return null;

		try {
			return new BufferedInputStream(new FileInputStream(getXmpFile()));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public boolean isDirectory() {
		return mImage.isDirectory();
	}

	@Override
	public Uri getSwapUri() {
		return Uri.parse("content://" + SwapProvider.AUTHORITY + "/"
				+ Util.swapExtention(mImage.getName(), ".jpg") + "#"
				+ mImage.getPath());
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

	@Override
	public InputStream getImageStream() {
		try {
			return new FileInputStream(mImage);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}
}
