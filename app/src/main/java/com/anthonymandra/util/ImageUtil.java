package com.anthonymandra.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.net.Uri;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.anthonymandra.content.Meta;
import com.anthonymandra.framework.DocumentUtil;
import com.anthonymandra.framework.UsefulDocumentFile;
import com.anthonymandra.imageprocessor.ImageProcessor;
import com.anthonymandra.imageprocessor.Margins;
import com.anthonymandra.imageprocessor.Watermark;
import com.anthonymandra.curator.R;
import com.anthonymandra.curator.data.AppDatabase;
import com.anthonymandra.curator.data.ImageInfo;
import com.anthonymandra.curator.settings.WatermarkSettingsFragment;
//import com.crashlytics.android.Crashlytics;
import com.drew.imaging.FileType;
import com.drew.imaging.FileTypeDetector;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;
import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

@SuppressLint("AndroidLintSimpleDateFormat") // These are for specific library formats
public class ImageUtil {
	private static String TAG = ImageUtil.class.getSimpleName();

	/**
	 * --- File Association Helpers ----------------------------------------------------------------
	 */

	public static Uri[] getAssociatedFiles(Context c, Uri image) {
		final List<Uri> files = new ArrayList<>();
		UsefulDocumentFile xmp = getXmpFile(c, image);
		UsefulDocumentFile jpg = getJpgFile(c, image);
		if (xmp.exists()) files.add(xmp.getUri());
		if (jpg.exists()) files.add(jpg.getUri());
		return files.toArray(new Uri[0]);
	}

	public static boolean hasXmpFile(Context c, Uri uri) {
		return getXmpFile(c, uri).exists();
	}

	public static boolean hasJpgFile(Context c, Uri uri) {
		return getJpgFile(c, uri).exists();
	}

	public static boolean hasXmpFile(File f) {
		return getXmpFile(f).exists();
	}

	public static boolean hasJpgFile(File f) {
		return getJpgFile(f).exists();
	}

	public static UsefulDocumentFile getXmpFile(Context c, Uri uri) {
		return getAssociatedFile(c, uri, "xmp");
	}

	public static UsefulDocumentFile getJpgFile(Context c, Uri uri) {
		return getAssociatedFile(c, uri, "jpg");
	}

	public static File getXmpFile(File f) {
		return getAssociatedFile(f, "xmp");
	}

	public static File getJpgFile(File f) {
		return getAssociatedFile(f, "jpg");
	}

	private static UsefulDocumentFile getAssociatedFile(Context c, Uri uri, String ext) {
		UsefulDocumentFile image = UsefulDocumentFile.fromUri(c, uri);
		String name = image.getName();
		if (ext != null) {
			name = FileUtil.swapExtention(name, ext);
		}

		Uri neighbor;
		if (FileUtil.isFileScheme(uri)) {
			File n = getAssociatedFile(new File(uri.getPath()), ext);
			neighbor = Uri.fromFile(n);
		} else {
			neighbor = DocumentUtil.getNeighborUri(uri, name);
		}

		if (neighbor == null)
			return null;

		return UsefulDocumentFile.fromUri(c, neighbor);
	}

	/**
	 * Gets a similarly named file with a new ext
	 *
	 * @param file
	 * @param ext
	 * @return
	 */
	private static File getAssociatedFile(File file, String ext) {
		String name = file.getName();
		name = FileUtil.swapExtention(name, ext);
		return new File(file.getParent(), name);
	}

	public static boolean isAndroidImage(final Context c, final Uri uri) {
		String type = c.getContentResolver().getType(uri);
		return isAndroidImage(type);
	}

	public static boolean isAndroidImage(final String mimeType) {
		for (String mime : ImageConstants.ANDROID_IMAGE_MIME) {
			if (mime.equals(mimeType))
				return true;
		}
		return false;
	}

	public static boolean isAndroidImage(final FileType fileType) {
		return FileType.Bmp == fileType ||
			FileType.Gif == fileType ||
			FileType.Jpeg == fileType ||
			FileType.Png == fileType;
	}

