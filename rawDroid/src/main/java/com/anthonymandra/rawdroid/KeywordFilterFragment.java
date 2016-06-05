package com.anthonymandra.rawdroid;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
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

public class KeywordFilterFragment extends KeywordBaseFragment
{
    private List<String> mDataSource;
    private ArrayAdapter<String> mAdapter;
    private GridView mGrid;
    private Set<String> mSelectedKeywords = new HashSet<>();

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
        mGrid = (GridView) getView(); // getActivity().findViewById(R.id.keywordGridView);
        mGrid.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                String keyword = mAdapter.getItem(position);
                if (mSelectedKeywords.contains(keyword))
                    mSelectedKeywords.remove(keyword);
                else
                    mSelectedKeywords.add(mAdapter.getItem(position));

                if (mListener != null)
                    mListener.onKeywordsSelected(getSelectedKeywords());

                mAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onResume()
    {
        super.onResume();
        try(Cursor cursor = getActivity().getContentResolver().query(Meta.Data.META_URI,
                new String[] {"DISTINCT " + Meta.Data.SUBJECT},
                null, null, null))
        {
            mDataSource = new ArrayList<>();
            Set<String> uniqueKeywords = new HashSet<>();
            if (cursor != null)
            {
                while (cursor.moveToNext())
                {
                    String all = cursor.getString(0);
                    if (all == null)
                        continue;
                    uniqueKeywords.addAll(Arrays.asList(ImageUtils.convertStringToArray(all)));
                }
            }
            mDataSource.addAll(uniqueKeywords);
        }

        mDataSource.remove(""); //Remove the blank
        Collections.sort(mDataSource);

        mAdapter = new SelectArrayAdapter<>(getActivity(), R.layout.keyword_entry, mDataSource);
        mGrid.setAdapter(mAdapter);
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

    class SelectArrayAdapter<T> extends ArrayAdapter<T>
    {

        public SelectArrayAdapter(Context context, int resource, List<T> objects)
        {
            super(context, resource, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            CheckedTextView v = (CheckedTextView) super.getView(position, convertView, parent);
            v.setChecked(mSelectedKeywords.contains(v.getText()));
            return v;
        }
    }
}
