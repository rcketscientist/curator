package com.anthonymandra.content;

import android.net.Uri;
import android.provider.BaseColumns;

public class RawImage
{
	public static final int ID_COLUMN = 0;
	public static final int NAME_COLUMN = 1;
	public static final int TIMESTAMP_COLUMN = 2;
	public static final int MODEL_COLUMN = 3;
	public static final int APERTURE_COLUMN = 4;
	public static final int EXPOSURE_COLUMN = 5;
	public static final int FLASH_COLUMN = 6;
	public static final int FOCAL_LENGTH_COLUMN = 7;
	public static final int ISO_COLUMN = 8;
	public static final int WHITE_BALANCE_COLUMN = 8;
	public static final int HEIGHT_COLUMN = 10;
	public static final int WIDTH_COLUMN = 11;
	public static final int LATITUDE_COLUMN = 12;
	public static final int LONGITUDE_COLUMN = 13;
	public static final int ALTITUDE_COLUMN = 14;
	public static final int MEDIA_ID_COLUMN = 15;

	public static final int THUMB_URI_COLUMN = 16;
	public static final int CONVERTED_RAW_URI_COLUMN = 17;

	public static final String AUTHORITY = "com.anthonymnadra.rawdroid.RawImage";

	public static final class RawImages implements BaseColumns
	{
		public static final Uri RAW_IMAGES_URI = Uri.parse("content://" + AUTHORITY + "/" + RawImages.RAW);
		public static final Uri RAW_THUMB_URI = Uri.parse("content://" + AUTHORITY + "/" + RawImages.THUMB);
		public static final Uri CONTENT_URI = RAW_IMAGES_URI;
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.rawdroid.raw";
		public static final String CONTENT_IMAGE_TYPE = "vnd.android.cursor.item/vnd.rawdroid.raw";
		public static final String CONTENT_THUMB_TYPE = "vnd.android.cursor.item/vnd.rawdroid.rawThumb";

		// Files
		public static final String RAW = "raw";
		public static final String THUMB = "thumb";
		public static final String CONVERTED_RAW = "convert";

		// Uri
		public static final String URI = "uri";
		public static final String CONVERTED_RAW_URI = "converted_uri";
		public static final String THUMB_URI = "thumb_uri";
		public static final String THUMB_CONTENT_URI = "thumb_content_uri";

		// Meta data
		public static final String NAME = "name";
		public static final String TIMESTAMP = "timestamp";
		public static final String MODEL = "model";
		public static final String APERTURE = "aperture";
		public static final String EXPOSURE = "exposure";
		public static final String FLASH = "flash";
		public static final String FOCAL_LENGTH = "focal_length";
		public static final String ISO = "iso";
		public static final String WHITE_BALANCE = "wb";
		public static final String HEIGHT = "height";
		public static final String WIDTH = "width";
		public static final String LATITUDE = "latitude";
		public static final String LONGITUDE = "longitude";
		public static final String ALTITUDE = "altitude";

		public static final String MEDIA_ID = "media_id";
		/**
		 * Name of the thumb data column.????
		 */
		public static final String _DATA = "_data";
	}
}