	public static boolean isTiffImage(final FileType fileType) {
		return FileType.Tiff == fileType;
	}

	public static boolean isTiffMime(String mimeType) {
		return ImageConstants.TIFF_MIME.equals(mimeType);
	}

	public static void importKeywords(Context c, Uri keywordUri) {
		Completable.fromAction(() -> {
			InputStream is = c.getContentResolver().openInputStream(keywordUri);
			InputStreamReader reader = new InputStreamReader(is);
			AppDatabase.Companion.getInstance(c).subjectDao().importKeywords(reader);
		})
			.subscribeOn(Schedulers.io())
			.observeOn(AndroidSchedulers.mainThread())
			.subscribe(
				() -> Toast.makeText(c, R.string.resultImportSuccessful, Toast.LENGTH_LONG).show(),
				(e) -> {
					Toast.makeText(c, R.string.resultImportFailed, Toast.LENGTH_LONG).show();
					Log.d(TAG, "Import failed: ", e);
				}
			);
	}

	/**
	 * Attempt to derive the image type from the path
	 *
	 * @param file
	 * @return
	 */
	public static Meta.ImageType getImageType(Uri file) {
		if (isRaw(file)) return Meta.ImageType.RAW;
		if (isNative(file)) return Meta.ImageType.COMMON;
		if (isTiffImage(file)) return Meta.ImageType.TIFF;
		return Meta.ImageType.UNKNOWN;
	}

	/**
	 * Converts fileType to imageType for decoding purposes
	 *
	 * @param fileType
	 * @return
	 */
	private static Meta.ImageType getImageType(FileType fileType) {
		if (FileType.Unknown == fileType)
			return Meta.ImageType.UNKNOWN;
		if (isAndroidImage(fileType))
			return Meta.ImageType.COMMON;
		if (isTiffImage(fileType))
			return Meta.ImageType.TIFF;
		return Meta.ImageType.RAW;
	}

	/**
	 * First attempts to derive the image type from path, then falls back to magic number
	 *
	 * @param c
	 * @param uri
	 * @return
	 */
	public static Meta.ImageType getImageType(final Context c, final Uri uri) {
		// Try to derive from path
		Meta.ImageType type = getImageType(uri);
		if (type != Meta.ImageType.UNKNOWN)
			return type;

		// If that fails use the magic number
		try (InputStream is = c.getContentResolver().openInputStream(uri)) {
			if (is == null)
				return Meta.ImageType.UNKNOWN;

			BufferedInputStream bis = new BufferedInputStream(is);
			FileType fileType = FileTypeDetector.detectFileType(bis);
			return getImageType(fileType);
		} catch (IOException e) {
			e.printStackTrace();
			return Meta.ImageType.UNKNOWN;
		}
	}

	public static boolean isImage(@NonNull String name) {
		return isRaw(name) || isNative(name) || isTiffImage(name);
	}

	public static boolean isRaw(@NonNull Uri uri) {
		String path = uri.getPath();
		// If the uri is not hierarchical
		return path != null && isRaw(path);
	}

	public static boolean isRaw(@NonNull String name) {
		return endsWith(ImageConstants.RAW_EXT, name);
	}

	public static boolean isJpeg(@NonNull File file) {
		return isJpeg(file.getName());
	}

	public static boolean isJpeg(@NonNull Uri uri) {
		String path = uri.getPath();
		// If the uri is not hierarchical
		return path != null && isJpeg(path);
	}

	public static boolean isJpeg(@NonNull String name) {
		return endsWith(ImageConstants.JPEG_EXT, name);
	}

	public static boolean isNative(@NonNull String name) {
		return endsWith(ImageConstants.COMMON_EXT, name);
	}

	public static boolean isNative(@NonNull Uri uri) {
		String path = uri.getPath();
		// If the uri is not hierarchical
		return path != null && isNative(path);
	}

	public static boolean isTiffImage(@NonNull String name) {
		return endsWith(ImageConstants.TIFF_EXT, name);
	}

	public static boolean isTiffImage(@NonNull File file) {
		return isTiffImage(file.getName());
	}

