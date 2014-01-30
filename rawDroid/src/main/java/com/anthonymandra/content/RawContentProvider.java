//package com.anthonymandra.content;
//
//import java.io.FileNotFoundException;
//import java.util.Locale;
//
//import android.content.ContentUris;
//import android.content.ContentValues;
//import android.content.Context;
//import android.content.UriMatcher;
//import android.content.res.Resources;
//import android.database.Cursor;
//import android.database.sqlite.SQLiteDatabase;
//import android.database.sqlite.SQLiteOpenHelper;
//import android.net.Uri;
//import android.os.ParcelFileDescriptor;
//import android.provider.BaseColumns;
//import android.text.TextUtils;
//
//import com.anthonymandra.framework.FileHandlerFactory;
//
//public class RawContentProvider extends RESTfulContentProvider
//{
//	public static final String VIDEO = "video";
//	public static final String DATABASE_NAME = VIDEO + ".db";
//	static int DATABASE_VERSION = 2;
//
//	public static final String RAW_TABLE_NAME = "raw";
//
//	private static final String FILE_CACHE_DIR = "/data/data/com.anthonymandra.rawdroid/file_cache";
//
//	private static final int RAW = 1;
//	private static final int RAW_ID = 2;
//	private static final int THUMB_RAW_ID = 3;
//	private static final int THUMB_ID = 4;
//
//	private static UriMatcher sUriMatcher;
//
//	// Statically construct a uri matcher that can detect URIs referencing
//	// more than 1 video, a single video, or a single thumb nail image.
//	static
//	{
//		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
//		sUriMatcher.addURI(RawImage.AUTHORITY, RawImage.RawImages.RAW, RAW);
//		// use of the hash character indicates matching of an id
//		sUriMatcher.addURI(RawImage.AUTHORITY, RawImage.RawImages.RAW + "/#", RAW_ID);
//		sUriMatcher.addURI(RawImage.AUTHORITY, RawImage.RawImages.THUMB + "/#", THUMB_RAW_ID);
//		sUriMatcher.addURI(RawImage.AUTHORITY, RawImage.RawImages.THUMB + "/*", THUMB_ID);
//	}
//
//	private DatabaseHelper mOpenHelper;
//	private SQLiteDatabase mDb;
//
//	private static class DatabaseHelper extends SQLiteOpenHelper
//	{
//		private DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory)
//		{
//			super(context, name, factory, DATABASE_VERSION);
//		}
//
//		@Override
//		public void onCreate(SQLiteDatabase sqLiteDatabase)
//		{
//			createTable(sqLiteDatabase);
//		}
//
//		private void createTable(SQLiteDatabase sqLiteDatabase)
//		{
//			String createvideoTable = "CREATE TABLE " + RAW_TABLE_NAME + " (" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
//					+ RawImage.RawImages.NAME + " TEXT, " + RawImage.RawImages.TIMESTAMP + " TEXT, " + RawImage.RawImages.MODEL + " TEXT,"
//					+ RawImage.RawImages.APERTURE + " TEXT," + RawImage.RawImages.EXPOSURE + " TEXT," + RawImage.RawImages.FLASH + " TEXT, "
//					+ RawImage.RawImages.FOCAL_LENGTH + " TEXT, " + RawImage.RawImages.ISO + " TEXT UNIQUE," + RawImage.RawImages.WHITE_BALANCE + " TEXT, "
//					+ RawImage.RawImages.HEIGHT + " TEXT, " + RawImage.RawImages.WIDTH + " TEXT, " + RawImage.RawImages.LATITUDE + " TEXT, "
//					+ RawImage.RawImages.LONGITUDE + " TEXT, " + RawImage.RawImages.ALTITUDE + " TEXT, " + RawImage.RawImages.MEDIA_ID + " TEXT, "
//					+ RawImage.RawImages.THUMB_CONTENT_URI + " TEXT UNIQUE," + RawImage.RawImages._DATA + " TEXT UNIQUE" + ");";
//			sqLiteDatabase.execSQL(createvideoTable);
//		}
//
//		@Override
//		public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldv, int newv)
//		{
//			sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + RAW_TABLE_NAME + ";");
//			createTable(sqLiteDatabase);
//		}
//	}
//
//	public RawContentProvider()
//	{
//		super();
//	}
//
//	public RawContentProvider(Context context)
//	{
//		super();
//		init();
//	}
//
//	private void init()
//	{
//		mOpenHelper = new DatabaseHelper(getContext(), DATABASE_NAME, null);
//		mDb = mOpenHelper.getWritableDatabase();
//		mFileHandlerFactory = new FileHandlerFactory(FILE_CACHE_DIR);
//	}
//
//	@Override
//	public SQLiteDatabase getDatabase()
//	{
//		return mDb;
//	}
//
//	@Override
//	public int delete(Uri uri, String where, String[] whereArgs)
//	{
//		int match = sUriMatcher.match(uri);
//		int affected;
//
//		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
//		switch (match)
//		{
//		case RAW:
//			affected = db.delete(RAW_TABLE_NAME, (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
//			break;
//		case RAW_ID:
//			long videoId = ContentUris.parseId(uri);
//			affected = db.delete(RAW_TABLE_NAME, BaseColumns._ID + "=" + videoId + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
//			getContext().getContentResolver().notifyChange(uri, null);
//			break;
//		default:
//			throw new IllegalArgumentException("unknown video element: " + uri);
//		}
//
//		return affected;
//	}
//
//	@Override
//	public String getType(Uri uri)
//	{
//		switch (sUriMatcher.match(uri))
//		{
//		case RAW:
//			return RawImage.RawImages.CONTENT_TYPE;
//
//		case RAW_ID:
//			return RawImage.RawImages.CONTENT_IMAGE_TYPE;
//
//		case THUMB_ID:
//			return RawImage.RawImages.CONTENT_THUMB_TYPE;
//
//		default:
//			throw new IllegalArgumentException("Unknown raw type: " + uri);
//		}
//	}
//
//	/**
//	 * Provides a handler that can parse YouTube gData RSS content.
//	 *
//	 * @param requestTag
//	 *            unique tag identifying this request.
//	 * @return a YouTubeHandler object.
//	 */
//	@Override
//	protected ResponseHandler newResponseHandler(String requestTag)
//	{
//		return new RawResponseHandler(this);
//	}
//
//	/**
//	 * Provides read only access to files that have been downloaded and stored in the provider cache. Specifically, in this provider, clients can
//	 * access the files of downloaded thumbnail images.
//	 */
//	@Override
//	public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException
//	{
//		// only support read only files
//		if (!"r".equals(mode.toLowerCase(Locale.US)))
//		{
//			throw new FileNotFoundException("Unsupported mode, " + mode + ", for uri: " + uri);
//		}
//
//		return openFileHelper(uri, mode);
//	}
//
//	@Override
//	public Uri insert(Uri uri, ContentValues initialValues)
//	{
//		// Validate the requested uri
//		if (sUriMatcher.match(uri) != RAW)
//		{
//			throw new IllegalArgumentException("Unknown URI " + uri);
//		}
//
//		ContentValues values;
//		if (initialValues != null)
//		{
//			values = new ContentValues(initialValues);
//		}
//		else
//		{
//			values = new ContentValues();
//		}
//
//		SQLiteDatabase db = getDatabase();
//		return insert(uri, initialValues, db);
//	}
//
//	/**
//	 * The delegate insert method, which also takes a database parameter. Note that this method is a direct implementation of a content provider
//	 * method.
//	 */
//	@Override
//	public Uri insert(Uri uri, ContentValues values, SQLiteDatabase db)
//	{
//		verifyValues(values);
//
//		// Validate the requested uri
//		int m = sUriMatcher.match(uri);
//		if (m != RAW)
//		{
//			throw new IllegalArgumentException("Unknown URI " + uri);
//		}
//
//		// insert the values into a new database row
//		String mediaID = (String) values.get(RawImage.RawImages.MEDIA_ID);
//
//		Long rowID = mediaExists(db, mediaID);
//		if (rowID == null)
//		{
//			long time = System.currentTimeMillis();
//			values.put(RawImage.RawImages.TIMESTAMP, time);
//			long rowId = db.insert(RAW_TABLE_NAME, RawImage.RawImages.RAW, values);
//			if (rowId >= 0)
//			{
//				Uri insertUri = ContentUris.withAppendedId(RawImage.RawImages.CONTENT_URI, rowId);
//				getContext().getContentResolver().notifyChange(insertUri, null);
//				return insertUri;
//			}
//
//			throw new IllegalStateException("could not insert content values: " + values);
//		}
//
//		return ContentUris.withAppendedId(RawImage.RawImages.CONTENT_URI, rowID);
//	}
//
//	private Long mediaExists(SQLiteDatabase db, String mediaID)
//	{
//		Cursor cursor = null;
//		Long rowID = null;
//		try
//		{
//			cursor = db.query(RAW_TABLE_NAME, null, RawImage.RawImages.MEDIA_ID + " = '" + mediaID + "'", null, null, null, null);
//			if (cursor.moveToFirst())
//			{
//				rowID = cursor.getLong(RawImage.ID_COLUMN);
//			}
//		}
//		finally
//		{
//			if (cursor != null)
//			{
//				cursor.close();
//			}
//		}
//		return rowID;
//	}
//
//	private void verifyValues(ContentValues values)
//	{
//		if (!values.containsKey(RawImage.RawImages.NAME))
//		{
//			Resources r = Resources.getSystem();
//			values.put(RawImage.RawImages.NAME, r.getString(android.R.string.untitled));
//		}
//
//		if (!values.containsKey(RawImage.RawImages.THUMB_CONTENT_URI))
//		{
//			throw new IllegalArgumentException("Thumb uri not specified: " + values);
//		}
//
//		// Make sure that the fields are all set (missing a lot currently)...
//		if (!values.containsKey(RawImage.RawImages.TIMESTAMP))
//		{
//			Long now = System.currentTimeMillis();
//			values.put(RawImage.RawImages.TIMESTAMP, now);
//		}
//
//		if (!values.containsKey(RawImage.RawImages.MEDIA_ID))
//		{
//			throw new IllegalArgumentException("Media ID not specified: " + values);
//		}
//	}
//
//	@Override
//	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
//	{
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs)
//	{
//		// TODO Auto-generated method stub
//		return 0;
//	}
//}
