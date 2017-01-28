package com.anthonymandra.rawdroid;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.GridView;

import com.anthonymandra.content.Meta;
import com.anthonymandra.util.ImageUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class KeywordFilterFragment extends KeywordBaseFragment implements LoaderManager.LoaderCallbacks<Cursor>
{
    private final static int LOADER_ID = 1;
    private final static String[] PROJECTION = new String[] {"DISTINCT " + Meta.SUBJECT};
    private final static String SELECTION = Meta.SUBJECT + " is not null and " + Meta.SUBJECT + " is not ?";
    private final static String[] ARGUMENTS = new String[] {""};

    private SelectArrayAdapter<String> mAdapter;
    private Set<String> mSelectedKeywords = new TreeSet<>();

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.xmp_subject, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        GridView mGrid = (GridView) getView().findViewById(R.id.keywordGridView);
        mAdapter = new SelectArrayAdapter<>(getActivity(), R.layout.keyword_entry);
        mGrid.setAdapter(mAdapter);

        mGrid.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                String keyword = mAdapter.getItem(position);
                if (mSelectedKeywords.contains(keyword))
                    mSelectedKeywords.remove(keyword);
                else
                    mSelectedKeywords.add(keyword);

                if (mListener != null)
                    mListener.onKeywordsSelected(getSelectedKeywords());

                mAdapter.notifyDataSetChanged();
            }
        });

        getLoaderManager().initLoader(LOADER_ID, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args)
    {
        /*
		 * Takes action based on the ID of the Loader that's being created
		 */
        switch (id) {
            case LOADER_ID:

                // Returns a new CursorLoader
                return new CursorLoader(
                        getActivity(),      	    // Parent activity context
                        Meta.CONTENT_URI,           // Table to query
                        PROJECTION,	                // Projection to return
                        SELECTION,       		    // No selection clause
                        ARGUMENTS, 			        // No selection arguments
                        Meta.SUBJECT + " ASC"       // Default sort order
                );
            default:
                // An invalid id was passed in
                return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor)
    {
        updateKeywords(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader)
    {
        // Do nothing.
    }

    private void updateKeywords(Cursor cursor)
    {
        Set<String> uniqueKeywords = new HashSet<>();
        if (cursor != null)
        {
            while (cursor.moveToNext())
            {
                String all = cursor.getString(0);
                uniqueKeywords.addAll(Arrays.asList(ImageUtils.convertStringToArray(all)));
            }
        }

        mAdapter.clear();
        mAdapter.addAll(uniqueKeywords);
    }

    public Collection<String> getSelectedKeywords()
    {
        return mSelectedKeywords;
    }

    @Override
    public void setSelectedKeywords(Collection<String> selected)
    {
        mSelectedKeywords.addAll(selected);
    }

    public void clearSelectedKeywords()
    {
        mSelectedKeywords.clear();
        mAdapter.notifyDataSetChanged();
    }

    private class SelectArrayAdapter<T> extends ArrayAdapter<T>
    {
        public SelectArrayAdapter(@NonNull Context context, @LayoutRes int resource)
        {
            super(context, resource);
        }

        public SelectArrayAdapter(Context context, int resource, List<T> objects)
        {
            super(context, resource, objects);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent)
        {
            CheckedTextView v = (CheckedTextView) super.getView(position, convertView, parent);

            if (position % 2 == 0)
                v.getBackground().setAlpha(255);
            else
                v.getBackground().setAlpha(230);

            v.setChecked(mSelectedKeywords.contains(v.getText()));
            return v;
        }
    }
}
