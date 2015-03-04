package com.anthonymandra.framework;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DecodeUtils;
import com.android.gallery3d.data.ImageCacheRequest;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.anthonymandra.dcraw.LibRaw;
import com.anthonymandra.dcraw.LibRaw.Margins;
import com.anthonymandra.dcraw.TiffDecoder;
import com.drew.metadata.xmp.XmpDirectory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;

public class LocalImage extends MetaMedia {
	private static final String TAG = LocalImage.class.getSimpleName();
	File mImage;
	File mXmp;

	public LocalImage(Context context, File image) {
		super(context, Uri.fromFile(image), nextVersionNumber());
		mImage = image;
	}

	// TEMPORARY to avoid full rewrite during testing
	public File getFile() {
		return mImage;
	}

	@Override
	public byte[] getImage() {
        return getImageBytes();
	}

    /* Error prone */
    @Override
    public boolean moveImage(File location) {
        return mImage.renameTo(location);
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
                                        int waterHeight, Margins margins) {
        if (Util.isNativeImage(this)) {
            return getImageBytes();
        }

        return LibRaw.getThumbWithWatermark(mImage, watermark, margins,
                waterWidth, waterHeight);
    }

	@Override
	public boolean delete() {
		if (hasXmpFile())
			getXmpFile().delete();
        if (hasJpgFile())
            getJpgFile().delete();
		return mImage.delete();
	}

	@Override
	public String getName() {
		return mImage.getName();
	}

	@Override
	public String getMimeType() {
		return null;
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
		if (!mImage.isFile())
			return false;
		return LibRaw.canDecode(mImage);
	}

	@SuppressLint("SimpleDateFormat")
	@Override
	public byte[] getThumb() {
		if (Util.isNativeImage(this)) {
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(mImage.getPath(), o);
			thumbWidth = o.outWidth;
			thumbHeight = o.outHeight;
			width = o.outWidth;
			height = o.outHeight;

			try {			
				ExifInterface ei = new ExifInterface(getFilePath());
				makeLegacy = ei.getAttribute(ExifInterface.TAG_MAKE);
				modelLegacy = ei.getAttribute(ExifInterface.TAG_MODEL);
				apertureLegacy = ei.getAttribute(ExifInterface.TAG_APERTURE);
				focalLegacy = ei.getAttribute(ExifInterface.TAG_FOCAL_LENGTH);
				isoLegacy = ei.getAttribute(ExifInterface.TAG_ISO);
				shutterLegacy = ei.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
				SimpleDateFormat sdf = new SimpleDateFormat(
						"yyyy:MM:dd HH:mm:ss");
				try {
					dateLegacy = mExifFormatter.parse(ei
							.getAttribute(ExifInterface.TAG_DATETIME));
				} catch (Exception e) {
					Log.d(TAG, "Date exif parse failed:", e);
				}
				orientLegacy = ei.getAttributeInt(
						ExifInterface.TAG_ORIENTATION, 0);
			} catch (IOException e) {
				e.printStackTrace();
			}

			return getImage();
		}
		
		else if (Util.isTiffImage(mImage))
		{
			int[] dim = new int[2];
//			int[] imageData = TiffDecoder.getThumb(mImage.getPath(), dim);
			int[] imageData = TiffDecoder.getImage(mImage.getPath(), dim);
			width = dim[0];
			thumbWidth = width;
			height = dim[1];
			thumbHeight = height;		
			Bitmap bmp = Bitmap.createBitmap(imageData, width, height, Bitmap.Config.ARGB_8888);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			bmp.compress(CompressFormat.JPEG, 100, baos);
			return baos.toByteArray();
		}

		String[] exif = new String[12];

		byte[] imageData = LibRaw.getThumb(mImage, exif);

		try {
			setThumbHeight(Integer.parseInt(exif[8]));
			setThumbWidth(Integer.parseInt(exif[9]));
			setHeight(Integer.parseInt(exif[10]));
			setWidth(Integer.parseInt(exif[11]));
		} catch (Exception e) {
			Log.d(TAG, "Dimensions exif parse failed:", e);
		}

		makeLegacy = exif[0];
		modelLegacy = exif[1];
		apertureLegacy = exif[2];
		focalLegacy = exif[3];
		isoLegacy = exif[4];
		shutterLegacy = exif[5];

		try {
			dateLegacy = mLibrawFormatter.parse(exif[6].trim());
		} catch (Exception e) {
			Log.d(TAG, "Date exif parse failed:", e);
		}

		try {
			orientLegacy = Integer.parseInt(exif[7]);
		} catch (Exception e) {
			Log.d(TAG, "Orientation exif parse failed:", e);
		}
		
		putContent();

		return imageData;
	}

