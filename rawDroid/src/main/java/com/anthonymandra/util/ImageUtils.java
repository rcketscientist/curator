package com.anthonymandra.util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DecodeUtils;
import com.android.gallery3d.data.ImageCacheRequest;
import com.android.gallery3d.util.ThreadPool;
import com.anthonymandra.content.KeywordProvider;
import com.anthonymandra.content.Meta;
import com.anthonymandra.framework.DocumentUtil;
import com.anthonymandra.framework.MetaMedia;
import com.anthonymandra.framework.UsefulDocumentFile;
import com.anthonymandra.rawdroid.R;
import com.anthonymandra.imageprocessor.Exif;
import com.anthonymandra.imageprocessor.ImageProcessor;
import com.crashlytics.android.Crashlytics;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.exif.makernotes.CanonMakernoteDirectory;
import com.drew.metadata.exif.makernotes.FujifilmMakernoteDirectory;
import com.drew.metadata.exif.makernotes.LeicaMakernoteDirectory;
import com.drew.metadata.exif.makernotes.NikonType2MakernoteDirectory;
import com.drew.metadata.exif.makernotes.PanasonicMakernoteDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import com.drew.metadata.xmp.XmpReader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@SuppressLint("AndroidLintSimpleDateFormat") // These are for specific library formats
public class ImageUtils
{
    private static String TAG = ImageUtils.class.getSimpleName();

    private static SimpleDateFormat mLibrawFormatter = new SimpleDateFormat("EEE MMM d hh:mm:ss yyyy");
    private static SimpleDateFormat mMetaExtractorFormat = new SimpleDateFormat("yyyy:MM:dd H:m:s");

    public static Metadata readMetadata(Context c, Uri uri)
    {
        Metadata meta = readMeta(c, uri);
        return readXmp(c, uri, meta);
    }

    /**
     * Returns metadata from the given image uri
     * @param c
     * @param uri
     * @return
     */
    private static Metadata readMeta(Context c, Uri uri)
    {
        InputStream image = null;
        Metadata meta = new Metadata();
        try
        {
            image = c.getContentResolver().openInputStream(uri);
            meta = ImageMetadataReader.readMetadata(image);  //TODO: possibly replace with exiv, too much overhead
        }
        catch (Exception e)
        {
            Crashlytics.setString("readMetaUri", uri.toString());
            Crashlytics.logException(e);
        }
        finally
        {
            Utils.closeSilently(image);
        }
        return meta;
    }

    /**
     * Reads associated xmp file if it exists
     */
    private static Metadata readXmp(Context c, Uri uri)
    {
        return readXmp(c, uri, new Metadata());
    }

    /**
     * Reads associated xmp file if it exists and adds the data to meta
     * @param uri image file
     */
    private static Metadata readXmp(Context c, Uri uri, Metadata meta)
    {
        UsefulDocumentFile xmpDoc = getXmpFile(c, uri);
        if (xmpDoc == null || !xmpDoc.exists())
            return meta;

        InputStream xmpStream = null;
        try
        {
            xmpStream = FileUtil.getInputStream(c, xmpDoc.getUri());
            XmpReader reader = new XmpReader();
            byte[] buffer = new byte[xmpStream.available()];
            xmpStream.read(buffer);
            reader.extract(buffer, meta);
            return meta;
        }
        catch (IOException e)
        {
            Log.e(TAG, "Failed to open XMP.", e);
            return meta;
        }
        finally
        {
            Utils.closeSilently(xmpStream);
        }
    }

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
        return files.toArray(new Uri[files.size()]);
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
     * Updates the metadata database.  This should be run in the background.
     * @param databaseUpdates database updates
     */
    public static void updateMetaDatabase(Context c, ArrayList<ContentProviderOperation> databaseUpdates)
    {
        try
        {
            // TODO: If I implement bulkInsert it's faster
            ContentProviderResult[] results = c.getContentResolver().applyBatch(Meta.AUTHORITY, databaseUpdates);
        } catch (RemoteException | OperationApplicationException e)
        {
            //TODO: Notify user
            e.printStackTrace();
        }
    }

