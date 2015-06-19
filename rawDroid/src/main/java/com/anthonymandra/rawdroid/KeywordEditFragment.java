package com.anthonymandra.rawdroid;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Checkable;
import android.widget.GridView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import com.android.gallery3d.common.Utils;
import com.anthonymandra.content.KeywordProvider;
import com.anthonymandra.framework.PathEnumerationProvider;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class KeywordEditFragment extends KeywordBaseFragment implements LoaderManager.LoaderCallbacks<Cursor>
{
    private static final int KEYWORD_LOADER_ID = 1;
//    private KeywordDataSource mDataSource;
    private SimpleCursorAdapter mAdapter;
    private GridView mGrid;
    private Map<Long, String> mSelectedKeywords = new HashMap<>();

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
//        mDataSource = new KeywordDataSource(getActivity());
        getLoaderManager().initLoader(KEYWORD_LOADER_ID, null, this);
        String[] from = new String[] { KeywordProvider.Data.KEYWORD_NAME };
        int[] to = new int[] { R.id.keyword_entry };
        mAdapter = new SelectCursorAdapter(getActivity(), R.layout.keyword_entry, null,
                from, to, 0);
        mGrid = (GridView) getView().findViewById(R.id.keywordGridView);
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

                Cursor selectedTree = null;
                try
                {
                    if (((Checkable)view).isChecked())
                    {
                        selectedTree = getActivity().getContentResolver().query(
                                ContentUris.withAppendedId(KeywordProvider.Data.CONTENT_URI, id),
                                null,
                                PathEnumerationProvider.DESCENDANTS_QUERY_SELECTION,
                                null, null);
                        while(selectedTree.moveToNext())
                        {
                            mSelectedKeywords.remove(selectedTree.getLong(KeywordProvider.Data.COLUMN_ID));
                        }
                        mAdapter.notifyDataSetChanged();
                    }
                    else
                    {
                        long time = System.currentTimeMillis(); // We all ancestors of single selection to share insert time
                        selectedTree = getActivity().getContentResolver().query(
                                ContentUris.withAppendedId(KeywordProvider.Data.CONTENT_URI, id),
                                null,
                                PathEnumerationProvider.ANCESTORS_QUERY_SELECTION,
                                null, null);
                        Cursor keywords = mAdapter.getCursor();
                        while (selectedTree.moveToNext())
                        {
                            keywords.moveToPosition(-1);
                            long selectedId = selectedTree.getLong(KeywordProvider.Data.COLUMN_ID);
                            while (keywords.moveToNext())
                            {
                                if (keywords.getLong(KeywordProvider.Data.COLUMN_ID) == selectedId)
                                {
                                    mSelectedKeywords.put(selectedId, keywords.getString(KeywordProvider.Data.COLUMN_NAME));
                                    ContentValues cv = new ContentValues();
                                    cv.put(KeywordProvider.Data.KEYWORD_RECENT, time);
                                    getActivity().getContentResolver().update(
                                            ContentUris.withAppendedId(KeywordProvider.Data.CONTENT_URI, selectedId),
                                            cv,
                                            null, null);
                                }
                            }
                        }
//                        getLoaderManager().restartLoader(KEYWORD_LOADER_ID, null, KeywordEditFragment.this);
                    }
                }
                finally
                {
                    Utils.closeSilently(selectedTree);
                }

                mPauseListener = false;

                if (mListener != null)
                    mListener.onKeywordsSelected(getSelectedKeywords());
            }
        });
        mGrid.setAdapter(mAdapter);

        //TODO: Do I still want a generic keyword set?
//        if (getActivity().getContentResolver()..getCount() < 1)
//            generateKeywordTemplate();
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
        return mSelectedKeywords.values();
    }

    public void setSelectedKeywords(Collection<String> selected)
    {
        if (selected == null)
        {
            clearSelectedKeywords();
        }
        else
        {
            long time = System.currentTimeMillis(); //Keep the time the same for these entries
            for (String select : selected)
            {
                Cursor c = getActivity().getContentResolver().query(
                        KeywordProvider.Data.CONTENT_URI,
                        null,
                        KeywordProvider.Data.KEYWORD_NAME + " = ?",
                        new String[]{select},
                        null);
                if (c.moveToFirst())
                {
                    long id = c.getLong(KeywordProvider.Data.COLUMN_ID);
                    ContentValues cv = new ContentValues();
                    cv.put(KeywordProvider.Data.KEYWORD_RECENT, time);
                    int rowsAffected = getActivity().getContentResolver().update(
                            ContentUris.withAppendedId(KeywordProvider.Data.CONTENT_URI, id),
                            cv,
                            null, null);    //TODO: Might want to make this async
                    mSelectedKeywords.put(id, select);
                } else
                {
                    //TODO: Offer to add the keywords
                }
            }
        }
        mAdapter.notifyDataSetChanged();
    }

    public void clearSelectedKeywords()
    {
        mSelectedKeywords.clear();
        mAdapter.notifyDataSetChanged();
    }

    public boolean importKeywords(Reader keywordList)
    {
        return KeywordProvider.importKeywords(getActivity(), keywordList);
    }

    class SelectCursorAdapter extends SimpleCursorAdapter
    {

        public SelectCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags)
        {
            super(context, layout, c, from, to, flags);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor)
        {
            super.bindView(view, context, cursor);
            long id = cursor.getLong(KeywordProvider.Data.COLUMN_ID);
            ((Checkable) view).setChecked(mSelectedKeywords.get(id) != null);
        }
    }

    private void generateKeywordTemplate()
    {
        File keywords = new File(getActivity().getFilesDir().getAbsolutePath(), "keywords.txt");
        if (!keywords.exists())
        {
            Toast.makeText(getActivity(), "Keywords file not found.  Generic created for testing.  Import in options.", Toast.LENGTH_LONG).show();
            try
            {
                BufferedWriter bw = new BufferedWriter(new FileWriter(keywords));
                bw.write("Europe");
                bw.newLine();
                bw.write("\tFrance");
                bw.newLine();
                bw.write("\tItaly");
                bw.newLine();
                bw.write("\t\tRome");
                bw.newLine();
                bw.write("\t\tVenice");
                bw.newLine();
                bw.write("\tGermany");
                bw.newLine();
                bw.write("South America");
                bw.newLine();
                bw.write("\tBrazil");
                bw.newLine();
                bw.write("\tChile");
                bw.newLine();
                bw.write("United States");
                bw.newLine();
                bw.write("\tNew Jersey");
                bw.newLine();
                bw.write("\t\tTrenton");
                bw.newLine();
                bw.write("\tVirginia");
                bw.newLine();
                bw.write("\t\tRichmond");
                bw.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        try
        {
            importKeywords(new FileReader(keywords));
        } catch (FileNotFoundException e)
        {
            // Do nothing
        }

        mAdapter.notifyDataSetChanged();
    }
}
