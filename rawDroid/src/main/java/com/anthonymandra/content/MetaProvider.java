package com.anthonymandra.content;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.text.TextUtils;

import com.crashlytics.android.Crashlytics;

import java.io.FileNotFoundException;
import java.util.Locale;

public class MetaProvider extends ContentProvider
{
	private static final String TAG = MetaProvider.class.getSimpleName();
	public static final String DATABASE_NAME = "rawdroid.db";
	static int DATABASE_VERSION = 15;

	public static final String META_TABLE_NAME = "meta";

	private static final int META = 1;
	private static final int META_ID = 2;
    private static final int THUMB = 3;
    private static final int THUMB_ID = 4;
    private static final int IMAGE = 5;
    private static final int IMAGE_ID = 6;

	private static UriMatcher sUriMatcher;

	// Statically construct a uri matcher that can detect URIs referencing
	// more than 1 video, a single video, or a single thumb nail image.
	static
	{
		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		sUriMatcher.addURI(Meta.AUTHORITY, Meta.META, META);
		// use of the hash character indicates matching of an id
		sUriMatcher.addURI(Meta.AUTHORITY, Meta.META + "/#", META_ID);
	}

	private DatabaseHelper dbHelper;

	public static class DatabaseHelper extends SQLiteOpenHelper
	{
		public DatabaseHelper(Context context)
		{
			super(context, META_TABLE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase sqLiteDatabase)
		{
			createTable(sqLiteDatabase);
		}

		private void createTable(SQLiteDatabase sqLiteDatabase)
		{
			String createMetaTable = 
					"CREATE TABLE " + META_TABLE_NAME + " (" + 
					BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + 
					Meta.NAME 		    	+ " TEXT, " +
					Meta.TIMESTAMP     	+ " TEXT, " +
					Meta.MODEL 	    	+ " TEXT, " +
					Meta.APERTURE 	    	+ " TEXT, " +
					Meta.EXPOSURE 		    + " TEXT, " +
					Meta.FLASH 		    + " TEXT, " +
					Meta.FOCAL_LENGTH  	+ " TEXT, " +
					Meta.ISO 			    + " TEXT, " +
					Meta.WHITE_BALANCE     + " TEXT, " +
					Meta.HEIGHT 		    + " INTEGER, " +
					Meta.WIDTH 		    + " INTEGER, " +
					Meta.LATITUDE 		    + " TEXT, " +
					Meta.LONGITUDE 	    + " TEXT, " +
					Meta.ALTITUDE 		    + " TEXT, " +
					Meta.MEDIA_ID 		    + " TEXT, " +
					Meta.ORIENTATION 	    + " INTEGER, " +
					Meta.MAKE 			    + " TEXT, "	+
					Meta.URI 			    + " TEXT UNIQUE," 	+
					Meta.RATING			+ " REAL," +
					Meta.SUBJECT	    	+ " TEXT," +
					Meta.LABEL	    		+ " TEXT," +
					Meta.LENS_MODEL	    + " TEXT," +
					Meta.DRIVE_MODE	    + " TEXT," +
					Meta.EXPOSURE_MODE	    + " TEXT," +
					Meta.EXPOSURE_PROGRAM	+ " TEXT," +
					Meta.TYPE				+ " INTEGER," +
					Meta.PROCESSED			+ " BOOLEAN," +
					Meta.PARENT            + " TEXT);";
			sqLiteDatabase.execSQL(createMetaTable);
		}

		@Override
		public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldv, int newv)
		{
			sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + META_TABLE_NAME + ";");
			createTable(sqLiteDatabase);
		}
	}

	@Override
	public int delete(Uri uri, String where, String[] whereArgs)
	{
		int match = sUriMatcher.match(uri);
		int affected;

		SQLiteDatabase db = dbHelper.getWritableDatabase();
		switch (match)
		{
			case META:
				affected = db.delete(META_TABLE_NAME, where, whereArgs);
				break;
			case META_ID:
				long metaId = ContentUris.parseId(uri);
				affected = db.delete(META_TABLE_NAME,
						BaseColumns._ID + "=" + metaId + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""),
						whereArgs);
				break;
			default:
				throw new IllegalArgumentException("unknown meta element: " + uri);
		}

		getContext().getContentResolver().notifyChange(uri, null);
		return affected;
	}

	@Override
	public String getType(Uri uri)
	{
		switch (sUriMatcher.match(uri))
		{
			case META:
				return Meta.CONTENT_TYPE;

			case META_ID:
				return Meta.CONTENT_META_TYPE;

		default:
			throw new IllegalArgumentException("Unknown meta type: " + uri);
		}
	}

	/**
	 * Provides read only access to files that have been downloaded and stored in the provider cache. Specifically, in this provider, clients can
	 * access the files of downloaded thumbnail images.
	 */
	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException
	{
		// only support read only files
		if (!"r".equals(mode.toLowerCase(Locale.US)))
		{
			throw new FileNotFoundException("Unsupported mode, " + mode + ", for uri: " + uri);
		}

		return openFileHelper(uri, mode);
	}
	
	/**
	 * The delegate insert method, which also takes a database parameter. Note that this method is a direct implementation of a content provider
	 * method.
	 */
	@Override
	public Uri insert(Uri uri, ContentValues initialValues)
	{
//		verifyValues(values);

		// Validate the requested uri
		if (sUriMatcher.match(uri) != META)
		{
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }
        
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
//        long rowId = db.insert(META_TABLE_NAME, Data.KEYWORD_NAME, values);
		long rowId = db.insertWithOnConflict(META_TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);

        if (rowId > 0) 
        {
            Uri metaUri = ContentUris.withAppendedId(Meta.CONTENT_URI, rowId);
            getContext().getContentResolver().notifyChange(metaUri, null);
            return metaUri;
        }
 
        throw new SQLException("Failed to insert row into " + uri);
	}

