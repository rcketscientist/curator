package com.anthonymandra.util;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.anthonymandra.content.Meta;
import com.anthonymandra.framework.UsefulDocumentFile;
import com.crashlytics.android.Crashlytics;
import com.drew.imaging.FileType;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.exif.PanasonicRawIFD0Directory;
import com.drew.metadata.exif.makernotes.CanonMakernoteDirectory;
import com.drew.metadata.exif.makernotes.NikonType2MakernoteDirectory;
import com.drew.metadata.exif.makernotes.OlympusCameraSettingsMakernoteDirectory;
import com.drew.metadata.exif.makernotes.OlympusEquipmentMakernoteDirectory;
import com.drew.metadata.exif.makernotes.SonyType1MakernoteDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import com.drew.metadata.xmp.XmpReader;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class MetaUtil
{
	private static String TAG = MetaUtil.class.getSimpleName();

	static SimpleDateFormat mLibrawFormatter = new SimpleDateFormat("EEE MMM d hh:mm:ss yyyy");
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
	private static Metadata readMeta(Context c, @NonNull Uri uri)
	{
	    InputStream image = null;
	    Metadata meta = new Metadata();
	    try
	    {
	        image = c.getContentResolver().openInputStream(uri);
	        meta = ImageMetadataReader.readMetadata(image);
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
	    UsefulDocumentFile xmpDoc = ImageUtil.getXmpFile(c, uri);
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

	public static void getImageFileInfo(Context c, Uri image, ContentValues cv)
	{
	    UsefulDocumentFile file = UsefulDocumentFile.fromUri(c, image);
	    UsefulDocumentFile.FileData fd = file.getCachedData();

	    if (fd != null)
	    {
	        cv.put(Meta.NAME, fd.name);
	        cv.put(Meta.PARENT, fd.parent.toString());
	        cv.put(Meta.TIMESTAMP, fd.lastModified);
	    }
	    else
	    {
	        UsefulDocumentFile parent = file.getParentFile();
	        if (parent != null)
	            cv.put(Meta.PARENT, parent.toString());
	    }

	    cv.put(Meta.DOCUMENT_ID, file.getDocumentId());
	    cv.put(Meta.URI, image.toString());
	    cv.put(Meta.TYPE, ImageUtil.getImageType(c, image).getValue());
	}

	public static ContentProviderOperation newInsert(Context c, Uri image)
	{
	    ContentValues cv = new ContentValues();
	    getImageFileInfo(c, image, cv);

	   return ContentProviderOperation.newInsert(Meta.CONTENT_URI)
	            .withValues(cv)
	            .build();
	}

	public static ContentProviderOperation newDelete(Uri image)
	{
	    return ContentProviderOperation.newDelete(Meta.CONTENT_URI)
	            .withSelection(Meta.URI_SELECTION, new String[] { image.toString() })
	            .build();
	}

	public static ContentProviderOperation newUpdate(@NonNull Uri image, ContentValues cv)
	{
	    return ContentProviderOperation.newUpdate(Meta.CONTENT_URI)
	            .withSelection(Meta.URI_SELECTION, new String[] {image.toString()})
	            .withValues(cv)
	            .build();
	}

	/**
	 * Read meta data and convert to ContentValues for {@link com.anthonymandra.content.MetaProvider}
	 * @param c
	 * @param uri
	 * @return
	 */
	public static ContentValues getContentValues(@NonNull Context c, @NonNull Uri uri)
	{
	    Metadata meta = readMetadata(c, uri);
	    return getContentValues(meta);
	}

	/**
	 * Read meta data and convert to ContentValues for {@link com.anthonymandra.content.MetaProvider}
	 * @param c
	 * @param uri
	 * @return
	 */
	public static ContentValues getContentValues(@NonNull Context c, @NonNull Uri uri, @NonNull ContentValues toFill)
	{
	    Metadata meta = readMetadata(c, uri);
	    return getContentValues(toFill, meta);
	}

	/**
	 * Read meta data and convert to ContentValues for {@link com.anthonymandra.content.MetaProvider}
	 * @return
	 */
	public static ContentValues getContentValues(@NonNull InputStream stream, @NonNull FileType fileType)
	{
	    Metadata meta = null;
	    try
	    {
	        meta = ImageMetadataReader.readMetadata(stream, -1, fileType);
	    } catch (ImageProcessingException | IOException e)
	    {
	        e.printStackTrace();
	    }
		return getContentValues(meta);
	}

	/**
	 * Retrieves cursor for an array of uri
	 * @param c context
	 * @param uri uri to query
	 * @return cursor of row corresponding to uri
	 */
	public static @Nullable
	Cursor getMetaCursor(Context c, Uri uri)
	{
	    return getMetaCursor(c, uri, null);
	}

	public static @Nullable Cursor getMetaCursor(Context c, Uri uri, @Nullable String[] projection)
	{
	    return c.getContentResolver().query(Meta.CONTENT_URI,
	            projection,
	            Meta.URI_SELECTION,
	            new String[] {uri.toString()},
	            null);
	}

	/**
	 * Retrieves cursor for an array of uri
	 * @param c context
	 * @param uris array of uri to query (due to sql limitation this must be smaller than 999)
	 * @return cursor of rows corresponding to uris
	 */
	public static @Nullable Cursor getMetaCursor(Context c, String[] uris)
	{
	    return getMetaCursor(c, uris, null);
	}

	public static @Nullable Cursor getMetaCursor(Context c, String[] uris, @Nullable String[] projection)
	{
	    return c.getContentResolver().query(Meta.CONTENT_URI,
	            projection,
	            DbUtil.createMultipleIN(Meta.URI, uris.length),
	            uris,
	            null);
	}

	public static void setExifValues(final Uri uri, final Context c, final String[] exif)
	{
	    try (final Cursor cursor = getMetaCursor(c, uri))
	    {
	        if (cursor == null)
	            return;

	        // Check if meta is already processed
	        final int processedColumn = cursor.getColumnIndex(Meta.PROCESSED);
	        if (cursor.moveToFirst() && cursor.getInt(processedColumn) != 0)
	            return;
	    }

	    ContentValues cv = new ContentValues();
	    try
	    {
	        /*
	        For now only include image related information.  As this is a parse related
	        to the processing of the image for display this is reasonable.  It avoids
	        constant flickering as the timestamps cause a database update.
	         */
	        cv.put(Meta.HEIGHT, Integer.parseInt(exif[10]));
	        cv.put(Meta.WIDTH, Integer.parseInt(exif[11]));
	        cv.put(Meta.ORIENTATION, Integer.parseInt(exif[7]));
	    }
	    catch (Exception e)
	    {
	        Log.d(TAG, "Exif parse failed:", e);
	    }
	    c.getContentResolver().update(Meta.CONTENT_URI, cv, Meta.URI_SELECTION,
	            new String[]{uri.toString()});
	}

	/**
	 * Convert given meta into ContentValues for {@link com.anthonymandra.content.MetaProvider}
	 * @param meta
	 * @return
	 */
	public static ContentValues getContentValues(Metadata meta)
	{
	    final ContentValues toFill = new ContentValues();
	    return  getContentValues(toFill, meta);
	}

	/**
	 * Convert given meta into ContentValues for {@link com.anthonymandra.content.MetaProvider}
	 * @param toFill
	 * @param meta
	 * @return
	 */
	@Nullable
	public static ContentValues getContentValues(ContentValues toFill, Metadata meta)
	{
	    if (meta == null)
	    {
	        return null;
	    }

	    toFill.put(Meta.ALTITUDE, getAltitude(meta));
	    toFill.put(Meta.APERTURE, getAperture(meta));
	    toFill.put(Meta.EXPOSURE, getExposure(meta));
	    toFill.put(Meta.FLASH, getFlash(meta));
	    toFill.put(Meta.FOCAL_LENGTH, getFocalLength(meta));
	    toFill.put(Meta.HEIGHT, getImageHeight(meta));
	    toFill.put(Meta.ISO, getIso(meta));
	    toFill.put(Meta.LATITUDE, getLatitude(meta));
	    toFill.put(Meta.LONGITUDE, getLongitude(meta));
	    toFill.put(Meta.MODEL, getModel(meta));

	    toFill.put(Meta.ORIENTATION, getOrientation(meta));
	    String rawDate = getDateTime(meta);
	    if (rawDate != null)  // Don't overwrite null since we can rely on file time
	    {
	        try
	        {
	            Date date = mMetaExtractorFormat.parse(rawDate);
	            toFill.put(Meta.TIMESTAMP, date.getTime());
	        }
	        catch (ParseException | ArrayIndexOutOfBoundsException e)
	        {
	            Crashlytics.logException(e);
	        }
	    }
	    toFill.put(Meta.WHITE_BALANCE, getWhiteBalance(meta));
	    toFill.put(Meta.WIDTH, getImageWidth(meta));

	    toFill.put(Meta.RATING, getRating(meta));
	    toFill.put(Meta.SUBJECT, DbUtil.convertArrayToString(getSubject(meta)));
	    toFill.put(Meta.LABEL, getLabel(meta));

	    toFill.put(Meta.LENS_MODEL, getLensModel(meta));
	    toFill.put(Meta.DRIVE_MODE, getDriveMode(meta));
	    toFill.put(Meta.EXPOSURE_MODE, getExposureMode(meta));
	    toFill.put(Meta.EXPOSURE_PROGRAM, getExposureProgram(meta));
	//        MetaService.setProcessed(toFill, true);

	    return toFill;
	}

	/**
	 * --- Meta Getters ----------------------------------------------------------------------------
	 */

	private static String getDescription(Metadata meta, int tag)
	{
	    for (Directory dir : meta.getDirectories())
	    {
	        if (dir.containsTag(tag))
	            return dir.getDescription(tag);
	    }
	    return null;
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
	    if (dir == null || !dir.containsTag(tag))
	        return null;
	    try
	    {
		    return dir.getDouble(tag);
	    } catch (MetadataException e)
	    {
	        e.printStackTrace();
	    }
	    return null;
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
	    String aperture = getDescription(meta, ExifSubIFDDirectory.TAG_FNUMBER);
	    if (aperture == null)
	        aperture = getDescription(meta, ExifSubIFDDirectory.TAG_APERTURE);
	    return aperture;
	}

	private static String getExposure(Metadata meta)
	{
	    return getDescription(meta, ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
	}

	private static String getImageHeight(Metadata meta)
	{
	    String height = getDescription(meta, ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT);
	    if (height == null)
	        height = getDescription(meta, ExifIFD0Directory.TAG_IMAGE_HEIGHT);
	    if (height == null)
	        height = getDescription(meta, PanasonicRawIFD0Directory.TagSensorHeight);
	    return height;
	}

	private static String getImageWidth(Metadata meta)
	{
	    String width = getDescription(meta, ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH);
	    if (width == null)
	        width = getDescription(meta, ExifIFD0Directory.TAG_IMAGE_WIDTH);
	    if (width == null)
	        width = getDescription(meta, PanasonicRawIFD0Directory.TagSensorWidth);
	    return width;
	}

	private static String getFocalLength(Metadata meta)
	{
	    return getDescription(meta, ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
	}

	private static String getFlash(Metadata meta)
	{
	    return getDescription(meta, ExifSubIFDDirectory.TAG_FLASH);
	}

	private static String getShutterSpeed(Metadata meta)
	{
	    return getDescription(meta, ExifSubIFDDirectory.TAG_SHUTTER_SPEED);
	}

	private static String getWhiteBalance(Metadata meta)
	{
	/*        if (meta.containsDirectoryOfType(CanonMakernoteDirectory.class))
	        return meta.getFirstDirectoryOfType(CanonMakernoteDirectory.class).getDescription(CanonMakernoteDirectory.FocalLength.TAG_WHITE_BALANCE);
	    if (meta.containsDirectoryOfType(PanasonicMakernoteDirectory.class))
	        return meta.getFirstDirectoryOfType(PanasonicMakernoteDirectory.class).getDescription(PanasonicMakernoteDirectory.TAG_WHITE_BALANCE);
	    if (meta.containsDirectoryOfType(FujifilmMakernoteDirectory.class))
	        return meta.getFirstDirectoryOfType(FujifilmMakernoteDirectory.class).getDescription(FujifilmMakernoteDirectory.TAG_WHITE_BALANCE);
	    if (meta.containsDirectoryOfType(LeicaMakernoteDirectory.class))
	        return meta.getFirstDirectoryOfType(LeicaMakernoteDirectory.class).getDescription(LeicaMakernoteDirectory.TAG_WHITE_BALANCE);*/
	    return getDescription(meta, ExifSubIFDDirectory.TAG_WHITE_BALANCE_MODE);
	}

	private static String getExposureProgram(Metadata meta)
	{
	    return getDescription(meta, ExifSubIFDDirectory.TAG_EXPOSURE_PROGRAM);
	}

	private static String getExposureMode(Metadata meta)
	{
	    return getDescription(meta, ExifSubIFDDirectory.TAG_EXPOSURE_MODE);
	}

	private static String getLensMake(Metadata meta)
	{
	    return getDescription(meta, ExifSubIFDDirectory.TAG_LENS_MAKE);
	}

	private static String getLensModel(Metadata meta)
	{
	    if (meta.containsDirectoryOfType(CanonMakernoteDirectory.class))
	        return meta.getFirstDirectoryOfType(CanonMakernoteDirectory.class).getDescription(CanonMakernoteDirectory.TAG_LENS_MODEL);
	    if (meta.containsDirectoryOfType(NikonType2MakernoteDirectory.class))
	        return meta.getFirstDirectoryOfType(NikonType2MakernoteDirectory.class).getDescription(NikonType2MakernoteDirectory.TAG_LENS);
	    if (meta.containsDirectoryOfType(OlympusEquipmentMakernoteDirectory.class))
	        return meta.getFirstDirectoryOfType(OlympusEquipmentMakernoteDirectory.class).getDescription(OlympusEquipmentMakernoteDirectory.TAG_LENS_TYPE);
	    String lens = getDescription(meta, ExifSubIFDDirectory.TAG_LENS_MODEL);                 // We prefer the exif over Sony maker,
	    if (lens == null && meta.containsDirectoryOfType(SonyType1MakernoteDirectory.class))    // but use maker if exif doesn't exist
	        return meta.getFirstDirectoryOfType(SonyType1MakernoteDirectory.class).getDescription(SonyType1MakernoteDirectory.TAG_LENS_ID);
	    return lens;
	}

	private static String getDriveMode(Metadata meta)
	{
	    if (meta.containsDirectoryOfType(CanonMakernoteDirectory.class))
	        return meta.getFirstDirectoryOfType(CanonMakernoteDirectory.class).getDescription(CanonMakernoteDirectory.CameraSettings.TAG_CONTINUOUS_DRIVE_MODE);
	    if (meta.containsDirectoryOfType(NikonType2MakernoteDirectory.class))
	        return meta.getFirstDirectoryOfType(NikonType2MakernoteDirectory.class).getDescription(NikonType2MakernoteDirectory.TAG_SHOOTING_MODE);
	    if (meta.containsDirectoryOfType(OlympusCameraSettingsMakernoteDirectory.class))
	        return meta.getFirstDirectoryOfType(OlympusCameraSettingsMakernoteDirectory.class).getDescription(OlympusCameraSettingsMakernoteDirectory.TagDriveMode);
	    return null;
	}

	private static String getIso(Metadata meta)
	{
	    String iso = getDescription(meta, ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
	    if (iso == null)
	        iso = getDescription(meta, PanasonicRawIFD0Directory.TagIso);
	    return iso;
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
	    return getDescription(meta, ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
	}

	private static String getMake(Metadata meta)
	{
	    String make = getExifIFD0Description(meta, ExifIFD0Directory.TAG_MAKE);
	    if (make == null)
	        make = getDescription(meta, PanasonicRawIFD0Directory.TagMake);
	    return make;
	}

	private static String getModel(Metadata meta)
	{
	    String model = getExifIFD0Description(meta, ExifIFD0Directory.TAG_MODEL);
	    if (model == null)
	        model = getDescription(meta, PanasonicRawIFD0Directory.TagModel);
	    return model;
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
	    return getDescription(meta, GpsDirectory.TAG_ALTITUDE);
	}

	private static String getLatitude(Metadata meta)
	{
	    return getDescription(meta, GpsDirectory.TAG_LATITUDE);
	}

	private static String getLongitude(Metadata meta)
	{
	    return getDescription(meta, GpsDirectory.TAG_LONGITUDE);
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

	/**
	 * Update the xmp:rating, passing null will delete existing value.
	 */
	public static void updateRating(Metadata meta, @Nullable Integer rating)
	{
	    updateXmpDouble(meta, XmpDirectory.TAG_RATING, rating == null ? null : rating.doubleValue());
	}

	/**
	 * Update the xmp:rating, passing null will delete existing value.
	 */
	public static void updateRating(Metadata meta, @Nullable Double rating)
	{
	    updateXmpDouble(meta, XmpDirectory.TAG_RATING, rating);
	}

	/**
	 * Update the xmp:label, passing null will delete existing value.
	 */
	public static void updateLabel(Metadata meta, @Nullable String label)
	{
	    updateXmpString(meta, XmpDirectory.TAG_LABEL, label);
	}

	/**
	 * Update the xmp:subject, passing null will delete existing value.
	 */
	public static void updateSubject(Metadata meta, String[] subject)
	{
	    updateXmpStringArray(meta, XmpDirectory.TAG_SUBJECT, subject);
	}

	private static void updateXmpString(Metadata meta, int tag, String value)
	{
	    checkXmpDirectory(meta);

	    XmpDirectory xmp = meta.getFirstDirectoryOfType(XmpDirectory.class);
		if (xmp == null)
			return;

	    if (value == null)
	    {
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
		if (xmp == null)
			return;

	    if (value == null  || value.length == 0)
	    {
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
		if (xmp == null)
			return;

	    if (value == null)
	    {
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
		if (xmp == null)
			return;

	    if (value == null)
	    {
	        xmp.deleteProperty(tag);
	    }
	    else
	    {
	        xmp.updateInt(tag, value);
	    }
	}
}
