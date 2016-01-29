package com.anthonymandra.framework;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import android.widget.Toast;

import com.android.gallery3d.common.Utils;
import com.anthonymandra.content.KeywordProvider;
import com.anthonymandra.content.Meta;
import com.anthonymandra.rawdroid.R;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class ImageUtils
{
    private static String TAG = ImageUtils.class.getSimpleName();
    private static SimpleDateFormat mLibrawFormatter = new SimpleDateFormat("EEE MMM d hh:mm:ss yyyy");

    public static Metadata readMetadata(Context c, Uri uri)
    {
        Metadata meta = readMeta(c, uri);
        return readXmp(uri, meta);
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
        catch (ImageProcessingException e)
        {
            Log.w(TAG, "Failed to process file for meta data.", e);
        }
        catch (IOException e)
        {
            Log.w(TAG, "Failed to open file for meta data.", e);
        }
        finally
        {
            Utils.closeSilently(image);
        }
        return meta;
    }

    /**
     * Reads associated xmp file if it exists
     * @param uri
     * @return
     */
    private static Metadata readXmp(Uri uri)
    {
        return readXmp(uri, new Metadata());
    }

    /**
     * Reads associated xmp file if it exists and adds the data to meta
     * @param uri image file
     * @param meta
     * @return
     */
    private static Metadata readXmp(Uri uri, Metadata meta)
    {
        File image = new File(uri.getPath());
        File xmp = getXmpFile(image);
        if (!xmp.exists())
            return meta;

        FileInputStream xmpStream = null;
        try
        {
            xmpStream = new FileInputStream(xmp);
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
        /*  ghetto: mashing uris is not recommended, but since DocumentFile.findFile is slow we're
            going to skip that process and simply mash what we know the uri would be (as of 01/2016)
            new uri schemes could possibly break this */

        UsefulDocumentFile image = FileUtil.getDocumentFile(c, uri);
        String name = image.getName();
        if (ext != null)
        {
            name = Util.swapExtention(name, ext);
        }

        UsefulDocumentFile parent = image.getParentFile();
        String parentString = parent.getUri().toString();
        String associatedString = parentString + "/" + name;
        Uri associatedUri = Uri.parse(associatedString);
        return FileUtil.getDocumentFile(c, associatedUri);
    }

    /**
     * Gets a similarly named file with a new ext
     * @param file
     * @param ext
     * @return
     */
    private static File getAssociatedFile(File file, String ext) {
        String name = file.getName();
        name = Util.swapExtention(name, ext);
        return new File(file.getParent(), name);
    }

    /**
     * --- Content Helpers -------------------------------------------------------------------------
     */

    //TODO: Perhaps this makes more sense in the meta provider itself?
    /**
     * Read meta data and convert to ContentValues for {@link com.anthonymandra.content.MetaProvider}
     * @param c
     * @param uri
     * @return
     */
    public static ContentValues getContentValues(Context c, Uri uri)
    {
        Metadata meta = readMetadata(c, uri);
        return getContentValues(uri, meta, Util.getImageType(new File(uri.getPath())));
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
		        Meta.Data.URI + "=?",
		        new String[]{uri.toString()},
		        null);
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

        final Cursor cursor = c.getContentResolver().query(Meta.Data.CONTENT_URI, null, null, null, null);
        try
        {
            while (cursor.moveToNext())
            {
                String uriString = cursor.getString(Meta.URI_COLUMN);
                Uri uri = Uri.parse(uriString);
                File file = new File(uri.getPath());
                if (!file.exists())
                {
                    operations.add(ContentProviderOperation.newDelete(Meta.Data.CONTENT_URI)
                            .withSelection(Meta.Data.URI + "=?", new String[]{uriString}).build());
                }
            }
        }
        finally
        {
            cursor.close();
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
        c.getContentResolver().update(Meta.Data.CONTENT_URI, cv, Meta.Data.URI + "=?",
                new String[]{uri.toString()});
    }

    /**
     * Convert given meta into ContentValues for {@link com.anthonymandra.content.MetaProvider}
     * @param uri
     * @param meta
     * @return
     */
    public static ContentValues getContentValues(Uri uri, Metadata meta, int type)
    {
        final ContentValues cv = new ContentValues();
        File image = new File(uri.getPath());
        if (image != null)
        {
            cv.put(Meta.Data.NAME, image.getName());
        }

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
        cv.put(Meta.Data.TIMESTAMP, getDateTime(meta));
        cv.put(Meta.Data.WHITE_BALANCE, getWhiteBalance(meta));
        cv.put(Meta.Data.WIDTH, getImageWidth(meta));
        cv.put(Meta.Data.URI, uri.toString());
        cv.put(Meta.Data.RATING, getRating(meta));
        cv.put(Meta.Data.SUBJECT, convertArrayToString(getSubject(meta)));
        cv.put(Meta.Data.LABEL, getLabel(meta));

        cv.put(Meta.Data.LENS_MODEL, getLensModel(meta));
        cv.put(Meta.Data.DRIVE_MODE, getDriveMode(meta));
        cv.put(Meta.Data.EXPOSURE_MODE, getExposureMode(meta));
        cv.put(Meta.Data.EXPOSURE_PROGRAM, getExposureProgram(meta));
        cv.put(Meta.Data.TYPE, type);
        cv.put(Meta.Data.PROCESSED, true);
	    cv.put(Meta.Data.PARENT, new File(uri.getPath()).getParent());

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
        return getDescription(meta, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_DATETIME);
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

    public static boolean isTiffImage(final Context c, final Uri uri)
    {
        return isTiffImage(c.getContentResolver().getType(uri));
    }

    public static boolean isTiffImage(final String mimeType)
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
}
