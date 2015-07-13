package com.anthonymandra.framework;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.SparseArray;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class KeywordDataSource extends PathDataSource
{
    private static final int DATABASE_VERSION = 4;

    public static final String KEYWORD_TABLE_NAME = "keyword_table";
    public static final String KEYWORD_NAME = "name";
    public static final String KEYWORD_ID = BaseColumns._ID;
    public static final String KEYWORD_PATH = "path";
    public static final String KEYWORD_DEPTH = "depth";
    public static final String KEYWORD_SYNONYMS = "synonyms";
    public static final String KEYWORD_RECENT = "recent";

    public static final int COLUMN_ID = 0;
    public static final int COLUMN_NAME = 1;
    public static final int COLUMN_SYNONYMS = 2;
    public static final int COLUMN_RECENT = 3;
    public static final int COLUMN_PATH = 4;
    public static final int COLUMN_DEPTH = 5;

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
        return KEYWORD_ID;
    }

    @Override
    public String getColumnPath()
    {
        return KEYWORD_PATH;
    }

    @Override
    public String getColumnDepth()
    {
        return KEYWORD_DEPTH;
    }

    private class KeywordDBOpenHelper extends SQLiteOpenHelper
    {
        public KeywordDBOpenHelper(Context context)
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
                            KEYWORD_ID          + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            KEYWORD_NAME        + " TEXT, " +
                            KEYWORD_SYNONYMS    + " TEXT, " +
                            KEYWORD_RECENT      + " INTEGER, " +
                            KEYWORD_PATH        + " TEXT UNIQUE, " +
                            KEYWORD_DEPTH       + " INTEGER" +
                            ");";
            sqLiteDatabase.execSQL(createClosureTable);
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldv, int newv)
        {
            sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + KEYWORD_TABLE_NAME + ";");
            createTable(sqLiteDatabase);
        }
    }

    public KeywordDataSource(Context c)
    {
        mDbHelper = new KeywordDBOpenHelper(c);
    }

    public long getCount()
    {
        return DatabaseUtils.queryNumEntries(mDbHelper.getReadableDatabase(), getTableName());
    }

    public boolean importKeywords(Reader keywordList)
    {
        SQLiteDatabase db = getDbHelper().getWritableDatabase();
        db.delete(getTableName(), null, null);

        try
        {
            BufferedReader readbuffer = new BufferedReader(keywordList);

            String line;
            SparseArray<Long> parents = new SparseArray<>();
            parents.put(-1, -1L);
            while ((line = readbuffer.readLine()) != null)
            {
                String tokens[] = line.split("\t");
                int depth = tokens.length - 1;
                String name = tokens[depth];

                // If the entry is a synonym ex: {bread} then trim and add to parent
                if (name.startsWith("{") && name.endsWith("}"))
                {
                    name = name.substring(1, name.length()-2);

                    Cursor c = db.query(KEYWORD_TABLE_NAME, null,
                            KEYWORD_ID + "=?",
                            new String[] {Long.toString(parents.get(depth - 1))},
                            null, null,null);
                    c.moveToFirst();
                    List<String> synonyms = new ArrayList<>();
                    String[] activeSynonyms = ImageUtils.convertStringToArray(c.getString(c.getColumnIndex(KEYWORD_SYNONYMS)));
                    c.close();

                    if (activeSynonyms == null)
                        continue;

                    for (String synonym : activeSynonyms)
                        synonyms.add(synonym);
                    synonyms.add(name);

                    ContentValues cv = new ContentValues();
                    cv.put(KEYWORD_SYNONYMS, ImageUtils.convertArrayToString(synonyms.toArray(new String[synonyms.size()])));
                    cv.put(KEYWORD_ID, parents.get(depth - 1));
                    update(parents.get(depth - 1), cv);
                    continue;
                }

                ContentValues cv = new ContentValues();
                cv.put(KEYWORD_NAME, name);

                long childId = insert(parents.get(depth - 1), cv);
                parents.put(depth, childId);
            }
        } catch (IOException e)
        {
            return false;
        }
        return true;
    }
}