	public static boolean isTiffImage(@NonNull Uri uri) {
		String path = uri.getPath();
		// If the uri is not hierarchical
		return path != null && isTiffImage(path);
	}

	private static boolean endsWith(String[] extensions, @NonNull String path) {
		for (String ext : extensions) {
			if (path.toLowerCase().endsWith(ext.toLowerCase()))
				return true;
		}
		return false;
	}

	public static String getMimeType(String url) {
		String type = null;
		String extension = MimeTypeMap.getFileExtensionFromUrl(url);
		if (extension != null) {
			type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
		}
		return type;
	}

	private static byte[] getTiffImage(int fileDescriptor) {
		int[] dim = new int[2];
		int[] imageData = ImageProcessor.getTiff("", fileDescriptor, dim);  //TODO: I could get name here, but is it worth it?  Does this name do anything?
		int width = dim[0];
		int height = dim[1];

		// This is necessary since BitmapRegionDecoder only supports jpg and png
		// TODO: This could be done in native, we already have jpeg capability
		// Alternatively maybe glide can handle int[]?
		Bitmap bmp = Bitmap.createBitmap(imageData, width, height, Bitmap.Config.ARGB_8888);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		bmp.compress(Bitmap.CompressFormat.JPEG, 100, baos);

		return baos.toByteArray();
	}

	private static byte[] getRawThumb(int fileDescriptor) {
		String[] exif = new String[12];
		byte[] imageBytes = ImageProcessor.getThumb(fileDescriptor, exif);
		if (imageBytes == null)
			return null;

//        try
//        {
//            if (exif[Exif.TIMESTAMP] != null)
//                values.put(Meta.TIMESTAMP, MetaUtil.mLibrawFormatter.parse(exif[Exif.TIMESTAMP]).getTime());
//        }
//        catch (Exception e)
//        {
//            e.printStackTrace();
//        }
//        values.put(Meta.APERTURE, exif[Exif.APERTURE]);
//        values.put(Meta.MAKE, exif[Exif.MAKE]);
//        values.put(Meta.MODEL, exif[Exif.MODEL]);
//        values.put(Meta.FOCAL_LENGTH, exif[Exif.FOCAL]);
//        values.put(Meta.APERTURE, exif[Exif.HEIGHT]);
//        values.put(Meta.ISO, exif[Exif.ISO]);
//        values.put(Meta.ORIENTATION, exif[Exif.ORIENTATION]);
//        values.put(Meta.EXPOSURE, exif[Exif.SHUTTER]);
//        values.put(Meta.HEIGHT, exif[Exif.HEIGHT]);
//        values.put(Meta.WIDTH, exif[Exif.WIDTH]);
//        //TODO: Placing thumb dimensions since we aren't decoding RAW atm.
//        values.put(Meta.HEIGHT, exif[Exif.THUMB_HEIGHT]);
//        values.put(Meta.WIDTH, exif[Exif.THUMB_WIDTH]);
		// Are the thumb dimensions useful in database?

		return imageBytes;
	}

	public static @Nullable
	byte[] getThumb(final Context c, ImageInfo image) throws Exception {
		Uri uri = Uri.parse(image.getUri());
		try(AssetFileDescriptor fd = c.getContentResolver().openAssetFileDescriptor(uri, "r")) {
			if (fd == null)
				return null;

			Meta.ImageType imageType = Meta.ImageType.fromInt(image.getType());
			if (imageType == Meta.ImageType.UNKNOWN || imageType == Meta.ImageType.UNPROCESSED)
				imageType = getImageType(c, uri);

			switch (imageType) {
				case COMMON:
					BufferedInputStream imageStream = new BufferedInputStream(fd.createInputStream());
					return Util.toByteArray(imageStream);
				case TIFF:
					return getTiffImage(fd.getParcelFileDescriptor().getFd());
				default:
					return getRawThumb(fd.getParcelFileDescriptor().getFd());
			}
		}
	}

	private static byte[] getImageBytes(Context c, Uri uri) {
		InputStream is = null;
		try {
			is = c.getContentResolver().openInputStream(uri);
			return Util.toByteArray(is);
		} catch (Exception e) {
			return null;
		} finally {
			Util.closeSilently(is);
		}
	}

