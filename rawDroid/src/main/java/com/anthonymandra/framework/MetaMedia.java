package com.anthonymandra.framework;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.anthonymandra.content.Meta;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.exif.makernotes.CanonMakernoteDirectory;
import com.drew.metadata.exif.makernotes.CanonMakernoteDirectory.CameraSettings;
import com.drew.metadata.exif.makernotes.FujifilmMakernoteDirectory;
import com.drew.metadata.exif.makernotes.LeicaMakernoteDirectory;
import com.drew.metadata.exif.makernotes.NikonType2MakernoteDirectory;
import com.drew.metadata.exif.makernotes.PanasonicMakernoteDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import com.drew.metadata.xmp.XmpReader;
import com.drew.metadata.xmp.XmpWriter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public abstract class MetaMedia extends MediaItem
{
	private static final String TAG = MetaMedia.class.getSimpleName();

	protected boolean metaLoaded = false;
	protected Metadata mMetadata = new Metadata();
	protected int width;
	protected int height;
	protected int thumbWidth;
	protected int thumbHeight;
	
	protected Date dateLegacy;
	protected String makeLegacy;
	protected String modelLegacy;
	protected String apertureLegacy;
	protected String focalLegacy;
	protected String isoLegacy;
	protected String shutterLegacy;
	protected int orientLegacy = 0;

    protected SimpleDateFormat mExifFormatter = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
    protected SimpleDateFormat mLibrawFormatter = new SimpleDateFormat("EEE MMM d hh:mm:ss yyyy");
    protected Context mContext;

    public MetaMedia(Context context, Uri path, long version) {
        super(path, version);
        mContext = context;
    }

	public void clearXmp()
	{
		setLabel(null);
		setRating(Double.NaN);
		setSubject(new String[0]);
		resetXmp();
		// readXmp(); // When we delete fields we must reread to update.
	}

	private boolean isLoaded = false;

	public abstract boolean hasXmp();

	protected void resetXmp()
	{
		BufferedOutputStream bos = getXmpOutputStream();
		XmpWriter.write(bos, mMetadata);
		mMetadata = new Metadata();
		readXmp();
        Utils.closeSilently(bos);
	}

	protected void writeXmp(final OutputStream os)
	{
		new Runnable()
		{
			@Override
			public void run()
			{
				if (mMetadata.containsDirectoryOfType(XmpDirectory.class))
					XmpWriter.write(os, mMetadata);
			}
		}.run();
	}

	public String getAperture()
	{
		if (mMetadata.containsDirectoryOfType(ExifSubIFDDirectory.class) &&
			mMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class).containsTag(ExifSubIFDDirectory.TAG_APERTURE))
		{
			return mMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_APERTURE);
		}
		return apertureLegacy;
//		return null;
	}

	public String getExposure()
	{
		if (mMetadata.containsDirectoryOfType(ExifSubIFDDirectory.class))
		{
			ExifSubIFDDirectory exif = mMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
			if (exif.containsTag(ExifSubIFDDirectory.TAG_EXPOSURE_TIME))
			{
				return exif.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
			}
			else
			{
				return getShutterSpeed();
			}
		}

		return null;
	}

	public String getImageHeight()
	{
		if (mMetadata.containsDirectoryOfType(ExifSubIFDDirectory.class))
			return mMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT);
		return null;
	}

	public String getImageWidth()
	{
		if (mMetadata.containsDirectoryOfType(ExifSubIFDDirectory.class))
			return mMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH);
		return null;
	}

	public String getFocalLength()
	{
		if (mMetadata.containsDirectoryOfType(ExifSubIFDDirectory.class) &&
				mMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class).containsTag(ExifSubIFDDirectory.TAG_FOCAL_LENGTH))
		{
			return mMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
		}
			
		return focalLegacy;
//		return null;
	}

	public String getFlash()
	{
		if (mMetadata.containsDirectoryOfType(ExifSubIFDDirectory.class))
			return mMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_FLASH);
		return null;
	}

	public String getShutterSpeed()
	{
		if (mMetadata.containsDirectoryOfType(ExifSubIFDDirectory.class) &&
			mMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class).containsTag(ExifSubIFDDirectory.TAG_SHUTTER_SPEED))
		{
			return mMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_SHUTTER_SPEED);
		}
		return shutterLegacy;