    public static ContentProviderOperation newInsert(Context c, Uri image)
    {
        UsefulDocumentFile file = UsefulDocumentFile.fromUri(c, image);
        UsefulDocumentFile.FileData fd = file.getData();

        ContentValues cv = new ContentValues();
        if (fd != null)
        {
            cv.put(Meta.Data.NAME, fd.name);
            cv.put(Meta.Data.PARENT, fd.parent.toString());
            cv.put(Meta.Data.TIMESTAMP, fd.lastModified);
        }
        else
        {
            cv.put(Meta.Data.PARENT, file.getParentFile().getUri().toString());
        }
        cv.put(Meta.Data.URI, image.toString());
        cv.put(Meta.Data.TYPE, ImageUtils.getImageType(image));

       return ContentProviderOperation.newInsert(Meta.Data.CONTENT_URI)
                .withValues(cv)
                .build();
    }

    public static ContentProviderOperation newDelete(Uri image)
    {
        return ContentProviderOperation.newDelete(Meta.Data.CONTENT_URI)
                .withSelection(ImageUtils.getWhere(), new String[] { image.toString() })
                .build();
    }

    public static ContentProviderOperation newUpdate(Uri image, ContentValues cv)
    {
        return ContentProviderOperation.newUpdate(Meta.Data.CONTENT_URI)
                .withSelection(ImageUtils.getWhere(), new String[] {image.toString()})
                .withValues(cv)
                .build();
    }

    //TODO: Perhaps this makes more sense in the meta provider itself?
    /**
     * Read meta data and convert to ContentValues for {@link com.anthonymandra.content.MetaProvider}
     * @param c
     * @param uri
     * @return
     */
    public static ContentValues getContentValues(@NonNull Context c, @NonNull Uri uri)
    {
        Metadata meta = readMetadata(c, uri);
        return getContentValues(uri, meta, getImageType(uri));
    }

    //TODO: Perhaps this makes more sense in the meta provider itself?
    /**
     * Read meta data and convert to ContentValues for {@link com.anthonymandra.content.MetaProvider}
     * @param c
     * @param uri
     * @return
     */
    public static ContentValues getContentValues(Context c, Uri uri, int type)
    {
        Metadata meta = readMetadata(c, uri);
        return getContentValues(uri, meta, type);
    }

    public static Cursor getMetaCursor(Context c, Uri uri)
    {
        return c.getContentResolver().query(Meta.Data.CONTENT_URI,
		        null,
		        getWhere(),
		        new String[]{uri.toString()},
		        null);
    }

    public static boolean isInDatabase(Context c, Uri uri)
    {
        return getMetaCursor(c, uri).moveToFirst();
    }

    public static String getWhere()
    {
        return Meta.Data.URI + "=?";
    }

    public static boolean isProcessed(Context c, Uri uri)
    {
        final Cursor cursor = ImageUtils.getMetaCursor(c, uri);
        try
        {
            return cursor.moveToFirst() && cursor.getInt(Meta.PROCESSED_COLUMN) != 0;
        }
        finally
        {
            Utils.closeSilently(cursor);
        }
    }

    /**
     * Remove db entries for files that are no longer present.  This should be threaded.
     * @param c
     * @throws RemoteException
     * @throws OperationApplicationException
     */
    public static void cleanDatabase(final Context c) throws RemoteException, OperationApplicationException
    {
        final ArrayList<ContentProviderOperation> operations = new ArrayList<>();

        try(Cursor cursor = c.getContentResolver().query(Meta.Data.CONTENT_URI, null, null, null, null))
        {
            while (cursor.moveToNext())
            {
                String uriString = cursor.getString(Meta.URI_COLUMN);
                if (uriString == null)  // we've got some bogus data, just remove
                {
                    operations.add(ContentProviderOperation.newDelete(
                            Uri.withAppendedPath(Meta.Data.CONTENT_URI, cursor.getString(Meta.ID_COLUMN))).build());
                    continue;
                }
                Uri uri = Uri.parse(uriString);
                UsefulDocumentFile file = UsefulDocumentFile.fromUri(c, uri);
                if (!file.exists())
                {
                    operations.add(ContentProviderOperation.newDelete(Meta.Data.CONTENT_URI)
                            .withSelection(getWhere(), new String[]{uriString}).build());
                }
            }
        }

        // TODO: If I implement bulkInsert it's faster
        c.getContentResolver().applyBatch(Meta.AUTHORITY, operations);
    }


