package com.anthonymandra.rawdroid;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class XmpFilterFragment extends XmpBaseFragment
{
    private XmpFilterChangedListener mListener;
    public interface XmpFilterChangedListener
    {
        void onXmpFilterChanged(XmpValues xmp);
    }

    /**
     * Used to control updating of the listener to avoid updates during creation, or
     * multiple updates during bulk changes.
     */
    private boolean mPauseListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        mPauseListener = true;
        return inflater.inflate(R.layout.xmp_filter_landscape, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        setMultiselect(true);
        attachButtons();
    }

    @Override
    public void onStart()
    {
        super.onStart();
        mPauseListener = false;
    }

    private void attachButtons()
    {
        getActivity().findViewById(R.id.clearFilterButton).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                mPauseListener = true;
                clear();
                mPauseListener = false;
                mListener.onXmpFilterChanged(getXmp());
            }
        });
    }

    public void registerXmpFilterChangedListener(XmpFilterChangedListener listener)
    {
        mListener = listener;
    }

    public void unregisterXmpFilterChangedListener()
    {
        mListener = null;
    }

    @Override
    protected void onXmpChanged(XmpValues xmp)
    {
        super.onXmpChanged(xmp);
        if (mListener != null && !mPauseListener)
        {
            mListener.onXmpFilterChanged(xmp);
        }
    }
}
