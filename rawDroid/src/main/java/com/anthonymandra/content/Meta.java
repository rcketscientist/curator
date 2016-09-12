package com.anthonymandra.content;

import android.net.Uri;
import android.provider.BaseColumns;

import com.anthonymandra.rawdroid.BuildConfig;

public class Meta implements BaseColumns
{
	public enum ImageType
	{
		UNKNOWN (-1),
		RAW (0),
		COMMON (1),
		TIFF (2);

		private int value;
		ImageType(int value) {this.value = value;}
		public static ImageType fromOrdinal(int n) {return values()[n];}
	}

	public static final int RAW = 0;
	public static final int COMMON = 1;
	public static final int TIFF = 2;

	public static final String META = "meta";
//        public static final String THUMB = "thumb";
//        public static final String IMAGE = "image";

	public static final String AUTHORITY = BuildConfig.PROVIDER_AUTHORITY_META;
	public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + META);

	public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.rawdroid.meta";
	public static final String CONTENT_META_TYPE = "vnd.android.cursor.item/vnd.rawdroid.meta";

	// Uri
	public static final String URI = "uri";
//        public static final String THUMBNAIL_URI = "thumbnail_uri";
//        public static final String FULL_IMAGE_URI = "full_image_uri";

	// Meta data
	public static final String NAME = "name";
	public static final String TIMESTAMP = "timestamp";
	public static final String MODEL = "model";
	public static final String APERTURE = "aperture";
	public static final String EXPOSURE = "exposure";
	public static final String FLASH = "flash";
	public static final String FOCAL_LENGTH = "focal_length";
	public static final String ISO = "iso";
	public static final String WHITE_BALANCE = "white_balance";
	public static final String HEIGHT = "height";
	public static final String WIDTH = "width";
	public static final String LATITUDE = "latitude";
	public static final String LONGITUDE = "longitude";
	public static final String ALTITUDE = "altitude";
	public static final String ORIENTATION = "orientation";
	public static final String MAKE = "make";
//		public static final String THUMB_HEIGHT = "thumb_height";
//		public static final String THUMB_WIDTH = "thumb_width";

	public static final String RATING = "rating";
	public static final String SUBJECT = "subject";
	public static final String LABEL = "label";

	public static final String LENS_MODEL = "lens_model";
	public static final String DRIVE_MODE = "drive_mode";
	public static final String EXPOSURE_MODE = "exposure_mode";
	public static final String EXPOSURE_PROGRAM = "exposure_program";

	public static final String TYPE  = "type";
	public static final String PROCESSED  = "processed";
	public static final String PARENT  = "parent";

	public static final String MEDIA_ID = "media_id";
	/**
	 * Name of the thumb data column.????
	 */
//		public static final String _DATA = "_data";
}