    public static void setExifValues(final Uri uri, final Context c, final String[] exif)
    {
        final Cursor cursor = getMetaCursor(c, uri);

        try
        {
            cursor.moveToFirst();
            ContentValues values = new ContentValues();

            // Check if meta is already processed
            if (cursor.moveToFirst() && cursor.getInt(Meta.PROCESSED_COLUMN) != 0)
            {
                return;
            }
        }
        finally
        {
            cursor.close();
        }

        ContentValues cv = new ContentValues();
        try
        {
            /*
            For now only include image related information.  As this is a parse related
            to the processing of the image for display this is reasonable.  It avoids
            constant flickering as the timestamps cause a database update.
             */
            cv.put(Meta.Data.HEIGHT, Integer.parseInt(exif[10]));
            cv.put(Meta.Data.WIDTH, Integer.parseInt(exif[11]));
            cv.put(Meta.Data.ORIENTATION, Integer.parseInt(exif[7]));
//                    cv.put(Meta.Data.TIMESTAMP, mLibrawFormatter.parse(exif[6].trim()).getTime());
        }
        catch (Exception e)
        {
            Log.d(TAG, "Exif parse failed:", e);
        }
        c.getContentResolver().update(Meta.Data.CONTENT_URI, cv, getWhere(),
                new String[]{uri.toString()});
    }

    /**
     * Convert given meta into ContentValues for {@link com.anthonymandra.content.MetaProvider}
     * @param uri
     * @param meta
     * @return
     */
    @Nullable
    public static ContentValues getContentValues(@NonNull Uri uri, Metadata meta, int type)
    {
        final ContentValues cv = new ContentValues();
        if (meta == null)
        {
            return null;
        }

        cv.put(Meta.Data.ALTITUDE, getAltitude(meta));
        cv.put(Meta.Data.APERTURE, getAperture(meta));
        cv.put(Meta.Data.EXPOSURE, getExposure(meta));
        cv.put(Meta.Data.FLASH, getFlash(meta));
        cv.put(Meta.Data.FOCAL_LENGTH, getFocalLength(meta));
        cv.put(Meta.Data.HEIGHT, getImageHeight(meta));
        cv.put(Meta.Data.ISO, getIso(meta));
        cv.put(Meta.Data.LATITUDE, getLatitude(meta));
        cv.put(Meta.Data.LONGITUDE, getLongitude(meta));
        cv.put(Meta.Data.MODEL, getModel(meta));

        cv.put(Meta.Data.ORIENTATION, getOrientation(meta));
        String rawDate = getDateTime(meta);
        if (rawDate != null)  // Don't overwrite null since we can rely on file time
        {
            Date date = null;
            try
            {
                date = mMetaExtractorFormat.parse(rawDate);
                cv.put(Meta.Data.TIMESTAMP, date.getTime());
            }
            catch (ParseException e)
            {
                e.printStackTrace();
            }
        }
        cv.put(Meta.Data.WHITE_BALANCE, getWhiteBalance(meta));
        cv.put(Meta.Data.WIDTH, getImageWidth(meta));

        cv.put(Meta.Data.RATING, getRating(meta));
        cv.put(Meta.Data.SUBJECT, convertArrayToString(getSubject(meta)));
        cv.put(Meta.Data.LABEL, getLabel(meta));

        cv.put(Meta.Data.LENS_MODEL, getLensModel(meta));
        cv.put(Meta.Data.DRIVE_MODE, getDriveMode(meta));
        cv.put(Meta.Data.EXPOSURE_MODE, getExposureMode(meta));
        cv.put(Meta.Data.EXPOSURE_PROGRAM, getExposureProgram(meta));
        cv.put(Meta.Data.PROCESSED, true);

        return cv;
    }

