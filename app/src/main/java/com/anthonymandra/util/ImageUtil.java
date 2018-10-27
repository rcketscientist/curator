package com.anthonymandra.util;

import android.annotation.SuppressLint;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import com.anthonymandra.content.Meta;
import com.anthonymandra.framework.DocumentUtil;
import com.anthonymandra.framework.UsefulDocumentFile;
import com.anthonymandra.imageprocessor.ImageProcessor;
import com.anthonymandra.rawdroid.R;
import com.anthonymandra.rawdroid.data.AppDatabase;
import com.anthonymandra.rawdroid.data.ImageInfo;
import com.crashlytics.android.Crashlytics;
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

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

@SuppressLint("AndroidLintSimpleDateFormat") // These are for specific library formats
public class ImageUtil
{
    private static String TAG = ImageUtil.class.getSimpleName();

    /**
     * --- File Association Helpers ----------------------------------------------------------------
     */

    public static Uri[] getAssociatedFiles(Context c, Uri image)
    {
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

    public static UsefulDocumentFile getXmpFile(Context c, Uri uri)
    {
        return getAssociatedFile(c, uri, "xmp");
    }

    public static UsefulDocumentFile getJpgFile(Context c, Uri uri)
    {
        return getAssociatedFile(c, uri, "jpg");
    }

    public static File getXmpFile(File f)
    {
        return getAssociatedFile(f, "xmp");
    }

    public static File getJpgFile(File f)
    {
        return getAssociatedFile(f, "jpg");
    }

    private static UsefulDocumentFile getAssociatedFile(Context c, Uri uri, String ext)
    {
        UsefulDocumentFile image = UsefulDocumentFile.fromUri(c, uri);
        String name = image.getName();
        if (ext != null)
        {
            name = FileUtil.swapExtention(name, ext);
        }

        Uri neighbor;
        if (FileUtil.isFileScheme(uri))
        {
            File n = getAssociatedFile(new File(uri.getPath()), ext);
            neighbor = Uri.fromFile(n);
        }
        else
        {
            neighbor = DocumentUtil.getNeighborUri(uri, name);
        }

        if (neighbor == null)
            return null;

        return UsefulDocumentFile.fromUri(c, neighbor);
    }

    /**
     * Gets a similarly named file with a new ext
     * @param file
     * @param ext
     * @return
     */
    private static File getAssociatedFile(File file, String ext) {
        String name = file.getName();
        name = FileUtil.swapExtention(name, ext);
        return new File(file.getParent(), name);
    }

    /**
     * --- Content Helpers -------------------------------------------------------------------------
     */

    /**
     * Remove db entries for files that are no longer present.  This should be threaded.
     * @param c
     * @throws RemoteException
     * @throws OperationApplicationException
     */
    public static void cleanDatabase(final Context c) throws RemoteException, OperationApplicationException
    {
        final ArrayList<ContentProviderOperation> operations = new ArrayList<>();

        final String[] projection = new String[] { Meta.URI, BaseColumns._ID };
        try( Cursor cursor = c.getContentResolver().query(Meta.CONTENT_URI, projection, null, null, null))
        {
            if (cursor == null)
                return;

            final int uriColumn = cursor.getColumnIndex(Meta.URI);
            final int idColumn = cursor.getColumnIndex(BaseColumns._ID);
            if (uriColumn == -1 || idColumn == -1)
            {
                Crashlytics.logException(new Exception("column not found"));
                return;
            }

            while (cursor.moveToNext())
            {
                String uriString = cursor.getString(uriColumn);
                if (uriString == null)  // we've got some bogus data, just remove
                {
                    operations.add(ContentProviderOperation.newDelete(
                            Uri.withAppendedPath(Meta.CONTENT_URI, cursor.getString(idColumn))).build());
                    continue;
                }
                Uri uri = Uri.parse(uriString);
                UsefulDocumentFile file = UsefulDocumentFile.fromUri(c, uri);
                if (!file.exists())
                {
                    operations.add(ContentProviderOperation.newDelete(
                            Uri.withAppendedPath(Meta.CONTENT_URI, cursor.getString(idColumn))).build());
                }
            }
        }

        c.getContentResolver().applyBatch(Meta.AUTHORITY, operations);
    }

    public static boolean isAndroidImage(final Context c, final Uri uri)
    {
        String type = c.getContentResolver().getType(uri);
        return isAndroidImage(type);
    }

    public static boolean isAndroidImage(final String mimeType)
    {
        for (String mime : ImageConstants.ANDROID_IMAGE_MIME)
        {
            if (mime.equals(mimeType))
                return true;
        }
        return false;
    }

    public static boolean isAndroidImage(final FileType fileType)
    {
        return  FileType.Bmp == fileType ||
                FileType.Gif == fileType ||
                FileType.Jpeg == fileType ||
                FileType.Png == fileType;
    }

    public static boolean isTiffImage(final FileType fileType)
    {
        return FileType.Tiff == fileType;
    }

    public static boolean isTiffMime(String mimeType)
    {
        return ImageConstants.TIFF_MIME.equals(mimeType);
    }

    public static void importKeywords(Context c, Uri keywordUri)
    {
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
     * @param file
     * @return
     */
    public static Meta.ImageType getImageType(Uri file)
    {
        if (isRaw(file)) return Meta.ImageType.RAW;
        if (isNative(file)) return Meta.ImageType.COMMON;
        if (isTiffImage(file)) return Meta.ImageType.TIFF;
        return Meta.ImageType.UNKNOWN;
    }

	/**
     * Converts fileType to imageType for decoding purposes
     * @param fileType
     * @return
     */
    private static Meta.ImageType getImageType(FileType fileType)
    {
        if (FileType.Unknown == fileType)
            return Meta.ImageType.UNKNOWN;
        if (isAndroidImage(fileType))
            return Meta.ImageType.COMMON;
        if (isTiffImage(fileType))
            return  Meta.ImageType.TIFF;
        return Meta.ImageType.RAW;
    }

	/**
     * First attempts to derive the image type from path, then falls back to magic number
     * @param c
     * @param uri
     * @return
     */
    public static Meta.ImageType getImageType(final Context c, final Uri uri)
    {
        // Try to derive from path
        Meta.ImageType type = getImageType(uri);
        if (type != Meta.ImageType.UNKNOWN)
            return type;

        // If that fails use the magic number
        try (InputStream is = c.getContentResolver().openInputStream(uri))
        {
            if (is == null)
                return Meta.ImageType.UNKNOWN;

            BufferedInputStream bis = new BufferedInputStream(is);
            FileType fileType = FileTypeDetector.detectFileType(bis);
            return getImageType(fileType);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return Meta.ImageType.UNKNOWN;
        }
    }

    public static boolean isImage(String name)
    {
        return isRaw(name) || isNative(name) || isTiffImage(name);
    }

    public static boolean isRaw(Uri uri)
    {
	    String path = uri.getPath();
	    // If the uri is not hierarchical
	    return path != null && isRaw(path);
    }

    public static boolean isRaw(String name)
    {
        return endsWith(ImageConstants.RAW_EXT, name);
    }

    public static boolean isJpeg(File file)
    {
        return isJpeg(file.getName());
    }

    public static boolean isJpeg(Uri uri)
    {
	    String path = uri.getPath();
	    // If the uri is not hierarchical
	    return path != null && isJpeg(path);
    }

    public static boolean isJpeg(String name)
    {
        return endsWith(ImageConstants.JPEG_EXT, name);
    }

    public static boolean isNative(String name)
    {
        return endsWith(ImageConstants.COMMON_EXT, name);
    }

	public static boolean isNative(Uri uri)
    {
	    String path = uri.getPath();
	    // If the uri is not hierarchical
	    return path != null && isNative(path);
    }

    public static boolean isTiffImage(String name)
    {
        return endsWith(ImageConstants.TIFF_EXT, name);
    }

    public static boolean isTiffImage(File file)
    {
        return isTiffImage(file.getName());
    }

    public static boolean isTiffImage(Uri uri)
    {
	    String path = uri.getPath();
	    // If the uri is not hierarchical
	    return path != null && isTiffImage(path);
    }

    private static boolean endsWith(String[] extensions, String path)
    {
        for (String ext : extensions)
        {
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

    private static byte[] getAndroidImage(Context c, Uri uri, ContentValues values)
    {
        byte[] imageBytes = getImageBytes(c, uri);
        if (imageBytes != null)
        {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;

            // Decode dimensions
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, o);
            values.put(Meta.WIDTH, o.outWidth);
            values.put(Meta.HEIGHT, o.outHeight);
        }
        return imageBytes;
    }

    private static byte[] getAndroidImage(byte[] imageBytes, ContentValues values)
    {
        if (imageBytes != null)
        {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;

            // Decode dimensions
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, o);
            values.put(Meta.WIDTH, o.outWidth);
            values.put(Meta.HEIGHT, o.outHeight);
        }
        return imageBytes;
    }

    private static byte[] getTiffImage(int fileDescriptor)
    {
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

    private static byte[] getRawThumb(int fileDescriptor)
    {
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

	public static BufferedInputStream getImageStream(ContentProviderClient cpc, Uri uri) throws IOException, RemoteException
	{
		AssetFileDescriptor fd = cpc.openAssetFile(uri, "r");
		if (fd == null)
			return null;
		return new BufferedInputStream(fd.createInputStream());
	}

	private static final String[] TYPE_PROJECTION = new String[] {Meta.TYPE};
    public static byte[] getThumb(final Context c, final Uri uri) throws Exception {
        try (Cursor metaCursor = c.getContentResolver().query(
                Meta.CONTENT_URI, TYPE_PROJECTION,
                Meta.URI_SELECTION,
                new String[]{uri.toString()}, null, null))
        {
            // TODO: Does this check really save anything over just looking at the file every time?
            Meta.ImageType type = null;
            if (metaCursor != null && metaCursor.moveToFirst())
                type = Meta.ImageType.fromInt(metaCursor.getInt(metaCursor.getColumnIndex(Meta.TYPE)));
            return getThumb(c, uri, type);
        }
    }

	/**
     * Processes the thumbnail from an existing cursor pointing to the desired row
     * @param c context
     * @return byte array jpeg for display
     */
    @SuppressLint("SimpleDateFormat")
    public static byte[] getThumb(final Context c, final Uri uri, Meta.ImageType imageType) throws Exception {
        AssetFileDescriptor fd = c.getContentResolver().openAssetFileDescriptor(uri, "r");

        if (fd == null)
            return null;

        if (imageType == null || imageType == Meta.ImageType.UNPROCESSED)
            imageType = getImageType(c, uri);

        switch(imageType)
        {
            case COMMON:
                BufferedInputStream imageStream = new BufferedInputStream(fd.createInputStream());
                return Util.toByteArray(imageStream);
            case TIFF:
                return getTiffImage(fd.getParcelFileDescriptor().getFd());
            default:
                return getRawThumb(fd.getParcelFileDescriptor().getFd());
        }
    }

    // TODO: This is temporary to test ssiv
    public static byte[] getThumb2(final Context c, final Uri uri) throws Exception {
        AssetFileDescriptor fd = c.getContentResolver().openAssetFileDescriptor(uri, "r");

        if (fd == null)
            return null;

        Meta.ImageType imageType = getImageType(c, uri);

        switch(imageType)
        {
            case COMMON:
                BufferedInputStream imageStream = new BufferedInputStream(fd.createInputStream());
                return Util.toByteArray(imageStream);
            case TIFF:
                return getTiffImage(fd.getParcelFileDescriptor().getFd());
            default:
                return getRawThumb(fd.getParcelFileDescriptor().getFd());
        }
    }

    public static byte[] getThumb(final Context c, ImageInfo image) throws Exception {
        Uri uri = Uri.parse(image.getUri());
        AssetFileDescriptor fd = c.getContentResolver().openAssetFileDescriptor(uri, "r");

        if (fd == null)
            return null;

        Meta.ImageType imageType = Meta.ImageType.fromInt(image.getType());
        if (imageType == Meta.ImageType.UNKNOWN || imageType == Meta.ImageType.UNPROCESSED)
            imageType = getImageType(c, uri);

        switch(imageType)
        {
            case COMMON:
                BufferedInputStream imageStream = new BufferedInputStream(fd.createInputStream());
                return Util.toByteArray(imageStream);
            case TIFF:
                return getTiffImage(fd.getParcelFileDescriptor().getFd());
            default:
                return getRawThumb(fd.getParcelFileDescriptor().getFd());
        }
    }

    private static byte[] getImageBytes(Context c, Uri uri)
    {
        InputStream is = null;
        try
        {
            is = c.getContentResolver().openInputStream(uri);
            return Util.toByteArray(is);
        } catch (Exception e)
        {
            return null;
        }
        finally
        {
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
    public static int getExactSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight)
    {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth)
        {
            if (width > height)
            {
                inSampleSize = Math.round((float) height / (float) reqHeight);
            } else
            {
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

            while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap)
            {
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
    public static int getLargeSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight)
    {
        int imageWidth = options.outWidth;
        int imageHeight = options.outHeight;

        int scaleH = 1, scaleW = 1;
        if (imageHeight > reqHeight || imageWidth > reqWidth)
        {
            scaleH = (int) Math.pow(2, (int) Math.ceil(Math.log(reqHeight / (double) imageHeight) / Math.log(0.5)));
            scaleW = (int) Math.pow(2, (int) Math.ceil(Math.log(reqWidth / (double) imageWidth) / Math.log(0.5)));
        }
        return Math.max(scaleW, scaleH);
    }

    public static Bitmap createBitmapLarge(byte[] image, int viewWidth, int viewHeight, boolean minSize)
    {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(image, 0, image.length, o);
        o.inSampleSize = getLargeSampleSize(o, viewWidth, viewHeight);
        // setScalingPow2(image, viewWidth, viewHeight, o, minSize);
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(image, 0, image.length, o);
    }

    /**
     *
     * @param data image stream that must support mark and reset
     * @param viewWidth
     * @param viewHeight
     * @param minSize
     * @return
     */
    public static Bitmap createBitmapLarge(InputStream data, int viewWidth, int viewHeight, boolean minSize)
    {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(data, null, o);

        try
        {
            data.mark(data.available());
            data.reset();
        } catch (IOException e)
        {
            Crashlytics.logException(new Exception(
                    "InputStream does not support mark: " + data.getClass().getName(), e));
            return null;
        }

        o.inSampleSize = getLargeSampleSize(o, viewWidth, viewHeight);
        // setScalingPow2(image, viewWidth, viewHeight, o, minSize);
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeStream(data, null, o);
    }

    /**
     *
     * @param data image inputstream that must support mark.reset
     * @param width
     * @param height
     * @return
     */
    public static Bitmap createBitmapToSize(InputStream data, int width, int height)
    {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(data, null, o);
        o.inSampleSize = getExactSampleSize(o, width, height);
        o.inJustDecodeBounds = false;

        try
        {
            data.mark(data.available());
            data.reset();
        } catch (IOException e)
        {
            Crashlytics.logException(new Exception(
                    "InputStream does not support mark: " + data.getClass().getName(), e));
            return null;
        }

        return BitmapFactory.decodeStream(data, null, o);
    }

    public static Bitmap createBitmapToSize(Resources res, int resId, int width, int height)
    {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, o);
        o.inSampleSize = getExactSampleSize(o, width, height);
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, o);
    }

    public static Bitmap createBitmapToSize(byte[] image, int width, int height)
    {
        Bitmap result = null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(image, 0, image.length, o);
        o.inSampleSize = getExactSampleSize(o, width, height);
        o.inJustDecodeBounds = false;
        result = BitmapFactory.decodeByteArray(image, 0, image.length, o);
        return result;
    }

    public static Bitmap addWatermark2(Context context, Bitmap src)
    {
        int width = src.getWidth();
        int height = src.getHeight();
        Bitmap result = Bitmap.createBitmap(width, height, src.getConfig());

        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(src, 0, 0, null);
        int id = R.drawable.watermark1024;
        if (width < 3072)
            id = R.drawable.watermark512;
        else if (width < 1536)
            id = R.drawable.watermark256;
        else if (width < 768)
            id = R.drawable.watermark128;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        Bitmap watermark = BitmapFactory.decodeResource(context.getResources(), id, o);
        watermark.setDensity(result.getDensity());
        canvas.drawBitmap(watermark, width / 4 * 3, height / 4 * 3, null);

        return result;
    }

    public static Bitmap addWatermark(Context context, File file, Bitmap src)
    {
        int width = src.getWidth();
        int height = src.getHeight();

        int id = R.drawable.watermark1024;
        if (width < 3072)
            id = R.drawable.watermark512;
        else if (width < 1536)
            id = R.drawable.watermark256;
        else if (width < 768)
            id = R.drawable.watermark128;


        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        Bitmap watermark = BitmapFactory.decodeResource(context.getResources(), id, o);

        int startX = width / 4 * 3;
        int startY = height / 4 * 3;

        int watermarkWidth = watermark.getWidth();
        int watermarkHeight = watermark.getHeight();

        int pixels = watermarkWidth * watermarkHeight;
        int[] source = new int[width * height];
        int[] mark = new int[pixels];

        watermark.getPixels(mark, 0, watermarkWidth, 0, 0, watermarkWidth, watermarkHeight);
        src.getPixels(source, 0, width, 0, 0, width, height);

        int i = 0;
        for (int y = startY; y < startY + watermarkHeight; ++y)
        {
            for (int x = startX; x < startX + watermarkWidth; ++x)
            {
                int index = y * width + x;
                // Applying a 50% opacity on top of the given opacity.  Somewhat arbitrary, but looks the same as the canvas method.
                // Perhaps this is because the canvas applies 50% to stacked images, maybe just luck...
                @SuppressLint("Range") float opacity = Color.alpha(mark[i]) / 510f;
                source[index] = Color.argb(
                        Color.alpha(source[index]),
                        Math.min(Color.red(source[index]) + (int) (Color.red(mark[i]) * opacity), 255),
                        Math.min(Color.green(source[index]) + (int) (Color.green(mark[i]) * opacity), 255),
                        Math.min(Color.blue(source[index]) + (int) (Color.blue(mark[i]) * opacity), 255));
                ++i;
            }
        }

//        src.setPixels(source, 0, width, 0, 0, width, height);

        return Bitmap.createBitmap(source, width, height, Bitmap.Config.ARGB_8888);
    }

    public static Bitmap getDemoWatermark(Context context, int srcWidth)
    {
        int id;
        if (srcWidth > 5120)
            id = R.drawable.watermark1024;
        else if (srcWidth > 2560)
            id = R.drawable.watermark512;
        else if (srcWidth > 1280)
            id = R.drawable.watermark256;
        else
            id = R.drawable.watermark128;

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        return BitmapFactory.decodeResource(context.getResources(), id, o);
    }

    public static Bitmap addWatermark(Context context, Bitmap src)
    {
        int width = src.getWidth();
        int height = src.getHeight();

        int id = R.drawable.watermark1024;
        if (width < 3072)
            id = R.drawable.watermark512;
        else if (width < 1536)
            id = R.drawable.watermark256;
        else if (width < 768)
            id = R.drawable.watermark128;

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        Bitmap watermark = BitmapFactory.decodeResource(context.getResources(), id, o);

        int startX = width / 4 * 3;
        int startY = height / 4 * 3;

        int watermarkWidth = watermark.getWidth();
        int watermarkHeight = watermark.getHeight();

        int pixels = watermarkWidth * watermarkHeight;
        int[] source = new int[width * height];
        int[] mark = new int[pixels];

        watermark.getPixels(mark, 0, watermarkWidth, 0, 0, watermarkWidth, watermarkHeight);
        src.getPixels(source, 0, width, 0, 0, width, height);

        int i = 0;
        for (int y = startY; y < startY + watermarkHeight; ++y)
        {
            for (int x = startX; x < startX + watermarkWidth; ++x)
            {
                int index = y * width + x;
                // Applying a 50% opacity on top of the given opacity.  Somewhat arbitrary, but looks the same as the canvas method.
                // Perhaps this is because the canvas applies 50% to stacked images, maybe just luck...
                @SuppressLint("Range") float opacity = Color.alpha(mark[i]) / 510f;
                source[index] = Color.argb(
                        Color.alpha(source[index]),
                        Math.min(Color.red(source[index]) + (int) (Color.red(mark[i]) * opacity), 255),
                        Math.min(Color.green(source[index]) + (int) (Color.green(mark[i]) * opacity), 255),
                        Math.min(Color.blue(source[index]) + (int) (Color.blue(mark[i]) * opacity), 255));
                ++i;
            }
        }

//        src.setPixels(source, 0, width, 0, 0, width, height);

        return Bitmap.createBitmap(source, width, height, Bitmap.Config.ARGB_8888);
    }

    public static Bitmap addCustomWatermark(Bitmap src, String watermark, int alpha,
                                            int size, String location)
    {
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap result = Bitmap.createBitmap(w, h, src.getConfig());

        int x = 0, y = 0;

        // We center the text in their respective quadrants
        switch (location)
        {
            case "Center":
                x = w / 2;
                y = h / 2;
                break;
            case "Lower Left":
                x = w / 4;
                y = h / 4 * 3;
                break;
            case "Lower Right":
                x = w / 4 * 3;
                y = h / 4 * 3;
                break;
            case "Upper Left":
                x = w / 4;
                y = h / 4;
                break;
            case "Upper Right":
                x = w / 4 * 3;
                y = h / 4;
                break;
        }

        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(src, 0, 0, null);

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setShadowLayer(1, 1, 1, Color.BLACK);
        paint.setAlpha(alpha);
        paint.setTextSize(size);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(watermark, x, y, paint);

        return result;
    }

    public static Bitmap getWatermarkText(String text, int alpha, int size, String location)
    {
        if (text.isEmpty())
            return null;

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setShadowLayer(1, 1, 1, Color.BLACK);
        paint.setAlpha(alpha);
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
}
