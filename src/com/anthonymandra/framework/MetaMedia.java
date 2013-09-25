package com.anthonymandra.framework;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
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

import android.net.Uri;
import android.util.Log;

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

    public MetaMedia(Uri path, long version) {
        super(path, version);
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

		try
		{
			if (bos != null)
				bos.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	protected void writeXmp(final OutputStream os)
	{
		new Runnable()
		{
			@Override
			public void run()
			{
				if (mMetadata.containsDirectory(XmpDirectory.class))
					XmpWriter.write(os, mMetadata);
			}
		}.run();
	}

	public String getAperture()
	{
		if (mMetadata.containsDirectory(ExifSubIFDDirectory.class) &&
			mMetadata.getDirectory(ExifSubIFDDirectory.class).containsTag(ExifSubIFDDirectory.TAG_APERTURE))
		{
			return mMetadata.getDirectory(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_APERTURE);
		}
		return apertureLegacy;
//		return null;
	}

	public String getExposure()
	{
		if (mMetadata.containsDirectory(ExifSubIFDDirectory.class))
		{
			ExifSubIFDDirectory exif = mMetadata.getDirectory(ExifSubIFDDirectory.class);
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
		if (mMetadata.containsDirectory(ExifSubIFDDirectory.class))
			return mMetadata.getDirectory(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT);
		return null;
	}

	public String getImageWidth()
	{
		if (mMetadata.containsDirectory(ExifSubIFDDirectory.class))
			return mMetadata.getDirectory(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH);
		return null;
	}

	public String getFocalLength()
	{
		if (mMetadata.containsDirectory(ExifSubIFDDirectory.class) &&
				mMetadata.getDirectory(ExifSubIFDDirectory.class).containsTag(ExifSubIFDDirectory.TAG_FOCAL_LENGTH))
		{
			return mMetadata.getDirectory(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
		}
			
		return focalLegacy;
//		return null;
	}

	public String getFlash()
	{
		if (mMetadata.containsDirectory(ExifSubIFDDirectory.class))
			return mMetadata.getDirectory(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_FLASH);
		return null;
	}

	public String getShutterSpeed()
	{
		if (mMetadata.containsDirectory(ExifSubIFDDirectory.class) &&
			mMetadata.getDirectory(ExifSubIFDDirectory.class).containsTag(ExifSubIFDDirectory.TAG_SHUTTER_SPEED))
		{
			return mMetadata.getDirectory(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_SHUTTER_SPEED);
		}
		return shutterLegacy;
//		return null;
	}

	public String getWhiteBalance()
	{
		if (mMetadata.containsDirectory(CanonMakernoteDirectory.class))
			return mMetadata.getDirectory(CanonMakernoteDirectory.class).getDescription(CanonMakernoteDirectory.FocalLength.TAG_WHITE_BALANCE);
		if (mMetadata.containsDirectory(PanasonicMakernoteDirectory.class))
			return mMetadata.getDirectory(PanasonicMakernoteDirectory.class).getDescription(PanasonicMakernoteDirectory.TAG_WHITE_BALANCE);
		if (mMetadata.containsDirectory(FujifilmMakernoteDirectory.class))
			return mMetadata.getDirectory(FujifilmMakernoteDirectory.class).getDescription(FujifilmMakernoteDirectory.TAG_WHITE_BALANCE);
		if (mMetadata.containsDirectory(LeicaMakernoteDirectory.class))
			return mMetadata.getDirectory(LeicaMakernoteDirectory.class).getDescription(LeicaMakernoteDirectory.TAG_WHITE_BALANCE);
		if (mMetadata.containsDirectory(ExifSubIFDDirectory.class))
			return mMetadata.getDirectory(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_WHITE_BALANCE);
		return null;
	}

	public String getExposureProgram()
	{
		if (mMetadata.containsDirectory(ExifSubIFDDirectory.class))
			return mMetadata.getDirectory(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_PROGRAM);
		return null;
	}

	public String getExposureMode()
	{
		if (mMetadata.containsDirectory(ExifSubIFDDirectory.class))
			return mMetadata.getDirectory(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_MODE);
		return null;
	}

	public String getLensMake()
	{
		if (mMetadata.containsDirectory(ExifSubIFDDirectory.class))
			return mMetadata.getDirectory(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_LENS_MAKE);
		return null;
	}

	public String getLensModel()
	{
		if (mMetadata.containsDirectory(CanonMakernoteDirectory.class))
			return mMetadata.getDirectory(CanonMakernoteDirectory.class).getDescription(CanonMakernoteDirectory.TAG_LENS_MODEL);
		if (mMetadata.containsDirectory(NikonType2MakernoteDirectory.class))
			return mMetadata.getDirectory(NikonType2MakernoteDirectory.class).getDescription(NikonType2MakernoteDirectory.TAG_LENS);
		if (mMetadata.containsDirectory(ExifSubIFDDirectory.class))
			return mMetadata.getDirectory(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_LENS_MODEL);
		return null;
	}

	public String getDriveMode()
	{
		if (mMetadata.containsDirectory(CanonMakernoteDirectory.class))
			return mMetadata.getDirectory(CanonMakernoteDirectory.class).getDescription(CameraSettings.TAG_CONTINUOUS_DRIVE_MODE);
		if (mMetadata.containsDirectory(NikonType2MakernoteDirectory.class))
			return mMetadata.getDirectory(NikonType2MakernoteDirectory.class).getDescription(NikonType2MakernoteDirectory.TAG_SHOOTING_MODE);
		return null;
	}

	public String getIso()
	{
		if (mMetadata.containsDirectory(ExifSubIFDDirectory.class) &&
			mMetadata.getDirectory(ExifSubIFDDirectory.class).containsTag(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT))
		{
			return mMetadata.getDirectory(ExifSubIFDDirectory.class).getDescription(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
		}
		return isoLegacy;
//		return null;
	}

	public String getFNumber()
	{
		if (mMetadata.containsDirectory(ExifSubIFDDirectory.class))
		{
			ExifSubIFDDirectory exif = mMetadata.getDirectory(ExifSubIFDDirectory.class);
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
		if (mMetadata.containsDirectory(ExifSubIFDDirectory.class))
			return mMetadata.getDirectory(ExifIFD0Directory.class).getDescription(ExifIFD0Directory.TAG_DATETIME);
		return null;
	}

	public String getMake()
	{
		if (mMetadata.containsDirectory(ExifIFD0Directory.class) &&
			mMetadata.getDirectory(ExifIFD0Directory.class).containsTag(ExifIFD0Directory.TAG_MAKE))
		{
			return mMetadata.getDirectory(ExifIFD0Directory.class).getDescription(ExifIFD0Directory.TAG_MAKE);
		}

		return makeLegacy;
//		return null;
	}

	public String getModel()
	{
		if (mMetadata.containsDirectory(ExifIFD0Directory.class) &&
			mMetadata.getDirectory(ExifIFD0Directory.class).containsTag(ExifIFD0Directory.TAG_MODEL))
		{
			return mMetadata.getDirectory(ExifIFD0Directory.class).getDescription(ExifIFD0Directory.TAG_MODEL);
		}

		return modelLegacy;
//		return null;
	}

	public int getOrientation()
	{
		if (mMetadata.containsDirectory(ExifSubIFDDirectory.class))
		{
			try
			{
				return mMetadata.getDirectory(ExifIFD0Directory.class).getInt(ExifIFD0Directory.TAG_ORIENTATION);
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
		if (mMetadata.containsDirectory(GpsDirectory.class))
			return mMetadata.getDirectory(GpsDirectory.class).getDescription(GpsDirectory.TAG_ALTITUDE);
		return null;
	}

	public String getLatitude()
	{
		if (mMetadata.containsDirectory(GpsDirectory.class))
			return mMetadata.getDirectory(GpsDirectory.class).getDescription(GpsDirectory.TAG_LATITUDE);
		return null;
	}

	public String getLongitude()
	{
		if (mMetadata.containsDirectory(GpsDirectory.class))
			return mMetadata.getDirectory(GpsDirectory.class).getDescription(GpsDirectory.TAG_LONGITUDE);
		return null;
	}

	public double getRating()
	{
		if (mMetadata.containsDirectory(XmpDirectory.class))
		{
			XmpDirectory xmp = mMetadata.getDirectory(XmpDirectory.class);
			if (xmp.containsTag(XmpDirectory.TAG_RATING))
			{
				try
				{
					return mMetadata.getDirectory(XmpDirectory.class).getDouble(XmpDirectory.TAG_RATING);
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
		if (mMetadata.containsDirectory(XmpDirectory.class))
			return mMetadata.getDirectory(XmpDirectory.class).getDescription(XmpDirectory.TAG_LABEL);
		return null;
	}

	public String[] getSubject()
	{
		if (mMetadata.containsDirectory(XmpDirectory.class))
			return mMetadata.getDirectory(XmpDirectory.class).getStringArray(XmpDirectory.TAG_SUBJECT);
		return null;
	}

	public void setRating(double rating)
	{
		XmpDirectory xmp = mMetadata.getDirectory(XmpDirectory.class);
		if (Double.isNaN(rating))
		{
			if (xmp != null)
				xmp.deleteProperty(XmpDirectory.TAG_RATING);
		}
		else
		{
			mMetadata.getOrCreateDirectory(XmpDirectory.class).updateDouble(XmpDirectory.TAG_RATING, rating);
		}
	}

	public void setLabel(String label)
	{
		XmpDirectory xmp = mMetadata.getDirectory(XmpDirectory.class);
		if (label == null)
		{
			if (xmp != null)
				xmp.deleteProperty(XmpDirectory.TAG_LABEL);

		}
		else
		{
			mMetadata.getOrCreateDirectory(XmpDirectory.class).updateString(XmpDirectory.TAG_LABEL, label);
		}
	}

	public void setSubject(String[] subject)
	{
		XmpDirectory xmp = mMetadata.getDirectory(XmpDirectory.class);
		if (subject.length == 0)
		{
			if (xmp != null)
				xmp.deleteProperty(XmpDirectory.TAG_SUBJECT);
		}
		else
		{
			mMetadata.getOrCreateDirectory(XmpDirectory.class).updateStringArray(XmpDirectory.TAG_SUBJECT, subject);
		}
	}

	protected abstract BufferedInputStream getXmpInputStream();

	protected abstract BufferedOutputStream getXmpOutputStream();

	public boolean readMetadata()
	{
		// Avoid reloading
		if (isLoaded)
		{
			return true;
		}

		boolean metaResult = readMeta();
		boolean xmpResult = readXmp();
		boolean result = metaResult && xmpResult;
		isLoaded = result;
		return result;
	}

	private boolean readMeta()
	{
		// Metadata
		InputStream raw = getImage();
		try
		{
			mMetadata = ImageMetadataReader.readMetadata(raw);
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
			try
			{
				if (xmp != null)
					xmp.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	public int getWidth()
	{
		return width;
	}

	public void setWidth(int width)
	{
		this.width = width;
	}

	public int getHeight()
	{
		return height;
	}

	public void setHeight(int height)
	{
		this.height = height;
	}

	public int getThumbWidth()
	{
		return thumbWidth;
	}

	public void setThumbWidth(int thumbWidth)
	{
		this.thumbWidth = thumbWidth;
	}

	public int getThumbHeight()
	{
		return thumbHeight;
	}

	public void setThumbHeight(int thumbHeight)
	{
		this.thumbHeight = thumbHeight;
	}
}