//		return null;
	}

	public String getWhiteBalance()
	{
		if (mMetadata.containsDirectoryOfType(CanonMakernoteDirectory.class))
			return mMetadata.getFirstDirectoryOfType(CanonMakernoteDirectory.class).getDescription(CanonMakernoteDirectory.FocalLength.TAG_WHITE_BALANCE);
		if (mMetadata.containsDirectoryOfType(PanasonicMakernoteDirectory.class))
			return mMetadata.getFirstDirectoryOfType(PanasonicMakernoteDirectory.class).getDescription(PanasonicMakernoteDirectory.TAG_WHITE_BALANCE);
		if (mMetadata.containsDirectoryOfType(FujifilmMakernoteDirectory.class))
			return mMetadata.getFirstDirectoryOfType(FujifilmMakernoteDirectory.class).getDescription(FujifilmMakernoteDirectory.TAG_WHITE_BALANCE);
		if (mMetadata.containsDirectoryOfType(LeicaMakernoteDirectory.class))
			return mMetadata.getFirstDirectoryOfType(LeicaMakernoteDirectory.class).getDescription(LeicaMakernoteDirectory.TAG_WHITE_BALANCE);
		if (mMetadata.containsDirectoryOfType(ExifSubIFDDirectory.class))
			return mMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_WHITE_BALANCE);
		return null;
	}

	public String getExposureProgram()
	{
		if (mMetadata.containsDirectoryOfType(ExifSubIFDDirectory.class))
			return mMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_PROGRAM);
		return null;
	}

	public String getExposureMode()
	{
		if (mMetadata.containsDirectoryOfType(ExifSubIFDDirectory.class))
			return mMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_MODE);
		return null;
	}

	public String getLensMake()
	{
		if (mMetadata.containsDirectoryOfType(ExifSubIFDDirectory.class))
			return mMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_LENS_MAKE);
		return null;
	}

	public String getLensModel()
	{
		if (mMetadata.containsDirectoryOfType(CanonMakernoteDirectory.class))
			return mMetadata.getFirstDirectoryOfType(CanonMakernoteDirectory.class).getDescription(CanonMakernoteDirectory.TAG_LENS_MODEL);
		if (mMetadata.containsDirectoryOfType(NikonType2MakernoteDirectory.class))
			return mMetadata.getFirstDirectoryOfType(NikonType2MakernoteDirectory.class).getDescription(NikonType2MakernoteDirectory.TAG_LENS);
		if (mMetadata.containsDirectoryOfType(ExifSubIFDDirectory.class))
			return mMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_LENS_MODEL);
		return null;
	}

	public String getDriveMode()
	{
		if (mMetadata.containsDirectoryOfType(CanonMakernoteDirectory.class))
			return mMetadata.getFirstDirectoryOfType(CanonMakernoteDirectory.class).getDescription(CameraSettings.TAG_CONTINUOUS_DRIVE_MODE);
		if (mMetadata.containsDirectoryOfType(NikonType2MakernoteDirectory.class))
			return mMetadata.getFirstDirectoryOfType(NikonType2MakernoteDirectory.class).getDescription(NikonType2MakernoteDirectory.TAG_SHOOTING_MODE);
		return null;
	}

	public String getIso()
	{
		if (mMetadata.containsDirectoryOfType(ExifSubIFDDirectory.class) &&
			mMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class).containsTag(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT))
		{
			return mMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
		}
		return isoLegacy;
