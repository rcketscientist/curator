package com.anthonymandra.framework;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.android.gallery3d.data.MediaItem;
import com.anthonymandra.content.Meta;
import com.anthonymandra.util.ImageUtils;
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

import java.text.SimpleDateFormat;
import java.util.Date;

public abstract class MetaMedia extends MediaItem
{
	private static final String TAG = MetaMedia.class.getSimpleName();

	protected int width;
	protected int height;
//	protected int thumbWidth;
//	protected int thumbHeight;
	
	protected int orientation;

	public MetaMedia(Context context, Uri path) {
		super(context, path);
	}

	public MetaMedia(Context context, Cursor cursor) {
		super(context, cursor);

		width = cursor.getInt(cursor.getColumnIndex(Meta.WIDTH));
		height = cursor.getInt(cursor.getColumnIndex(Meta.HEIGHT));
		orientation = cursor.getInt(cursor.getColumnIndex(Meta.ORIENTATION));
	}

	public int getOrientation()
	{
		return orientation;
	}

	// The rotation of the full-resolution image. By default, it returns the value of
	// getRotation().
	public int getFullImageRotation()
	{
		return getRotation();
	}

	public int getRotation()
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

	public void setOrientation(int o)
	{
		orientation = o;
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
//	public int getThumbWidth()
//	{
//		return thumbWidth;
//	}
//
//	public int getThumbHeight()
//	{
//		return thumbHeight;
//	}
}