//	private Long mediaExists(SQLiteDatabase db, String mediaID)
//	{
//		Cursor cursor = null;
//		Long rowID = null;
//		try
//		{
//			cursor = db.query(META_TABLE_NAME, null, Metadata.Values.MEDIA_ID + " = '" + mediaID + "'", null, null, null, null);
//			if (cursor.moveToFirst())
//			{
//				rowID = cursor.getLong(Metadata.ID_COLUMN);
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
//		if (!values.containsKey(Metadata.Values.KEYWORD_NAME))
//		{
//			Resources r = Resources.getSystem();
//			values.put(Metadata.Values.KEYWORD_NAME, r.getString(android.R.string.untitled));
//		}
//
//		// Make sure that the fields are all set (missing a lot currently)...
//		if (!values.containsKey(Metadata.Values.TIMESTAMP))
//		{
//			Long now = System.currentTimeMillis();
//			values.put(Metadata.Values.TIMESTAMP, now);
//		}
//
//		if (!values.containsKey(Metadata.Values.MEDIA_ID))
//		{
//			throw new IllegalArgumentException("Media ID not specified: " + values);
//		}
//	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
	{
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(META_TABLE_NAME);

        switch (sUriMatcher.match(uri)) {    
            case META:
                break;
            case META_ID:
                selection = selection + "_id = " + ContentUris.parseId(uri);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
 
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
 
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
	}

	@Override
	public int update(Uri uri, ContentValues values, String where, String[] whereArgs)
	{
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count;

		//TODO: Not currently dealing with ids, but should have an id version
        switch (sUriMatcher.match(uri)) {
            case META:
	            try
	            {
		            count = db.update(META_TABLE_NAME, values, where, whereArgs);
	            }
	            catch(Exception e)
	            {
		            Crashlytics.logException(e);    //TODO: Avoid https://bitbucket.org/rcketscientist/rawdroid/issues/190/metaproviderjava-line-277-crashlytics
		            return 0;
	            }

                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
 
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
	}

	@Override
	public boolean onCreate() {
		dbHelper = new DatabaseHelper(getContext());
        return true;
	}

	public static String getUriWhere()
	{
		return Meta.URI + "=?";
	}
}