//		return null;
	}

	public String getFNumber()
	{
		if (mMetadata.containsDirectoryOfType(ExifSubIFDDirectory.class))
		{
			ExifSubIFDDirectory exif = mMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
			if (exif.containsTag(ExifSubIFDDirectory.TAG_FNUMBER))
			{
				return exif.getDescription(ExifSubIFDDirectory.TAG_FNUMBER);
			}
			else
			{
				return getAperture();
			}
		}

		return null;
	}

	public String getDateTime()
	{
		if (dateLegacy != null)
			return dateLegacy.toLocaleString();
		if (mMetadata.containsDirectoryOfType(ExifSubIFDDirectory.class))
			return mMetadata.getFirstDirectoryOfType(ExifIFD0Directory.class).getDescription(ExifIFD0Directory.TAG_DATETIME);
		return null;
	}

	public String getMake()
	{
		if (mMetadata.containsDirectoryOfType(ExifIFD0Directory.class) &&
			mMetadata.getFirstDirectoryOfType(ExifIFD0Directory.class).containsTag(ExifIFD0Directory.TAG_MAKE))
		{
			return mMetadata.getFirstDirectoryOfType(ExifIFD0Directory.class).getDescription(ExifIFD0Directory.TAG_MAKE);
		}

		return makeLegacy;
//		return null;
	}

	public String getModel()
	{
		if (mMetadata.containsDirectoryOfType(ExifIFD0Directory.class) &&
			mMetadata.getFirstDirectoryOfType(ExifIFD0Directory.class).containsTag(ExifIFD0Directory.TAG_MODEL))
		{
			return mMetadata.getFirstDirectoryOfType(ExifIFD0Directory.class).getDescription(ExifIFD0Directory.TAG_MODEL);
		}

		return modelLegacy;
//		return null;
	}

	public int getOrientation()
	{
		if (mMetadata.containsDirectoryOfType(ExifSubIFDDirectory.class))
		{
			try
			{
				return mMetadata.getFirstDirectoryOfType(ExifIFD0Directory.class).getInt(ExifIFD0Directory.TAG_ORIENTATION);
			}
			catch (MetadataException e)
			{
				e.printStackTrace();
			}
		}
		return orientLegacy;
	}

	// The rotation of the full-resolution image. By default, it returns the value of
	// getRotation().
	public int getFullImageRotation()
	{
		return getRotation();
	}

	public int getRotation()
	{
		int orientation = getOrientation();
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

	public String getAltitude()
	{
		if (mMetadata.containsDirectoryOfType(GpsDirectory.class))
			return mMetadata.getFirstDirectoryOfType(GpsDirectory.class).getDescription(GpsDirectory.TAG_ALTITUDE);
		return null;
	}

	public String getLatitude()
	{
		if (mMetadata.containsDirectoryOfType(GpsDirectory.class))
			return mMetadata.getFirstDirectoryOfType(GpsDirectory.class).getDescription(GpsDirectory.TAG_LATITUDE);
		return null;
	}

	public String getLongitude()
	{
		if (mMetadata.containsDirectoryOfType(GpsDirectory.class))
			return mMetadata.getFirstDirectoryOfType(GpsDirectory.class).getDescription(GpsDirectory.TAG_LONGITUDE);
		return null;
	}

	public double getRating()
	{
		if (mMetadata.containsDirectoryOfType(XmpDirectory.class))
		{
			XmpDirectory xmp = mMetadata.getFirstDirectoryOfType(XmpDirectory.class);
			if (xmp.containsTag(XmpDirectory.TAG_RATING))
			{
				try
				{
					return mMetadata.getFirstDirectoryOfType(XmpDirectory.class).getDouble(XmpDirectory.TAG_RATING);
				}
				catch (MetadataException e)
				{
					e.printStackTrace();
				}
			}
		}
		return Double.NaN;
	}

	public String getLabel()
	{
		if (mMetadata.containsDirectoryOfType(XmpDirectory.class))
			return mMetadata.getFirstDirectoryOfType(XmpDirectory.class).getDescription(XmpDirectory.TAG_LABEL);
		return null;
	}

	public String[] getSubject()
	{
		if (mMetadata.containsDirectoryOfType(XmpDirectory.class))
			return mMetadata.getFirstDirectoryOfType(XmpDirectory.class).getStringArray(XmpDirectory.TAG_SUBJECT);
		return null;
	}

    private void checkXmpDirectory()
    {
        if (!mMetadata.containsDirectoryOfType(XmpDirectory.class))
            mMetadata.addDirectory(new XmpDirectory());
    }

	public void setRating(double rating)
	{
        checkXmpDirectory();

		XmpDirectory xmp = mMetadata.getFirstDirectoryOfType(XmpDirectory.class);
		if (Double.isNaN(rating))
		{
			if (xmp != null)
				xmp.deleteProperty(XmpDirectory.TAG_RATING);
		}
		else
		{
			xmp.updateDouble(XmpDirectory.TAG_RATING, rating);
		}
	}

	public void setLabel(String label)
	{
        checkXmpDirectory();

		XmpDirectory xmp = mMetadata.getFirstDirectoryOfType(XmpDirectory.class);
		if (label == null)
		{
			if (xmp != null)
				xmp.deleteProperty(XmpDirectory.TAG_LABEL);

		}
		else
		{
			xmp.updateString(XmpDirectory.TAG_LABEL, label);
		}
	}

	public void setSubject(String[] subject)
	{
        checkXmpDirectory();

		XmpDirectory xmp = mMetadata.getFirstDirectoryOfType(XmpDirectory.class);
		if (subject.length == 0)
		{
			if (xmp != null)
				xmp.deleteProperty(XmpDirectory.TAG_SUBJECT);
		}
		else
		{
			xmp.updateStringArray(XmpDirectory.TAG_SUBJECT, subject);
		}
	}

	protected abstract BufferedInputStream getXmpInputStream();

	protected abstract BufferedOutputStream getXmpOutputStream();

	public boolean readMetadata()
	{
		// Avoid reloading
		if (!isLoaded)
		{
			boolean metaResult = readMeta();
			boolean xmpResult = readXmp();
			boolean result = metaResult && xmpResult;
			isLoaded = result;
		}

		return isLoaded;
	}

	private boolean readMeta()
	{
		// Metadata
		InputStream raw = getImageStream();
		try
		{
			mMetadata = ImageMetadataReader.readMetadata(raw);
			putContent();
			return true;
		}
		catch (ImageProcessingException e)
		{
			Log.w(TAG, "Failed to process file for meta data.", e);
			return false;
		}
		catch (IOException e)
		{
			Log.w(TAG, "Failed to open file for meta data.", e);
			return false;
		}
        catch (Exception e)
        {
            Log.w(TAG, "Unknown meta data error.", e);
            return false;
        }
		finally
		{
            Utils.closeSilently(raw);
		}
	}
	
	protected void putContent()
	{
		final ContentValues cv = new ContentValues();
		cv.put(Meta.Data.ALTITUDE, getAltitude());
		cv.put(Meta.Data.APERTURE, getAperture());
		cv.put(Meta.Data.EXPOSURE, getExposure());
		cv.put(Meta.Data.FLASH, getFlash());
		cv.put(Meta.Data.FOCAL_LENGTH, getFocalLength());
		cv.put(Meta.Data.HEIGHT, height);
		cv.put(Meta.Data.ISO, getIso());
		cv.put(Meta.Data.LATITUDE, getLatitude());
		cv.put(Meta.Data.LONGITUDE, getLongitude());
		cv.put(Meta.Data.MODEL, getModel());
		cv.put(Meta.Data.NAME, getName());
		cv.put(Meta.Data.ORIENTATION, getOrientation());
		cv.put(Meta.Data.TIMESTAMP, getDateTime());
		cv.put(Meta.Data.WHITE_BALANCE, getWhiteBalance());
		cv.put(Meta.Data.WIDTH, width);
		cv.put(Meta.Data.URI, getUri().toString());
		cv.put(Meta.Data.THUMB_HEIGHT, thumbHeight);
		cv.put(Meta.Data.THUMB_WIDTH, thumbWidth);		
		
		new Thread(new Runnable() {			
			@Override
			public void run() {
				mContext.getContentResolver().insert(Meta.Data.CONTENT_URI, cv);				
			}
		}).start();
		
	}
	
	protected void getContent()
	{
		String[] selection = new String[]{ getUri().toString() };
		Cursor meta = mContext.getContentResolver().query(Meta.Data.CONTENT_URI, null, Meta.Data.URI + "=?", selection, null);
		if (meta == null || !meta.moveToNext())
		{
			return;
		}
		
		loadContent(meta);
	}
	
	protected void loadContent(Cursor meta)
	{
		width = meta.getInt(Meta.WIDTH_COLUMN);
		height = meta.getInt(Meta.HEIGHT_COLUMN);
		thumbWidth = meta.getInt(Meta.THUMB_WIDTH_COLUMN);
		thumbHeight = meta.getInt(Meta.THUMB_HEIGHT_COLUMN);
		orientLegacy = meta.getInt(Meta.ORIENTATION_COLUMN);
		apertureLegacy = meta.getString(Meta.APERTURE_COLUMN);
		shutterLegacy = meta.getString(Meta.EXPOSURE_COLUMN);		
		focalLegacy = meta.getString(Meta.FOCAL_LENGTH_COLUMN);		
		isoLegacy = meta.getString(Meta.ISO_COLUMN);		
		modelLegacy = meta.getString(Meta.MODEL_COLUMN);		
		makeLegacy = meta.getString(Meta.MAKE_COLUMN);				
	}

	private boolean readXmp()
	{
		InputStream xmp = getXmpInputStream();
		try
		{
			if (xmp != null)
			{
				XmpReader reader = new XmpReader();
				byte[] buffer = new byte[xmp.available()];
				xmp.read(buffer);
				reader.extract(buffer, mMetadata);
			}
			return true;
		}
		catch (IOException e)
		{
			Log.e(TAG, "Failed to open XMP.", e);
			return false;
		}
		finally
		{
            Utils.closeSilently(xmp);
		}
	}

	public int getWidth()
	{
		checkMetaLoaded();
		return width;
	}

	public void setWidth(int width)
	{
		this.width = width;
	}

	public int getHeight()
	{
		checkMetaLoaded();
		return height;
	}

	public void setHeight(int height)
	{
		this.height = height;
	}

	public int getThumbWidth()
	{
		checkMetaLoaded();
		return thumbWidth;
	}

	public void setThumbWidth(int thumbWidth)
	{
		this.thumbWidth = thumbWidth;
	}

	public int getThumbHeight()
	{
		checkMetaLoaded();
		return thumbHeight;
	}

	public void setThumbHeight(int thumbHeight)
	{
		this.thumbHeight = thumbHeight;
	}
	
	protected void checkMetaLoaded()
	{
		if (!metaLoaded)
		{
			getContent();
		}
	}
}
