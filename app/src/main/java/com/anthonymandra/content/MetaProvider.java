package com.anthonymandra.content;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import androidx.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MetaProvider extends ContentProvider
{
	private static final String TAG = MetaProvider.class.getSimpleName();

	// To avoid notify on every applyBatch transaction https://gist.github.com/saik0/4079112
	private final ThreadLocal<Boolean> mApplyingBatch = new ThreadLocal<>();
	private final ThreadLocal<Set<Uri>> mChangedUris = new ThreadLocal<>();

	private static final int DATABASE_VERSION = 17;

	private static final String META_TABLE_NAME = "meta";

	private static final int META = 1;
	private static final int META_ID = 2;

	private static final UriMatcher sUriMatcher;

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

	private static class DatabaseHelper extends SQLiteOpenHelper
	{
		DatabaseHelper(Context context)
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
					Meta.DOCUMENT_ID    	+ " TEXT UNIQUE, " +
					Meta.TIMESTAMP     	    + " TEXT, " +
					Meta.MODEL 	    	    + " TEXT, " +
					Meta.APERTURE 	    	+ " TEXT, " +
					Meta.EXPOSURE 		    + " TEXT, " +
					Meta.FLASH 		        + " TEXT, " +
					Meta.FOCAL_LENGTH  	    + " TEXT, " +
					Meta.ISO 			    + " TEXT, " +
					Meta.WHITE_BALANCE      + " TEXT, " +
					Meta.HEIGHT 		    + " INTEGER, " +
					Meta.WIDTH 		        + " INTEGER, " +
					Meta.LATITUDE 		    + " TEXT, " +
					Meta.LONGITUDE 	        + " TEXT, " +
					Meta.ALTITUDE 		    + " TEXT, " +
					Meta.MEDIA_ID 		    + " TEXT, " +
					Meta.ORIENTATION 	    + " INTEGER, " +
					Meta.MAKE 			    + " TEXT, "	+
					Meta.URI 			    + " TEXT," 	+
					Meta.RATING			    + " REAL," +
					Meta.SUBJECT	    	+ " TEXT," +
					Meta.LABEL	    		+ " TEXT," +
					Meta.LENS_MODEL	        + " TEXT," +
					Meta.DRIVE_MODE	        + " TEXT," +
					Meta.EXPOSURE_MODE	    + " TEXT," +
					Meta.EXPOSURE_PROGRAM	+ " TEXT," +
					Meta.TYPE				+ " INTEGER," +
					Meta.PROCESSED			+ " BOOLEAN," +
					Meta.PARENT             + " TEXT);";
			sqLiteDatabase.execSQL(createMetaTable);
		}

		@Override
		public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion)
		{
			sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + META_TABLE_NAME + ";");
			createTable(sqLiteDatabase);
		}
	}

	@Override
	public int delete(@NonNull Uri uri, String where, String[] whereArgs)
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

		if (applyingBatch())
			mChangedUris.get().add(uri);
		else
			//noinspection ConstantConditions
			getContext().getContentResolver().notifyChange(uri, null);

		return affected;
	}

	@Override
	public String getType(@NonNull Uri uri)
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
	public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException
	{
		// only support read only files
		if (!"r".equals(mode.toLowerCase(Locale.US)))
		{
			throw new FileNotFoundException("Unsupported mode, " + mode + ", for uri: " + uri);
		}

		return openFileHelper(uri, mode);
	}

	private boolean applyingBatch() {
		return mApplyingBatch.get() != null && mApplyingBatch.get();
	}

	@NonNull
	@Override
	public ContentProviderResult[] applyBatch(@NonNull ArrayList<ContentProviderOperation> operations) throws OperationApplicationException
	{
		ContentProviderResult[] result = new ContentProviderResult[operations.size()];
		mChangedUris.set(new HashSet<Uri>());
		
		int i = 0;
		// Opens the database object in "write" mode.
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		// Begin a transaction
		db.beginTransaction();
		try
		{
			for (ContentProviderOperation operation : operations)
			{
				// Chain the result for back references
				result[i++] = operation.apply(this, result, i);

			}

			db.setTransactionSuccessful();
		}
		catch (OperationApplicationException e)
		{
			Log.d(TAG, "batch failed: " + e.getLocalizedMessage());
		}
		finally
		{
			db.endTransaction();
			mApplyingBatch.set(false);
			for (Uri uri : mChangedUris.get())
			{
				//noinspection ConstantConditions
				getContext().getContentResolver().notifyChange(uri, null);
			}
		}

		return result;
	}

	public int bulkInsert(@NonNull Uri uri, @NonNull ContentValues[] values)
	{
		int numInserted = 0;

		if (sUriMatcher.match(uri) != META)
		{
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		SQLiteDatabase sqlDB = dbHelper.getWritableDatabase();
		sqlDB.beginTransaction();
		try
		{
			for (ContentValues cv : values)
			{
				try
				{
					long newID = sqlDB.insertOrThrow(META_TABLE_NAME, null, cv);
					numInserted++;
				}
				catch (Exception e)
				{
					Log.e(TAG, "Failed to add: " + cv.getAsString(Meta.URI), e);
				}
			}
			sqlDB.setTransactionSuccessful();
			//noinspection ConstantConditions
			getContext().getContentResolver().notifyChange(uri, null);
		}
		finally
		{
			sqlDB.endTransaction();
		}
		return numInserted;
	}
	
	@Override
	public Uri insert(@NonNull Uri uri, ContentValues initialValues)
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

		long rowId = db.insertWithOnConflict(META_TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);

        if (rowId > 0) 
        {
            Uri metaUri = ContentUris.withAppendedId(Meta.CONTENT_URI, rowId);
	        if (applyingBatch())
		        mChangedUris.get().add(uri);
	        else
		        //noinspection ConstantConditions
                getContext().getContentResolver().notifyChange(uri, null);
            return metaUri;
        }
 
        throw new SQLException("Failed to add row into " + uri);
	}



	@Override
	public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
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

		//noinspection ConstantConditions
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
	}

	@Override
	public int update(@NonNull Uri uri, ContentValues values, String where, String[] whereArgs)
	{
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count;

		//TODO: Not currently dealing with ids, but should have an id version
        switch (sUriMatcher.match(uri)) {
            case META:
	            try
	            {
		            count = db.update(META_TABLE_NAME, values, where, whereArgs);
		            if (count > 0)
		            {
			            if (applyingBatch())
				            mChangedUris.get().add(uri);
			            else
				            //noinspection ConstantConditions
				            getContext().getContentResolver().notifyChange(uri, null);
			            return count;
		            }
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

        return count;
	}

	@Override
	public boolean onCreate() {
		dbHelper = new DatabaseHelper(getContext());
        return true;
	}
}