    public static String strSeparator = "__,__";
    public static String convertArrayToString(String[] array){
        if (array == null)
            return null;
        String str = "";
        for (int i = 0;i<array.length; i++) {
            str = str+array[i];
            // Do not append comma at the end of last element
            if(i<array.length-1){
                str = str+strSeparator;
            }
        }
        return str;
    }
    public static String[] convertStringToArray(String str){
        if (str == null)
            return null;
        String[] arr = str.split(strSeparator);
        return arr;
    }

    /**
     * --- Meta Getters ----------------------------------------------------------------------------
     */

    private static String getDescription(Metadata meta, Class type, int tag)
    {
        Directory dir = meta.getFirstDirectoryOfType(type);
        if (dir == null || !dir.containsTag(tag))
            return null;
        return dir.getDescription(tag);
    }

    private static String[] getStringArray(Metadata meta, Class type, int tag)
    {
        Directory dir = meta.getFirstDirectoryOfType(type);
        if (dir == null || !dir.containsTag(tag))
            return null;
        return dir.getStringArray(tag);
    }

    private static int getInt(Metadata meta, Class type, int tag)
    {
        Directory dir = meta.getFirstDirectoryOfType(type);
        int result = 0;
        if (dir == null || !dir.containsTag(tag))
            return result;
        try
        {
            result = dir.getInt(tag);
        } catch (MetadataException e)
        {
            e.printStackTrace();
        }
        return result;
    }

    private static Double getDouble(Metadata meta, Class type, int tag)
    {
        Directory dir = meta.getFirstDirectoryOfType(type);
        Double result = null;
        if (dir == null || !dir.containsTag(tag))
            return result;
        try
        {
            result = dir.getDouble(tag);
        } catch (MetadataException e)
        {
            e.printStackTrace();
        }
        return result;
    }

    private static String getExifIFD0Description(Metadata meta, int tag)
    {
        Directory exifsub = meta.getFirstDirectoryOfType(ExifIFD0Directory.class);
        if (exifsub == null)
            return null;
        return exifsub.getDescription(tag);
    }

    private static String getAperture(Metadata meta)
    {
        return getDescription(meta, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_APERTURE);
    }

