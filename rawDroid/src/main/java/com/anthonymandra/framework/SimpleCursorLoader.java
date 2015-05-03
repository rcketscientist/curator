package com.anthonymandra.framework;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.content.AsyncTaskLoader;

public class SimpleCursorLoader extends AsyncTaskLoader<Cursor>
{
    public interface CursorDataSource
    {
        Cursor query(String[] projection, String selection, String[] selectionArgs, String sortOrder);
    }

    protected final ForceLoadContentObserver mObserver;

    CursorDataSource mDataSource;
    String[] mProjection;
    String mSelection;
    String[] mSelectionArgs;
    String mSortOrder;

    private Cursor mCursor;

    public SimpleCursorLoader(Context context)
    {
        super(context);
        mObserver = new ForceLoadContentObserver();
    }

    /**
     * Creates a fully-specified CursorLoader.  See
     * {@link ContentResolver#query(Uri, String[], String, String[], String)
     * ContentResolver.query()} for documentation on the meaning of the
     * parameters.  These will be passed as-is to that call.
     */
    public SimpleCursorLoader(Context context, CursorDataSource dataSource, String[] projection,
                              String selection, String[] selectionArgs, String sortOrder) {
        super(context);
        mObserver = new ForceLoadContentObserver();
        mDataSource = dataSource;
        mProjection = projection;
        mSelection = selection;
        mSelectionArgs = selectionArgs;
        mSortOrder = sortOrder;
    }

    @Override
    public Cursor loadInBackground()
    {
        Cursor cursor = mDataSource.query(null, null, null, mSortOrder);
        if (cursor != null) {
            // Ensure the cursor window is filled.
            cursor.getCount();
            cursor.registerContentObserver(mObserver);
        }
        return cursor;
    }

    /* Runs on the UI thread */
    @Override
    public void deliverResult(Cursor cursor)
    {
        if (isReset())
        {
            // An async query came in while the loader is stopped
            if (cursor != null)
            {
                cursor.close();
            }
            return;
        }
        Cursor oldCursor = mCursor;
        mCursor = cursor;

        if (isStarted())
        {
            super.deliverResult(cursor);
        }

        if (oldCursor != null && oldCursor != cursor && !oldCursor.isClosed())
        {
            oldCursor.close();
        }
    }

    /**
     * Starts an asynchronous load of the contacts list data. When the result is ready the callbacks
     * will be called on the UI thread. If a previous load has been completed and is still valid
     * the result may be passed to the callbacks immediately.
     * <p>
     * Must be called from the UI thread
     */
    @Override
    protected void onStartLoading()
    {
        if (mCursor != null)
        {
            deliverResult(mCursor);
        }
        if (takeContentChanged() || mCursor == null)
        {
            forceLoad();
        }
    }

    /**
     * Must be called from the UI thread
     */
    @Override
    protected void onStopLoading()
    {
        // Attempt to cancel the current load task if possible.
        cancelLoad();
    }

    @Override
    public void onCanceled(Cursor cursor)
    {
        if (cursor != null && !cursor.isClosed())
        {
            cursor.close();
        }
    }

    @Override
    protected void onReset()
    {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();

        if (mCursor != null && !mCursor.isClosed())
        {
            mCursor.close();
        }
        mCursor = null;
    }
}