	@Override
	public BufferedOutputStream getXmpOutputStream() {
		try {
			return new BufferedOutputStream(new FileOutputStream(getXmpFile()));
		} catch (FileNotFoundException e) {
			return null;
		}
	}

	@Override
	public long getFileSize() {
		return mImage.length();
	}

	@Override

	public boolean rename(String baseName) {
		Boolean success;

        success = renameAssociatedFile(mImage, baseName);
		if (hasXmpFile())
        {
            success = success && renameAssociatedFile(getXmpFile(), baseName);
        }
        if (hasJpgFile())
        {
            success = success && renameAssociatedFile(getJpgFile(), baseName);
        }

		return success;
	}

    public boolean renameAssociatedFile(File original, String baseName)
    {
        String filename = original.getName();
        String ext = filename.substring(filename.lastIndexOf("."),
                filename.length());

        String rename = baseName + ext;
        File renameFile = new File(mImage.getParent(), rename);
        return original.renameTo(renameFile);
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

    public void writeXmp() {
		OutputStream os = getXmpOutputStream();
		if (os == null)
			return;

		writeXmp(os);
        Utils.closeSilently(os);
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
	public boolean copy(File destination) {
		if (mXmp != null) {
			File xmpDest = new File(destination, mXmp.getName());
			BufferedInputStream xmpStream = getXmpInputStream();
			if (xmpStream != null) {
				Util.copy(xmpStream, xmpDest);
				Utils.closeSilently(xmpStream);
			}
		}

		if (mImage == null) {
			return false;
		}

		return Util.copy(mImage, new File(destination, mImage.getName()));
	}

	@Override
	public Uri getSwapUri() {
		return Uri.parse("content://" + SwapProvider.AUTHORITY + "/"
				+ Util.swapExtention(mImage.getName(), ".jpg") + "#"
				+ mImage.getPath());
	}

	@Override
	public Job<Bitmap> requestImage(GalleryApp app, int type) {
		return new LocalImageRequest(app, Uri.fromFile(mImage), type, this);
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
	public Uri getUri() {
		return Uri.fromFile(mImage);
	}

	@Override
	public long getDataVersion() {
		return mDataVersion;
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

	private boolean writeThumb(byte[] source, File destination) {
		if (source == null) {
			return false;
		}
		FileChannel out = null;
		try {
			out = new FileOutputStream(destination).getChannel();
			out.write(ByteBuffer.wrap(source));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} finally {
			Utils.closeSilently(out);
		}
		return true;
	}

	@Override
	public boolean writeThumb(File destination) {
		return LibRaw.writeThumbFile(mImage.getPath(), 100, Bitmap.Config.ARGB_8888, CompressFormat.JPEG, destination.getPath());
	}

	@Override
	public boolean writeThumbWatermark(File destination, byte[] waterMap,
			int waterWidth, int waterHeight, Margins waterMargins) {
		return LibRaw.writeThumbFileWatermark(mImage.getPath(), 100, Bitmap.Config.ARGB_8888, CompressFormat.JPEG, destination.getPath(), waterMap, waterMargins.getArray(), waterWidth, waterHeight);
	}

}
