package com.anthonymandra.framework;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.Nullable;

import java.util.UUID;

public abstract class PathDataSource implements SimpleCursorLoader.CursorDataSource
{
    public abstract String getTableName();
    public abstract SQLiteOpenHelper getDbHelper();
    public abstract String getColumnId();
    public abstract String getColumnPath();
    public abstract String getColumnDepth();

    private static final String PATH_DELIMITER = "/";

    /**
     * Insert a root node
     * @param initialValues
     * @return
     */
    public long insert(ContentValues initialValues)
    {
        return insert(-1, initialValues);
    }

    /**
     * Insert a child node
     * @param parentId row ID of parent
     * @param initialValues
     * @return
     */
    public long insert(long parentId, ContentValues initialValues)
    {
        String parentPath = PATH_DELIMITER;  // Default path defines a root node
        int parentDepth = -1;
        SQLiteDatabase db = getDbHelper().getWritableDatabase();
        if (parentId != -1)
        {
            Cursor parent = db.query(getTableName(),
                    new String[]{getColumnPath(), getColumnDepth()},
                    getColumnId() + " = ?",
                    new String[]{Long.toString(parentId)},
                    null, null, null);

            // If parent lookup succeeds set the path
            if (parent.moveToFirst())
            {
                parentPath = parent.getString(parent.getColumnIndex(getColumnPath())) + PATH_DELIMITER;
                parentDepth = parent.getInt(parent.getColumnIndex(getColumnDepth()));
            }
        }

        // Since the column is unique we must put a unique placeholder
        initialValues.put(getColumnPath(), UUID.randomUUID().toString());

        long childId = db.insert(getTableName(), null, initialValues);
        if (childId == -1)
            return -1;

        // Add the child id to the parent's path
        String childPath = parentPath + childId + PATH_DELIMITER;
        // Update the child entry with its full path
        initialValues.put(getColumnPath(), childPath);
        initialValues.put(getColumnDepth(), parentDepth + 1);
        db.update(getTableName(), initialValues, getColumnId() + " = ?",
                new String[] {Long.toString(childId)});

        return childId;
    }

    public int update(long id, ContentValues cv)
    {
        SQLiteDatabase db = getDbHelper().getWritableDatabase();
        return db.update(getTableName(), cv, getColumnId() + "=?", new String[] { Long.toString(id) });
    }

    public int update(ContentValues cv, String where, String[] whereArgs)
    {
        SQLiteDatabase db = getDbHelper().getWritableDatabase();
        return db.update(getTableName(), cv, where, whereArgs);
    }

    public int delete(long id)
    {
        SQLiteDatabase db = getDbHelper().getWritableDatabase();
        return db.delete(getTableName(), getColumnId() + "=?", new String[]{Long.toString(id)});
    }

    public int delete(String where, String[] whereArgs)
    {
        SQLiteDatabase db = getDbHelper().getWritableDatabase();
        return db.delete(getTableName(), where, whereArgs);
    }

    public Cursor query(@Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String order)
    {
        SQLiteDatabase db = getDbHelper().getReadableDatabase();
        return db.query(getTableName(), projection, selection, selectionArgs, null, null, order);
    }

    public Cursor query()
    {
        return query(null, null, null, null);
    }

    /**
     * Returns a cursor containing all ancestor rows
     * @param id child row id
     * @return
     */
    public Cursor getAncestors(long id)
    {
        SQLiteDatabase db = getDbHelper().getReadableDatabase();
        Cursor child = db.query(getTableName(), null,
                getColumnId() + "=?",
                new String[] { Long.toString(id)},
                null, null, null);

        child.moveToFirst();
        String path = child.getString(child.getColumnIndex(getColumnPath()));
        return db.query(getTableName(), null,
                "? LIKE " + getColumnPath() + " || '%'",
                new String[]{path},
                null, null, null);
    }

    /**
     * Returns a cursor containing all descendant rows
     * @param id parent row id
     * @return
     */
    public Cursor getDescendants(long id)
    {
        SQLiteDatabase db = getDbHelper().getReadableDatabase();
        Cursor parent = db.query(getTableName(), null,
                getColumnId() + "=?",
                new String[] { Long.toString(id)},
                null, null, null);

        parent.moveToFirst();
        String path = parent.getString(parent.getColumnIndex(getColumnPath()));
        return db.query(getTableName(), null,
                getColumnPath() + " LIKE ?" +  " || '%'",
                new String[] {path},
                null, null, null);
    }
}
