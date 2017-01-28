package com.anthonymandra.rawdroid;

import android.annotation.SuppressLint;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Checkable;
import android.widget.CheckedTextView;
import android.widget.GridView;
import android.widget.SimpleCursorAdapter;

import com.anthonymandra.content.KeywordProvider;
import com.anthonymandra.framework.PathEnumerationProvider;
import com.anthonymandra.util.DbUtil;
import com.crashlytics.android.Crashlytics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class

KeywordEditFragment extends KeywordBaseFragment implements LoaderManager.LoaderCallbacks<Cursor>
{
    private static final int KEYWORD_LOADER_ID = 1;
    private SimpleCursorAdapter mAdapter;
    @SuppressLint("UseSparseArrays")
    private final Set<String> mSelectedKeywords = new HashSet<>();

    private boolean mPauseListener;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.xmp_subject_edit, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(KEYWORD_LOADER_ID, null, this);

        mAdapter = new SelectCursorAdapter(getActivity());
        GridView mGrid = (GridView) getView().findViewById(R.id.keywordGridView);
        mGrid.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                if (mPauseListener)
                    return;

                // TODO: This will drastically affect the code
//                boolean checked = ((CheckedTextView) mAdapter.getItem(position)).isChecked();

                mPauseListener = true;

                final Cursor keywords = mAdapter.getCursor();
                if (((Checkable)view).isChecked())
                {
                    try (Cursor selectedTree = getActivity().getContentResolver().query(
                            ContentUris.withAppendedId(KeywordProvider.Data.CONTENT_URI, id),
                            null,
                            PathEnumerationProvider.DESCENDANTS_QUERY_SELECTION,
                            null, null))
                    {
                        while (selectedTree != null && selectedTree.moveToNext())
                        {
                            mSelectedKeywords.remove(selectedTree.getString(KeywordProvider.Data.COLUMN_NAME));
                        }
                        mAdapter.notifyDataSetChanged();
                    }
                }
                else
                {
                    long time = System.currentTimeMillis(); // We all ancestors of single selection to share insert time
                    try (Cursor selectedTree = getActivity().getContentResolver().query(
                            ContentUris.withAppendedId(KeywordProvider.Data.CONTENT_URI, id),
                            null,
                            PathEnumerationProvider.ANCESTORS_QUERY_SELECTION,
                            null, null))
                    {
                        if (selectedTree != null)
                        {
                            while (selectedTree.moveToNext())
                            {
                                keywords.moveToPosition(-1);
                                long selectedId = selectedTree.getLong(KeywordProvider.Data.COLUMN_ID);
                                while (keywords.moveToNext())
                                {
                                    if (keywords.getLong(KeywordProvider.Data.COLUMN_ID) == selectedId)
                                    {
                                        mSelectedKeywords.add(keywords.getString(KeywordProvider.Data.COLUMN_NAME));
                                        ContentValues cv = new ContentValues();
                                        cv.put(KeywordProvider.Data.KEYWORD_RECENT, time);
                                        getActivity().getContentResolver().update(
                                                ContentUris.withAppendedId(KeywordProvider.Data.CONTENT_URI, selectedId),
                                                cv,
                                                null, null);
                                    }
                                }
                            }
                        }
                    }
                }
                mPauseListener = false;

                if (mListener != null)
                    mListener.onKeywordsSelected(getSelectedKeywords());
            }
        });
        mGrid.setAdapter(mAdapter);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args)
    {
        /*
		 * Takes action based on the ID of the Loader that's being created
		 */
        switch (id) {
            case KEYWORD_LOADER_ID:

                // Returns a new CursorLoader
                return new CursorLoader(
                        getActivity(),      	            // Parent activity context
                        KeywordProvider.Data.CONTENT_URI,   // Table to query
                        null,				                // Projection to return
                        null,       		                // No selection clause
                        null, 			                    // No selection arguments
                        ORDER_BY                            // Default sort order
                );
            default:
                // An invalid id was passed in
                return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data)
    {
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader)
    {
        mAdapter.swapCursor(null);
    }

    public Collection<String> getSelectedKeywords()
    {
        return mSelectedKeywords;
    }

    public void setSelectedKeywords(Collection<String> selected)
    {
        if (selected == null)
        {
            clearSelectedKeywords();
            return;
        }

        // If a meta lookup is threaded it could try to set keywords AFTER a user navigated away
        if (getActivity() == null)
            return;

        long time = System.currentTimeMillis(); //Keep the time the same for these entries
        mSelectedKeywords.clear();

        final ArrayList<ContentProviderOperation> updateRecent = new ArrayList<>();

        try(Cursor c = getActivity().getContentResolver().query(
                KeywordProvider.Data.CONTENT_URI,
                null,
                DbUtil.createMultipleIN(KeywordProvider.Data.KEYWORD_NAME, selected.size()),
                selected.toArray(new String[selected.size()]),
                null))
        {
            while (c != null && c.moveToNext())
            {
                long id = c.getLong(KeywordProvider.Data.COLUMN_ID);
                ContentValues cv = new ContentValues();
                cv.put(KeywordProvider.Data.KEYWORD_RECENT, time);

                updateRecent.add(ContentProviderOperation.newUpdate(ContentUris.withAppendedId(KeywordProvider.Data.CONTENT_URI, id))
                        .withValues(cv)
                        .build());

                mSelectedKeywords.add(c.getString(KeywordProvider.Data.COLUMN_NAME));
            }
        }

        if (updateRecent.size() > 0)
        {
            new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        getActivity().getContentResolver().applyBatch(KeywordProvider.Data.AUTHORITY, updateRecent);
                    }
                    catch (RemoteException | OperationApplicationException e)
                    {
                        Crashlytics.logException(
                                new Exception("KeywordEditFragment: setSelected applyBatch error", e));
                    }
                }
            }).start();
        }
    }

    public void clearSelectedKeywords()
    {
        mSelectedKeywords.clear();
        mAdapter.notifyDataSetChanged();
    }

    private class SelectCursorAdapter extends SimpleCursorAdapter
    {
        SelectCursorAdapter(Context context)
        {
            super(context, R.layout.keyword_entry, null,
                    new String[] { KeywordProvider.Data.KEYWORD_NAME },
                    new int[] { R.id.keyword_entry }, 0);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor)
        {
            super.bindView(view, context, cursor);
            int position = cursor.getPosition();

            if (position % 2 == 0)
                view.getBackground().setAlpha(255);
            else
                view.getBackground().setAlpha(230);

            CheckedTextView v = (CheckedTextView) view;
            //noinspection SuspiciousMethodCalls
            v.setChecked(mSelectedKeywords.contains(v.getText()));
        }


    }
}