    private static String getExposure(Metadata meta)
    {
        return getDescription(meta, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
    }

    private static String getImageHeight(Metadata meta)
    {
        return getDescription(meta, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT);
    }

    private static String getImageWidth(Metadata meta)
    {
        return getDescription(meta, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH);
    }

    private static String getFocalLength(Metadata meta)
    {
        return getDescription(meta, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
    }

    private static String getFlash(Metadata meta)
    {
        return getDescription(meta, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_FLASH);
    }

    private static String getShutterSpeed(Metadata meta)
    {
        return getDescription(meta, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_SHUTTER_SPEED);
    }

    private static String getWhiteBalance(Metadata meta)
    {
        if (meta.containsDirectoryOfType(CanonMakernoteDirectory.class))
            return meta.getFirstDirectoryOfType(CanonMakernoteDirectory.class).getDescription(CanonMakernoteDirectory.FocalLength.TAG_WHITE_BALANCE);
        if (meta.containsDirectoryOfType(PanasonicMakernoteDirectory.class))
            return meta.getFirstDirectoryOfType(PanasonicMakernoteDirectory.class).getDescription(PanasonicMakernoteDirectory.TAG_WHITE_BALANCE);
        if (meta.containsDirectoryOfType(FujifilmMakernoteDirectory.class))
            return meta.getFirstDirectoryOfType(FujifilmMakernoteDirectory.class).getDescription(FujifilmMakernoteDirectory.TAG_WHITE_BALANCE);
        if (meta.containsDirectoryOfType(LeicaMakernoteDirectory.class))
            return meta.getFirstDirectoryOfType(LeicaMakernoteDirectory.class).getDescription(LeicaMakernoteDirectory.TAG_WHITE_BALANCE);
        return getDescription(meta, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_WHITE_BALANCE);
    }

    private static String getExposureProgram(Metadata meta)
    {
        return getDescription(meta, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_EXPOSURE_PROGRAM);
    }

    private static String getExposureMode(Metadata meta)
    {
        return getDescription(meta, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_EXPOSURE_MODE);
    }

    private static String getLensMake(Metadata meta)
    {
        return getDescription(meta, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_LENS_MAKE);
    }

    private static String getLensModel(Metadata meta)
    {
        if (meta.containsDirectoryOfType(CanonMakernoteDirectory.class))
            return meta.getFirstDirectoryOfType(CanonMakernoteDirectory.class).getDescription(CanonMakernoteDirectory.TAG_LENS_MODEL);
        if (meta.containsDirectoryOfType(NikonType2MakernoteDirectory.class))
            return meta.getFirstDirectoryOfType(NikonType2MakernoteDirectory.class).getDescription(NikonType2MakernoteDirectory.TAG_LENS);
        return getDescription(meta, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_LENS_MODEL);
    }

    private static String getDriveMode(Metadata meta)
    {
        if (meta.containsDirectoryOfType(CanonMakernoteDirectory.class))
            return meta.getFirstDirectoryOfType(CanonMakernoteDirectory.class).getDescription(CanonMakernoteDirectory.CameraSettings.TAG_CONTINUOUS_DRIVE_MODE);
        if (meta.containsDirectoryOfType(NikonType2MakernoteDirectory.class))
            return meta.getFirstDirectoryOfType(NikonType2MakernoteDirectory.class).getDescription(NikonType2MakernoteDirectory.TAG_SHOOTING_MODE);
        return null;
    }

    private static String getIso(Metadata meta)
    {
        return getDescription(meta, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
    }

    private static String getFNumber(Metadata meta)
    {
        Directory exif = meta.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (exif != null)
        {
            if (exif.containsTag(ExifSubIFDDirectory.TAG_FNUMBER))
                return exif.getDescription(ExifSubIFDDirectory.TAG_FNUMBER);
            else
                return exif.getDescription(ExifSubIFDDirectory.TAG_APERTURE);
        }
        return null;
    }

    private static String getDateTime(Metadata meta)
    {
        return getDescription(meta, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
    }

    private static String getMake(Metadata meta)
    {
        return getExifIFD0Description(meta, ExifIFD0Directory.TAG_MAKE);
    }

    private static String getModel(Metadata meta)
    {
        return getExifIFD0Description(meta, ExifIFD0Directory.TAG_MODEL);
    }

    private static int getOrientation(Metadata meta)
    {
        return getInt(meta, ExifIFD0Directory.class, ExifIFD0Directory.TAG_ORIENTATION);
    }

    public static int getRotation(int orientation)
    {
        switch (orientation)
        {
            case 1:
                return 0;
            case 3:
                return 180;
            case 6:
                return 90;
            case 8:
                return 270;
            case 90:
                return 90;
            case 180:
                return 180;
            case 270:
                return 270;
            default:
                return 0;
        }
    }

    private static String getAltitude(Metadata meta)
    {
        return getDescription(meta, GpsDirectory.class, GpsDirectory.TAG_ALTITUDE);
    }

    private static String getLatitude(Metadata meta)
    {
        return getDescription(meta, GpsDirectory.class, GpsDirectory.TAG_LATITUDE);
    }

    private static String getLongitude(Metadata meta)
    {
        return getDescription(meta, GpsDirectory.class, GpsDirectory.TAG_LONGITUDE);
    }

    private static Double getRating(Metadata meta)
    {
        return getDouble(meta, XmpDirectory.class, XmpDirectory.TAG_RATING);
    }

    private static String getLabel(Metadata meta)
    {
        if (meta.containsDirectoryOfType(XmpDirectory.class))
            return meta.getFirstDirectoryOfType(XmpDirectory.class).getDescription(XmpDirectory.TAG_LABEL);
        return null;
    }

    private static String[] getSubject(Metadata meta)
    {
        return getStringArray(meta, XmpDirectory.class, XmpDirectory.TAG_SUBJECT);
    }

    private static void checkXmpDirectory(Metadata meta)
    {
        if (!meta.containsDirectoryOfType(XmpDirectory.class))
            meta.addDirectory(new XmpDirectory());
    }

    public static void updateRating(Metadata meta, Integer rating)
    {
        updateXmpDouble(meta, XmpDirectory.TAG_RATING, rating == null ? null : rating.doubleValue());
    }

    public static void updateRating(Metadata meta, Double rating)
    {
        updateXmpDouble(meta, XmpDirectory.TAG_RATING, rating);
    }

    public static void updateLabel(Metadata meta, String label)
    {
        updateXmpString(meta, XmpDirectory.TAG_LABEL, label);
    }

    public static void updateSubject(Metadata meta, String[] subject)
    {
        updateXmpStringArray(meta, XmpDirectory.TAG_SUBJECT, subject);
    }

    private static void updateXmpString(Metadata meta, int tag, String value)
    {
        checkXmpDirectory(meta);

        XmpDirectory xmp = meta.getFirstDirectoryOfType(XmpDirectory.class);
        if (value == null)
        {
            if (xmp != null)
                xmp.deleteProperty(tag);
        }
        else
        {
            xmp.updateString(tag, value);
        }
    }

    private static void updateXmpStringArray(Metadata meta, int tag, String[] value)
    {
        checkXmpDirectory(meta);

        XmpDirectory xmp = meta.getFirstDirectoryOfType(XmpDirectory.class);
        if (value == null  || value.length == 0)
        {
            if (xmp != null)
                xmp.deleteProperty(tag);
        }
        else
        {
            xmp.updateStringArray(tag, value);
        }
    }

    private static void updateXmpDouble(Metadata meta, int tag, Double value)
    {
        checkXmpDirectory(meta);

        XmpDirectory xmp = meta.getFirstDirectoryOfType(XmpDirectory.class);
        if (value == null)
        {
            if (xmp != null)
                xmp.deleteProperty(tag);
        }
        else
        {
            xmp.updateDouble(tag, value);
        }
    }

    private static void updateXmpInteger(Metadata meta, int tag, Integer value)
    {
        checkXmpDirectory(meta);

        XmpDirectory xmp = meta.getFirstDirectoryOfType(XmpDirectory.class);
        if (value == null)
        {
            if (xmp != null)
                xmp.deleteProperty(tag);
        }
        else
        {
            xmp.updateInt(tag, value);
        }
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

    public static boolean isTiffMime(String mimeType)
    {
        return ImageConstants.TIFF_MIME.equals(mimeType);
    }

    public static boolean importKeywords(Context c, Uri keywordUri)
    {
        boolean success = false;
        InputStreamReader reader = null;
        try
        {
            InputStream is = c.getContentResolver().openInputStream(keywordUri);
            reader = new InputStreamReader(is);
            // Attempt to import keywords
            success = KeywordProvider.importKeywords(c, reader);
            int message;
            if (success)
            {
                message = R.string.resultImportSuccessful;
            }
            else
            {
                message = R.string.resultImportFailed;
            }
            Toast.makeText(c, message, Toast.LENGTH_SHORT).show();
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
        finally
        {
            Utils.closeSilently(reader);
        }
        return success;
    }

    public static int getImageType(File file)
    {
        if (isRaw(file)) return Meta.RAW;
        if (isNative(file)) return Meta.COMMON;
        if (isTiffImage(file)) return Meta.TIFF;
        return -1;
    }

    public static int getImageType(Uri file)
    {
        if (isRaw(file)) return Meta.RAW;
        if (isNative(file)) return Meta.COMMON;
        if (isTiffImage(file)) return Meta.TIFF;
        return -1;
    }

    public static class JpegFilter implements FileFilter
    {
        @Override
        public boolean accept(File file) { return isJpeg(file); }
    }

    public static class RawFilter implements FileFilter
    {
        @Override
        public boolean accept(File file) { return isRaw(file); }
    }

    public static class ImageFilter implements FileFilter
    {
        @Override
        public boolean accept(File file)
        {
            return isRaw(file) || isJpeg(file);
        }
    }

    public static boolean isImage(String name)
    {
        return isRaw(name) || isJpeg(name) || isTiffImage(name);
    }

    public static boolean isImage(File f)
    {
        return isImage(f.getName());
    }

    public static boolean isImage(Uri uri)
    {
        String path = uri.getPath();
        if (path == null) // If the uri is not hierarchical
            return false;
        return isImage(path);
    }

    public static boolean isRaw(File file)
    {
        return isRaw(file.getName());
    }

    public static boolean isRaw(Uri uri)
    {
        String path = uri.getPath();
        if (path == null) // If the uri is not hierarchical
            return false;
        return isRaw(path);
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
        if (path == null) // If the uri is not hierarchical
            return false;
        return isJpeg(path);
    }

    public static boolean isJpeg(String name)
    {
        return endsWith(ImageConstants.JPEG_EXT, name);
    }

    public static boolean isNative(String name)
    {
        return endsWith(ImageConstants.COMMON_EXT, name);
    }

    public static boolean isNative(File file)
    {
        return isNative(file.getName());
    }

    public static boolean isNative(Uri uri)
    {
        String path = uri.getPath();
        if (path == null) // If the uri is not hierarchical
            return false;
        return isNative(path);
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
        if (path == null) // If the uri is not hierarchical
            return false;
        return isTiffImage(path);
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
            values.put(Meta.Data.WIDTH, o.outWidth);
            values.put(Meta.Data.HEIGHT, o.outHeight);
        }
        return imageBytes;
    }

    private static byte[] getTiffImage(int fileDescriptor, ContentValues values)
    {
        int[] dim = new int[2];
        int[] imageData = ImageProcessor.getTiffFd("", fileDescriptor, dim);  //TODO: I could get name here, but is it worth it?  Does this name do anything?
        int width = dim[0];
        int height = dim[1];
        values.put(Meta.Data.WIDTH, width);
        values.put(Meta.Data.HEIGHT, height);

        // This is necessary since BitmapRegionDecoder only supports jpg and png
        // TODO: This could be done in native, we already have jpeg capability
        // Alternatively maybe glide can handle int[]?
        Bitmap bmp = Bitmap.createBitmap(imageData, width, height, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 100, baos);

        return baos.toByteArray();
    }

    private static byte[] getRawThumb(int fileDescriptor, ContentValues values)
    {
        String[] exif = new String[12];
        byte[] imageBytes = ImageProcessor.getThumb(fileDescriptor, exif);

        try
        {
            values.put(Meta.Data.TIMESTAMP, mLibrawFormatter.parse(exif[Exif.TIMESTAMP]).getTime());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        values.put(Meta.Data.APERTURE, exif[Exif.APERTURE]);
        values.put(Meta.Data.MAKE, exif[Exif.MAKE]);
        values.put(Meta.Data.MODEL, exif[Exif.MODEL]);
        values.put(Meta.Data.FOCAL_LENGTH, exif[Exif.FOCAL]);
        values.put(Meta.Data.APERTURE, exif[Exif.HEIGHT]);
        values.put(Meta.Data.ISO, exif[Exif.ISO]);
        values.put(Meta.Data.ORIENTATION, exif[Exif.ORIENTATION]);
        values.put(Meta.Data.EXPOSURE, exif[Exif.SHUTTER]);
        values.put(Meta.Data.HEIGHT, exif[Exif.HEIGHT]);
        values.put(Meta.Data.WIDTH, exif[Exif.WIDTH]);
        //TODO: Placing thumb dimensions since we aren't decoding raw atm.
        values.put(Meta.Data.HEIGHT, exif[Exif.THUMB_HEIGHT]);
        values.put(Meta.Data.WIDTH, exif[Exif.THUMB_WIDTH]);
        // Are the thumb dimensions useful in database?

        return imageBytes;
    }

    @SuppressLint("SimpleDateFormat")
    public static byte[] getThumb(final Context c, final Uri uri) {
        //TODO: Split this into component methods
        String type = getMimeType(uri.toString());

        byte[] imageBytes = null;
        final ContentValues values = new ContentValues();

        // If it's a supported images, just get the bytes
        if (ImageUtils.isAndroidImage(type))
        {
            imageBytes = getAndroidImage(c, uri, values);
        }
        // Otherwise we'll need a file descriptor to pass to native
        else
        {
            // Get a file descriptor to pass to native methods
            int fd;
            ParcelFileDescriptor pfd = null;

            try
            {
                pfd = c.getContentResolver().openFileDescriptor(uri, "r");
                fd = pfd.getFd();
                if (ImageUtils.isTiffMime(type))
                {
                    imageBytes = getTiffImage(fd, values);
                }
                else
                {
                    imageBytes = getRawThumb(fd, values);
                }
            }
            catch (FileNotFoundException e)
            {
                e.printStackTrace();
            }
            finally
            {
                Utils.closeSilently(pfd);
            }
        }

        // If there are content values fire off a thread to update the provider so we get the image
        // asap while still having basic info for viewer if not fully processed yet
        if (values.size() > 0)
        {
            new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    ContentProviderClient cpc = null;
                    Cursor cursor = null;
                    try
                    {
                        cpc = c.getContentResolver().acquireContentProviderClient(Meta.AUTHORITY);
                        if (cpc != null)
                        {
                            cursor = cpc.query(Meta.Data.CONTENT_URI, new String[]{Meta.Data.PROCESSED},
                                    ImageUtils.getWhere(), new String[]{uri.toString()}, null, null);
                            if (cursor != null)
                            {
                                cursor.moveToFirst();
                                if (cursor.getInt(0) == 0)   // If it hasn't been processed yet, insert basics
                                {
                                    cpc.update(Meta.Data.CONTENT_URI, values, ImageUtils.getWhere(), new String[]{uri.toString()});
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
                        Utils.closeSilently(cursor);
                    }
                }
            }).start();

        }
        return imageBytes;
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
            Utils.closeSilently(is);
        }
    }

    public static ThreadPool.Job<Bitmap> requestImage(GalleryApp app, int type, Uri image) {
        return new ImageRequest(app, image, type);
    }

    public static class ImageRequest extends ImageCacheRequest
    {
        Uri mImage;
        ImageRequest(GalleryApp application, Uri image, int type) {
            super(application, image, type, MetaMedia.getTargetSize(type));
            mImage = image;
        }

        @Override
        public Bitmap onDecodeOriginal(ThreadPool.JobContext jc, final int type) {
            byte[] imageData = getThumb(mApplication.getAndroidContext(), mImage);

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

    public static ThreadPool.Job<BitmapRegionDecoder> requestLargeImage(Context c, Uri image) {
        return new LargeImageRequest(c, image);
    }

    public static class LargeImageRequest implements
            ThreadPool.Job<BitmapRegionDecoder>
    {
        Context mContext;
        Uri mImage;

        public LargeImageRequest(Context c, Uri image) {
            mImage = image;
            mContext = c;
        }

        public BitmapRegionDecoder run(ThreadPool.JobContext jc) {
            byte[] imageData = getThumb(mContext, mImage);
            BitmapRegionDecoder brd = DecodeUtils.createBitmapRegionDecoder(jc,
                    imageData, 0, imageData.length, false);
            return brd;
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

    /**
     * Get the size in bytes of a bitmap.
     *
     * @param bitmap
     * @return size in bytes
     */
    @TargetApi(12)
    public static int getBitmapSize(Bitmap bitmap)
    {
        // From KitKat onward use getAllocationByteCount() as allocated bytes can potentially be
        // larger than bitmap byte count.
        if (Util.hasKitkat()) {
            return bitmap.getAllocationByteCount();
        }

        if (Util.hasHoneycombMR1()) {
            return bitmap.getByteCount();
        }

        // Pre HC-MR1
        return bitmap.getRowBytes() * bitmap.getHeight();
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
                float opacity = Color.alpha(mark[i]) / 510f;
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

    public static byte[] getBitmapBytes(Bitmap src)
    {
        ByteBuffer dst = ByteBuffer.allocate(getBitmapSize(src));
        dst.order(ByteOrder.nativeOrder());
        src.copyPixelsToBuffer(dst);
        return dst.array();
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
                float opacity = Color.alpha(mark[i]) / 510f;
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
