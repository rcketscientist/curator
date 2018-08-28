package com.anthonymandra.content;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;
import androidx.annotation.NonNull;
import android.text.TextUtils;
import android.util.SparseArray;

import com.anthonymandra.framework.PathEnumerationProvider;
import com.anthonymandra.rawdroid.BuildConfig;
import com.anthonymandra.util.DbUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class KeywordProvider extends PathEnumerationProvider
{
    @SuppressWarnings({"WeakerAccess", "unused"})
    public static final class Data implements BaseColumns
    {
        public static final String AUTHORITY = BuildConfig.PROVIDER_AUTHORITY_KEYWORD;
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + Data.KEYWORD);
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.rawdroid.keyword";
        public static final String CONTENT_ID_TYPE = "vnd.android.cursor.item/vnd.rawdroid.keyword";
        public static final String KEYWORD = "keyword";

        public static final String KEYWORD_NAME = "name";

        public static final String KEYWORD_PATH = "path";
        public static final String KEYWORD_DEPTH = "depth";
        public static final String KEYWORD_SYNONYMS = "synonyms";
        public static final String KEYWORD_RECENT = "recent";

        public static final String KEYWORD_PARENT = "parent";

        public static final int COLUMN_ID = 0;
        public static final int COLUMN_NAME = 1;
        public static final int COLUMN_SYNONYMS = 2;
        public static final int COLUMN_RECENT = 3;
        public static final int COLUMN_PATH = 4;
        public static final int COLUMN_DEPTH = 5;
    }

    private static final String KEYWORD_TABLE_NAME = "keyword_table";
    private static final int KEYWORDS = 1;
    private static final int KEYWORD_ID = 2;

    private static final UriMatcher sUriMatcher;

    // Statically construct a uri matcher that can detect URIs referencing
    // more than 1 video, a single video, or a single thumb nail image.
    static
    {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Data.AUTHORITY, Data.KEYWORD, KEYWORDS);
        // use of the hash character indicates matching of an id
        sUriMatcher.addURI(Data.AUTHORITY, Data.KEYWORD + "/#", KEYWORD_ID);
    }

    private static final int DATABASE_VERSION = 5;

    private KeywordDBOpenHelper mDbHelper;

    @Override
    public String getTableName()
    {
        return KEYWORD_TABLE_NAME;
    }

    @Override
    public SQLiteOpenHelper getDbHelper()
    {
        return mDbHelper;
    }

    @Override
    public String getColumnId()
    {
        return Data._ID;
    }

    @Override
    public String getColumnPath()
    {
        return Data.KEYWORD_PATH;
    }

    @Override
    public String getColumnDepth()
    {
        return Data.KEYWORD_DEPTH;
    }

    @Override
    public String getParentId()
    {
        return Data.KEYWORD_PARENT;
    }

    private class KeywordDBOpenHelper extends SQLiteOpenHelper
    {
        KeywordDBOpenHelper(Context context)
        {
            super(context, KEYWORD_TABLE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase)
        {
            createTable(sqLiteDatabase);
        }

        private void createTable(SQLiteDatabase sqLiteDatabase)
        {
            final String createClosureTable =
                    "CREATE TABLE " + KEYWORD_TABLE_NAME + " (" +
                            Data._ID                 + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            Data.KEYWORD_NAME        + " TEXT, " +
                            Data.KEYWORD_SYNONYMS    + " TEXT, " +
                            Data.KEYWORD_RECENT      + " INTEGER, " +
                            Data.KEYWORD_PATH        + " TEXT UNIQUE, " +
                            Data.KEYWORD_DEPTH       + " INTEGER" +
                            ");";
            sqLiteDatabase.execSQL(createClosureTable);
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion)
        {
            sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + KEYWORD_TABLE_NAME + ";");
            createTable(sqLiteDatabase);
        }
    }

    @Override
    public String getType(@NonNull Uri uri)
    {
        switch (sUriMatcher.match(uri))
        {
            case KEYWORDS:
                return Data.CONTENT_TYPE;

            case KEYWORD_ID:
                return Data.CONTENT_ID_TYPE;

            default:
                throw new IllegalArgumentException("Unknown keyword type: " + uri);
        }
    }

    @Override
    public boolean onCreate() {
        mDbHelper = new KeywordDBOpenHelper(getContext());
        return true;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs)
    {
        int match = sUriMatcher.match(uri);
        int affected;

        SQLiteDatabase db = getDbHelper().getWritableDatabase();
        switch (match)
        {
            case KEYWORDS:
                affected = db.delete(KEYWORD_TABLE_NAME, selection, selectionArgs);
                break;
            case KEYWORD_ID:
                long id = ContentUris.parseId(uri);
                affected = db.delete(KEYWORD_TABLE_NAME,
                        BaseColumns._ID + "=" + id + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("unknown keyword element: " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return affected;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs)
    {
        SQLiteDatabase db = getDbHelper().getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri))
        {
            case KEYWORDS:
                count = db.update(KEYWORD_TABLE_NAME, values, selection, selectionArgs);
                break;
            case KEYWORD_ID:
                count = db.update(KEYWORD_TABLE_NAME, values, Data._ID + " = " + uri.getPathSegments().get(1) +
                        (!TextUtils.isEmpty(selection) ? " AND (" +selection + ')' : ""), selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    public static boolean importKeywords(Context context, Reader keywordList)
    {
        // Clear the existing database
        context.getContentResolver().delete(Data.CONTENT_URI, null, null);

        try
        {
            BufferedReader readbuffer = new BufferedReader(keywordList);

            String line;
            SparseArray<Long> parents = new SparseArray<>();
            while ((line = readbuffer.readLine()) != null)
            {
                String tokens[] = line.split("\t");
                int depth = tokens.length - 1;
                String name = tokens[depth];

                // If the entry is a synonym ex: {bread} then trim and add to parent
                if (name.startsWith("{") && name.endsWith("}"))
                {
                    name = name.substring(1, name.length()-1);

                    try (Cursor c = context.getContentResolver().query(
                            Data.CONTENT_URI,
                            null,
                            Data._ID + "=?",
                            new String[]{Long.toString(parents.get(depth - 1))},
                            null))
                    {

                        if (c == null)
                            continue;

                        c.moveToFirst();
                        List<String> synonyms = new ArrayList<>();
                        String[] activeSynonyms = DbUtil.convertStringToArray(
                                c.getString(c.getColumnIndex(Data.KEYWORD_SYNONYMS)));

                        if (activeSynonyms == null)
                            continue;

                        synonyms.addAll(Arrays.asList(activeSynonyms));
                        synonyms.add(name);

                        ContentValues cv = new ContentValues();
                        cv.put(Data.KEYWORD_SYNONYMS, DbUtil.convertArrayToString(
                                synonyms.toArray(new String[synonyms.size()])));
                        cv.put(Data._ID, parents.get(depth - 1));
                        context.getContentResolver().update(
                                ContentUris.withAppendedId(Data.CONTENT_URI, parents.get(depth - 1)),
                                cv,
                                null, null);
                    }
                    continue;
                }

                ContentValues cv = new ContentValues();
                cv.put(Data.KEYWORD_NAME, name);
                Long id = parents.get(depth - 1);
                if (id != null)
                {
                    cv.put(Data.KEYWORD_PARENT, id);
                }

                Uri uri = context.getContentResolver().insert(
                        Data.CONTENT_URI,
                        cv);

                long childId = uri != null ? ContentUris.parseId(uri) : -1;
                parents.put(depth, childId);
            }
        } catch (IOException e)
        {
            return false;
        }
        return true;

    }
}