	/**
	 * Gets an exact sample size, should not be used for large images since certain ratios generate "white images"
	 *
	 * @param options
	 * @param reqWidth
	 * @param reqHeight
	 * @return
	 */
	public static int getExactSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {
			if (width > height) {
				inSampleSize = Math.round((float) height / (float) reqHeight);
			} else {
				inSampleSize = Math.round((float) width / (float) reqWidth);
			}

			// This offers some additional logic in case the image has a strange
			// aspect ratio. For example, a panorama may have a much larger
			// width than height. In these cases the total pixels might still
			// end up being too large to fit comfortably in memory, so we should
			// be more aggressive with sample down the image (=larger
			// inSampleSize).
			final float totalPixels = width * height;

			// Anything more than 2x the requested pixels we'll sample down
			// further.
			final float totalReqPixelsCap = reqWidth * reqHeight * 2;

			while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
				inSampleSize++;
			}
		}
		return inSampleSize;
	}

	/**
	 * Legacy sample size, more reliable for multiple devices (no "white image")
	 *
	 * @param options
	 * @param reqWidth
	 * @param reqHeight
	 * @return
	 */
	public static int getLargeSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		int imageWidth = options.outWidth;
		int imageHeight = options.outHeight;

		int scaleH = 1, scaleW = 1;
		if (imageHeight > reqHeight || imageWidth > reqWidth) {
			scaleH = (int) Math.pow(2, (int) Math.ceil(Math.log(reqHeight / (double) imageHeight) / Math.log(0.5)));
			scaleW = (int) Math.pow(2, (int) Math.ceil(Math.log(reqWidth / (double) imageWidth) / Math.log(0.5)));
		}
		return Math.max(scaleW, scaleH);
	}

	public static Bitmap createBitmapLarge(byte[] image, int viewWidth, int viewHeight, boolean minSize) {
		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeByteArray(image, 0, image.length, o);
		o.inSampleSize = getLargeSampleSize(o, viewWidth, viewHeight);
		// setScalingPow2(image, viewWidth, viewHeight, o, minSize);
		o.inJustDecodeBounds = false;
		return BitmapFactory.decodeByteArray(image, 0, image.length, o);
	}

	/**
	 * @param data       image stream that must support mark and reset
	 * @param viewWidth
	 * @param viewHeight
	 * @param minSize
	 * @return
	 */
	public static Bitmap createBitmapLarge(InputStream data, int viewWidth, int viewHeight, boolean minSize) {
		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(data, null, o);

		try {
			data.mark(data.available());
			data.reset();
		} catch (IOException e) {
//			Crashlytics.logException(new Exception(
//				"InputStream does not support mark: " + data.getClass().getName(), e));
			return null;
		}

		o.inSampleSize = getLargeSampleSize(o, viewWidth, viewHeight);
		// setScalingPow2(image, viewWidth, viewHeight, o, minSize);
		o.inJustDecodeBounds = false;
		return BitmapFactory.decodeStream(data, null, o);
	}

	/**
	 * @param data   image inputstream that must support mark.reset
	 * @param width
	 * @param height
	 * @return
	 */
	public static Bitmap createBitmapToSize(InputStream data, int width, int height) {
		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(data, null, o);
		o.inSampleSize = getExactSampleSize(o, width, height);
		o.inJustDecodeBounds = false;

		try {
			data.mark(data.available());
			data.reset();
		} catch (IOException e) {
//			Crashlytics.logException(new Exception(
//				"InputStream does not support mark: " + data.getClass().getName(), e));
			return null;
		}

		return BitmapFactory.decodeStream(data, null, o);
	}

	public static Bitmap createBitmapToSize(Resources res, int resId, int width, int height) {
		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeResource(res, resId, o);
		o.inSampleSize = getExactSampleSize(o, width, height);
		o.inJustDecodeBounds = false;
		return BitmapFactory.decodeResource(res, resId, o);
	}

	public static Bitmap createBitmapToSize(byte[] image, int width, int height) {
		Bitmap result = null;
		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeByteArray(image, 0, image.length, o);
		o.inSampleSize = getExactSampleSize(o, width, height);
		o.inJustDecodeBounds = false;
		result = BitmapFactory.decodeByteArray(image, 0, image.length, o);
		return result;
	}

	public static Bitmap applyWatermark(Bitmap src, Watermark watermark) {
		int width = src.getWidth();
		int height = src.getHeight();

		int pixels = watermark.getWidth() * watermark.getHeight();

		int[] source = new int[width * height];
		int[] mark = new int[pixels];

		Point location = watermark.getLocation(height, width);

		watermark.getWatermark().getPixels(mark, 0, watermark.getWidth(), 0, 0, watermark.getWidth(), watermark.getHeight());
		src.getPixels(source, 0, width, 0, 0, width, height);

		int i = 0;
		for (int y = location.y; y < location.y + watermark.getHeight(); ++y) {
			for (int x = location.x; x < location.x + watermark.getWidth(); ++x) {
				int index = y * width + x;

				float opacity = Color.alpha(mark[i]) / 255f;
				source[index] = Color.argb(
					Color.alpha(source[index]),
					Math.min(Color.red(source[index]) + (int) (Color.red(mark[i]) * opacity), 255),
					Math.min(Color.green(source[index]) + (int) (Color.green(mark[i]) * opacity), 255),
					Math.min(Color.blue(source[index]) + (int) (Color.blue(mark[i]) * opacity), 255));
				++i;
			}
		}

		return Bitmap.createBitmap(source, width, height, Bitmap.Config.ARGB_8888);
	}

	public static Bitmap getWatermarkText(String text, int alpha, int size) {
		if (text.isEmpty())
			return null;

		int opacityByte = (int) (alpha / 100f * 255);
		Paint paint = new Paint();
		paint.setColor(Color.WHITE);
		paint.setShadowLayer(1, 1, 1, Color.BLACK);
		paint.setAlpha(opacityByte);
		paint.setTextSize(size);
		paint.setAntiAlias(true);
		paint.setTextAlign(Paint.Align.LEFT);

		int width = (int) (paint.measureText(text) + 0.5f); // round
		float baseline = (int) (-paint.ascent() + 0.5f); // ascent() is negative
		int height = (int) (baseline + paint.descent() + 0.5f);

		Bitmap watermark = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(watermark);
		canvas.drawText(text, 0, baseline, paint);

		return watermark;
	}

	@Nullable
	public static Watermark getWatermark(final Context c)	// TODO: Should take a WatermarkConfig
	{
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(c);
		boolean showWatermark = pref.getBoolean(WatermarkSettingsFragment.KEY_EnableWatermark, false);

		if (showWatermark)
		{
			String watermarkText = pref.getString(WatermarkSettingsFragment.KEY_WatermarkText, "");
			int watermarkAlpha = pref.getInt(WatermarkSettingsFragment.KEY_WatermarkAlpha, 75);
			int watermarkSize = pref.getInt(WatermarkSettingsFragment.KEY_WatermarkSize, 150);

			int top = Integer.parseInt(pref.getString(WatermarkSettingsFragment.KEY_WatermarkTopMargin, "-1"));
			int bottom = Integer.parseInt(pref.getString(WatermarkSettingsFragment.KEY_WatermarkBottomMargin, "-1"));
			int right = Integer.parseInt(pref.getString(WatermarkSettingsFragment.KEY_WatermarkRightMargin, "-1"));
			int left = Integer.parseInt(pref.getString(WatermarkSettingsFragment.KEY_WatermarkLeftMargin, "-1"));
			Margins margins = new Margins(top, left, bottom, right);

			if (watermarkText.isEmpty())
			{
				Toast.makeText(c, R.string.warningBlankWatermark, Toast.LENGTH_LONG).show();
				return null;
			}
			else
			{
				Bitmap watermark = ImageUtil.getWatermarkText(watermarkText, watermarkAlpha, watermarkSize);
				if (watermark == null)
					return null;
				return new Watermark(
					margins,
					watermark);
			}
		}
		return null;
	}
}
