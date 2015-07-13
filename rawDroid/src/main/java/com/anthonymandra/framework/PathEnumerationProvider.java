package com.anthonymandra.framework;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

import com.anthonymandra.content.Meta;

import java.util.UUID;

public abstract class PathEnumerationProvider extends ContentProvider
{
    public abstract String getTableName();
    public abstract SQLiteOpenHelper getDbHelper();
    public abstract String getColumnId();
    public abstract String getColumnPath();
    public abstract String getColumnDepth();
    public abstract String getParentId();

    public static final String ANCESTORS_QUERY_SELECTION = "ancestors";
    public static final String DESCENDANTS_QUERY_SELECTION = "descendants";

    private static final String PATH_DELIMITER = "/";

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
    {
        if (ANCESTORS_QUERY_SELECTION.equals(selection))
        {
            return getAncestors(ContentUris.parseId(uri));
        }
        else if (DESCENDANTS_QUERY_SELECTION.equals(selection))
        {
            return getDescendants(ContentUris.parseId(uri));
        }
        else
        {
            SQLiteDatabase db = getDbHelper().getReadableDatabase();
            return db.query(getTableName(), projection, selection, selectionArgs, null, null, sortOrder);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues)
    {
        String parentPath = PATH_DELIMITER;  // Default path defines a root node
        int parentDepth = -1;
        SQLiteDatabase db = getDbHelper().getWritableDatabase();
        if (initialValues.containsKey(getParentId()))
        {
            Cursor parent = db.query(getTableName(),
                    new String[]{getColumnPath(), getColumnDepth()},
                    getColumnId() + " = ?",
                    new String[]{initialValues.getAsString(getParentId())},
                    null, null, null);

            initialValues.remove(getParentId());    // Remove the id since it's not an actual column

            // If parent lookup succeeds set the path
            if (parent.moveToFirst())
            {
                parentPath = parent.getString(parent.getColumnIndex(getColumnPath())) + PATH_DELIMITER;
                parentDepth = parent.getInt(parent.getColumnIndex(getColumnDepth()));
            }
            parent.close();
        }

        // Since the column is unique we must put a unique placeholder
        initialValues.put(getColumnPath(), UUID.randomUUID().toString());

        long childId = db.insert(getTableName(), null, initialValues);
        if (childId == -1)
            return null;

        // Add the child id to the parent's path
        String childPath = parentPath + childId + PATH_DELIMITER;
        // Update the child entry with its full path
        initialValues.put(getColumnPath(), childPath);
        initialValues.put(getColumnDepth(), parentDepth + 1);
        int rowsAffected = db.update(getTableName(), initialValues, getColumnId() + " = ?",
                new String[] {Long.toString(childId)});

        if (rowsAffected > 0)
        {
            Uri metaUri = ContentUris.withAppendedId(Meta.Data.CONTENT_URI, childId);
            getContext().getContentResolver().notifyChange(metaUri, null);
            return metaUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    /**
     * Returns a cursor containing all ancestor rows
     * @param id child row id
     * @return
     */
    protected Cursor getAncestors(long id)
    {
        SQLiteDatabase db = getDbHelper().getReadableDatabase();
        Cursor child = db.query(getTableName(), null,
                getColumnId() + "=?",
                new String[] { Long.toString(id)},
                null, null, null);

        child.moveToFirst();
        String path = child.getString(child.getColumnIndex(getColumnPath()));
        child.close();

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
    protected Cursor getDescendants(long id)
    {
        SQLiteDatabase db = getDbHelper().getReadableDatabase();
        Cursor parent = db.query(getTableName(), null,
                getColumnId() + "=?",
                new String[] { Long.toString(id)},
                null, null, null);

        parent.moveToFirst();
        String path = parent.getString(parent.getColumnIndex(getColumnPath()));
        parent.close();

        return db.query(getTableName(), null,
                getColumnPath() + " LIKE ?" +  " || '%'",
                new String[] {path},
                null, null, null);
    }
}
