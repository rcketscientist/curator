package com.anthonymandra.rawdroid;


import android.support.v4.app.Fragment;

import com.anthonymandra.framework.KeywordDataSource;

import java.util.Collection;

public abstract class KeywordBaseFragment extends Fragment
{
    public interface OnKeywordsSelectedListener
    {
        void onKeywordsSelected(Collection<String> selectedKeywords);
    }

    public static final String ORDER_BY =
            KeywordDataSource.KEYWORD_RECENT    + " DESC, " +   // Order by recent selection
            KeywordDataSource.KEYWORD_DEPTH     + " ASC, "  +   // Order by depth Animal > Dog
            KeywordDataSource.KEYWORD_NAME      + " ASC";       // Order by A > Z

    protected OnKeywordsSelectedListener mListener;

    public abstract Collection<String> getSelectedKeywords();
    public abstract void setSelectedKeywords(Collection<String> selected);
    public abstract void clearSelectedKeywords();

    public void setOnKeywordsSelectedListener(OnKeywordsSelectedListener listener)
    {
        mListener = listener;
    }
}
