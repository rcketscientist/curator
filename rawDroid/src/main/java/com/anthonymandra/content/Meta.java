package com.anthonymandra.content;

import android.net.Uri;
import android.provider.BaseColumns;

import com.anthonymandra.rawdroid.BuildConfig;

public class Meta
{
	// These are shortcuts for cursor indexing.  Must match createTable()
	public static final int ID_COLUMN = 0;
	public static final int NAME_COLUMN = 1;
	public static final int TIMESTAMP_COLUMN = 2;
	public static final int MODEL_COLUMN = 3;
	public static final int APERTURE_COLUMN = 4;
	public static final int EXPOSURE_COLUMN = 5;
	public static final int FLASH_COLUMN = 6;
	public static final int FOCAL_LENGTH_COLUMN = 7;
	public static final int ISO_COLUMN = 8;
	public static final int WHITE_BALANCE_COLUMN = 9;
	public static final int HEIGHT_COLUMN = 10;
	public static final int WIDTH_COLUMN = 11;
	public static final int LATITUDE_COLUMN = 12;
	public static final int LONGITUDE_COLUMN = 13;
	public static final int ALTITUDE_COLUMN = 14;
	public static final int MEDIA_ID_COLUMN = 15;
	public static final int ORIENTATION_COLUMN = 16;
	public static final int MAKE_COLUMN = 17;
	public static final int URI_COLUMN = 18;
	public static final int RATING_COLUMN = 19;
	public static final int SUBJECT_COLUMN = 20;
	public static final int LABEL_COLUMN = 21;
	public static final int LENS_MODEL_COLUMN = 22;
	public static final int DRIVE_MODE_COLUMN = 23;
	public static final int EXPOSURE_MODE_COLUMN = 24;
	public static final int EXPOSURE_PROGRAM_COLUMN = 25;
	public static final int TYPE_COLUMN = 26;
	public static final int PROCESSED_COLUMN = 27;

	public static final int RAW = 0;
	public static final int COMMON = 1;
	public static final int TIFF = 2;

	public static final String AUTHORITY = BuildConfig.PROVIDER_AUTHORITY_META;

	public static final class Data implements BaseColumns
	{
		public static final Uri META_URI = Uri.parse("content://" + AUTHORITY + "/" + Data.META);
		public static final Uri CONTENT_URI = META_URI;
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.rawdroid.meta";
		public static final String CONTENT_META_TYPE = "vnd.android.cursor.item/vnd.rawdroid.meta";

		public static final String META = "meta";
//        public static final String THUMB = "thumb";
//        public static final String IMAGE = "image";


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

		public static final String MEDIA_ID = "media_id";
		/**
		 * Name of the thumb data column.????
		 */
//		public static final String _DATA = "_data";
	}
}
