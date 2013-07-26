package com.anthonymandra.rawdroid;

import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.exif.makernotes.CanonMakernoteDirectory;
import com.drew.metadata.exif.makernotes.CanonMakernoteDirectory.CameraSettings;
import com.drew.metadata.exif.makernotes.NikonType2MakernoteDirectory;

public class SupportedMeta
{
	// SubIFD
	String Iso = "-";
	String Exposure = "-";
	String Aperture = "-";
	String FocalLength = "-";
	String Height = "-";
	String Width = "-";
	String Flash = "-";
	String Name = "-";
	String WhiteBalance = "-";
	String ExposureMode = "-";
	String ExposureProgram = "-";
	// IFD0
	String Date = "-";
	String Make = "-";
	String Model = "-";
	// GPS
	String Altitude = "-";
	String Latitude = "-";
	String Longitude = "-";
	// MakerNotes
	String Lens = "-";
	String DriveMode = "-";
	// Orientation
	int Orientation = 0;

	public SupportedMeta(String name, Metadata metadata)
	{
		Name = name;
		for (final Directory directory : metadata.getDirectories())
		{
			for (final Tag tag : directory.getTags())
			{
				// Log.i(TAG, tag.toString());

				if (directory instanceof ExifSubIFDDirectory)
				{
					switch (tag.getTagType())
					{
					case ExifSubIFDDirectory.TAG_APERTURE:
						Aperture = tag.getDescription();
						break;
					case ExifSubIFDDirectory.TAG_EXPOSURE_TIME:
						Exposure = tag.getDescription();
						break;
					case ExifSubIFDDirectory.TAG_FLASH:
						Flash = tag.getDescription();
						break;
					case ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT:
						Height = tag.getDescription();
						break;
					case ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH:
						Width = tag.getDescription();
						break;
					case ExifSubIFDDirectory.TAG_FOCAL_LENGTH:
						FocalLength = tag.getDescription();
						break;
					case ExifSubIFDDirectory.TAG_SHUTTER_SPEED:
						Exposure = tag.getDescription();
						break;
					// case ExifSubIFDDirectory.TAG_WHITE_BALANCE:
					// Aperture = tag.getDescription();
					// break;
					case ExifSubIFDDirectory.TAG_WHITE_BALANCE_MODE:
						WhiteBalance = tag.getDescription();
						break;
					case ExifSubIFDDirectory.TAG_EXPOSURE_PROGRAM:
						ExposureProgram = tag.getDescription();
						break;
					case ExifSubIFDDirectory.TAG_EXPOSURE_MODE:
						ExposureMode = tag.getDescription();
						break;
					case ExifSubIFDDirectory.TAG_LENS_MAKE:
						Lens = tag.getDescription();
						break;
					case ExifSubIFDDirectory.TAG_LENS_MODEL:
						Lens += tag.getDescription();
						break;
					case ExifSubIFDDirectory.TAG_ISO_EQUIVALENT:
						Iso = tag.getDescription();
						break;
					case ExifSubIFDDirectory.TAG_FNUMBER:
						Aperture = tag.getDescription();
						break;
					default:
						// Log.i(TAG, tag.toString());
						break;
					}
				}
				if (directory instanceof ExifIFD0Directory)
				{
					switch (tag.getTagType())
					{
					case ExifIFD0Directory.TAG_DATETIME:
						Date = tag.getDescription();
						break;
					case ExifIFD0Directory.TAG_MAKE:
						Make = tag.getDescription();
						break;
					case ExifIFD0Directory.TAG_MODEL:
						Model = tag.getDescription();
						break;
					// case ExifIFD0Directory.TAG_ORIENTATION:
					// Iso = tag.getDescription();
					// break;
					default:
						// Log.i(TAG, tag.toString());
						break;
					}
				}
				if (directory instanceof GpsDirectory)
				{
					switch (tag.getTagType())
					{

					case GpsDirectory.TAG_ALTITUDE:
						Altitude = tag.getDescription();
						break;
					case GpsDirectory.TAG_LATITUDE:
						Latitude = tag.getDescription();
						break;
					case GpsDirectory.TAG_LONGITUDE:
						Longitude = tag.getDescription();
						break;
					default:
						// Log.i(TAG, tag.toString());
						break;
					}
				}
				if (directory instanceof CanonMakernoteDirectory)
				{
					switch (tag.getTagType())
					{
					// Canon
					case CanonMakernoteDirectory.TAG_LENS_MODEL:
						Lens = tag.getDescription();
						break;
					case CameraSettings.TAG_CONTINUOUS_DRIVE_MODE:
						DriveMode = tag.getDescription();
						break;
					default:
						// Log.i(TAG, tag.toString());
						break;
					}
				}
				if (directory instanceof NikonType2MakernoteDirectory)
				{
					switch (tag.getTagType())
					{
					case NikonType2MakernoteDirectory.TAG_LENS:
						Lens = tag.getDescription();
						break;
					case NikonType2MakernoteDirectory.TAG_SHOOTING_MODE:
						DriveMode = tag.getDescription();
						break;
					default:
						// Log.i(TAG, tag.toString());
						break;
					}
				}
			}
		}
	}
}
