package com.anthonymandra.content;

import java.io.FileNotFoundException;
import java.util.Locale;

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

import com.anthonymandra.content.Meta.Data;

public class MetaProvider extends ContentProvider
{
	public static final String DATABASE_NAME = "rawdroid.db";
	static int DATABASE_VERSION = 5;

	public static final String META_TABLE_NAME = "meta";

	private static final int META = 1;
	private static final int META_ID = 2;

	private static UriMatcher sUriMatcher;

	// Statically construct a uri matcher that can detect URIs referencing
	// more than 1 video, a single video, or a single thumb nail image.
	static
	{
		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		sUriMatcher.addURI(Meta.AUTHORITY, Meta.Data.META, META);
		// use of the hash character indicates matching of an id
		sUriMatcher.addURI(Meta.AUTHORITY, Meta.Data.META + "/#", META_ID);
	}

	private DatabaseHelper dbHelper;

	private static class DatabaseHelper extends SQLiteOpenHelper
	{
		private DatabaseHelper(Context context)
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
					Meta.Data.NAME 			+ " TEXT, " + 
					Meta.Data.TIMESTAMP 	+ " TEXT, " + 
					Meta.Data.MODEL 		+ " TEXT, " + 
					Meta.Data.APERTURE 		+ " TEXT, " + 
					Meta.Data.EXPOSURE 		+ " TEXT, " + 
					Meta.Data.FLASH 		+ " TEXT, " + 
					Meta.Data.FOCAL_LENGTH 	+ " TEXT, " + 
					Meta.Data.ISO 			+ " TEXT, " + 
					Meta.Data.WHITE_BALANCE + " TEXT, " + 
					Meta.Data.HEIGHT 		+ " INTEGER, " + 
					Meta.Data.WIDTH 		+ " INTEGER, " + 
					Meta.Data.LATITUDE 		+ " TEXT, " + 
					Meta.Data.LONGITUDE 	+ " TEXT, " + 
					Meta.Data.ALTITUDE 		+ " TEXT, " + 
					Meta.Data.MEDIA_ID 		+ " TEXT, " +
					Meta.Data.ORIENTATION 	+ " INTEGER, " +
					Meta.Data.MAKE 			+ " TEXT, "	+ 
					Meta.Data.URI 			+ " TEXT," 	+ 
					Meta.Data.THUMB_HEIGHT	+ " INTEGER," + 
					Meta.Data.THUMB_WIDTH	+ " INTEGER" + ");";
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
				affected = db.delete(META_TABLE_NAME, (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
				break;
			case META_ID:
				long metaId = ContentUris.parseId(uri);
				affected = db.delete(META_TABLE_NAME, BaseColumns._ID + "=" + metaId + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
				getContext().getContentResolver().notifyChange(uri, null);
				break;
			default:
				throw new IllegalArgumentException("unknown meta element: " + uri);
		}

		return affected;
	}

	@Override
	public String getType(Uri uri)
	{
		switch (sUriMatcher.match(uri))
		{
		case META:
			return Meta.Data.CONTENT_TYPE;

		case META_ID:
			return Meta.Data.CONTENT_META_TYPE;

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
        
        long rowId = db.insert(META_TABLE_NAME, Data.NAME, values);
        if (rowId > 0) 
        {
            Uri metaUri = ContentUris.withAppendedId(Data.CONTENT_URI, rowId);
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
//		if (!values.containsKey(Metadata.Values.NAME))
//		{
//			Resources r = Resources.getSystem();
//			values.put(Metadata.Values.NAME, r.getString(android.R.string.untitled));
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
//        qb.setProjectionMap(notesProjectionMap);
 
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
        switch (sUriMatcher.match(uri)) {
            case META:
                count = db.update(META_TABLE_NAME, values, where, whereArgs);
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
}
